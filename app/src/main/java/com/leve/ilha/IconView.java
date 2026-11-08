package com.leve.ilha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** Ícones desenhados no código (sem imagens, APK menor). */
final class IconView extends View {
    static final int PLAY = 0, PAUSE = 1, NEXT = 2, PREV = 3, PHONE = 4, HANGUP = 5, SPEAKER = 6;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF rect = new RectF();
    private int type;

    IconView(Context c, int type) {
        super(c);
        this.type = type;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        setColor(0xFFFFFFFF);
    }

    void setColor(int c) {
        fill.setColor(c);
        stroke.setColor(c);
        invalidate();
    }

    void setType(int t) {
        if (t != type) { type = t; invalidate(); }
    }

    @Override protected void onDraw(Canvas cv) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) * 0.3f;
        path.reset();
        switch (type) {
            case PLAY:
                path.moveTo(cx - r * 0.8f, cy - r * 1.1f);
                path.lineTo(cx + r * 1.1f, cy);
                path.lineTo(cx - r * 0.8f, cy + r * 1.1f);
                path.close();
                cv.drawPath(path, fill);
                break;
            case PAUSE:
                cv.drawRect(cx - r * 0.9f, cy - r, cx - r * 0.25f, cy + r, fill);
                cv.drawRect(cx + r * 0.25f, cy - r, cx + r * 0.9f, cy + r, fill);
                break;
            case NEXT:
                path.moveTo(cx - r, cy - r);
                path.lineTo(cx + r * 0.6f, cy);
                path.lineTo(cx - r, cy + r);
                path.close();
                cv.drawPath(path, fill);
                cv.drawRect(cx + r * 0.75f, cy - r, cx + r * 1.05f, cy + r, fill);
                break;
            case PREV:
                path.moveTo(cx + r, cy - r);
                path.lineTo(cx - r * 0.6f, cy);
                path.lineTo(cx + r, cy + r);
                path.close();
                cv.drawPath(path, fill);
                cv.drawRect(cx - r * 1.05f, cy - r, cx - r * 0.75f, cy + r, fill);
                break;
            case PHONE: {
                float w = r * 0.62f, big = r * 1.35f;
                stroke.setStrokeWidth(w);
                float ax = cx + big * 0.5f, ay = cy - big * 0.5f;
                rect.set(ax - big, ay - big, ax + big, ay + big);
                cv.drawArc(rect, 90f, 90f, false, stroke);
                cv.drawCircle(ax - big, ay, w * 0.75f, fill);
                cv.drawCircle(ax, ay + big, w * 0.75f, fill);
                break;
            }
            case HANGUP: {
                float w = r * 0.62f, big = r * 1.3f;
                stroke.setStrokeWidth(w);
                float ax = cx, ay = cy + big * 0.75f;
                rect.set(ax - big, ay - big, ax + big, ay + big);
                cv.drawArc(rect, 215f, 110f, false, stroke);
                double a1 = Math.toRadians(215), a2 = Math.toRadians(325);
                cv.drawCircle((float) (ax + big * Math.cos(a1)), (float) (ay + big * Math.sin(a1)), w * 0.75f, fill);
                cv.drawCircle((float) (ax + big * Math.cos(a2)), (float) (ay + big * Math.sin(a2)), w * 0.75f, fill);
                break;
            }
            default: { // SPEAKER
                path.moveTo(cx - r * 1.1f, cy - r * 0.4f);
                path.lineTo(cx - r * 0.5f, cy - r * 0.4f);
                path.lineTo(cx + r * 0.1f, cy - r * 0.95f);
                path.lineTo(cx + r * 0.1f, cy + r * 0.95f);
                path.lineTo(cx - r * 0.5f, cy + r * 0.4f);
                path.lineTo(cx - r * 1.1f, cy + r * 0.4f);
                path.close();
                cv.drawPath(path, fill);
                stroke.setStrokeWidth(r * 0.22f);
                rect.set(cx + r * 0.1f - r * 0.6f, cy - r * 0.6f, cx + r * 0.1f + r * 0.6f, cy + r * 0.6f);
                cv.drawArc(rect, -50f, 100f, false, stroke);
                rect.set(cx + r * 0.1f - r * 1.05f, cy - r * 1.05f, cx + r * 0.1f + r * 1.05f, cy + r * 1.05f);
                cv.drawArc(rect, -50f, 100f, false, stroke);
                break;
            }
        }
    }
}
