package com.leve.ilha;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Serviço único: lê notificações e desenha a "ilha" só quando necessário (zero custo em repouso). */
public class IslandService extends NotificationListenerService {
    static IslandService instance;
    private static final long SHOW_MS = 4000;

    private WindowManager wm;
    private LinearLayout pill;
    private TextView title, body;
    private WindowManager.LayoutParams lp;
    private PendingIntent tap;
    private boolean attached, closing;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideTask = this::collapse;

    @Override public void onListenerConnected() {
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
    }

    @Override public void onListenerDisconnected() {
        instance = null;
        removeNow();
    }

    @Override public void onDestroy() {
        instance = null;
        removeNow();
        super.onDestroy();
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        int ignore = Notification.FLAG_ONGOING_EVENT | Notification.FLAG_GROUP_SUMMARY
                | Notification.FLAG_FOREGROUND_SERVICE;
        if ((n.flags & ignore) != 0) return;
        if (getPackageName().equals(sbn.getPackageName())) return;
        Bundle e = n.extras;
        CharSequence t = e.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence x = e.getCharSequence(Notification.EXTRA_TEXT);
        if (TextUtils.isEmpty(t) && TextUtils.isEmpty(x)) return;
        show(t, x, n.contentIntent);
    }

    void show(CharSequence t, CharSequence x, PendingIntent pi) {
        if (!Settings.canDrawOverlays(this)) return;
        if (wm == null) wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        tap = pi;
        handler.removeCallbacks(hideTask);

        if (attached) {                       // já visível: só atualiza o texto
            closing = false;
            pill.animate().cancel();
            pill.setScaleX(1f); pill.setScaleY(1f); pill.setAlpha(1f);
        } else {
            build();
        }
        set(title, t);
        set(body, x);

        if (!attached) {
            try { wm.addView(pill, lp); } catch (Exception ex) { return; }
            attached = true;
            pill.setScaleX(0.3f); pill.setScaleY(0.3f); pill.setAlpha(0f);
            pill.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(320)
                    .setInterpolator(new OvershootInterpolator(1.1f)).start();
        }
        handler.postDelayed(hideTask, SHOW_MS);
    }

    private void set(TextView tv, CharSequence s) {
        tv.setText(s);
        tv.setVisibility(TextUtils.isEmpty(s) ? View.GONE : View.VISIBLE);
    }

    private void build() {
        SharedPreferences p = getSharedPreferences("p", MODE_PRIVATE);
        int w = dp(p.getInt("w", 260));

        pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setPadding(dp(18), dp(9), dp(18), dp(11));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF000000);
        bg.setCornerRadius(dp(26));
        pill.setBackground(bg);

        title = text(14, 0xFFFFFFFF, true, 1);
        body = text(12, 0xFFB8B8B8, false, 2);
        pill.addView(title);
        pill.addView(body);
        pill.setPivotX(w / 2f);
        pill.setPivotY(0f);
        pill.setOnClickListener(v -> {
            if (tap != null) {
                try { tap.send(); } catch (Exception ignored) { }
            }
            collapse();
        });

        lp = new WindowManager.LayoutParams(
                w, WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        lp.y = dp(p.getInt("y", 8));
    }

    private TextView text(int sp, int color, boolean bold, int lines) {
        TextView tv = new TextView(this);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        if (bold) tv.setTypeface(null, Typeface.BOLD);
        tv.setMaxLines(lines);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        return tv;
    }

    private void collapse() {
        handler.removeCallbacks(hideTask);
        if (!attached || pill == null) return;
        closing = true;
        pill.animate().scaleX(0.3f).scaleY(0.3f).alpha(0f).setDuration(180)
                .withEndAction(() -> { if (closing) removeNow(); }).start();
    }

    private void removeNow() {
        handler.removeCallbacks(hideTask);
        View v = pill;
        boolean was = attached;
        attached = false;
        closing = false;
        pill = null;
        if (was && v != null) {
            v.animate().cancel();
            try { wm.removeView(v); } catch (Exception ignored) { }
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
