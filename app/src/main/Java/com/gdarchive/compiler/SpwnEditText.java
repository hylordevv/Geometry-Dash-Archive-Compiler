package com.gdarchive.compiler;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.*;
import android.text.style.*;
import android.util.AttributeSet;
import android.widget.EditText;
import java.util.*;
import java.util.regex.*;

/** Editor de codigo SPWN: realce de sintaxe (em segundo plano leve), sugestoes e marcacao de erros. */
public class SpwnEditText extends EditText {
    public static final String[] KEYWORDS = {"let", "return", "if", "else", "for", "in", "while", "match", "type", "impl", "extract", "import",
            "obj", "trigger", "break", "continue", "null", "true", "false", "switch", "case", "async", "sync", "is", "as", "mut"};
    public static final String[] COMMON = {"$.print", "$.add", "obj_props", "wait", ".move", ".alpha", ".toggle_on", ".toggle_off", ".pulse",
            ".follow", ".rotate", ".call", "@group", "@color", "@block", "@item", "@number", "@string", "@array", "@dictionary"};
    static final Pattern KW = Pattern.compile("\\b(" + String.join("|", KEYWORDS) + ")\\b");
    static final Pattern NUM = Pattern.compile("\\b\\d+(\\.\\d+)?[gcbi]?\\b");
    static final Pattern STR = Pattern.compile("\"(\\\\.|[^\"\\\\])*\"");
    static final Pattern COM = Pattern.compile("//[^\\n]*|/\\*.*?\\*/", Pattern.DOTALL);
    static final Pattern AT = Pattern.compile("@\\w+|\\$\\.?\\w*");

    final Handler h = new Handler(Looper.getMainLooper());
    boolean busy;
    final Runnable job = this::highlight;

    public SpwnEditText(Context c) {
        super(c);
        setTypeface(Typeface.MONOSPACE);
        setTextSize(13);
        setTextColor(0xFFEEEEEE);
        setHintTextColor(0x66FFFFFF);
        setBackgroundColor(0xCC050A1A);
        setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
        setHorizontallyScrolling(true);
        setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable e) { if (!busy) { h.removeCallbacks(job); h.postDelayed(job, 250); } }
        });
    }

    void paint(Editable e, Pattern p, int color) {
        Matcher m = p.matcher(e);
        while (m.find()) e.setSpan(new ForegroundColorSpan(color), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    void highlight() {
        Editable e = getText();
        if (e.length() > 80000) return; // arquivos enormes: sem realce para poupar RAM
        busy = true;
        for (ForegroundColorSpan s : e.getSpans(0, e.length(), ForegroundColorSpan.class)) e.removeSpan(s);
        paint(e, NUM, 0xFFFFB74D);
        paint(e, AT, 0xFF4DD0E1);
        paint(e, KW, 0xFFFF7043);
        paint(e, STR, 0xFF81C784);
        paint(e, COM, 0xFF90A4AE);
        busy = false;
    }

    /** Palavra sendo digitada na posicao do cursor. */
    public String currentWord() {
        int pos = getSelectionStart();
        if (pos < 0) return "";
        CharSequence t = getText();
        int s = pos;
        while (s > 0 && (Character.isLetterOrDigit(t.charAt(s - 1)) || "_$.@".indexOf(t.charAt(s - 1)) >= 0)) s--;
        return t.subSequence(s, pos).toString();
    }

    public List<String> suggestions() {
        String w = currentWord();
        List<String> r = new ArrayList<>();
        if (w.isEmpty()) return r;
        for (String k : KEYWORDS) if (k.startsWith(w) && !k.equals(w)) r.add(k);
        for (String k : COMMON) if (k.startsWith(w) && !k.equals(w)) r.add(k);
        return r;
    }

    public void replaceCurrentWord(String s) {
        int pos = getSelectionStart();
        int start = pos - currentWord().length();
        getText().replace(start, pos, s);
    }

    public void insert(String s) {
        int a = Math.max(0, getSelectionStart());
        getText().insert(a, s);
    }

    /** Marca linhas citadas na saida do compilador. */
    public void markErrors(String out) {
        Editable e = getText();
        for (BackgroundColorSpan s : e.getSpans(0, e.length(), BackgroundColorSpan.class)) e.removeSpan(s);
        Matcher m = Pattern.compile("(?:line |:)(\\d+)(?::\\d+)?").matcher(out);
        String t = e.toString();
        while (m.find()) {
            int line = Integer.parseInt(m.group(1));
            int idx = 0, cur = 1;
            while (cur < line && idx >= 0) { idx = t.indexOf('\n', idx); if (idx >= 0) idx++; cur++; }
            if (idx < 0 || cur != line) continue;
            int end = t.indexOf('\n', idx);
            if (end < 0) end = t.length();
            if (end > idx) e.setSpan(new BackgroundColorSpan(0x66FF1744), idx, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
