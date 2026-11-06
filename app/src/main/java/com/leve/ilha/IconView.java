package com.leve.ilha;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/** Ícones de mídia desenhados no código (sem imagens, APK menor). */
final class IconView extends View {
    static final int PLAY = 0, PAUSE = 1, NEXT = 2, PREV = 3;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private int type;

    IconView(Context c, int type) {
        super(c);
        this.type = type;
        p.setColor(0xFFFFFFFF);
    }

    void setType(int t) {
        if (t != type) { type = t; invalidate(); }
    }

    @Override protected void onDraw(Canvas cv) {
        float cx = getWidth() / 2f, cy = getHeight() / 2f;
        float r = Math.min(getWidth(), getHeight()) * 0.28f;
        path.reset();
        switch (type) {
            case PLAY:
                path.moveTo(cx - r * 0.8f, cy - r * 1.1f);
                path.lineTo(cx + r * 1.1f, cy);
                path.lineTo(cx - r * 0.8f, cy + r * 1.1f);
                path.close();
                cv.drawPath(path, p);
                break;
            case PAUSE:
                cv.drawRect(cx - r * 0.9f, cy - r, cx - r * 0.25f, cy + r, p);
                cv.drawRect(cx + r * 0.25f, cy - r, cx + r * 0.9f, cy + r, p);
                break;
            case NEXT:
                path.moveTo(cx - r, cy - r);
                path.lineTo(cx + r * 0.6f, cy);
                path.lineTo(cx - r, cy + r);
                path.close();
                cv.drawPath(path, p);
                cv.drawRect(cx + r * 0.75f, cy - r, cx + r * 1.05f, cy + r, p);
                break;
            default: // PREV
                path.moveTo(cx + r, cy - r);
                path.lineTo(cx - r * 0.6f, cy);
                path.lineTo(cx + r, cy + r);
                path.close();
                cv.drawPath(path, p);
                cv.drawRect(cx - r * 1.05f, cy - r, cx - r * 0.75f, cy + r, p);
                break;
        }
    }
}
