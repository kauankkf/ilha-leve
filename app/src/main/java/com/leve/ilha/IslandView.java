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

import java.util.Locale;

/**
 * Janela da ilha. Linha: [pílula principal][bolinha secundária].
 * Estados: CAM (bolinha na câmera) -> COMPACT -> EXPANDED (cartão).
 * A janela só muda de tamanho nas trocas de estado; a animação roda dentro dela.
 */
final class IslandView extends FrameLayout {
    static final int ST_NONE = 0, ST_CAM = 1, ST_COMPACT = 2, ST_EXPANDED = 3;
    private static final int GREEN = 0xFF3DDC84;
    private static final int CARD = 0xFF2B2B2B;

    private final IslandService svc;
    private final WindowManager wm;
    private final WindowManager.LayoutParams wlp;
    private final LinearLayout row;
    private final FrameLayout pill, bubble;
    private final LinearLayout.LayoutParams pillLp;
    private final GradientDrawable bg = new GradientDrawable();
    private final GradientDrawable cardGrad = new GradientDrawable();
    private final View cardBg;
    private final FrameLayout compact;
    private final LinearLayout expanded, controls, progressRow;
    private final FrameLayout.LayoutParams expLp;
    private final IconSlot left, cover, bubbleCover;
    private final IconView leftPhone, bubblePhone, playIc;
    private final Equalizer eq;
    private final TextView timeTv, timeBig, title, text, elapsedTv, durTv;
    private final FrameLayout speakerBtn, hangBtn;
    private final ProgressView progress;
    private final int camPx, compactW, compactH, expW, margin, bubbleExtra;

    private int state = ST_NONE;
    private boolean attached, hasSec;
    private float radius, ca, ea;
    private ValueAnimator anim;
    private int animTarget = -1, animTw, animTh;
    private Content cur;
    private String lastTime = "00:00";

    IslandView(final IslandService s, SharedPreferences p) {
        super(s);
        svc = s;
        wm = (WindowManager) s.getSystemService(Context.WINDOW_SERVICE);
        camPx = dp(p.getInt("cam", 26));
        compactW = dp(p.getInt("cw", 150));
        compactH = dp(36);
        expW = dp(p.getInt("w", 300));
        margin = dp(20);
        bubbleExtra = dp(36);

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

        // ---------- linha: pílula + bolinha ----------
        row = new LinearLayout(s);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        addView(row, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        bg.setColor(0xFF000000);
        pill = new FrameLayout(s);
        pill.setBackground(bg);
        pill.setClipToOutline(true);
        pillLp = new LinearLayout.LayoutParams(camPx, camPx);
        row.addView(pill, pillLp);
        radius = camPx / 2f;
        bg.setCornerRadius(radius);

        bubble = new FrameLayout(s);
        GradientDrawable bb = new GradientDrawable();
        bb.setShape(GradientDrawable.OVAL);
        bb.setColor(0xFF000000);
        bubble.setBackground(bb);
        bubbleCover = new IconSlot(s, false, dp(5), 11);
        bubble.addView(bubbleCover, new FrameLayout.LayoutParams(dp(20), dp(20), Gravity.CENTER));
        bubblePhone = new IconView(s, IconView.PHONE);
        bubblePhone.setColor(GREEN);
        bubble.addView(bubblePhone, new FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER));
        bubble.setOnClickListener(v -> s.onBubbleTap());
        bubble.setVisibility(View.GONE);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(dp(30), dp(30));
        blp.leftMargin = dp(6);
        row.addView(bubble, blp);

        // ---------- fundo do cartão (gradiente com a cor da capa) ----------
        cardGrad.setOrientation(GradientDrawable.Orientation.BL_TR);
        cardGrad.setColors(new int[]{CARD, CARD});
        cardBg = new View(s);
        cardBg.setBackground(cardGrad);
        pill.addView(cardBg, new FrameLayout.LayoutParams(-1, -1));

        // ---------- forma compacta ----------
        compact = new FrameLayout(s);
        left = new IconSlot(s, false, dp(6), 13);
        FrameLayout.LayoutParams llp = new FrameLayout.LayoutParams(dp(24), dp(24),
                Gravity.START | Gravity.CENTER_VERTICAL);
        llp.leftMargin = dp(10);
        compact.addView(left, llp);
        leftPhone = new IconView(s, IconView.PHONE);
        leftPhone.setColor(GREEN);
        compact.addView(leftPhone, llp);
        eq = new Equalizer(s);
        FrameLayout.LayoutParams elp = new FrameLayout.LayoutParams(dp(20), dp(14),
                Gravity.END | Gravity.CENTER_VERTICAL);
        elp.rightMargin = dp(12);
        compact.addView(eq, elp);
        timeTv = tv(14, GREEN, true, 1);
        FrameLayout.LayoutParams tlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        tlp.rightMargin = dp(14);
        compact.addView(timeTv, tlp);
        compact.setOnClickListener(v -> s.onCompactTap());
        pill.addView(compact, new FrameLayout.LayoutParams(compactW, compactH,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL));

        // ---------- cartão expandido ----------
        LinearLayout top = new LinearLayout(s);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        cover = new IconSlot(s, false, dp(12), 22);
        top.addView(cover, new LinearLayout.LayoutParams(dp(52), dp(52)));
        LinearLayout col = new LinearLayout(s);
        col.setOrientation(LinearLayout.VERTICAL);
        title = tv(16, 0xFFFFFFFF, true, 1);
        text = tv(13, 0xFFC4C4C4, false, 1);
        col.addView(title);
        col.addView(text);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        clp.leftMargin = dp(12);
        top.addView(col, clp);
        timeBig = tv(16, GREEN, true, 1);
        top.addView(timeBig);

        IconView spIc = new IconView(s, IconView.SPEAKER);
        speakerBtn = circleBtn(s, 0x40FFFFFF, spIc);
        speakerBtn.setOnClickListener(v -> s.callSpeaker());
        LinearLayout.LayoutParams sbl = new LinearLayout.LayoutParams(dp(42), dp(42));
        sbl.leftMargin = dp(8);
        top.addView(speakerBtn, sbl);
        IconView hgIc = new IconView(s, IconView.HANGUP);
        hangBtn = circleBtn(s, 0xFFFF3B30, hgIc);
        hangBtn.setOnClickListener(v -> s.callHangup());
        LinearLayout.LayoutParams hbl = new LinearLayout.LayoutParams(dp(42), dp(42));
        hbl.leftMargin = dp(8);
        top.addView(hangBtn, hbl);
        top.setOnClickListener(v -> s.onExpandedTap());

        controls = new LinearLayout(s);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.setGravity(Gravity.CENTER);
        FrameLayout prevB = circleBtn(s, 0x33000000, new IconView(s, IconView.PREV));
        playIc = new IconView(s, IconView.PLAY);
        FrameLayout playB = circleBtn(s, 0x4D000000, playIc);
        FrameLayout nextB = circleBtn(s, 0x33000000, new IconView(s, IconView.NEXT));
        prevB.setOnClickListener(v -> s.mediaPrev());
        playB.setOnClickListener(v -> s.mediaPlayPause());
        nextB.setOnClickListener(v -> s.mediaNext());
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(dp(40), dp(40));
        pl.leftMargin = dp(10);
        pl.rightMargin = dp(10);
        LinearLayout.LayoutParams pbig = new LinearLayout.LayoutParams(dp(54), dp(54));
        pbig.leftMargin = dp(10);
        pbig.rightMargin = dp(10);
        controls.addView(prevB, pl);
        controls.addView(playB, pbig);
        controls.addView(nextB, new LinearLayout.LayoutParams(pl));

        progressRow = new LinearLayout(s);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);
        elapsedTv = tv(11, 0xCCFFFFFF, false, 1);
        durTv = tv(11, 0xCCFFFFFF, false, 1);
        progress = new ProgressView(s);
        progress.setOnSeek(f -> s.mediaSeek(f));
        progressRow.addView(elapsedTv);
        LinearLayout.LayoutParams prl = new LinearLayout.LayoutParams(0, dp(22), 1f);
        prl.leftMargin = dp(6);
        prl.rightMargin = dp(6);
        progressRow.addView(progress, prl);
        progressRow.addView(durTv);

        expanded = new LinearLayout(s);
        expanded.setOrientation(LinearLayout.VERTICAL);
        expanded.setPadding(dp(14), dp(14), dp(14), dp(12));
        expanded.addView(top);
        LinearLayout.LayoutParams ctl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ctl.topMargin = dp(8);
        expanded.addView(controls, ctl);
        LinearLayout.LayoutParams prg = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        prg.topMargin = dp(2);
        expanded.addView(progressRow, prg);
        expLp = new FrameLayout.LayoutParams(expW, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        pill.addView(expanded, expLp);

        setGroupAlpha(0f, 0f);
    }

    // ------------------------------------------------------------------ API

    boolean isActive() { return attached; }

    void show(Content c, Content sec, int target) {
        hasSec = sec != null;
        bind(c, sec);
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
        if (state == ST_COMPACT && (anim == null || !anim.isRunning())) {
            resizeWindow(compactWindowW(), compactH + margin);
        }
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
        lastTime = s;
        timeTv.setText(s);
        if (cur != null) {
            if (cur.type == Content.CALL) text.setText(s);
            else if (cur.type == Content.TIMER) timeBig.setText(s);
        }
    }

    void updateProgress() {
        if (cur == null || cur.type != Content.MEDIA) return;
        long dur = cur.duration, pos = cur.currentPos();
        elapsedTv.setText(fmt(pos));
        durTv.setText(dur > 0 ? fmt(dur) : "--:--");
        progress.setFrac(dur > 0 ? pos / (float) dur : 0f);
    }

    // ------------------------------------------------------------- conteúdo

    private void bind(Content c, Content sec) {
        cur = c;
        boolean media = c.type == Content.MEDIA;
        boolean call = c.type == Content.CALL;
        boolean timer = c.type == Content.TIMER;

        // compacta
        leftPhone.setVisibility(call ? View.VISIBLE : View.GONE);
        left.setVisibility(call ? View.GONE : View.VISIBLE);
        if (!call) left.set(c.icon, glyph(c));
        eq.setVisibility(media ? View.VISIBLE : View.GONE);
        eq.setRunning(media && c.playing);
        timeTv.setVisibility(call || timer ? View.VISIBLE : View.GONE);

        // expandida
        cover.setBg(call ? 0xFF3C9B4D : 0xFF2A2A2A);
        cover.set(c.icon, glyph(c));
        title.setText(c.title);
        if (call) {
            text.setText(lastTime);
            text.setVisibility(View.VISIBLE);
        } else {
            text.setText(c.text);
            text.setVisibility(TextUtils.isEmpty(c.text) ? View.GONE : View.VISIBLE);
        }
        timeBig.setVisibility(timer ? View.VISIBLE : View.GONE);
        speakerBtn.setVisibility(call && c.speaker != null ? View.VISIBLE : View.GONE);
        hangBtn.setVisibility(call && c.hangup != null ? View.VISIBLE : View.GONE);
        controls.setVisibility(media ? View.VISIBLE : View.GONE);
        progressRow.setVisibility(media ? View.VISIBLE : View.GONE);
        playIc.setType(c.playing ? IconView.PAUSE : IconView.PLAY);
        cardGrad.setColors(new int[]{mix(CARD, c.tint, 0.55f), CARD});
        updateProgress();

        // bolinha secundária
        if (sec != null) {
            boolean sc = sec.type == Content.CALL;
            bubblePhone.setVisibility(sc ? View.VISIBLE : View.GONE);
            bubbleCover.setVisibility(sc ? View.GONE : View.VISIBLE);
            if (!sc) bubbleCover.set(sec.icon, glyph(sec));
        }
        setGroupAlpha(ca, ea);
    }

    private String glyph(Content c) {
        switch (c.type) {
            case Content.CALL: {
                CharSequence t = c.title;
                return TextUtils.isEmpty(t) ? "?" : t.toString().substring(0, 1).toUpperCase(Locale.ROOT);
            }
            case Content.TIMER: return "\u23F1";
            case Content.MEDIA: return "\u266A";
            default: return "\uD83D\uDD14";
        }
    }

    private int expandedHeight() {
        expanded.measure(View.MeasureSpec.makeMeasureSpec(expW, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return expanded.getMeasuredHeight();
    }

    private int compactWindowW() {
        return compactW + (hasSec ? bubbleExtra : 0) + 2 * margin;
    }

    // ----------------------------------------------------------- animações

    private void goTo(final int target) {
        final int tw, th, winW;
        final float tr;
        if (target == ST_EXPANDED) {
            tw = expW;
            th = expandedHeight();
            tr = dp(28);
            winW = expW + 2 * margin;
            expLp.height = th;
            expanded.setLayoutParams(expLp);
        } else if (target == ST_COMPACT) {
            tw = compactW;
            th = compactH;
            tr = compactH / 2f;
            winW = compactWindowW();
        } else {
            tw = camPx;
            th = camPx;
            tr = camPx / 2f;
            winW = camPx + 2 * margin;
        }
        if (state == target && pillLp.width == tw && pillLp.height == th) return;
        if (anim != null && anim.isRunning() && animTarget == target
                && animTw == tw && animTh == th) return;

        resizeWindow(Math.max(wlp.width, winW), Math.max(wlp.height, th + margin));
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
                    resizeWindow(compactWindowW(), compactH + margin);
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
        cardBg.setAlpha(e);
        bubble.setAlpha(c);
        bubble.setVisibility(hasSec && c > 0.01f ? View.VISIBLE : View.GONE);
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

    private FrameLayout circleBtn(Context c, int color, IconView ic) {
        FrameLayout f = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(color);
        f.setBackground(g);
        f.addView(ic, new FrameLayout.LayoutParams(-1, -1));
        return f;
    }

    private TextView tv(int sp, int color, boolean bold, int lines) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(null, Typeface.BOLD);
        t.setMaxLines(lines);
        t.setEllipsize(TextUtils.TruncateAt.END);
        return t;
    }

    private static String fmt(long ms) {
        long s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    private static float c01(float v) { return v < 0f ? 0f : (v > 1f ? 1f : v); }
}
