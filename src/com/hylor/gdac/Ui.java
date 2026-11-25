package com.hylor.gdac;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.widget.Button;

/** Tema (claro/escuro) e estilos de botoes no visual do editor do GD. */
public class Ui {

    static boolean dark = true;
    static int bg, panel, panel2, text, dim, line;
    static int editorBg, editorText, gutterBg, gutterText;
    static int cKw, cNum, cStr, cProp, cCmt;

    static final int GREEN = 0xFF5ACB2E;
    static final int GREEN_DK = 0xFF3B9A1A;
    static final int YELLOW = 0xFFFFD43B;
    static final int YELLOW_DK = 0xFFE0A800;
    static final int OUTLINE = 0xFF0B0B0B;

    static void theme(boolean d) {
        dark = d;
        if (d) {
            bg = 0xFF14171C;
            panel = 0xFF1F232A;
            panel2 = 0xFF2A2F38;
            text = 0xFFE8ECF2;
            dim = 0xFF93A0B2;
            line = 0xFF3A414D;
            editorBg = 0xFF0F1216;
            editorText = 0xFFE6EAF0;
            gutterBg = 0xFF181C22;
            gutterText = 0xFF5E6B7D;
            cKw = 0xFFC792EA;
            cNum = 0xFFF78C6C;
            cStr = 0xFFC3E88D;
            cProp = 0xFF82AAFF;
            cCmt = 0xFF637777;
        } else {
            bg = 0xFFF2F4F8;
            panel = 0xFFFFFFFF;
            panel2 = 0xFFE4E8EF;
            text = 0xFF1B2330;
            dim = 0xFF5C6B80;
            line = 0xFFD2D8E2;
            editorBg = 0xFFFFFFFF;
            editorText = 0xFF1B2330;
            gutterBg = 0xFFEEF1F6;
            gutterText = 0xFF8A97AB;
            cKw = 0xFF7C3AED;
            cNum = 0xFFC2410C;
            cStr = 0xFF15803D;
            cProp = 0xFF1D4ED8;
            cCmt = 0xFF94A3B8;
        }
    }

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static GradientDrawable box(Context c, int fill, int stroke, float strokeDp, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) {
            g.setStroke(dp(c, strokeDp), stroke);
        }
        return g;
    }

    static StateListDrawable pressable(Context c, int fill, int pressed, int stroke, float strokeDp, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] { android.R.attr.state_pressed }, box(c, pressed, stroke, strokeDp, radiusDp));
        s.addState(new int[] {}, box(c, fill, stroke, strokeDp, radiusDp));
        return s;
    }

    static void reset(Button b) {
        Context c = b.getContext();
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setStateListAnimator(null);
        b.setPadding(dp(c, 12), dp(c, 8), dp(c, 12), dp(c, 8));
    }

    /** Botao verde estilo GD; selecionado = amarelo. */
    static Button gdButton(Context c, String label, boolean selected) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(13);
        reset(b);
        styleGd(b, selected);
        return b;
    }

    static void styleGd(Button b, boolean selected) {
        Context c = b.getContext();
        if (selected) {
            b.setBackground(pressable(c, YELLOW, YELLOW_DK, OUTLINE, 2, 8));
            b.setTextColor(0xFF1A1400);
        } else {
            b.setBackground(pressable(c, GREEN, GREEN_DK, OUTLINE, 2, 8));
            b.setTextColor(0xFFFFFFFF);
        }
    }

    /** Botao secundario (segue o tema). */
    static Button flatButton(Context c, String label) {
        Button b = new Button(c);
        b.setText(label);
        b.setTextSize(12);
        reset(b);
        b.setBackground(pressable(c, panel2, line, line, 1, 8));
        b.setTextColor(text);
        return b;
    }
}
