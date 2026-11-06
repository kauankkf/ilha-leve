package com.leve.ilha;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A ilha em si. Estados: CAM (bolinha do tamanho da câmera) -> COMPACT (pílula pequena)
 * -> EXPANDED (pílula grande). A janela só é redimensionada nas trocas de estado,
 * a animação roda dentro dela.
 */
final class IslandView extends FrameLayout {
    static final int ST_NONE = 0, ST_CAM = 1, ST_COMPACT = 2, ST_EXPANDED = 3;

    private final IslandService svc;
    private final WindowManager wm;
    private final WindowManager.LayoutParams wlp;
    private final FrameLayout pill;
    private final FrameLayout.LayoutParams pillLp;
    private final GradientDrawable bg = new GradientDrawable();
    private final FrameLayout compact;
    private final LinearLayout expanded;
    private final FrameLayout.LayoutParams expLp;
    private final IconSlot left, cover;
    private final Equalizer eq;
    private final TextView timeTv, timeBig, title, text;
    private final LinearLayout controls;
    private final IconView play;
    private final int camPx, compactW, compactH, expW, margin;

    private int state = ST_NONE;
    private boolean attached;
    private float radius, ca, ea;
    private ValueAnimator anim;
    private int animTarget = -1, animTw, animTh;
    private Content cur;

    IslandView(final IslandService s, SharedPreferences p) {
        super(s);
        svc = s;
        wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
        camPx = dp(p.getInt("cam", 26));
        compactW = dp(p.getInt("cw", 150));
        compactH = dp(36);
        expW = dp(p.getInt("w", 300));
        margin = dp(20);

        wlp = new WindowManager.LayoutParams(camPx + 2 * margin, camPx + margin,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        wlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        wlp.y = dp(p.getInt("y", 8));
        if (Build.VERSION.SDK_INT >= 28) {
            wlp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }

        bg.setColor(0xFF000000);
        pill = new FrameLayout(s);
        pill.setBackground(bg);
        pill.setClipToOutline(true);
        pillLp = new FrameLayout.LayoutParams(camPx, camPx, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        addView(pill, pillLp);
        radius = camPx / 2f;
        bg.setCornerRadius(radius);

        // ---- forma compacta: capa/ícone à esquerda, equalizador/tempo à direita ----
        compact = new FrameLayout(s);
        left = new IconSlot(s, true, 0, 13);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(dp(24), dp(24),
                Gravity.START | Gravity.CENTER_VERTICAL);
        llp.leftMargin = dp(10);
        compact.addView(left, llp);
        eq = new Equalizer(s);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(dp(20), dp(14),
                Gravity.END | Gravity.CENTER_VERTICAL);
        elp.rightMargin = dp(12);
        compact.addView(eq, elp);
        timeTv = tv(13, 0xFF7CFFB2, true, 1);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        tlp.rightMargin = dp(12);
        compact.addView(timeTv, tlp);
        compact.setOnClickListener(v -> s.onCompactTap());
        pill.addView(compact, new FrameLayout.LayoutParams(compactW, compactH,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        // ---- forma expandida ----
        LinearLayout row = new LinearLayout(s);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        cover = new IconSlot(s, false, dp(12), 24);
        row.addView(cover, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout col = new LinearLayout(s);
        col.setOrientation(LinearLayout.VERTICAL);
        title = tv(15, 0xFFFFFFFF, true, 1);
        text = tv(13, 0xFFB8B8B8, false, 2);
        col.addView(title);
        col.addView(text);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(12);
        row.addView(col, clp);
        timeBig = tv(16, 0xFF7CFFB2, true, 1);
        row.addView(timeBig);
        row.setOnClickListener(v -> s.onExpandedTap());

        controls = new LinearLayout(s);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        IconView prev = new IconView(s, IconView.PREV);
        play = new IconView(s, IconView.PLAY);
        IconView next = new IconView(s, IconView.NEXT);
        prev.setOnClickListener(v -> s.mediaPrev());
        play.setOnClickListener(v -> s.mediaPlayPause());
        next.setOnClickListener(v -> s.mediaNext());
        controls.addView(prev, new LinearLayout.LayoutParams(dp(64), dp(44)));
        controls.addView(play, new LinearLayout.LayoutParams(dp(64), dp(44)));
        controls.addView(next, new LinearLayout.LayoutParams(dp(64), dp(44)));

        expanded = new LinearLayout(s);
        expanded.setOrientation(LinearLayout.VERTICAL);
        expanded.setPadding(dp(16), dp(14), dp(16), dp(12));
        expanded.addView(row);
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctl.topMargin = dp(6);
        expanded.addView(controls, ctl);
        expLp = new FrameLayout.LayoutParams(expW, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pill.addView(expanded, expLp);

        setGroupAlpha(0f, 0f);
    }

    // ------------------------------------------------------------------ API

    boolean isActive() { return attached; }

    void show(Content c, int target) {
        bindContent(c);
        if (!attached) {
            try { wm.addView(this, wlp); } catch (Exception e) { return; }
            attached = true;
            state = ST_CAM;
            pillLp.width = camPx;
            pillLp.height = camPx;
            pill.setLayoutParams(pillLp);
            radius = camPx / 2f;
            bg.setCornerRadius(radius);
            pill.invalidateOutline();
            setGroupAlpha(0f, 0f);
        }
        goTo(target);
    }

    void hide() {
        if (!attached) return;
        if (state == ST_CAM && pillLp.width == camPx && pillLp.height == camPx) {
            detach();
            return;
        }
        goTo(ST_CAM);
    }

    void destroy() { detach(); }

    void setTime(String s) {
        timeTv.setText(s);
        timeBig.setText(s);
    }

    // ------------------------------------------------------------- conteúdo

    private void bindContent(Content c) {
        cur = c;
        boolean media = c.type == Content.MEDIA;
        boolean timed = c.type == Content.CALL || c.type == Content.TIMER;
        String g = c.type == Content.CALL ? "\uD83D\uDCDE"
                : c.type == Content.TIMER ? "\u23F1"
                : media ? "\u266A" : "\uD83D\uDD14";
        left.set(c.icon, g);
        cover.set(c.icon, g);
        title.setText(c.title);
        text.setText(c.text);
        text.setVisibility(TextUtils.isEmpty(c.text) ? View.GONE : View.VISIBLE);
        eq.setVisibility(media ? View.VISIBLE : View.GONE);
        eq.setRunning(media && c.playing);
        timeTv.setVisibility(timed ? View.VISIBLE : View.GONE);
        timeBig.setVisibility(timed ? View.VISIBLE : View.GONE);
        play.setType(c.playing ? IconView.PAUSE : IconView.PLAY);
    }

    private int expandedHeight() {
        controls.setVisibility(cur != null && cur.type == Content.MEDIA ? View.VISIBLE : View.GONE);
        expanded.measure(View.MeasureSpec.makeMeasureSpec(expW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return expanded.getMeasuredHeight();
    }

    // ----------------------------------------------------------- animações

    private void goTo(final int target) {
        final int tw, th;
        final float tr;
        if (target == ST_EXPANDED) {
            tw = expW;
            th = expandedHeight();
            tr = dp(28);
            expLp.height = th;
            expanded.setLayoutParams(expLp);
        } else if (target == ST_COMPACT) {
            tw = compactW;
            th = compactH;
            tr = compactH / 2f;
        } else {
            tw = camPx;
            th = camPx;
            tr = camPx / 2f;
        }
        if (state == target && pillLp.width == tw && pillLp.height == th) return;
        if (anim != null && anim.isRunning() && animTarget == target
                && animTw == tw && animTh == th) return;

        // a janela só cresce antes da animação (e encolhe depois dela)
        resizeWindow(Math.max(wlp.width, tw + 2 * margin), Math.max(wlp.height, th + margin));
        state = target;
        startAnim(tw, th, tr, target);
    }

    private void startAnim(final int tw, final int th, final float tr, final int target) {
        if (anim != null) anim.cancel();
        animTarget = target;
        animTw = tw;
        animTh = th;
        final int sw = pillLp.width, sh = pillLp.height;
        final float sr = radius, sca = ca, sea = ea;

        ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
        anim = a;
        a.setDuration(target == ST_EXPANDED ? 380 : target == ST_COMPACT ? 300 : 220);
        a.setInterpolator(target == ST_CAM
                ? new AccelerateDecelerateInterpolator() : new OvershootInterpolator(0.8f));
        a.addUpdateListener(va -> {
            float f = (Float) va.getAnimatedValue();
            pillLp.width = Math.min(wlp.width, Math.round(sw + (tw - sw) * f));
            pillLp.height = Math.min(wlp.height, Math.round(sh + (th - sh) * f));
            pill.setLayoutParams(pillLp);
            float rr = lerp(sr, tr, c01(f));
            rr = Math.min(rr, Math.min(pillLp.width, pillLp.height) / 2f);
            radius = rr;
            bg.setCornerRadius(rr);
            pill.invalidateOutline();
            if (target == ST_EXPANDED) {
                setGroupAlpha(lerp(sca, 0f, c01(f * 2.5f)), lerp(sea, 1f, c01((f - 0.35f) / 0.65f)));
            } else if (target == ST_COMPACT) {
                setGroupAlpha(lerp(sca, 1f, c01((f - 0.35f) / 0.65f)), lerp(sea, 0f, c01(f * 2.5f)));
            } else {
                setGroupAlpha(lerp(sca, 0f, c01(f * 2.5f)), lerp(sea, 0f, c01(f * 2.5f)));
            }
        });
        a.addListener(new AnimatorListenerAdapter() {
            private boolean canceled;
            @Override public void onAnimationCancel(Animator an) { canceled = true; }
            @Override public void onAnimationEnd(Animator an) {
                if (canceled) return;
                animTarget = -1;
                setGroupAlpha(target == ST_COMPACT ? 1f : 0f, target == ST_EXPANDED ? 1f : 0f);
                if (target == ST_COMPACT) {
                    resizeWindow(compactW + 2 * margin, compactH + margin);
                } else if (target == ST_CAM) {
                    detach();
                }
            }
        });
        a.start();
    }

    private void setGroupAlpha(float c, float e) {
        ca = c;
        ea = e;
        compact.setAlpha(c);
        compact.setVisibility(c <= 0.01f ? View.GONE : View.VISIBLE);
        expanded.setAlpha(e);
        expanded.setVisibility(e <= 0.01f ? View.GONE : View.VISIBLE);
    }

    private void resizeWindow(int w, int h) {
        if (!attached || (wlp.width == w && wlp.height == h)) return;
        wlp.width = w;
        wlp.height = h;
        try { wm.updateViewLayout(this, wlp); } catch (Exception ignored) { }
    }

    private void detach() {
        if (anim != null) {
            anim.cancel();
            anim = null;
        }
        animTarget = -1;
        eq.setRunning(false);
        if (attached) {
            try { wm.removeView(this); } catch (Exception ignored) { }
        }
        attached = false;
        state = ST_NONE;
    }

    // -------------------------------------------------------------- helpers

    private TextView tv(int sp, int color, boolean bold, int lines) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setMaxLines(lines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float c01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
