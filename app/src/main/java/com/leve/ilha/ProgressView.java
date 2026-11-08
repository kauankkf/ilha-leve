package com.leve.ilha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

/** Barra de progresso da música; arrastar e soltar faz seek. */
final class ProgressView extends View {
    interface OnSeek { void onSeek(float frac); }

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float knobR, barH;
    private float frac;
    private boolean dragging;
    private OnSeek cb;

    ProgressView(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        knobR = 6 * d;
        barH = 4 * d;
        track.setColor(0x4DFFFFFF);
        fill.setColor(0xFFFFFFFF);
    }

    void setOnSeek(OnSeek o) { cb = o; }

    void setFrac(float f) {
        if (dragging) return;
        frac = f < 0 ? 0 : (f > 1 ? 1 : f);
        invalidate();
    }

    @Override protected void onDraw(Canvas cv) {
        float cy = getHeight() / 2f, x0 = knobR, x1 = getWidth() - knobR;
        cv.drawRoundRect(x0, cy - barH / 2, x1, cy + barH / 2, barH / 2, barH / 2, track);
        float x = x0 + (x1 - x0) * frac;
        cv.drawRoundRect(x0, cy - barH / 2, x, cy + barH / 2, barH / 2, barH / 2, fill);
        cv.drawCircle(x, cy, knobR, fill);
    }

    private void move(float x) {
        float x0 = knobR, x1 = getWidth() - knobR;
        float f = (x - x0) / Math.max(1f, x1 - x0);
        frac = f < 0 ? 0 : (f > 1 ? 1 : f);
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                dragging = true;
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                move(e.getX());
                return true;
            case MotionEvent.ACTION_MOVE:
                move(e.getX());
                return true;
            case MotionEvent.ACTION_UP:
                dragging = false;
                move(e.getX());
                if (cb != null) cb.onSeek(frac);
                return true;
            case MotionEvent.ACTION_CANCEL:
                dragging = false;
                return true;
            default:
                return super.onTouchEvent(e);
        }
    }
}
