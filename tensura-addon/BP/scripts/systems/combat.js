// Dano recebido/causado: resistências e fraquezas raciais, roubo de vida,
// efeitos ao golpear, armas imbuídas e mordida do basilisco.
import { world, system, EntityDamageCause } from '@minecraft/server';
import { causeGroup, ELEMENTS } from '../data/elements.js';
import { getData } from './storage.js';
import { recentSpellHits, dealDamage, addEffects, heal, knockFrom, particle, V } from './util.js';
import { onSummonHit } from './summons.js';
import { powerFor } from '../core/stats.js';
import { onPlayerHit } from './sage.js';

const extraHits = new Set(); // dano extra de fraqueza já aplicado (evita recursão)

export function startCombat() {
  world.afterEvents.entityHurt.subscribe((ev) => {
    const victim = ev.hurtEntity;
    const src = ev.damageSource;
    const attacker = src.damagingEntity;
    const now = system.currentTick;
    const spell = recentSpellHits.get(victim.id) === now;

    // Resistência/fraqueza racial a dano comum (magias já calculam isso em dealDamage).
    if (victim.typeId === 'minecraft:player' && !spell && victim.isValid) {
      const d = getData(victim);
      const g = causeGroup(src.cause);
      if (d.stats && g) {
        // Esquiva (Técnica de Esquiva / Sentido de Batalha): anula o dano deste golpe.
        if (d.stats.dodge > 0 && Math.random() < d.stats.dodge && src.cause !== EntityDamageCause.override) {
          const hp = victim.getComponent('minecraft:health');
          if (hp && hp.currentValue > 0) hp.setCurrentValue(Math.min(hp.effectiveMax, hp.currentValue + ev.damage));
          try { /** @type {any} */ (victim).onScreenDisplay.setActionBar('§bEsquiva!'); } catch { /* */ }
          return;
        }
        // Contra-ataque: devolve parte do dano corpo a corpo ao atacante.
        if (d.stats.counter && attacker?.isValid && src.cause === EntityDamageCause.entityAttack && Math.random() < d.stats.counter.chance) {
          system.run(() => { if (attacker.isValid) dealDamage(victim, attacker, ev.damage * d.stats.counter.mult, 'physical'); });
        }
        const r = d.stats.resist[g] ?? 0;
        if (r > 0) {
          const h = victim.getComponent('minecraft:health');
          if (h && h.currentValue > 0) h.setCurrentValue(Math.min(h.effectiveMax, h.currentValue + ev.damage * r));
        }
        const w = d.stats.weak[g] ?? 0;
        if (w > 0 && !extraHits.has(victim.id) && src.cause !== EntityDamageCause.override) {
          extraHits.add(victim.id);
          system.run(() => {
            try { if (victim.isValid) victim.applyDamage(ev.damage * w, { cause: EntityDamageCause.override }); } catch { /* */ }
            extraHits.delete(victim.id);
          });
        }
      }
    }

    if (!attacker?.isValid) return;
    if (attacker.typeId.startsWith('tensura:')) { onSummonHit(attacker, victim); return; }
    if (attacker.typeId !== 'minecraft:player' || spell) return;
    const d = getData(attacker);
    if (!d.stats) return;

    if (src.cause === EntityDamageCause.projectile && (d.stats.power.projectile ?? 1) > 1) {
      dealDamage(attacker, victim, ev.damage * (d.stats.power.projectile - 1), 'physical');
    }
    if (victim.typeId !== 'minecraft:player') onPlayerHit(attacker, victim);
    if (src.cause !== EntityDamageCause.entityAttack) return;
    // Golpe corpo a corpo.
    if (d.stats.lifesteal > 0) heal(attacker, ev.damage * d.stats.lifesteal);
    for (const o of d.stats.onHit) {
      if (Math.random() > o.chance) continue;
      if (o.eff) addEffects(victim, o.eff);
      if (o.knock) knockFrom(victim, attacker.location, o.knock, 0.35);
    }
    const im = d.imbue;
    if (im && now < im.until) {
      system.run(() => {
        if (!victim.isValid) return;
        dealDamage(attacker, victim, im.dmg, im.el);
        if (im.ignite) { try { victim.setOnFire(im.ignite, true); } catch { /* */ } }
        if (im.eff?.length) addEffects(victim, im.eff);
        particle(victim.dimension, ELEMENTS[im.el]?.impact ?? 'minecraft:critical_hit_emitter', V.add(victim.location, { x: 0, y: 1, z: 0 }));
      });
    } else if (im) {
      d.imbue = null;
    }
  });
}

export { powerFor };
