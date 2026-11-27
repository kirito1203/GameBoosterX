// Painel individual do Grande Sábio. Aberto pelo botão GRANDE SÁBIO do menu principal e pelo
// item Núcleo do Grande Sábio: as duas entradas chamam openSage() e usam os mesmos dados (d.sage).
import { system, world } from '@minecraft/server';
import { ModalFormData, MessageFormData } from '@minecraft/server-ui';
import { CONFIG } from '../config.js';
import { SKILLS } from '../data/registry.js';
import { ask, normalize } from '../core/sageAsk.js';
import { buildSageForm, formatCreature, formatItem, formatSkill, FREQ } from '../core/sageView.js';
import { hasCapability } from '../core/sage.js';
import { getData, progressionState, saveNow } from '../systems/storage.js';
import {
  award, recordAnalysis, analyzeHeld, targetOf, readEntity, evolveSage, manifest, sageSound,
} from '../systems/sage.js';
import { showForm, formOf } from './treeUI.js';

const EXAMPLES = [
  'Qual é minha raça atual?', 'Quais habilidades posso aprender?', 'Qual é meu nível atual?', 'Quais são minhas fraquezas?',
  'Como posso evoluir?', 'Quais magias posso desbloquear?', 'Quais habilidades podem ser combinadas?',
  'Qual é o nível de ameaça desta criatura?', 'Quais requisitos faltam para minha próxima evolução?',
  'Quais capacidades você possui atualmente?', 'Qual é o seu nível?', 'O que você recomenda?',
];
const openFor = new Set();
const lastTarget = new Map(); // player.id → última criatura analisada (para perguntas de ameaça)

function day() {
  try { return world.getDay(); } catch { return 0; }
}

/** Responde uma pergunta, grava no histórico (se ligado) e dá XP (limite diário). */
function answer(player, question) {
  const d = getData(player);
  const s = d.sage;
  const t = targetOf(player, 24);
  const target = t ? readEntity(t) : lastTarget.get(player.id) ?? null;
  const r = ask(question, { d, sage: s, state: progressionState(d), target });
  const a = r.text.replace(/§./g, '');
  s.qa.push({ q: question.slice(0, 120), a: a.slice(0, 600), day: day() });
  if (s.qa.length > CONFIG.sage.historyLimit) s.qa.splice(0, s.qa.length - CONFIG.sage.historyLimit);
  d.sageDirty = true;
  if (r.intent !== 'unknown' && r.intent !== 'empty') award(player, 'question', { daily: 'question' });
  sageSound(player, r.intent === 'unknown' ? 'note.bass' : 'note.pling', r.intent === 'unknown' ? 0.8 : 1.6);
  saveNow(player);
  return r;
}

async function askForm(player) {
  const f = new ModalFormData().title('§lPerguntar ao Grande Sábio')
    .textField('Escreva sua pergunta:', 'Ex.: Como posso evoluir?', { defaultValue: '' })
    .submitButton('ENVIAR');
  const res = await showForm(player, f);
  if (!res || res.canceled) return null;
  const q = String(res.formValues?.[0] ?? '').trim();
  if (!normalize(q)) return '§7Nenhuma pergunta enviada.';
  answer(player, q);
  return '';
}

async function examplesForm(player) {
  const f = new ModalFormData().title('§lExemplos de perguntas').dropdown('Escolha uma pergunta:', EXAMPLES, { defaultValueIndex: 0 }).submitButton('ENVIAR');
  const res = await showForm(player, f);
  if (!res || res.canceled) return null;
  answer(player, EXAMPLES[Number(res.formValues?.[0] ?? 0)]);
  return '';
}

async function confirm(player, text) {
  const f = new MessageFormData().title('§lGrande Sábio').body(text).button1('Cancelar').button2('§cConfirmar');
  const res = await showForm(player, f);
  return !!res && !res.canceled && res.selection === 1;
}

async function skillForm(player) {
  const f = new ModalFormData().title('§lAnalisar habilidade ou magia').textField('Nome da habilidade ou magia:', 'Ex.: Bola de Fogo').submitButton('ANALISAR');
  const res = await showForm(player, f);
  if (!res || res.canceled) return null;
  const q = normalize(String(res.formValues?.[0] ?? ''));
  if (!q) return '§7Nenhum nome informado.';
  const all = Object.values(SKILLS).filter((s) => s.type !== 'race_root');
  const hit = all.find((s) => normalize(s.name) === q || normalize(s.original) === q)
    ?? all.find((s) => normalize(s.name).includes(q) || normalize(s.original).includes(q));
  if (!hit) return `§cNão encontrei "${q}" entre as ${all.length} habilidades do addon. Dados insuficientes.`;
  sageSound(player, 'beacon.power', 1.8);
  const d = getData(player);
  return `${formatSkill(hit)}\n${d.unlocked.has(hit.id) ? '§aVocê já possui esta habilidade.' : '§7Você ainda não possui esta habilidade.'}`;
}

async function settingsForm(player) {
  const d = getData(player);
  const st = d.sage.settings;
  const freqKeys = Object.keys(FREQ);
  const f = new ModalFormData().title('§lConfigurações do Grande Sábio')
    .toggle('Efeitos sonoros', { defaultValue: st.sound })
    .toggle('Notificações', { defaultValue: st.notify })
    .dropdown('Frequência das notificações', freqKeys.map((k) => FREQ[k]), { defaultValueIndex: Math.max(0, freqKeys.indexOf(st.frequency)) })
    .toggle('Salvar histórico de conversas', { defaultValue: st.saveQa })
    .submitButton('Salvar');
  const res = await showForm(player, f);
  if (!res || res.canceled || !res.formValues) return null;
  const [sound, notify, freq, saveQa] = res.formValues;
  Object.assign(st, { sound: !!sound, notify: !!notify, frequency: freqKeys[Number(freq)] ?? 'normal', saveQa: !!saveQa });
  d.sageDirty = true;
  saveNow(player);
  return '§aConfigurações salvas.';
}

function itemsText(d) {
  const list = Object.entries(d.sage.items);
  if (!list.length) return '§7Nenhum item analisado ainda.';
  return ['§lItens analisados:§r', ...list.map(([id, it]) => `• ${it.name} §8(${id})§r — ${it.category}, ${it.count}x`)].join('\n');
}

/**
 * Abre o painel. Retorna 'back' (voltar ao menu principal) ou 'close'.
 * @param {import('@minecraft/server').Player} player
 * @param {string} [startCat]
 */
export async function openSage(player, startCat = 'ask', fromMenu = false) {
  if (openFor.has(player.id)) return 'close';
  const d0 = getData(player);
  if (!d0.race) { player.sendMessage('§7Escolha uma raça antes de consultar o Grande Sábio.'); return 'close'; }
  openFor.add(player.id);
  let cat = startCat;
  let page = 0;
  let note = '';
  let result = 'close';
  try {
    for (;;) {
      const d = getData(player);
      const model = buildSageForm({ cat, d, sage: d.sage, page, note });
      note = '';
      const res = await showForm(player, formOf(model));
      if (!player.isValid) return 'close';
      if (!res || res.canceled || res.selection === undefined) break;
      const act = model.hit[res.selection];
      if (!act) continue;
      if (act.type === 'close') break;
      if (act.type === 'back') { result = fromMenu ? 'back' : 'close'; break; }
      switch (act.type) {
        case 'cat': cat = act.cat; page = 0; sageSound(player, 'random.click', 1.4); break;
        case 'page': page = act.page; break;
        case 'ask': { const r = await askForm(player); if (r) note = r; break; }
        case 'examples': { const r = await examplesForm(player); if (r) note = r; break; }
        case 'clear':
          if (await confirm(player, 'Apagar todo o histórico de conversas? Esta ação não pode ser desfeita.')) {
            d.sage.qa = []; d.sageDirty = true; saveNow(player); note = '§aHistórico de conversas apagado.';
          }
          break;
        case 'analyze_target': {
          const t = targetOf(player, hasCapability(d.sage, 'adv_analysis') ? 48 : 24);
          if (!t) { note = '§cNenhuma criatura na mira. Feche o painel, mire numa criatura (até 24 blocos) e abra de novo.'; break; }
          const rec = recordAnalysis(player, t);
          if (rec) { lastTarget.set(player.id, rec); note = formatCreature(rec, d.sage); }
          saveNow(player);
          break;
        }
        case 'analyze_item': { const r = analyzeHeld(player); note = r.error ? `§c${r.error}` : formatItem(r.rec); saveNow(player); break; }
        case 'analyze_skill': { const r = await skillForm(player); if (r) note = r; break; }
        case 'items': note = itemsText(d); break;
        case 'settings': { const r = await settingsForm(player); if (r) note = r; break; }
        case 'evolve': {
          openFor.delete(player.id);
          await evolveSage(player);
          openFor.add(player.id);
          cat = 'level';
          break;
        }
        case 'manifest':
          // A manifestação acontece no mundo: fecha o painel para o jogador ver.
          system.run(() => manifest(player, 'Estou aqui. Pergunte o que precisar pelo painel do Grande Sábio.'));
          return 'close';
        default: break;
      }
    }
  } finally {
    openFor.delete(player.id);
    saveNow(player);
  }
  return result;
}
