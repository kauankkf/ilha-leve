package com.leve.ilha;

import android.app.PendingIntent;
import android.graphics.drawable.Drawable;

/** O que a ilha está mostrando agora. */
final class Content {
    static final int MEDIA = 0, CALL = 1, TIMER = 2, TOAST = 3;
    int type;
    CharSequence title, text;
    Drawable icon;
    boolean playing, chrono, countdown;
    long when;
    PendingIntent tap;
    String key;
}
