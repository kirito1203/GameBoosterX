package com.gameboosterx.rog.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.gameboosterx.rog.R;
import com.gameboosterx.rog.data.GameRepository;

import java.util.List;

/**
 * AUTO GAME DETECT — lista jogos instalados (categoria de jogo declarada pelo app
 * ou reconhecidos pelo catálogo) e permite marcar quais serão monitorados.
 * Jogos marcados carregam o perfil automaticamente quando abertos.
 */
public class GamesActivity extends BaseActivity {

    private LinearLayout content;
    private GameRepository repo;
    private boolean showAll;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_module);
        setupHeader("MEUS JOGOS", "Detecção e perfis");
        content = findViewById(R.id.content);
        repo = new GameRepository(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        content.removeAllViews();
        TextView loading = addNote(content, "Procurando jogos instalados…");
        runBg(() -> {
            List<GameRepository.AppItem> items = repo.scanInstalled();
            ui.post(() -> {
                content.removeView(loading);
                render(items);
            });
        });
    }

    private void render(List<GameRepository.AppItem> items) {
        addNote(content, "Marque os jogos que o Game Booster X deve reconhecer. Toque no nome para abrir o perfil. "
                + "Com o Acesso de Uso concedido, o perfil é aplicado automaticamente quando o jogo abre.");

        addSection(content, "JOGOS DETECTADOS");
        int games = 0;
        for (GameRepository.AppItem it : items) {
            if (it.selected || it.catalog != null || it.isGame) {
                addItem(it);
                games++;
            }
        }
        if (games == 0) addNote(content, "Nenhum jogo detectado automaticamente. Use \"Mostrar todos os apps\" abaixo.");

        addSection(content, "OUTROS APPS");
        if (!showAll) {
            addRow(content, "Mostrar todos os apps", "Alguns jogos não se declaram como jogo; marque-os manualmente.",
                    "Mostrar", v -> { showAll = true; load(); });
        } else {
            for (GameRepository.AppItem it : items) {
                if (!(it.selected || it.catalog != null || it.isGame)) addItem(it);
            }
        }
    }

    private void addItem(GameRepository.AppItem it) {
        View v = LayoutInflater.from(this).inflate(R.layout.item_game, content, false);
        ((ImageView) v.findViewById(R.id.itemGameIcon)).setImageDrawable(repo.icon(it.pkg));
        ((TextView) v.findViewById(R.id.itemGameName)).setText(it.label);
        String sub = it.catalog != null ? "Perfil dedicado: " + it.catalog.title
                : (it.isGame ? "Jogo detectado • perfil genérico" : it.pkg);
        ((TextView) v.findViewById(R.id.itemGameSub)).setText(sub);
        CheckBox cb = v.findViewById(R.id.itemGameCheck);
        cb.setChecked(it.selected);
        cb.setOnCheckedChangeListener((btn, checked) -> {
            repo.setSelected(it.pkg, checked);
            it.selected = checked;
        });
        v.findViewById(R.id.itemGameText).setOnClickListener(x ->
                startActivity(new Intent(this, ProfileActivity.class).putExtra("pkg", it.pkg)));
        content.addView(v);
    }
}
