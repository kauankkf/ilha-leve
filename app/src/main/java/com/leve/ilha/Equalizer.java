package com.leve.ilha;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.LinearInterpolator;

/** Barrinhas de equalizador. Só anima enquanto a música toca. */
final class Equalizer extends View {
    private static final float[] PH = {0f, 1.7f, 3.1f, 4.4f};
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private ValueAnimator anim;
    private float t;
    private boolean running;

    Equalizer(Context c) {
        super(c);
        p.setColor(0xFF7CFFB2);
    }

    void setRunning(boolean r) {
        if (r == running) return;
        running = r;
        if (r) {
            if (anim == null) {
                anim = ValueAnimator.ofFloat(0f, (float) (2 * Math.PI));
                anim.setDuration(900);
                anim.setRepeatCount(ValueAnimator.INFINITE);
                anim.setInterpolator(new LinearInterpolator());
                anim.addUpdateListener(a -> { t = (Float) a.getAnimatedValue(); invalidate(); });
            }
            if (!anim.isStarted()) anim.start();
        } else {
            if (anim != null) anim.cancel();
            invalidate();
        }
    }

    @Override protected void onDraw(Canvas cv) {
        int bars = PH.length;
        float w = getWidth(), h = getHeight();
        float bw = w / (bars * 2 - 1);
        for (int i = 0; i < bars; i++) {
            float v = running
                    ? 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(t * (1 + i % 2) + PH[i]))
                    : 0.35f;
            float bh = Math.max(bw, v * h);
            float x = i * 2 * bw;
            rect.set(x, (h - bh) / 2f, x + bw, (h + bh) / 2f);
            cv.drawRoundRect(rect, bw / 2f, bw / 2f, p);
        }
    }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }
}
