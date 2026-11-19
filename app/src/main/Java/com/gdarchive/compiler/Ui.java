package com.gdarchive.compiler;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;

/** Estilo visual inspirado no editor do Geometry Dash. */
public class Ui {
    public static final int GREEN = 0xFF3FBF2A, BLUE = 0xFF1E88E5, ORANGE = 0xFFFF8F00, RED = 0xFFE53935, GRAY = 0xFF546E7A, PURPLE = 0xFF8E24AA, DARK = 0xFF0B2E86;

    static int dp(Context c, int v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    static GradientDrawable shape(Context c, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, 6));
        g.setStroke(dp(c, 2), Color.BLACK);
        return g;
    }

    public static Button btn(Context c, String t, int color, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(13);
        b.setTypeface(null, android.graphics.Typeface.BOLD);
        b.setShadowLayer(2, 1, 1, Color.BLACK);
        b.setMinWidth(0); b.setMinHeight(0); b.setMinimumWidth(0); b.setMinimumHeight(0);
        b.setPadding(dp(c, 10), dp(c, 8), dp(c, 10), dp(c, 8));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_pressed}, shape(c, Color.argb(255, Color.red(color) / 2, Color.green(color) / 2, Color.blue(color) / 2)));
        s.addState(new int[]{}, shape(c, color));
        b.setBackground(s);
        b.setOnClickListener(l);
        return b;
    }

    public static android.widget.TextView label(Context c, String t, int size) {
        android.widget.TextView v = new android.widget.TextView(c);
        v.setText(t);
        v.setTextSize(size);
        v.setTextColor(Color.WHITE);
        v.setShadowLayer(3, 2, 2, Color.BLACK);
        v.setTypeface(null, android.graphics.Typeface.BOLD);
        v.setGravity(Gravity.CENTER_VERTICAL);
        return v;
    }

    public static GradientDrawable panel(Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(0xAA000000);
        g.setCornerRadius(dp(c, 8));
        g.setStroke(dp(c, 2), 0xFF000000);
        return g;
    }
}
