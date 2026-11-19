package com.gdarchive.compiler;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

public class MainActivity extends Activity {
    static final int RQ_OPEN = 1, RQ_EXPORT = 2, RQ_SAVE_SPWN = 3, RQ_IMPORT_SPWN = 4, RQ_SAVE_TXT = 5, RQ_OPEN_PROJ = 6, RQ_SAVE_PROJ = 7;
    static final String STARTER = "// Geometry Dash Archive Compiler - SPWN\nextract obj_props\n\n$.print(\"Ola, GD!\")\n$.add(obj {\n    OBJ_ID: 1,\n    X: 45,\n    Y: 45,\n})\n";

    GDLevel level = new GDLevel();
    EditorView editor;
    SpwnEditText script;
    EditText decompOut, aiIn;
    TextView console, aiOut, info;
    ListView trigList;
    LinearLayout suggestBar;
    FrameLayout content;
    View[] tabs = new View[5];
    byte[] pendingBytes;
    String lastBuiltLevel;
    final ExecutorService ex = Executors.newSingleThreadExecutor();
    final Handler ui = new Handler(Looper.getMainLooper());
    final List<GDObject> trigObjs = new ArrayList<>();

    int dp(int v) { return Ui.dp(this, v); }
    void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        android.graphics.drawable.GradientDrawable bgd = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[]{0xFF1F6BFF, 0xFF0A2C86});
        root.setBackground(bgd);
        root.setPadding(0, dp(24), 0, 0);

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(8), dp(4), dp(8), dp(4));
        TextView title = Ui.label(this, "Geometry Dash Archive Compiler", 15);
        title.setTextColor(0xFFFFD400);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(Ui.btn(this, "Menu", Ui.GREEN, v -> mainMenu()));
        root.addView(top);

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        tabs[0] = buildEditorTab();
        tabs[1] = buildScriptTab();
        tabs[2] = buildDecompTab();
        tabs[3] = buildTriggerTab();
        tabs[4] = buildAiTab();
        for (View v : tabs) content.addView(v, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout bar = new LinearLayout(this);
        bar.setPadding(dp(4), dp(4), dp(4), dp(4));
        String[] names = {"Editor", "Script", "Decomp", "Triggers", "IA"};
        int[] cols = {Ui.GREEN, Ui.BLUE, Ui.PURPLE, Ui.ORANGE, Ui.RED};
        for (int i = 0; i < 5; i++) {
            final int k = i;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(dp(2), 0, dp(2), 0);
            bar.addView(Ui.btn(this, names[i], cols[i], v -> showTab(k)), lp);
        }
        root.addView(bar);
        setContentView(root);

        loadAutosave();
        editor.setLevel(level);
        showTab(0);
    }

    void showTab(int k) {
        for (int i = 0; i < tabs.length; i++) tabs[i].setVisibility(i == k ? View.VISIBLE : View.GONE);
        if (k == 3) refreshTriggers();
    }

    // ================= ABA EDITOR =================
    View buildEditorTab() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        editor = new EditorView(this);
        editor.listener = new EditorView.Listener() {
            public void onSelection(String s) { info.setText(s); }
            public void onChanged() { autosave(); }
        };
        l.addView(editor, new LinearLayout.LayoutParams(-1, 0, 1));
        info = Ui.label(this, "", 12);
        info.setBackground(Ui.panel(this));
        info.setPadding(dp(8), dp(4), dp(8), dp(4));
        l.addView(info);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout tb = new LinearLayout(this);
        hs.addView(tb);
        l.addView(hs);
        add(tb, "Selec.", Ui.BLUE, () -> editor.tool = EditorView.T_SELECT);
        add(tb, "Mao", Ui.BLUE, () -> editor.tool = EditorView.T_PAN);
        add(tb, "Colocar", Ui.GREEN, () -> { editor.tool = EditorView.T_PLACE; palette(); });
        add(tb, "Multi", Ui.GRAY, () -> { editor.multi = !editor.multi; toast("Multi-selecao: " + (editor.multi ? "ligada" : "desligada")); });
        add(tb, "Todos", Ui.GRAY, () -> editor.selectAll());
        add(tb, "Desfazer", Ui.ORANGE, () -> editor.undo());
        add(tb, "Refazer", Ui.ORANGE, () -> editor.redo());
        add(tb, "Rot 90", Ui.BLUE, () -> editor.rotate(90));
        add(tb, "Rot 15", Ui.BLUE, () -> editor.rotate(15));
        add(tb, "Escala +", Ui.BLUE, () -> editor.scale(1.25f));
        add(tb, "Escala -", Ui.BLUE, () -> editor.scale(0.8f));
        add(tb, "Duplicar", Ui.GREEN, () -> editor.duplicate());
        add(tb, "Apagar", Ui.RED, () -> editor.delete());
        add(tb, "Grupo", Ui.PURPLE, () -> input("Adicionar ao grupo ID", "ex: 5", "", s -> { try { editor.addGroup(Integer.parseInt(s.trim())); } catch (Exception e) { toast("ID invalido"); } }));
        add(tb, "Z +", Ui.GRAY, () -> editor.zOrder(1));
        add(tb, "Z -", Ui.GRAY, () -> editor.zOrder(-1));
        add(tb, "Cor", Ui.PURPLE, () -> input("Canal de cor 1 (chave 21)", "ex: 1005", "", s -> editor.setProp("21", s.trim())));
        add(tb, "Props", Ui.ORANGE, () -> props());
        add(tb, "Layer", Ui.GRAY, () -> { editor.layerFilter = editor.layerFilter >= 3 ? -1 : editor.layerFilter + 1; toast("Layer: " + (editor.layerFilter < 0 ? "todas" : editor.layerFilter)); editor.invalidate(); });
        add(tb, "Mover layer", Ui.GRAY, () -> input("Layer do editor (chave 20)", "0..", "0", s -> editor.setProp("20", s.trim())));
        add(tb, "Grade", Ui.GRAY, () -> { float[] st = {30, 15, 7.5f, 0}; int i = 0; while (i < 3 && st[i] != editor.snapStep) i++; editor.snapStep = st[(i + 1) % 4]; toast("Snap: " + (editor.snapStep == 0 ? "off" : editor.snapStep)); });
        add(tb, "Zoom +", Ui.GRAY, () -> editor.zoomBy(1.3f));
        add(tb, "Zoom -", Ui.GRAY, () -> editor.zoomBy(0.77f));
        add(tb, "Inicio", Ui.GRAY, () -> editor.home());
        add(tb, "Preview", Ui.GREEN, () -> editor.togglePreview());
        return l;
    }

    void add(LinearLayout row, String t, int col, Runnable r) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        row.addView(Ui.btn(this, t, col, v -> r.run()), lp);
    }

    void palette() {
        final String[] cats = GDData.PALETTE.keySet().toArray(new String[0]);
        String[] items = new String[cats.length + 1];
        System.arraycopy(cats, 0, items, 0, cats.length);
        items[cats.length] = "ID personalizado...";
        new AlertDialog.Builder(this).setTitle("Categorias de objetos").setItems(items, (d, w) -> {
            if (w == cats.length) { input("ID do objeto", "ex: 1", "1", s -> setPlace(s)); return; }
            int[] ids = GDData.PALETTE.get(cats[w]);
            String[] n = new String[ids.length];
            for (int i = 0; i < ids.length; i++) n[i] = GDData.name(ids[i]) + " (" + ids[i] + ")";
            new AlertDialog.Builder(this).setTitle(cats[w]).setItems(n, (d2, i) -> { editor.placeId = ids[i]; toast("Toque no mapa para colocar " + n[i]); }).show();
        }).show();
    }
    void setPlace(String s) { try { editor.placeId = Integer.parseInt(s.trim()); toast("Toque no mapa para colocar"); } catch (Exception e) { toast("ID invalido"); } }

    void props() {
        if (editor.sel.isEmpty()) { toast("Selecione um objeto."); return; }
        final boolean one = editor.sel.size() == 1;
        StringBuilder sb = new StringBuilder();
        if (one) for (Map.Entry<String, String> e : editor.sel.iterator().next().p.entrySet()) sb.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        StringBuilder legend = new StringBuilder();
        for (Map.Entry<String, String> e : GDData.KEYS.entrySet()) legend.append(e.getKey()).append(':').append(e.getValue()).append("  ");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        TextView lg = new TextView(this);
        lg.setText((one ? "Uma chave=valor por linha.\n" : "Chaves aplicadas a TODOS os selecionados.\n") + legend);
        lg.setTextSize(11);
        box.addView(lg);
        final EditText et = new EditText(this);
        et.setTypeface(Typeface.MONOSPACE);
        et.setText(sb.toString());
        et.setMinLines(6);
        et.setGravity(Gravity.TOP);
        box.addView(et);
        ScrollView sv = new ScrollView(this);
        sv.addView(box);
        new AlertDialog.Builder(this).setTitle("Propriedades").setView(sv).setPositiveButton("Aplicar", (d, w) -> {
            Map<String, String> kv = new LinkedHashMap<>();
            for (String ln : et.getText().toString().split("\n")) {
                int i = ln.indexOf('=');
                if (i > 0) kv.put(ln.substring(0, i).trim(), ln.substring(i + 1).trim());
            }
            if (one) editor.each(o -> { o.p.clear(); o.p.putAll(kv); });
            else editor.each(o -> o.p.putAll(kv));
        }).setNegativeButton("Cancelar", null).show();
    }

    // ================= ABA SCRIPT =================
    View buildScriptTab() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        script = new SpwnEditText(this);
        script.setText(STARTER);
        l.addView(script, new LinearLayout.LayoutParams(-1, 0, 2));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        suggestBar = new LinearLayout(this);
        hs.addView(suggestBar);
        l.addView(hs);
        script.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(android.text.Editable e) { refreshSuggestions(); }
        });
        HorizontalScrollView ks = new HorizontalScrollView(this);
        LinearLayout kr = new LinearLayout(this);
        ks.addView(kr);
        for (String s : new String[]{"{", "}", "(", ")", "[", "]", ":", ";", ".", "$", "@", "\"", "=", "->", "=>", "//", "  "})
            add(kr, s, Ui.GRAY, () -> script.insert(s));
        l.addView(ks);
        LinearLayout row = new LinearLayout(this);
        add(row, "Compilar", Ui.GREEN, this::build);
        add(row, "Importar resultado", Ui.BLUE, this::importBuilt);
        add(row, "Importar .spwn", Ui.GRAY, () -> pick(RQ_IMPORT_SPWN));
        add(row, "Salvar .spwn", Ui.GRAY, () -> save(RQ_SAVE_SPWN, "script.spwn", script.getText().toString().getBytes()));
        HorizontalScrollView hr = new HorizontalScrollView(this);
        hr.addView(row);
        l.addView(hr);
        console = new TextView(this);
        console.setTypeface(Typeface.MONOSPACE);
        console.setTextSize(11);
        console.setTextColor(0xFFB2FF59);
        console.setBackgroundColor(0xCC000000);
        console.setText("Saida do compilador SPWN aparece aqui.");
        ScrollView sv = new ScrollView(this);
        sv.addView(console);
        l.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        return l;
    }

    void refreshSuggestions() {
        suggestBar.removeAllViews();
        for (String s : script.suggestions()) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins(dp(2), dp(2), dp(2), dp(2));
            suggestBar.addView(Ui.btn(this, s, Ui.PURPLE, v -> script.replaceCurrentWord(s)), lp);
        }
    }

    void build() {
        if (!SpwnRunner.available(this)) {
            console.setText("libspwn.so (compilador SPWN original) nao esta neste APK.\nCompile pelo workflow do GitHub Actions (veja README). Nada foi simulado.");
            return;
        }
        console.setText("Compilando com o SPWN original...");
        final String code = script.getText().toString();
        final String args = Ai.prefs(this).getString("spwn_args", "--no-level");
        ex.execute(() -> {
            String out;
            try { out = SpwnRunner.run(this, code, args); } catch (Exception e) { out = "ERRO: " + e; }
            final String o = out;
            ui.post(() -> {
                console.setText(o);
                script.markErrors(o);
                lastBuiltLevel = SpwnRunner.findLevelString(o);
                if (lastBuiltLevel != null) toast("Nivel gerado. Toque em Importar resultado.");
            });
        });
    }

    void importBuilt() {
        if (lastBuiltLevel == null) { toast("Nenhum level string na ultima saida. Ajuste os argumentos em Configuracoes."); return; }
        loadLevelString(lastBuiltLevel, "SPWN");
    }

    // ================= ABA DECOMPILADOR =================
    View buildDecompTab() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout r = new LinearLayout(this);
        hs.addView(r);
        add(r, "Dados estruturados", Ui.BLUE, () -> run(() -> Decompiler.structured(level), s -> decompOut.setText(s)));
        add(r, "Reconstrucao SPWN-like", Ui.PURPLE, () -> run(() -> Decompiler.spwnLike(level), s -> decompOut.setText(s)));
        add(r, "Aplicar JSON ao nivel", Ui.GREEN, this::applyStructured);
        add(r, "Colar level string", Ui.ORANGE, () -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm.hasPrimaryClip() && cm.getPrimaryClip().getItemCount() > 0) loadLevelString(cm.getPrimaryClip().getItemAt(0).getText().toString(), "area de transferencia");
        });
        add(r, "Texto bruto", Ui.GRAY, () -> decompOut.setText(level.toRaw()));
        add(r, "Mandar p/ Script", Ui.GRAY, () -> { script.setText(decompOut.getText()); showTab(1); });
        add(r, "Salvar saida", Ui.GRAY, () -> save(RQ_SAVE_TXT, "decompilado.txt", decompOut.getText().toString().getBytes()));
        l.addView(hs);
        decompOut = new EditText(this);
        decompOut.setTypeface(Typeface.MONOSPACE);
        decompOut.setTextSize(11);
        decompOut.setTextColor(Color.WHITE);
        decompOut.setBackgroundColor(0xCC050A1A);
        decompOut.setGravity(Gravity.TOP);
        decompOut.setHorizontallyScrolling(true);
        decompOut.setHint("A reconstrucao SPWN-like NAO e o codigo original. O JSON estruturado preserva todos os campos e pode ser editado e reaplicado.");
        l.addView(decompOut, new LinearLayout.LayoutParams(-1, 0, 1));
        return l;
    }

    interface Job<T> { T call() throws Exception; }
    <T> void run(Job<T> j, Consumer<T> done) {
        ex.execute(() -> {
            try { final T r = j.call(); ui.post(() -> done.accept(r)); }
            catch (Exception e) { ui.post(() -> toast("Erro: " + e)); }
        });
    }

    void applyStructured() {
        try {
            GDLevel n = Decompiler.fromStructured(decompOut.getText().toString(), level);
            List<String> issues = Logic.validate(n);
            backup("antes-de-aplicar");
            editor.pushUndo();
            level.header = n.header;
            level.objects = n.objects;
            editor.setLevel(level);
            toast("Aplicado. Validacao: " + issues.get(0));
        } catch (Exception e) { toast("JSON invalido: " + e.getMessage()); }
    }

    // ================= ABA TRIGGERS =================
    View buildTriggerTab() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout r = new LinearLayout(this);
        hs.addView(r);
        add(r, "Atualizar", Ui.BLUE, this::refreshTriggers);
        add(r, "Validar refs", Ui.ORANGE, () -> text("Validacao", String.join("\n", Logic.validate(level))));
        add(r, "Grafo de logica", Ui.PURPLE, () -> text("Grafo", Logic.graph(level)));
        add(r, "Organizar", Ui.GREEN, this::organize);
        l.addView(hs);
        trigList = new ListView(this);
        trigList.setBackgroundColor(0x88000000);
        l.addView(trigList, new LinearLayout.LayoutParams(-1, 0, 1));
        trigList.setOnItemClickListener((p, v, i, id) -> {
            GDObject o = trigObjs.get(i);
            editor.sel.clear(); editor.sel.add(o);
            editor.camX = o.x() - 150; editor.camY = o.y() - 150;
            editor.changed();
            props();
        });
        return l;
    }

    void refreshTriggers() {
        trigObjs.clear();
        List<String> rows = new ArrayList<>();
        for (GDObject o : level.objects) if (GDData.TRIGGERS.containsKey(o.id())) { trigObjs.add(o); rows.add(GDData.describe(o)); }
        trigList.setAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, rows) {
            @Override public View getView(int p, View c, ViewGroup g) {
                TextView t = (TextView) super.getView(p, c, g);
                t.setTextColor(Color.WHITE); t.setTextSize(12); t.setTypeface(Typeface.MONOSPACE);
                return t;
            }
        });
        if (rows.isEmpty()) toast("Nenhum trigger no nivel. Adicione em Editor > Colocar > Triggers.");
    }

    void organize() {
        new AlertDialog.Builder(this).setTitle("Auto organizador").setMessage(Logic.organizePreview(level))
                .setPositiveButton("Aplicar (com backup)", (d, w) -> {
                    backup("antes-de-organizar");
                    editor.pushUndo();
                    Logic.organizeApply(level);
                    editor.changed();
                    toast("Organizado. Use Desfazer no Editor para reverter.");
                }).setNegativeButton("Cancelar", null).show();
    }

    // ================= ABA IA =================
    View buildAiTab() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        aiIn = new EditText(this);
        aiIn.setHint("Pergunte sobre SPWN, triggers, erros...");
        aiIn.setTextColor(Color.WHITE);
        aiIn.setHintTextColor(0x88FFFFFF);
        aiIn.setBackgroundColor(0xCC050A1A);
        aiIn.setMinLines(3);
        aiIn.setGravity(Gravity.TOP);
        l.addView(aiIn, new LinearLayout.LayoutParams(-1, -2));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout r = new LinearLayout(this);
        hs.addView(r);
        add(r, "Perguntar", Ui.GREEN, () -> ai(aiIn.getText().toString()));
        add(r, "Explicar script", Ui.BLUE, () -> ai("Explique este script SPWN:\n" + cut(script.getText().toString())));
        add(r, "Corrigir erro", Ui.RED, () -> ai("Este script SPWN deu erro. Explique e sugira correcao.\nSCRIPT:\n" + cut(script.getText().toString()) + "\nSAIDA:\n" + cut(console.getText().toString())));
        add(r, "Explicar triggers", Ui.PURPLE, () -> ai("Explique a logica destes triggers de Geometry Dash:\n" + cut(Logic.graph(level))));
        l.addView(hs);
        aiOut = new TextView(this);
        aiOut.setTextColor(Color.WHITE);
        aiOut.setTextSize(13);
        aiOut.setTextIsSelectable(true);
        aiOut.setPadding(dp(8), dp(8), dp(8), dp(8));
        aiOut.setBackgroundColor(0x88000000);
        ScrollView sv = new ScrollView(this);
        sv.addView(aiOut);
        l.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1));
        return l;
    }

    String cut(String s) { return s.length() > 6000 ? s.substring(0, 6000) + "\n...[cortado]" : s; }

    void ai(String q) {
        aiOut.setText("Consultando IA...");
        ex.execute(() -> {
            String r;
            try { r = Ai.ask(this, Ai.SYSTEM, q); } catch (Exception e) { r = "Erro: " + e.getMessage(); }
            final String o = r;
            ui.post(() -> aiOut.setText(o));
        });
    }

    // ================= MENU / ARQUIVOS =================
    void mainMenu() {
        String[] it = {"Abrir nivel (.gdlvl / CCLocalLevels.dat / texto)", "Exportar nivel codificado", "Exportar texto bruto", "Novo nivel",
                "Abrir projeto (.gdacproj)", "Salvar projeto", "Backups / comparar / restaurar", "Configuracoes", "Sobre"};
        new AlertDialog.Builder(this).setItems(it, (d, w) -> {
            try {
                switch (w) {
                    case 0: pick(RQ_OPEN); break;
                    case 1: backup("export"); save(RQ_EXPORT, level.name + ".gdlvl", Codec.encode(level.toRaw()).getBytes("UTF-8")); break;
                    case 2: save(RQ_EXPORT, level.name + ".txt", level.toRaw().getBytes("UTF-8")); break;
                    case 3: backup("antes-de-novo"); level = new GDLevel(); editor.setLevel(level); break;
                    case 4: pick(RQ_OPEN_PROJ); break;
                    case 5: save(RQ_SAVE_PROJ, level.name + ".gdacproj", projectJson().getBytes("UTF-8")); break;
                    case 6: backups(); break;
                    case 7: settings(); break;
                    default: text("Sobre", "Geometry Dash Archive Compiler 1.0\nEditor, SPWN original (binario nativo), decompilador, recompilador, validador de triggers.\n\nNunca sobrescreve arquivos originais: exportacoes sempre criam um arquivo novo.");
                }
            } catch (Exception e) { toast("Erro: " + e); }
        }).show();
    }

    void pick(int rq) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*");
        startActivityForResult(i, rq);
    }

    void save(int rq, String name, byte[] data) {
        pendingBytes = data;
        Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, name);
        startActivityForResult(i, rq);
    }

    @Override protected void onActivityResult(int rq, int res, Intent data) {
        super.onActivityResult(rq, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        final Uri u = data.getData();
        if (rq == RQ_EXPORT || rq == RQ_SAVE_SPWN || rq == RQ_SAVE_TXT || rq == RQ_SAVE_PROJ) {
            try (OutputStream o = getContentResolver().openOutputStream(u, "wt")) { o.write(pendingBytes); toast("Salvo."); }
            catch (Exception e) { toast("Falha ao salvar: " + e); }
            return;
        }
        run(() -> {
            try (InputStream in = getContentResolver().openInputStream(u)) { return Codec.readAll(in); }
        }, bytes -> {
            try {
                if (rq == RQ_IMPORT_SPWN) { script.setText(new String(bytes, "UTF-8")); showTab(1); }
                else if (rq == RQ_OPEN_PROJ) openProject(new String(bytes, "UTF-8"));
                else openLevelBytes(bytes, u.getLastPathSegment());
            } catch (Exception e) { toast("Erro: " + e); }
        });
    }

    void openLevelBytes(byte[] b, String name) throws Exception {
        String head = new String(b, 0, Math.min(5, b.length), "ISO-8859-1");
        boolean text = head.startsWith("H4sI") || head.contains(";") || head.contains(",");
        if (!text || head.startsWith("<?xml") || (name != null && name.endsWith(".dat"))) {
            final List<String[]> lv = Codec.localLevels(b);
            if (lv.isEmpty()) { toast("Nenhum nivel encontrado."); return; }
            if (lv.size() == 1) { level.name = lv.get(0)[0]; loadLevelString(lv.get(0)[1], lv.get(0)[0]); return; }
            String[] n = new String[lv.size()];
            for (int i = 0; i < n.length; i++) n[i] = lv.get(i)[0];
            new AlertDialog.Builder(this).setTitle("Escolha o nivel").setItems(n, (d, i) -> { level.name = lv.get(i)[0]; loadLevelString(lv.get(i)[1], lv.get(i)[0]); }).show();
            return;
        }
        loadLevelString(new String(b, "UTF-8"), name == null ? "Importado" : name.replaceAll("\\.[^.]*$", ""));
    }

    void loadLevelString(String s, String origin) {
        run(() -> GDLevel.fromRaw(Codec.decodeLevelData(s)), n -> {
            backup("antes-de-importar");
            n.name = origin == null ? "Importado" : origin.replaceAll("[^\\w\\- ]", "_");
            level = n;
            editor.setLevel(level);
            List<String> v = Logic.validate(level);
            toast(level.objects.size() + " objetos carregados. " + v.get(0));
            showTab(0);
        });
    }

    // ================= PROJETO / BACKUPS =================
    String projectJson() throws JSONException {
        return new JSONObject().put("name", level.name).put("level", level.toRaw()).put("script", script.getText().toString()).toString();
    }

    void openProject(String json) throws Exception {
        JSONObject j = new JSONObject(json);
        backup("antes-de-abrir-projeto");
        level = GDLevel.fromRaw(j.getString("level"));
        level.name = j.optString("name", "Projeto");
        script.setText(j.optString("script", STARTER));
        editor.setLevel(level);
    }

    File autosaveFile() { return new File(getFilesDir(), "autosave.json"); }
    long lastAuto;
    void autosave() {
        long n = System.currentTimeMillis();
        if (n - lastAuto < 3000) return;
        lastAuto = n;
        try (FileOutputStream o = new FileOutputStream(autosaveFile())) { o.write(projectJson().getBytes("UTF-8")); } catch (Exception ignored) { }
    }
    @Override protected void onPause() { super.onPause(); lastAuto = 0; autosave(); }
    void loadAutosave() {
        try (InputStream in = new FileInputStream(autosaveFile())) { openProjectSilent(new String(Codec.readAll(in), "UTF-8")); } catch (Exception ignored) { script.setText(STARTER); }
    }
    void openProjectSilent(String json) throws Exception {
        JSONObject j = new JSONObject(json);
        level = GDLevel.fromRaw(j.getString("level"));
        level.name = j.optString("name", "Projeto");
        script.setText(j.optString("script", STARTER));
    }

    File backupDir() { File d = new File(getFilesDir(), "backups"); d.mkdirs(); return d; }
    void backup(String why) {
        try {
            String n = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + "_" + why + ".gdlvl";
            try (FileOutputStream o = new FileOutputStream(new File(backupDir(), n))) { o.write(Codec.encode(level.toRaw()).getBytes("UTF-8")); }
        } catch (Exception ignored) { }
    }

    void backups() {
        final File[] fs = backupDir().listFiles();
        if (fs == null || fs.length == 0) { toast("Sem backups ainda."); return; }
        Arrays.sort(fs, (a, c) -> c.getName().compareTo(a.getName()));
        String[] n = new String[fs.length];
        for (int i = 0; i < n.length; i++) n[i] = fs[i].getName();
        new AlertDialog.Builder(this).setTitle("Backups").setItems(n, (d, i) ->
                new AlertDialog.Builder(this).setTitle(n[i]).setItems(new String[]{"Restaurar", "Comparar com o atual", "Apagar"}, (d2, w) -> backupAction(fs[i], w)).show()).show();
    }

    void backupAction(File f, int w) {
        try {
            String enc;
            try (InputStream in = new FileInputStream(f)) { enc = new String(Codec.readAll(in), "UTF-8"); }
            GDLevel old = GDLevel.fromRaw(Codec.decodeLevelData(enc));
            if (w == 0) { backup("antes-de-restaurar"); old.name = level.name; level = old; editor.setLevel(level); toast("Restaurado."); }
            else if (w == 1) text("Comparacao", diff(old, level));
            else { f.delete(); toast("Apagado."); }
        } catch (Exception e) { toast("Erro: " + e); }
    }

    String diff(GDLevel a, GDLevel b) {
        Map<String, Integer> m = new HashMap<>();
        for (GDObject o : a.objects) m.merge(o.serialize(), 1, Integer::sum);
        int added = 0;
        for (GDObject o : b.objects) { String k = o.serialize(); Integer c = m.get(k); if (c == null || c == 0) added++; else m.put(k, c - 1); }
        int removed = 0;
        for (int c : m.values()) removed += c;
        return "Backup: " + a.objects.size() + " objetos\nAtual: " + b.objects.size() + " objetos\nPresentes so no atual (novos/alterados): " + added + "\nPresentes so no backup (removidos/alterados): " + removed;
    }

    // ================= DIALOGOS =================
    void text(String title, String body) {
        TextView t = new TextView(this);
        t.setText(body);
        t.setTypeface(Typeface.MONOSPACE);
        t.setTextSize(11);
        t.setTextIsSelectable(true);
        t.setPadding(dp(12), dp(8), dp(12), dp(8));
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        new AlertDialog.Builder(this).setTitle(title).setView(sv).setPositiveButton("OK", null).show();
    }

    void input(String title, String hint, String init, Consumer<String> ok) {
        final EditText et = new EditText(this);
        et.setHint(hint);
        et.setText(init);
        new AlertDialog.Builder(this).setTitle(title).setView(et).setPositiveButton("OK", (d, w) -> ok.accept(et.getText().toString())).setNegativeButton("Cancelar", null).show();
    }

    void settings() {
        final SharedPreferences p = Ai.prefs(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), 0);
        final EditText key = new EditText(this), model = new EditText(this), args = new EditText(this);
        key.setHint("Chave de API (IA, opcional)"); key.setText(p.getString("ai_key", ""));
        key.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        model.setHint("Modelo"); model.setText(p.getString("ai_model", "claude-sonnet-5-5"));
        args.setHint("Argumentos extras do SPWN"); args.setText(p.getString("spwn_args", "--no-level"));
        box.addView(key); box.addView(model); box.addView(args);
        new AlertDialog.Builder(this).setTitle("Configuracoes").setView(box).setPositiveButton("Salvar", (d, w) ->
                p.edit().putString("ai_key", key.getText().toString().trim()).putString("ai_model", model.getText().toString().trim())
                        .putString("spwn_args", args.getText().toString().trim()).apply()).show();
    }
}
