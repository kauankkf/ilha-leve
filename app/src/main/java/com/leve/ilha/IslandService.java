package com.leve.ilha;

import android.app.Notification;
import android.content.ComponentName;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
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
 * Cérebro da ilha. Itens ativos: ligação, timer e música. O principal aparece na pílula,
 * o segundo vira a bolinha ao lado; tocar na bolinha troca os dois. Notificações comuns
 * aparecem como cartão por alguns segundos.
 */
public class IslandService extends NotificationListenerService {
    static IslandService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private IslandView view;
    private Content toast, call, timer, media, shownPrimary, shownSecondary;
    private String callKey, timerKey;
    private boolean userExpanded;
    private int forced = -1;

    private MediaSessionManager msm;
    private MediaController mc;

    private final Runnable clearToastTask = () -> { toast = null; render(); };
    private final Runnable collapseTask = () -> { userExpanded = false; render(); };
    private final Runnable tick = new Runnable() {
        @Override public void run() {
            Content p = shownPrimary;
            if (view == null || p == null) return;
            if (p.type == Content.CALL || p.type == Content.TIMER) view.setTime(timeText(p));
            else if (p.type == Content.MEDIA) view.updateProgress();
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

    @Override public void onListenerDisconnected() { cleanup(); }

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
        toast = call = timer = media = shownPrimary = shownSecondary = null;
        callKey = timerKey = null;
        userExpanded = false;
        forced = -1;
    }

    // ------------------------------------------------------------- notificações

    @Override public void onNotificationPosted(StatusBarNotification sbn) { handle(sbn, true); }

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
            if (isNew) forced = -1;
            if (isNew && fresh && !chrono) {       // chamada tocando: expande por alguns segundos
                setToast(call, 8000);
            } else {
                render();
            }
            return;
        }
        if (!isCall && ongoing && chrono) {
            boolean isNew = !sbn.getKey().equals(timerKey);
            timer = make(Content.TIMER, sbn, t, x);
            timerKey = sbn.getKey();
            if (isNew) forced = -1;
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
        if (TextUtils.isEmpty(c.title)) c.title = type == Content.CALL ? "Chamada" : "Timer";
        c.tap = n.contentIntent;
        c.when = n.when;
        c.chrono = n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER, false);
        c.countdown = n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN, false);
        if (type == Content.TOAST) {
            try { c.icon = getPackageManager().getApplicationIcon(sbn.getPackageName()); }
            catch (Exception ignored) { }
        } else if (type == Content.CALL) {
            c.tint = 0xFF2E7D32;
            try {
                Icon li = n.getLargeIcon();
                if (li != null) c.icon = li.loadDrawable(this);
            } catch (Exception ignored) { }
            if (n.actions != null) {
                for (Notification.Action a : n.actions) {
                    String ttl = a.title == null ? "" : a.title.toString().toLowerCase(Locale.ROOT);
                    if (ttl.contains("desligar") || ttl.contains("encerrar") || ttl.contains("hang")
                            || ttl.contains("end") || ttl.contains("decline") || ttl.contains("recusar")) {
                        c.hangup = a.actionIntent;
                    } else if (ttl.contains("viva") || ttl.contains("alto") || ttl.contains("speaker")) {
                        c.speaker = a.actionIntent;
                    }
                }
                if (c.hangup == null && n.actions.length == 1) c.hangup = n.actions[0].actionIntent;
            }
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
                if (b != null) {
                    c.icon = new BitmapDrawable(getResources(), b);
                    c.tint = avg(b);
                }
                c.tap = mc.getSessionActivity();
                c.duration = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
                c.position = s.getPosition();
                c.posTime = s.getLastPositionUpdateTime();
                c.speed = s.getPlaybackSpeed();
            }
        }
        media = c;
        render();
    }

    private static int avg(Bitmap b) {
        try {
            return Bitmap.createScaledBitmap(b, 1, 1, true).getPixel(0, 0) | 0xFF000000;
        } catch (Exception e) {
            return 0xFF3A3A3A;
        }
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

    void mediaSeek(float frac) {
        if (mc != null && media != null && media.duration > 0) {
            mc.getTransportControls().seekTo((long) (frac * media.duration));
        }
        bump();
    }

    // ------------------------------------------------------------------ ligação

    void callHangup() {
        if (call != null && call.hangup != null) {
            try { call.hangup.send(); } catch (Exception ignored) { }
        }
    }

    void callSpeaker() {
        if (call != null && call.speaker != null) {
            try { call.speaker.send(); } catch (Exception ignored) { }
        }
        bump();
    }

    // ------------------------------------------------------------------ toques

    void onCompactTap() {
        userExpanded = true;
        bump();
        render();
    }

    void onBubbleTap() {
        if (shownSecondary == null) return;
        forced = shownSecondary.type;     // a bolinha vira a principal
        userExpanded = true;
        bump();
        render();
    }

    void onExpandedTap() {
        Content c = shownPrimary;
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

    private void validateForced() {
        if (forced == Content.MEDIA && (media == null || (!media.playing && !userExpanded))) forced = -1;
        if (forced == Content.CALL && call == null) forced = -1;
        if (forced == Content.TIMER && timer == null) forced = -1;
    }

    private Content pickPrimary() {
        if (forced == Content.MEDIA) return media;
        if (forced == Content.CALL) return call;
        if (forced == Content.TIMER) return timer;
        if (call != null) return call;
        if (timer != null) return timer;
        if (media != null && media.playing) return media;
        return null;
    }

    private Content pickSecondary(Content p) {
        if (p == null) return null;
        Content[] order = {call, timer, media != null && media.playing ? media : null};
        for (Content c : order) {
            if (c != null && c != p) return c;
        }
        return null;
    }

    private void render() {
        Content p, sec;
        int target;
        if (toast != null) {
            p = toast;
            sec = null;
            target = IslandView.ST_EXPANDED;
        } else {
            validateForced();
            p = pickPrimary();
            sec = pickSecondary(p);
            target = userExpanded ? IslandView.ST_EXPANDED : IslandView.ST_COMPACT;
        }
        if (p == null) {
            shownPrimary = shownSecondary = null;
            userExpanded = false;
            forced = -1;
            handler.removeCallbacks(tick);
            handler.removeCallbacks(collapseTask);
            if (view != null) view.hide();
            return;
        }
        if (!Settings.canDrawOverlays(this)) return;
        if (view == null || !view.isActive()) {
            view = new IslandView(this, getSharedPreferences("p", MODE_PRIVATE));
        }
        shownPrimary = p;
        shownSecondary = sec;
        view.show(p, sec, target);
        handler.removeCallbacks(tick);
        boolean needTick = p.type == Content.CALL || p.type == Content.TIMER
                || (p.type == Content.MEDIA && target == IslandView.ST_EXPANDED);
        if (needTick) tick.run();
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
