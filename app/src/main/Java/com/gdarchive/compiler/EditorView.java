package com.gdarchive.compiler;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.*;
import java.util.function.Consumer;

/** Editor em Canvas: grade, camera, selecao, mover, rotacionar, escalar, duplicar, desfazer. */
public class EditorView extends View {
    public interface Listener { void onSelection(String info); void onChanged(); }
    public static final int T_SELECT = 0, T_PAN = 1, T_PLACE = 2;

    public GDLevel level = new GDLevel();
    public final Set<GDObject> sel = new LinkedHashSet<>();
    public int tool = T_SELECT, placeId = 1, layerFilter = -1;
    public float snapStep = 30f;
    public boolean multi = false, preview = false;
    public Listener listener;
    float camX = -60, camY = -60, zoom = 1.2f;
    final ArrayDeque<List<String>> undo = new ArrayDeque<>(), redo = new ArrayDeque<>();
    final Paint bg = new Paint(), line = new Paint(), fill = new Paint(), stroke = new Paint(), txt = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Path path = new Path();
    final ScaleGestureDetector sg;
    float lx, ly, downX, downY;
    boolean moved, dragging;
    GDObject hitObj;
    Map<GDObject, float[]> origin = new HashMap<>();
    float accX, accY;
    long lastFrame;

    public EditorView(Context c) {
        super(c);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setAntiAlias(true);
        fill.setAntiAlias(true);
        txt.setColor(Color.WHITE);
        txt.setTextAlign(Paint.Align.CENTER);
        txt.setFakeBoldText(true);
        sg = new ScaleGestureDetector(c, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScale(ScaleGestureDetector d) {
                float fx = d.getFocusX(), fy = getHeight() - d.getFocusY();
                float wx = fx / zoom + camX, wy = fy / zoom + camY;
                zoom = Math.max(0.15f, Math.min(6f, zoom * d.getScaleFactor()));
                camX = wx - fx / zoom;
                camY = wy - fy / zoom;
                invalidate();
                return true;
            }
        });
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        bg.setShader(new LinearGradient(0, 0, 0, h, 0xFF1F6BFF, 0xFF0A2C86, Shader.TileMode.CLAMP));
    }

    // ---------- undo ----------
    List<String> snapshot() {
        List<String> s = new ArrayList<>(level.objects.size());
        for (GDObject o : level.objects) s.add(o.serialize());
        return s;
    }
    void restore(List<String> s) {
        level.objects = new ArrayList<>(s.size());
        for (String t : s) level.objects.add(GDObject.parse(t));
        sel.clear();
    }
    public void pushUndo() {
        undo.push(snapshot());
        if (undo.size() > 20) undo.removeLast();
        redo.clear();
    }
    public void undo() { if (!undo.isEmpty()) { redo.push(snapshot()); restore(undo.pop()); changed(); } }
    public void redo() { if (!redo.isEmpty()) { undo.push(snapshot()); restore(redo.pop()); changed(); } }
    void changed() { invalidate(); if (listener != null) { listener.onChanged(); listener.onSelection(info()); } }

    public void setLevel(GDLevel l) { level = l; sel.clear(); undo.clear(); redo.clear(); camX = -60; camY = -60; changed(); }

    // ---------- acoes ----------
    public void each(Consumer<GDObject> c) { if (sel.isEmpty()) return; pushUndo(); for (GDObject o : sel) c.accept(o); changed(); }
    public void rotate(float deg) { each(o -> o.setF("6", (o.f("6", 0) + deg) % 360)); }
    public void scale(float f) { each(o -> o.setF("32", Math.max(0.05f, o.f("32", 1) * f))); }
    public void setProp(String k, String v) { each(o -> o.set(k, v)); }
    public void addGroup(int g) {
        each(o -> { Set<Integer> s = o.groups(); s.add(g); StringBuilder b = new StringBuilder(); for (int x : s) { if (b.length() > 0) b.append('.'); b.append(x); } o.set("57", b.toString()); });
    }
    public void zOrder(int d) { each(o -> o.set("25", Integer.toString(o.intv("25", 0) + d))); }
    public void duplicate() {
        if (sel.isEmpty()) return;
        pushUndo();
        List<GDObject> n = new ArrayList<>();
        for (GDObject o : sel) { GDObject c = o.copy(); c.setF("2", o.x() + 30); n.add(c); }
        level.objects.addAll(n);
        sel.clear(); sel.addAll(n);
        changed();
    }
    public void delete() {
        if (sel.isEmpty()) return;
        pushUndo();
        level.objects.removeAll(sel);
        sel.clear();
        changed();
    }
    public void selectAll() { sel.clear(); sel.addAll(level.objects); changed(); }
    public void home() { camX = -60; camY = -60; zoom = 1.2f; invalidate(); }
    public void zoomBy(float f) { zoom = Math.max(0.15f, Math.min(6f, zoom * f)); invalidate(); }
    public void togglePreview() {
        preview = !preview;
        lastFrame = 0;
        if (preview) postOnAnimation(frame);
    }
    final Runnable frame = new Runnable() {
        @Override public void run() {
            if (!preview) return;
            long now = System.nanoTime();
            if (lastFrame != 0) camX += 311.6f * (now - lastFrame) / 1e9f;
            lastFrame = now;
            invalidate();
            postOnAnimation(this);
        }
    };

    public String info() {
        if (sel.isEmpty()) return "Objetos: " + level.objects.size() + "  |  zoom " + String.format(Locale.US, "%.2f", zoom);
        if (sel.size() == 1) return GDData.describe(sel.iterator().next());
        return sel.size() + " objetos selecionados";
    }

    float snap(float v) { return snapStep <= 0 ? v : Math.round((v - 15) / snapStep) * snapStep + 15; }
    float wx(float sx) { return sx / zoom + camX; }
    float wy(float sy) { return (getHeight() - sy) / zoom + camY; }

    GDObject hit(float sx, float sy) {
        float x = wx(sx), y = wy(sy);
        for (int i = level.objects.size() - 1; i >= 0; i--) {
            GDObject o = level.objects.get(i);
            if (layerFilter >= 0 && o.intv("20", 0) != layerFilter) continue;
            float h = 15 * Math.max(0.5f, o.f("32", 1)) + 4 / zoom;
            if (Math.abs(o.x() - x) <= h && Math.abs(o.y() - y) <= h) return o;
        }
        return null;
    }

    // ---------- toque ----------
    @Override public boolean onTouchEvent(MotionEvent e) {
        sg.onTouchEvent(e);
        if (sg.isInProgress() || e.getPointerCount() > 1) { moved = true; return true; }
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lx = downX = x; ly = downY = y; moved = false; dragging = false; accX = accY = 0;
                hitObj = tool == T_SELECT ? hit(x, y) : null;
                if (hitObj != null && sel.contains(hitObj)) {
                    origin.clear();
                    for (GDObject o : sel) origin.put(o, new float[]{o.x(), o.y()});
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!moved && Math.hypot(x - downX, y - downY) < 12) return true;
                if (!moved && hitObj != null && sel.contains(hitObj) && tool == T_SELECT) { pushUndo(); dragging = true; }
                moved = true;
                if (dragging) {
                    accX += (x - lx) / zoom; accY -= (y - ly) / zoom;
                    for (Map.Entry<GDObject, float[]> en : origin.entrySet()) {
                        en.getKey().setF("2", snap(en.getValue()[0] + accX));
                        en.getKey().setF("3", snap(en.getValue()[1] + accY));
                    }
                } else {
                    camX -= (x - lx) / zoom; camY += (y - ly) / zoom;
                }
                lx = x; ly = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
                if (dragging) { changed(); return true; }
                if (!moved) tap(x, y);
                return true;
            default: return true;
        }
    }

    void tap(float x, float y) {
        if (tool == T_PLACE) {
            pushUndo();
            GDObject o = new GDObject();
            o.set("1", Integer.toString(placeId));
            o.setF("2", snap(wx(x)));
            o.setF("3", snap(wy(y)));
            level.objects.add(o);
            sel.clear(); sel.add(o);
            changed();
            return;
        }
        GDObject h = hit(x, y);
        if (h == null) { if (!multi) sel.clear(); }
        else if (multi) { if (!sel.remove(h)) sel.add(h); }
        else { sel.clear(); sel.add(h); }
        changed();
    }

    // ---------- desenho ----------
    @Override protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawRect(0, 0, w, h, bg);
        float step = 30 * zoom;
        if (step > 7) {
            line.setColor(0x33FFFFFF);
            line.setStrokeWidth(1);
            for (float gx = (float) Math.floor(camX / 30) * 30; (gx - camX) * zoom < w; gx += 30) {
                float sx = (gx - camX) * zoom;
                line.setStrokeWidth(Math.floorMod(Math.round(gx / 30), 5) == 0 ? 2 : 1);
                c.drawLine(sx, 0, sx, h, line);
            }
            for (float gy = (float) Math.floor(camY / 30) * 30; ; gy += 30) {
                float sy = h - (gy - camY) * zoom;
                if (sy < 0) break;
                line.setStrokeWidth(1);
                c.drawLine(0, sy, w, sy, line);
            }
        }
        float gy = h - (0 - camY) * zoom;
        if (gy < h) {
            fill.setColor(0xFF0B1F66);
            c.drawRect(0, gy, w, h, fill);
            line.setColor(Color.WHITE);
            line.setStrokeWidth(3);
            c.drawLine(0, gy, w, gy, line);
        }
        for (GDObject o : level.objects) {
            if (layerFilter >= 0 && o.intv("20", 0) != layerFilter) continue;
            float s = 30 * zoom * Math.max(0.05f, o.f("32", 1));
            float sx = (o.x() - camX) * zoom, sy = h - (o.y() - camY) * zoom;
            if (sx < -s || sx > w + s || sy < -s || sy > h + s) continue;
            c.save();
            c.rotate(o.f("6", 0), sx, sy);
            drawObj(c, o, sx, sy, s);
            if (sel.contains(o)) {
                stroke.setColor(0xFFFFEB3B);
                stroke.setStrokeWidth(3);
                c.drawRect(sx - s / 2 - 2, sy - s / 2 - 2, sx + s / 2 + 2, sy + s / 2 + 2, stroke);
            }
            c.restore();
        }
    }

    void drawObj(Canvas c, GDObject o, float sx, float sy, float s) {
        int id = o.id();
        float r = s / 2;
        stroke.setStrokeWidth(Math.max(1.5f, s / 12));
        if (GDData.TRIGGERS.containsKey(id)) {
            fill.setColor(0xFFFF8F00); stroke.setColor(Color.BLACK);
            c.drawRoundRect(sx - r, sy - r, sx + r, sy + r, r / 4, r / 4, fill);
            c.drawRoundRect(sx - r, sy - r, sx + r, sy + r, r / 4, r / 4, stroke);
            txt.setTextSize(Math.max(8, s * 0.3f));
            c.drawText(GDData.TRIGGERS.get(id).substring(0, Math.min(3, GDData.TRIGGERS.get(id).length())), sx, sy + s * 0.1f, txt);
        } else if (GDData.SPIKES.contains(id)) {
            path.reset();
            path.moveTo(sx - r, sy + r); path.lineTo(sx, sy - r); path.lineTo(sx + r, sy + r); path.close();
            fill.setColor(0xFF111111); stroke.setColor(Color.WHITE);
            c.drawPath(path, fill); c.drawPath(path, stroke);
        } else if (GDData.ORBS.contains(id)) {
            int[] cols = {0xFFFFEB3B, 0xFF29B6F6, 0xFFEC407A, 0xFF66BB6A, 0xFF212121, 0xFFEF5350};
            fill.setColor(cols[Math.abs(id) % cols.length]); stroke.setColor(Color.WHITE);
            c.drawCircle(sx, sy, r * 0.8f, fill); c.drawCircle(sx, sy, r * 0.8f, stroke);
        } else if (GDData.PADS.contains(id)) {
            fill.setColor(0xFFFFEB3B); stroke.setColor(Color.BLACK);
            c.drawRect(sx - r, sy + r * 0.6f, sx + r, sy + r, fill);
            c.drawRect(sx - r, sy + r * 0.6f, sx + r, sy + r, stroke);
        } else if (GDData.PORTALS.contains(id)) {
            fill.setColor(0x9900E5FF); stroke.setColor(0xFF00E5FF);
            c.drawRoundRect(sx - r * 0.5f, sy - r * 1.6f, sx + r * 0.5f, sy + r * 1.6f, r, r, fill);
            c.drawRoundRect(sx - r * 0.5f, sy - r * 1.6f, sx + r * 0.5f, sy + r * 1.6f, r, r, stroke);
        } else {
            int hsv = Math.abs(id * 47) % 360;
            fill.setColor(Color.HSVToColor(new float[]{hsv, 0.35f, 0.55f}));
            stroke.setColor(Color.WHITE);
            c.drawRect(sx - r, sy - r, sx + r, sy + r, fill);
            c.drawRect(sx - r, sy - r, sx + r, sy + r, stroke);
        }
    }
}
