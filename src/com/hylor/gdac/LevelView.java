package com.hylor.gdac;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import java.util.ArrayList;

/** Mapa do nivel no estilo do editor do GD: fundo azul, grade, chao, objetos. */
public class LevelView extends View {

    interface Listener {
        void beforeChange();

        void changed();

        void selectionChanged(Level.GObj o);
    }

    static final int BUILD = 0;
    static final int EDIT = 1;
    static final int DELETE = 2;

    static final int K_BLOCK = 0, K_SLAB = 1, K_SPIKE = 2, K_SMALL = 3, K_PAD = 4,
            K_ORB = 5, K_PORTAL = 6, K_TRIGGER = 7, K_OTHER = 8;

    static final int[] PADS = {35, 67, 140, 1332};
    static final int[] ORBS = {36, 84, 141, 1022, 1330, 1333, 1594};
    static final int[] PORTALS = {10, 11, 12, 13, 45, 46, 47, 99, 101, 111, 200, 201, 202, 203,
        286, 287, 660, 745, 747, 1331, 1334, 1933};
    static final int[] TRIGGERS = {899, 901, 1006, 1007, 1049, 1268, 1346, 1347, 1520, 1585, 1595,
        1611, 1612, 1613, 1615, 1616, 1812, 1814, 1815, 1817, 1818, 1819, 1912, 1913, 1914, 1915,
        1916, 1917, 1932, 1934, 1935, 2062, 2063, 2066, 2067, 2068, 2899};

    static final Paint FILL = new Paint(Paint.ANTI_ALIAS_FLAG);
    static final Paint STROKE = new Paint(Paint.ANTI_ALIAS_FLAG);
    static final Paint TXT = new Paint(Paint.ANTI_ALIAS_FLAG);
    static final Path PATH = new Path();
    static final RectF RECT = new RectF();

    static {
        STROKE.setStyle(Paint.Style.STROKE);
        STROKE.setStrokeJoin(Paint.Join.ROUND);
        TXT.setTextAlign(Paint.Align.CENTER);
        TXT.setTypeface(Typeface.DEFAULT_BOLD);
    }

    Level level;
    Listener listener;
    int mode = BUILD;
    int paletteId = 1;
    Level.GObj selected;

    float camX = -30f;
    float camY = -60f;
    float zoom = 1f;
    final float density;
    final int slop;

    final Paint bgPaint = new Paint();
    final Paint gridPaint = new Paint();
    final Paint groundPaint = new Paint();
    final Paint groundLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint selPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint hudPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    final ScaleGestureDetector scaler;

    float downX, downY, lastX, lastY;
    boolean moved, dragObj, dragStarted, fitPending;

    public LevelView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        slop = ViewConfiguration.get(context).getScaledTouchSlop();
        zoom = 1.15f * density;
        gridPaint.setColor(0x26FFFFFF);
        gridPaint.setStrokeWidth(1f);
        groundPaint.setColor(0xFF0C2A75);
        groundLine.setColor(0xFFFFFFFF);
        groundLine.setStrokeWidth(2f * density);
        selPaint.setStyle(Paint.Style.STROKE);
        selPaint.setColor(0xFFFFD43B);
        selPaint.setStrokeWidth(2.5f * density);
        hudPaint.setColor(0xFFFFFFFF);
        hudPaint.setTextSize(12 * density);
        hudPaint.setTypeface(Typeface.DEFAULT_BOLD);
        hudPaint.setShadowLayer(3f * density, 0f, 0f, 0xFF000000);
        scaler = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector d) {
                float nz = Math.max(0.15f * density, Math.min(6f * density, zoom * d.getScaleFactor()));
                float fx = d.getFocusX();
                float fy = d.getFocusY();
                float wx = camX + fx / zoom;
                float wy = camY + (getHeight() - fy) / zoom;
                zoom = nz;
                camX = wx - fx / zoom;
                camY = wy - (getHeight() - fy) / zoom;
                invalidate();
                return true;
            }
        });
    }

    void setLevel(Level l) {
        level = l;
        selected = null;
        invalidate();
    }

    void setMode(int m) {
        mode = m;
        if (m != EDIT) {
            selected = null;
        }
        invalidate();
    }

    void resetCamera() {
        camX = -30f;
        camY = -60f;
        zoom = 1.15f * density;
        invalidate();
    }

    void fit() {
        if (getWidth() == 0 || level == null || level.objs.isEmpty()) {
            fitPending = getWidth() == 0;
            if (getWidth() != 0) {
                resetCamera();
            }
            return;
        }
        float minX = 1e9f, maxX = -1e9f, minY = 1e9f;
        for (int i = 0; i < level.objs.size(); i++) {
            Level.GObj o = level.objs.get(i);
            minX = Math.min(minX, o.x);
            maxX = Math.max(maxX, o.x);
            minY = Math.min(minY, o.y);
        }
        float span = Math.max(300f, maxX - minX + 120f);
        zoom = Math.max(0.15f * density, Math.min(2f * density, getWidth() / span));
        camX = minX - 60f;
        camY = Math.max(-60f, minY - 60f);
        fitPending = false;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        bgPaint.setShader(new LinearGradient(0, 0, 0, h, 0xFF3F8CFF, 0xFF1D52C4, Shader.TileMode.CLAMP));
        if (fitPending) {
            fit();
        }
    }

    // ---------- classificacao e formas ----------

    static boolean in(int id, int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == id) {
                return true;
            }
        }
        return false;
    }

    static int kind(int id) {
        if (id >= 1 && id <= 7) {
            return K_BLOCK;
        }
        if (id == 40) {
            return K_SLAB;
        }
        if (id == 8) {
            return K_SPIKE;
        }
        if (id == 39) {
            return K_SMALL;
        }
        if (in(id, PADS)) {
            return K_PAD;
        }
        if (in(id, ORBS)) {
            return K_ORB;
        }
        if (in(id, PORTALS)) {
            return K_PORTAL;
        }
        if (in(id, TRIGGERS)) {
            return K_TRIGGER;
        }
        return K_OTHER;
    }

    static int accent(int id) {
        switch (id) {
            case 35: case 36: case 11: return 0xFFFFD83D;
            case 67: case 84: case 10: case 201: return 0xFF3DB3FF;
            case 140: case 141: case 13: case 203: return 0xFFFF6FD8;
            case 1022: case 12: case 202: return 0xFF4CE06A;
            case 1332: case 1330: return 0xFF555555;
            case 47: case 111: return 0xFFFF9F43;
            case 660: return 0xFF30D5E8;
            case 745: case 1334: return 0xFFE85050;
            case 1331: return 0xFFB16BFF;
            case 200: return 0xFF9AA0AA;
            default: return 0xFF30D5E8;
        }
    }

    static void box(Canvas c, float l, float t, float r, float b, int fill, int stroke, float round) {
        RECT.set(l, t, r, b);
        FILL.setColor(fill);
        STROKE.setColor(stroke);
        c.drawRoundRect(RECT, round, round, FILL);
        c.drawRoundRect(RECT, round, round, STROKE);
    }

    static void label(Canvas c, String s, float size) {
        if (size < 14f) {
            return;
        }
        TXT.setColor(0xFFFFFFFF);
        TXT.setTextSize(size * 0.34f);
        c.drawText(s, 0f, TXT.getTextSize() * 0.35f, TXT);
    }

    static void drawShape(Canvas c, int id, float cx, float cy, float s, float rot, boolean fx, boolean fy) {
        int k = kind(id);
        float h = s / 2f;
        c.save();
        c.translate(cx, cy);
        if (rot != 0f) {
            c.rotate(rot);
        }
        if (fx || fy) {
            c.scale(fx ? -1f : 1f, fy ? -1f : 1f);
        }
        STROKE.setStrokeWidth(Math.max(1f, s * 0.07f));
        int col = accent(id);
        switch (k) {
            case K_BLOCK:
                box(c, -h, -h, h, h, 0xFF1A1A1A, 0xFFEDEDED, s * 0.05f);
                break;
            case K_SLAB:
                box(c, -h, -h * 0.5f, h, h * 0.5f, 0xFF1A1A1A, 0xFFEDEDED, s * 0.05f);
                break;
            case K_SPIKE:
                PATH.reset();
                PATH.moveTo(-h, h);
                PATH.lineTo(h, h);
                PATH.lineTo(0f, -h);
                PATH.close();
                FILL.setColor(0xFF1A1A1A);
                STROKE.setColor(0xFFEDEDED);
                c.drawPath(PATH, FILL);
                c.drawPath(PATH, STROKE);
                break;
            case K_SMALL:
                PATH.reset();
                PATH.moveTo(-h, h * 0.5f);
                PATH.lineTo(h, h * 0.5f);
                PATH.lineTo(0f, -h * 0.5f);
                PATH.close();
                FILL.setColor(0xFF1A1A1A);
                STROKE.setColor(0xFFEDEDED);
                c.drawPath(PATH, FILL);
                c.drawPath(PATH, STROKE);
                break;
            case K_PAD:
                box(c, -h * 0.9f, h * 0.35f, h * 0.9f, h, col, 0xFFFFFFFF, s * 0.1f);
                break;
            case K_ORB:
                FILL.setColor(col);
                STROKE.setColor(0xFFFFFFFF);
                c.drawCircle(0f, 0f, h * 0.75f, FILL);
                c.drawCircle(0f, 0f, h * 0.75f, STROKE);
                c.drawCircle(0f, 0f, h * 0.4f, STROKE);
                break;
            case K_PORTAL:
                RECT.set(-h * 0.55f, -h * 2.5f, h * 0.55f, h * 2.5f);
                FILL.setColor((col & 0x00FFFFFF) | 0x99000000);
                STROKE.setColor(col);
                c.drawRoundRect(RECT, h * 0.5f, h * 0.5f, FILL);
                c.drawRoundRect(RECT, h * 0.5f, h * 0.5f, STROKE);
                break;
            case K_TRIGGER:
                box(c, -h * 0.85f, -h * 0.85f, h * 0.85f, h * 0.85f, 0xFF6D3FC4, 0xFFFFFFFF, s * 0.12f);
                label(c, "T", s * 1.4f);
                break;
            default:
                box(c, -h, -h, h, h, 0xFF4B5563, 0xFFE5E7EB, s * 0.05f);
                label(c, String.valueOf(id), s);
                break;
        }
        c.restore();
    }

    // ---------- desenho ----------

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth();
        int h = getHeight();
        c.drawRect(0, 0, w, h, bgPaint);
        float step = 30f * zoom;
        if (step >= 7f * density) {
            float wx0 = (float) Math.floor(camX / 30f) * 30f;
            for (float wx = wx0; ; wx += 30f) {
                float sx = (wx - camX) * zoom;
                if (sx > w) {
                    break;
                }
                c.drawLine(sx, 0, sx, h, gridPaint);
            }
            float wy0 = (float) Math.floor(camY / 30f) * 30f;
            for (float wy = wy0; ; wy += 30f) {
                float sy = h - (wy - camY) * zoom;
                if (sy < 0) {
                    break;
                }
                c.drawLine(0, sy, w, sy, gridPaint);
            }
        }
        float gy = h - (0f - camY) * zoom;
        if (gy < h) {
            c.drawRect(0, Math.max(gy, 0f), w, h, groundPaint);
            c.drawLine(0, gy, w, gy, groundLine);
        }
        if (level != null) {
            float s = 30f * zoom;
            ArrayList<Level.GObj> list = level.objs;
            for (int i = 0, n = list.size(); i < n; i++) {
                Level.GObj o = list.get(i);
                float cx = (o.x - camX) * zoom;
                float cy = h - (o.y - camY) * zoom;
                if (cx < -s * 3 || cx > w + s * 3 || cy < -s * 3 || cy > h + s * 3) {
                    continue;
                }
                drawShape(c, o.id, cx, cy, s, o.rot, o.fx, o.fy);
            }
            if (selected != null) {
                float cx = (selected.x - camX) * zoom;
                float cy = h - (selected.y - camY) * zoom;
                float hh = kind(selected.id) == K_PORTAL ? s * 1.4f : s * 0.58f;
                c.drawRect(cx - s * 0.58f, cy - hh, cx + s * 0.58f, cy + hh, selPaint);
            }
            c.drawText(list.size() + " objetos", 10 * density, h - 10 * density, hudPaint);
        }
        String hint = mode == BUILD ? "CONSTRUIR: toque para colocar"
                : mode == EDIT ? "EDITAR: toque para selecionar, arraste para mover"
                : "EXCLUIR: toque no objeto";
        c.drawText(hint, 10 * density, 20 * density, hudPaint);
    }

    // ---------- toque ----------

    static float snap(float v) {
        return (float) Math.floor(v / 30f) * 30f + 15f;
    }

    Level.GObj pick(float wx, float wy) {
        if (level == null) {
            return null;
        }
        float tol = Math.max(15f, 14f * density / zoom);
        for (int i = level.objs.size() - 1; i >= 0; i--) {
            Level.GObj o = level.objs.get(i);
            float hh = kind(o.id) == K_PORTAL ? Math.max(tol, 40f) : tol;
            if (Math.abs(wx - o.x) <= tol && Math.abs(wy - o.y) <= hh) {
                return o;
            }
        }
        return null;
    }

    float worldX(float sx) {
        return camX + sx / zoom;
    }

    float worldY(float sy) {
        return camY + (getHeight() - sy) / zoom;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        scaler.onTouchEvent(e);
        if (scaler.isInProgress()) {
            moved = true;
            return true;
        }
        float x = e.getX();
        float y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downX = x;
                downY = y;
                lastX = x;
                lastY = y;
                moved = false;
                dragStarted = false;
                dragObj = mode == EDIT && selected != null
                        && pick(worldX(x), worldY(y)) == selected;
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                moved = true;
                dragObj = false;
                return true;
            case MotionEvent.ACTION_MOVE:
                if (e.getPointerCount() > 1) {
                    lastX = x;
                    lastY = y;
                    return true;
                }
                if (!moved && Math.hypot(x - downX, y - downY) > slop) {
                    moved = true;
                }
                if (moved) {
                    if (dragObj) {
                        if (!dragStarted) {
                            dragStarted = true;
                            if (listener != null) {
                                listener.beforeChange();
                            }
                        }
                        float nx = snap(worldX(x));
                        float ny = Math.max(15f, snap(worldY(y)));
                        selected.put("2", Level.fmt(nx));
                        selected.put("3", Level.fmt(ny));
                    } else {
                        camX -= (x - lastX) / zoom;
                        camY += (y - lastY) / zoom;
                    }
                    invalidate();
                }
                lastX = x;
                lastY = y;
                return true;
            case MotionEvent.ACTION_UP:
                if (!moved) {
                    tap(x, y);
                } else if (dragObj && dragStarted && listener != null) {
                    listener.changed();
                    listener.selectionChanged(selected);
                }
                return true;
            default:
                return true;
        }
    }

    void tap(float sx, float sy) {
        if (level == null) {
            return;
        }
        float wx = worldX(sx);
        float wy = worldY(sy);
        if (mode == BUILD) {
            float gx = snap(wx);
            float gy = snap(wy);
            if (gy < 15f) {
                return;
            }
            if (listener != null) {
                listener.beforeChange();
            }
            Level.GObj o = Level.make(paletteId, gx, gy);
            level.objs.add(o);
            if (listener != null) {
                listener.changed();
            }
            invalidate();
        } else if (mode == EDIT) {
            selected = pick(wx, wy);
            if (listener != null) {
                listener.selectionChanged(selected);
            }
            invalidate();
        } else {
            Level.GObj o = pick(wx, wy);
            if (o != null) {
                if (listener != null) {
                    listener.beforeChange();
                }
                level.objs.remove(o);
                if (listener != null) {
                    listener.changed();
                }
                invalidate();
            }
        }
    }

    // ---------- acoes sobre o selecionado ----------

    void duplicateSelected() {
        if (selected == null) {
            return;
        }
        if (listener != null) {
            listener.beforeChange();
        }
        Level.GObj c = selected.copy();
        c.put("2", Level.fmt(c.x + 30f));
        level.objs.add(c);
        selected = c;
        if (listener != null) {
            listener.changed();
            listener.selectionChanged(selected);
        }
        invalidate();
    }

    void rotateSelected(float delta) {
        if (selected == null) {
            return;
        }
        if (listener != null) {
            listener.beforeChange();
        }
        float r = (selected.rot + delta) % 360f;
        if (r < 0) {
            r += 360f;
        }
        selected.put("6", Level.fmt(r));
        if (listener != null) {
            listener.changed();
            listener.selectionChanged(selected);
        }
        invalidate();
    }

    void flipSelected() {
        if (selected == null) {
            return;
        }
        if (listener != null) {
            listener.beforeChange();
        }
        if (selected.fx) {
            selected.remove("4");
        } else {
            selected.put("4", "1");
        }
        if (listener != null) {
            listener.changed();
            listener.selectionChanged(selected);
        }
        invalidate();
    }

    void deleteSelected() {
        if (selected == null || level == null) {
            return;
        }
        if (listener != null) {
            listener.beforeChange();
        }
        level.objs.remove(selected);
        selected = null;
        if (listener != null) {
            listener.changed();
            listener.selectionChanged(null);
        }
        invalidate();
    }

    // ---------- cartao da paleta ----------

    static class Card extends View {
        final int id;
        final String text;
        boolean sel;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        final float d;

        Card(Context ctx, int id, String text) {
            super(ctx);
            this.id = id;
            this.text = text;
            d = ctx.getResources().getDisplayMetrics().density;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(10 * d);
        }

        @Override
        protected void onMeasure(int wSpec, int hSpec) {
            setMeasuredDimension((int) (60 * d), (int) (68 * d));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth();
            float h = getHeight();
            RectF r = new RectF(2 * d, 2 * d, w - 2 * d, h - 2 * d);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.panel2);
            c.drawRoundRect(r, 8 * d, 8 * d, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(sel ? 3 * d : 1 * d);
            p.setColor(sel ? Ui.YELLOW : Ui.line);
            c.drawRoundRect(r, 8 * d, 8 * d, p);
            drawShape(c, id, w / 2f, h * 0.40f, 24 * d, 0f, false, false);
            p.setStyle(Paint.Style.FILL);
            p.setColor(Ui.text);
            c.drawText(text, w / 2f, h - 8 * d, p);
        }
    }
}
