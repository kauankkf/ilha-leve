package com.leve.ilha;

import android.content.Context;
import android.graphics.Outline;
import android.graphics.drawable.Drawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

/** Quadradinho/bolinha com capa ou ícone; se não houver imagem, mostra um emoji. */
final class IconSlot extends FrameLayout {
    private final ImageView img;
    private final TextView glyph;

    IconSlot(Context c, final boolean oval, final int radius, int glyphSp) {
        super(c);
        setBackgroundColor(0xFF2A2A2A);
        img = new ImageView(c);
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        glyph = new TextView(c);
        glyph.setGravity(Gravity.CENTER);
        glyph.setTextSize(glyphSp);
        glyph.setTextColor(0xFFFFFFFF);
        addView(img, new FrameLayout.LayoutParams(-1, -1));
        addView(glyph, new FrameLayout.LayoutParams(-1, -1));
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                if (oval) o.setOval(0, 0, v.getWidth(), v.getHeight());
                else o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        setClipToOutline(true);
    }

    void set(Drawable d, String g) {
        if (d != null) {
            img.setImageDrawable(d);
            img.setVisibility(View.VISIBLE);
            glyph.setVisibility(View.GONE);
        } else {
            img.setImageDrawable(null);
            img.setVisibility(View.GONE);
            glyph.setVisibility(View.VISIBLE);
            glyph.setText(g);
        }
    }
}
