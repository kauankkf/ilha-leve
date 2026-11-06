package com.leve.ilha;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {
    private SharedPreferences p;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        p = getSharedPreferences("p", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView info = new TextView(this);
        info.setText("Ilha Leve\nSiga os 3 passos. Os ajustes abaixo valem na próxima vez que a ilha aparecer "
                + "(ou ao tocar em Testar).");
        root.addView(info);

        root.addView(btn("1. Permitir exibir sobre outros apps", v -> startActivity(
                new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName())))));
        root.addView(btn("2. Permitir acesso às notificações", v -> startActivity(
                new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))));
        root.addView(btn("3. Bateria / autostart (Xiaomi)", v -> startActivity(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + getPackageName())))));

        addSeek(root, "Posição vertical", "y", 0, 80, 8);
        addSeek(root, "Tamanho da câmera (bolinha inicial)", "cam", 16, 48, 26);
        addSeek(root, "Largura da forma compacta", "cw", 100, 220, 150);
        addSeek(root, "Largura da forma expandida", "w", 240, 360, 300);

        root.addView(btn("Testar ilha", v -> {
            if (!Settings.canDrawOverlays(this)) {
                toast("Falta o passo 1");
            } else if (IslandService.instance == null) {
                toast("Falta o passo 2");
            } else {
                IslandService.instance.showTest();
            }
        }));

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private void addSeek(LinearLayout root, final String name, final String key,
                         final int lo, int hi, int def) {
        final TextView label = new TextView(this);
        label.setPadding(0, dp(16), 0, 0);
        int cur = p.getInt(key, def);
        label.setText(name + ": " + cur + " dp");
        root.addView(label);

        SeekBar s = new SeekBar(this);
        s.setMax(hi - lo);
        s.setProgress(cur - lo);
        s.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar sb, int pr, boolean user) {
                p.edit().putInt(key, pr + lo).apply();
                label.setText(name + ": " + (pr + lo) + " dp");
            }
            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        root.addView(s);
    }

    private Button btn(String t, View.OnClickListener l) {
        Button x = new Button(this);
        x.setText(t);
        x.setAllCaps(false);
        x.setOnClickListener(l);
        return x;
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
