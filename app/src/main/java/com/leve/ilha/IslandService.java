package com.leve.ilha;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.text.TextUtils;

import java.util.List;
import java.util.Locale;

/**
 * Cérebro da ilha: lê notificações e sessões de mídia e decide o que mostrar.
 * Prioridade fixa: ligação > timer/cronômetro > música. Notificações comuns
 * aparecem expandidas por alguns segundos por cima de tudo.
 */
public class IslandService extends NotificationListenerService {
    static IslandService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private IslandView view;
    private Content toast, call, timer, media, shown;
    private String callKey, timerKey;
    private boolean userExpanded;

    private MediaSessionManager msm;
    private MediaController mc;

    private final Runnable clearToastTask = () -> { toast = null; render(); };
    private final Runnable collapseTask = () -> { userExpanded = false; render(); };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (view == null || shown == null) return;
            view.setTime(timeText(shown));
            handler.postDelayed(this, 1000);
        }
    };

    private final MediaSessionManager.OnActiveSessionsChangedListener sessListener =
            this::pickController;

    private final MediaController.Callback mcCb = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { refreshMedia(); }
        @Override public void onMetadataChanged(MediaMetadata m) { refreshMedia(); }
        @Override public void onSessionDestroyed() {
            handler.post(() -> {
                try {
                    pickController(msm.getActiveSessions(new ComponentName(
                            IslandService.this, IslandService.class)));
                } catch (Exception e) {
                    pickController(null);
                }
            });
        }
    };

    // ------------------------------------------------------------ ciclo de vida

    @Override public void onListenerConnected() {
        instance = this;
        try {
            msm = (MediaSessionManager) getSystemService(Context.MEDIA_SESSION_SERVICE);
            ComponentName cn = new ComponentName(this, IslandService.class);
            msm.addOnActiveSessionsChangedListener(sessListener, cn, handler);
            pickController(msm.getActiveSessions(cn));
        } catch (Exception ignored) { }
        try {
            for (StatusBarNotification s : getActiveNotifications()) handle(s, false);
        } catch (Exception ignored) { }
    }

    @Override public void onListenerDisconnected() {
        cleanup();
    }

    @Override public void onDestroy() {
        cleanup();
        super.onDestroy();
    }

    private void cleanup() {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        try { if (msm != null) msm.removeOnActiveSessionsChangedListener(sessListener); }
        catch (Exception ignored) { }
        if (mc != null) {
            try { mc.unregisterCallback(mcCb); } catch (Exception ignored) { }
            mc = null;
        }
        if (view != null) {
            view.destroy();
            view = null;
        }
        toast = call = timer = media = shown = null;
        callKey = timerKey = null;
        userExpanded = false;
    }

    // ------------------------------------------------------------- notificações

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        handle(sbn, true);
    }

    @Override public void onNotificationRemoved(StatusBarNotification sbn) {
        String k = sbn.getKey();
        boolean changed = false;
        if (k != null && k.equals(callKey)) { call = null; callKey = null; changed = true; }
        if (k != null && k.equals(timerKey)) { timer = null; timerKey = null; changed = true; }
        if (changed) render();
    }

    private void handle(StatusBarNotification sbn, boolean fresh) {
        if (getPackageName().equals(sbn.getPackageName())) return;
        Notification n = sbn.getNotification();
        if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        Bundle e = n.extras;
        if (Notification.CATEGORY_TRANSPORT.equals(n.category)
                || e.containsKey(Notification.EXTRA_MEDIA_SESSION)) return; // música vem da sessão

        CharSequence t = e.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence x = e.getCharSequence(Notification.EXTRA_TEXT);
        boolean ongoing = (n.flags & (Notification.FLAG_ONGOING_EVENT
                | Notification.FLAG_FOREGROUND_SERVICE)) != 0;
        boolean isCall = Notification.CATEGORY_CALL.equals(n.category);
        boolean chrono = e.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);

        if (isCall && ongoing) {
            boolean isNew = !sbn.getKey().equals(callKey);
            call = make(Content.CALL, sbn, t, x);
            callKey = sbn.getKey();
            if (toast != null && toast.type == Content.CALL) toast = null;
            if (isNew && fresh && !chrono) {       // chamada tocando: expande por alguns segundos
                setToast(call, 8000);
            } else {
                render();
            }
            return;
        }
        if (!isCall && ongoing && chrono) {
            timer = make(Content.TIMER, sbn, t, x);
            timerKey = sbn.getKey();
            render();
            return;
        }
        if (ongoing || !fresh) return;
        if (TextUtils.isEmpty(t) && TextUtils.isEmpty(x)) return;
        setToast(make(Content.TOAST, sbn, t, x), 4000);
    }

    private Content make(int type, StatusBarNotification sbn, CharSequence t, CharSequence x) {
        Notification n = sbn.getNotification();
        Content c = new Content();
        c.type = type;
        c.key = sbn.getKey();
        c.title = TextUtils.isEmpty(t) ? x : t;
        c.text = TextUtils.isEmpty(t) ? null : x;
        if (TextUtils.isEmpty(c.title)) {
            c.title = type == Content.CALL ? "Chamada" : "Timer";
        }
        c.tap = n.contentIntent;
        c.when = n.when;
        c.chrono = n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        c.countdown = n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false);
        if (type == Content.TOAST) {
            try { c.icon = getPackageManager().getApplicationIcon(sbn.getPackageName()); }
            catch (Exception ignored) { }
        }
        return c;
    }

    private void setToast(Content c, long ms) {
        toast = c;
        handler.removeCallbacks(clearToastTask);
        handler.postDelayed(clearToastTask, ms);
        render();
    }

    // -------------------------------------------------------------------- mídia

    private void pickController(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController c : list) {
                PlaybackState s = c.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { best = c; break; }
            }
            if (best == null && mc != null) {
                for (MediaController c : list) {
                    if (c.getSessionToken().equals(mc.getSessionToken())) { best = c; break; }
                }
            }
            if (best == null && !list.isEmpty()) best = list.get(0);
        }
        if (mc != null && (best == null || !best.getSessionToken().equals(mc.getSessionToken()))) {
            try { mc.unregisterCallback(mcCb); } catch (Exception ignored) { }
            mc = null;
        }
        if (best != null && mc == null) {
            mc = best;
            mc.registerCallback(mcCb, handler);
        }
        refreshMedia();
    }

    private void refreshMedia() {
        Content c = null;
        if (mc != null) {
            PlaybackState s = mc.getPlaybackState();
            int st = s == null ? PlaybackState.STATE_NONE : s.getState();
            MediaMetadata m = mc.getMetadata();
            if ((st == PlaybackState.STATE_PLAYING || st == PlaybackState.STATE_PAUSED) && m != null) {
                c = new Content();
                c.type = Content.MEDIA;
                c.playing = st == PlaybackState.STATE_PLAYING;
                String title = m.getString(MediaMetadata.METADATA_KEY_TITLE);
                c.title = TextUtils.isEmpty(title) ? "Reproduzindo" : title;
                c.text = m.getString(MediaMetadata.METADATA_KEY_ARTIST);
                Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
                if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
                if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
                if (b != null) c.icon = new BitmapDrawable(getResources(), b);
                c.tap = mc.getSessionActivity();
            }
        }
        media = c;
        render();
    }

    void mediaPrev() {
        if (mc != null) mc.getTransportControls().skipToPrevious();
        bump();
    }

    void mediaNext() {
        if (mc != null) mc.getTransportControls().skipToNext();
        bump();
    }

    void mediaPlayPause() {
        if (mc != null) {
            PlaybackState s = mc.getPlaybackState();
            if (s != null && s.getState() == PlaybackState.STATE_PLAYING) {
                mc.getTransportControls().pause();
            } else {
                mc.getTransportControls().play();
            }
        }
        bump();
    }

    // ------------------------------------------------------------------ toques

    void onCompactTap() {
        userExpanded = true;
        bump();
        render();
    }

    void onExpandedTap() {
        Content c = shown;
        if (c != null && c.tap != null) {
            try { c.tap.send(); } catch (Exception ignored) { }
        }
        handler.removeCallbacks(clearToastTask);
        handler.removeCallbacks(collapseTask);
        toast = null;
        userExpanded = false;
        render();
    }

    private void bump() {
        if (!userExpanded) return;
        handler.removeCallbacks(collapseTask);
        handler.postDelayed(collapseTask, 6000);
    }

    void showTest() {
        if (view != null) {
            view.destroy();
            view = null;
        }
        Content c = new Content();
        c.type = Content.TOAST;
        c.title = "Ilha Leve";
        c.text = "Funcionando! Toque para fechar.";
        setToast(c, 4000);
    }

    // ------------------------------------------------------------------ render

    private Content persistent() {
        if (call != null) return call;
        if (timer != null) return timer;
        if (media != null && (media.playing || userExpanded)) return media;
        return null;
    }

    private void render() {
        Content c = toast != null ? toast : persistent();
        if (c == null) {
            shown = null;
            userExpanded = false;
            handler.removeCallbacks(tick);
            handler.removeCallbacks(collapseTask);
            if (view != null) view.hide();
            return;
        }
        if (!Settings.canDrawOverlays(this)) return;
        if (view == null || !view.isActive()) {
            view = new IslandView(this, getSharedPreferences("p", MODE_PRIVATE));
        }
        shown = c;
        view.show(c, (toast != null || userExpanded) ? IslandView.ST_EXPANDED : IslandView.ST_COMPACT);
        handler.removeCallbacks(tick);
        if (c.type == Content.CALL || c.type == Content.TIMER) tick.run();
    }

    private String timeText(Content c) {
        if (!c.chrono) return "";
        long now = System.currentTimeMillis();
        long ms = c.countdown ? c.when - now : now - c.when;
        if (ms < 0) ms = 0;
        long s = ms / 1000;
        long h = s / 3600, m = (s % 3600) / 60, sec = s % 60;
        return h > 0
                ? String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
                : String.format(Locale.US, "%02d:%02d", m, sec);
    }
}
