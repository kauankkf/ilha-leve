package com.leve.ilha;

import android.app.PendingIntent;
import android.graphics.drawable.Drawable;
import android.os.SystemClock;

/** O que a ilha está mostrando. */
final class Content {
    static final int MEDIA = 0, CALL = 1, TIMER = 2, TOAST = 3;
    int type;
    CharSequence title, text;
    Drawable icon;
    boolean playing, chrono, countdown;
    long when;
    PendingIntent tap, hangup, speaker;
    String key;
    int tint = 0xFF3A3A3A;
    long position, posTime, duration;
    float speed = 1f;

    long currentPos() {
        long p = position;
        if (playing) p += (long) ((SystemClock.elapsedRealtime() - posTime) * speed);
        if (duration > 0 && p > duration) p = duration;
        return p < 0 ? 0 : p;
    }
}
