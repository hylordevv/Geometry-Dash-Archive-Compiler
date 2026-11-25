package com.hylor.gdac;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.widget.EditText;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Editor de codigo: numeros de linha, realce de sintaxe, desfazer/refazer. */
public class CodeEditor extends EditText {

    static class Hl extends ForegroundColorSpan {
        Hl(int color) {
            super(color);
        }
    }

    static final Pattern P_NUM = Pattern.compile("-?\\b\\d+(?:\\.\\d+)?\\b");
    static final Pattern P_PROP = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*(?=\\()|\\b[A-Z][A-Z0-9_]*(?=\\s*:)");
    static final Pattern P_KW = Pattern.compile("^\\s*[A-Za-z_][A-Za-z0-9_]*|\\$\\.add", Pattern.MULTILINE);
    static final Pattern P_STR = Pattern.compile("\"(?:[^\"\\\\\\n]|\\\\.)*\"");
    static final Pattern P_CMT = Pattern.compile("(?://|#)[^\\n]*");

    final Paint gutterBgPaint = new Paint();
    final Paint gutterTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint gutterLinePaint = new Paint();
    final int gutterW;
    final float density;

    final ArrayList<String> undoStack = new ArrayList<String>();
    final ArrayList<String> redoStack = new ArrayList<String>();
    String lastSnap = "";
    boolean muted = false;
    Runnable onUserChange;

    final Handler handler = new Handler(Looper.getMainLooper());
    final Runnable snapTask = new Runnable() {
        public void run() {
            snapshot();
        }
    };
    final Runnable hlTask = new Runnable() {
        public void run() {
            highlightVisible();
        }
    };

    public CodeEditor(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        gutterW = (int) (44 * density);
        setTypeface(Typeface.MONOSPACE);
        setTextSize(13);
        setGravity(Gravity.TOP | Gravity.START);
        setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        setHorizontallyScrolling(true);
        setPadding(gutterW + (int) (8 * density), (int) (8 * density), (int) (12 * density), (int) (8 * density));
        gutterTextPaint.setTypeface(Typeface.MONOSPACE);
        gutterTextPaint.setTextAlign(Paint.Align.RIGHT);
        gutterTextPaint.setTextSize(11 * density);
        addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            public void afterTextChanged(Editable e) {
                scheduleHighlight();
                if (muted) {
                    return;
                }
                handler.removeCallbacks(snapTask);
                handler.postDelayed(snapTask, 700);
                if (onUserChange != null) {
                    onUserChange.run();
                }
            }
        });
        applyTheme();
    }

    void applyTheme() {
        setBackgroundColor(Ui.editorBg);
        setTextColor(Ui.editorText);
        setHintTextColor(Ui.gutterText);
        gutterBgPaint.setColor(Ui.gutterBg);
        gutterTextPaint.setColor(Ui.gutterText);
        gutterLinePaint.setColor(Ui.line);
        invalidate();
        scheduleHighlight();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        Layout l = getLayout();
        if (l == null) {
            return;
        }
        int sx = getScrollX();
        int sy = getScrollY();
        int h = getHeight();
        canvas.drawRect(sx, sy, sx + gutterW, sy + h, gutterBgPaint);
        canvas.drawLine(sx + gutterW, sy, sx + gutterW, sy + h, gutterLinePaint);
        int first = l.getLineForVertical(sy);
        int last = l.getLineForVertical(sy + h);
        int top = getExtendedPaddingTop();
        for (int i = first; i <= last; i++) {
            float y = l.getLineBaseline(i) + top;
            canvas.drawText(String.valueOf(i + 1), sx + gutterW - 6 * density, y, gutterTextPaint);
        }
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        scheduleHighlight();
    }

    void scheduleHighlight() {
        if (handler == null) {
            return;
        }
        handler.removeCallbacks(hlTask);
        handler.postDelayed(hlTask, 80);
    }

    void highlightVisible() {
        Layout l = getLayout();
        Editable e = getText();
        if (l == null || e == null || l.getLineCount() == 0) {
            return;
        }
        int lc = l.getLineCount();
        int first = Math.max(0, l.getLineForVertical(getScrollY()) - 25);
        int last = Math.min(lc - 1, l.getLineForVertical(getScrollY() + getHeight()) + 25);
        int rs = l.getLineStart(first);
        int re = l.getLineEnd(last);
        Hl[] old = e.getSpans(rs, re, Hl.class);
        for (int i = 0; i < old.length; i++) {
            e.removeSpan(old[i]);
        }
        paint(e, P_NUM, Ui.cNum, rs, re);
        paint(e, P_PROP, Ui.cProp, rs, re);
        paint(e, P_KW, Ui.cKw, rs, re);
        paint(e, P_STR, Ui.cStr, rs, re);
        paint(e, P_CMT, Ui.cCmt, rs, re);
    }

    static void paint(Editable e, Pattern p, int color, int rs, int re) {
        Matcher m = p.matcher(e);
        m.region(rs, re);
        while (m.find()) {
            e.setSpan(new Hl(color), m.start(), m.end(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    // ---------- edicao ----------

    void insert(String s, int caretFromEnd) {
        int a = getSelectionStart();
        int b = getSelectionEnd();
        if (a < 0 || b < 0) {
            a = length();
            b = a;
        }
        int lo = Math.min(a, b);
        int hi = Math.max(a, b);
        getText().replace(lo, hi, s);
        setSelection(lo + s.length() - caretFromEnd);
    }

    void setTextMuted(String s) {
        muted = true;
        int sel = getSelectionStart();
        setText(s);
        setSelection(Math.max(0, Math.min(sel, s.length())));
        muted = false;
        scheduleHighlight();
    }

    /** Carrega texto novo (limpa o historico). */
    void loadText(String s) {
        setTextMuted(s);
        undoStack.clear();
        redoStack.clear();
        lastSnap = s;
        setSelection(0);
    }

    void snapshot() {
        String cur = getText().toString();
        if (cur.equals(lastSnap)) {
            return;
        }
        if (lastSnap.length() < 600000) {
            undoStack.add(lastSnap);
            if (undoStack.size() > 40) {
                undoStack.remove(0);
            }
            redoStack.clear();
        }
        lastSnap = cur;
    }

    boolean undo() {
        handler.removeCallbacks(snapTask);
        snapshot();
        if (undoStack.isEmpty()) {
            return false;
        }
        String prev = undoStack.remove(undoStack.size() - 1);
        redoStack.add(getText().toString());
        setTextMuted(prev);
        lastSnap = prev;
        if (onUserChange != null) {
            onUserChange.run();
        }
        return true;
    }

    boolean redo() {
        handler.removeCallbacks(snapTask);
        snapshot();
        if (redoStack.isEmpty()) {
            return false;
        }
        String next = redoStack.remove(redoStack.size() - 1);
        undoStack.add(getText().toString());
        setTextMuted(next);
        lastSnap = next;
        if (onUserChange != null) {
            onUserChange.run();
        }
        return true;
    }
}
