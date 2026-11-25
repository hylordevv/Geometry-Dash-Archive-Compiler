package com.hylor.gdac;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class MainActivity extends Activity implements LevelView.Listener {

    static final int REQ_IMPORT = 1;
    static final int REQ_EXPORT = 2;
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    static final String[] CAT_NAMES = {"Blocos", "Espinhos", "Pads", "Orbs", "Portais", "Velocidade", "Triggers"};
    static final String[][] CAT_ITEMS = {
        {"1:Bloco", "40:Laje"},
        {"8:Espinho", "39:Pequeno"},
        {"35:Amarelo", "67:Azul", "140:Rosa"},
        {"36:Amarelo", "84:Azul", "141:Rosa"},
        {"12:Cubo", "13:Nave", "47:Bola", "111:UFO", "660:Wave", "745:Robo", "1331:Aranha", "10:Grav.", "11:Inv."},
        {"200:0.5x", "201:1x", "202:2x", "203:3x", "1334:4x"},
        {"901:Mover", "899:Cor", "1007:Alpha", "1049:Toggle", "1268:Spawn", "1346:Girar", "1006:Pulso", "1616:Parar"}
    };

    static final String[] EXPORT_NAMES = {
        "GDLVL (.gdlvl) - formato do GD/GDShare",
        "GMD (.gmd)",
        "LVL (.lvl)",
        "Nivel codificado (texto .txt)",
        "Codigo legivel (.gdc)",
        "SPWN (.spwn)"
    };
    static final String[] EXPORT_EXT = {"gdlvl", "gmd", "lvl", "txt", "gdc", "spwn"};

    // sobrevive ao recreate() do tema
    static String sLevel, sCode;
    static boolean sCodeDirty, sMapDirty;
    static int sTab = 1;

    SharedPreferences prefs;
    Level level;
    final ArrayList<String> mapUndo = new ArrayList<String>();
    final ArrayList<String> mapRedo = new ArrayList<String>();
    boolean mapDirty = true;
    boolean codeDirty = false;
    int tab = -1;
    int pendingTab = 1;

    LinearLayout nav, mapBox, codeBox, aiBox, modePane;
    Button[] tabBtns = new Button[5];
    LevelView levelView;
    HorizontalScrollView paletteView;
    LinearLayout editBar, deleteHint;
    TextView editInfo;
    final ArrayList<LevelView.Card> cards = new ArrayList<LevelView.Card>();
    CodeEditor editor;
    TextView aiInfo, chatView;
    ScrollView chatScroll;
    EditText aiInput;
    CheckBox ctxBox;
    final ArrayList<String[]> chat = new ArrayList<String[]>();
    String lastAnswer = "";
    byte[] pendingExport;

    int dp(float v) {
        return Ui.dp(this, v);
    }

    void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }

    static String msg(Throwable e) {
        String m = e.getMessage();
        return m == null ? e.toString() : m;
    }

    void showError(Throwable e) {
        new AlertDialog.Builder(this).setTitle("Erro").setMessage(msg(e))
                .setPositiveButton("OK", null).show();
    }

    LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    Button flat(String label, View.OnClickListener l) {
        Button b = Ui.flatButton(this, label);
        b.setOnClickListener(l);
        return b;
    }

    // ------------------------------------------------------------ ciclo de vida

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("gdac", MODE_PRIVATE);
        Ui.theme(prefs.getBoolean("dark", true));
        level = sLevel != null ? Level.parse(sLevel) : Level.blank();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.bg);
        root.addView(buildTop(), lp(MATCH, WRAP));

        FrameLayout content = new FrameLayout(this);
        mapBox = buildMap();
        codeBox = buildCode();
        aiBox = buildAi();
        content.addView(mapBox, new FrameLayout.LayoutParams(MATCH, MATCH));
        content.addView(codeBox, new FrameLayout.LayoutParams(MATCH, MATCH));
        content.addView(aiBox, new FrameLayout.LayoutParams(MATCH, MATCH));
        root.addView(content, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        nav = buildNav();
        root.addView(nav, lp(MATCH, WRAP));
        setContentView(root);

        final View decor = getWindow().getDecorView();
        decor.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            public void onGlobalLayout() {
                Rect r = new Rect();
                decor.getWindowVisibleDisplayFrame(r);
                int hidden = decor.getRootView().getHeight() - r.height();
                nav.setVisibility(hidden > dp(160) ? View.GONE : View.VISIBLE);
            }
        });

        if (sCode != null) {
            editor.loadText(sCode);
            codeDirty = sCodeDirty;
            mapDirty = sMapDirty;
        }
        int start = sTab;
        sLevel = null;
        sCode = null;
        selectTab(start);
        levelView.fit();
    }

    void saveState() {
        sLevel = level.toLevelString();
        sCode = editor.getText().toString();
        sCodeDirty = codeDirty;
        sMapDirty = mapDirty;
        sTab = tab;
    }

    // ------------------------------------------------------------ construcao da UI

    View buildTop() {
        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Ui.panel);
        top.setPadding(dp(12), dp(6), dp(6), dp(6));
        TextView t = new TextView(this);
        t.setText("GD Archive Compiler");
        t.setTextColor(Ui.text);
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        top.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        top.addView(flat("Desfazer", new View.OnClickListener() {
            public void onClick(View v) { doUndo(); }
        }), lpm(WRAP, WRAP, 2, 0, 2, 0));
        top.addView(flat("Refazer", new View.OnClickListener() {
            public void onClick(View v) { doRedo(); }
        }), lpm(WRAP, WRAP, 2, 0, 2, 0));
        top.addView(flat("Menu", new View.OnClickListener() {
            public void onClick(View v) { showMenu(); }
        }), lpm(WRAP, WRAP, 2, 0, 2, 0));
        return top;
    }

    LinearLayout buildNav() {
        LinearLayout n = new LinearLayout(this);
        n.setOrientation(LinearLayout.HORIZONTAL);
        n.setBackgroundColor(Ui.panel);
        n.setPadding(dp(4), dp(6), dp(4), dp(6));
        String[] labels = {"Construir", "Editar", "Excluir", "Codigo", "IA"};
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button b = Ui.gdButton(this, labels[i], false);
            b.setTextSize(12);
            b.setPadding(dp(2), dp(9), dp(2), dp(9));
            b.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) { selectTab(idx); }
            });
            tabBtns[i] = b;
            n.addView(b, lpm(0, WRAP, 3, 0, 3, 0));
            ((LinearLayout.LayoutParams) b.getLayoutParams()).weight = 1f;
        }
        return n;
    }

    LinearLayout buildMap() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        levelView = new LevelView(this);
        levelView.listener = this;
        levelView.setLevel(level);
        box.addView(levelView, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        modePane = new LinearLayout(this);
        modePane.setOrientation(LinearLayout.VERTICAL);
        modePane.setBackgroundColor(Ui.panel);
        box.addView(modePane, lp(MATCH, WRAP));
        buildPalette();
        buildEditBar();
        buildDeleteHint();
        return box;
    }

    void buildPalette() {
        paletteView = new HorizontalScrollView(this);
        paletteView.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(6), dp(4), dp(6), dp(6));
        for (int c = 0; c < CAT_NAMES.length; c++) {
            LinearLayout grp = new LinearLayout(this);
            grp.setOrientation(LinearLayout.VERTICAL);
            grp.setPadding(dp(4), 0, dp(6), 0);
            TextView h = new TextView(this);
            h.setText(CAT_NAMES[c]);
            h.setTextSize(11);
            h.setTextColor(Ui.dim);
            h.setPadding(dp(4), 0, 0, dp(2));
            grp.addView(h);
            LinearLayout cs = new LinearLayout(this);
            cs.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < CAT_ITEMS[c].length; i++) {
                String item = CAT_ITEMS[c][i];
                int colon = item.indexOf(':');
                final int id = Integer.parseInt(item.substring(0, colon));
                LevelView.Card card = new LevelView.Card(this, id, item.substring(colon + 1));
                card.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { selectPalette(id); }
                });
                cards.add(card);
                cs.addView(card, lpm(WRAP, WRAP, 2, 0, 2, 0));
            }
            grp.addView(cs);
            row.addView(grp);
        }
        Button custom = flat("Outro ID...", new View.OnClickListener() {
            public void onClick(View v) { askCustomId(); }
        });
        row.addView(custom, lpm(WRAP, WRAP, 6, 18, 6, 0));
        paletteView.addView(row);
        selectPalette(1);
    }

    void selectPalette(int id) {
        levelView.paletteId = id;
        for (int i = 0; i < cards.size(); i++) {
            LevelView.Card c = cards.get(i);
            c.sel = c.id == id;
            c.invalidate();
        }
    }

    void askCustomId() {
        final EditText et = new EditText(this);
        et.setInputType(InputType.TYPE_CLASS_NUMBER);
        et.setHint("ID do objeto, ex.: 1");
        new AlertDialog.Builder(this).setTitle("Colocar objeto por ID").setView(et)
                .setPositiveButton("Usar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            selectPalette(Integer.parseInt(et.getText().toString().trim()));
                        } catch (NumberFormatException e) {
                            toast("ID invalido");
                        }
                    }
                }).setNegativeButton("Cancelar", null).show();
    }

    void buildEditBar() {
        editBar = new LinearLayout(this);
        editBar.setOrientation(LinearLayout.VERTICAL);
        editInfo = new TextView(this);
        editInfo.setTextColor(Ui.text);
        editInfo.setTextSize(12);
        editInfo.setPadding(dp(12), dp(8), dp(12), dp(2));
        editBar.addView(editInfo);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(6), dp(4), dp(6), dp(8));
        row.addView(flat("Propriedades", new View.OnClickListener() {
            public void onClick(View v) { editProps(); }
        }), lpm(WRAP, WRAP, 3, 0, 3, 0));
        row.addView(flat("Duplicar", new View.OnClickListener() {
            public void onClick(View v) { levelView.duplicateSelected(); }
        }), lpm(WRAP, WRAP, 3, 0, 3, 0));
        row.addView(flat("Girar 90", new View.OnClickListener() {
            public void onClick(View v) { levelView.rotateSelected(90f); }
        }), lpm(WRAP, WRAP, 3, 0, 3, 0));
        row.addView(flat("Espelhar", new View.OnClickListener() {
            public void onClick(View v) { levelView.flipSelected(); }
        }), lpm(WRAP, WRAP, 3, 0, 3, 0));
        row.addView(flat("Excluir", new View.OnClickListener() {
            public void onClick(View v) { levelView.deleteSelected(); }
        }), lpm(WRAP, WRAP, 3, 0, 3, 0));
        hs.addView(row);
        editBar.addView(hs);
        updateEditInfo();
    }

    void buildDeleteHint() {
        deleteHint = new LinearLayout(this);
        TextView t = new TextView(this);
        t.setText("Toque em um objeto para excluir. Use Desfazer se errar.");
        t.setTextColor(Ui.text);
        t.setTextSize(13);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        deleteHint.addView(t);
    }

    void updateEditInfo() {
        Level.GObj o = levelView == null ? null : levelView.selected;
        if (o == null) {
            editInfo.setText("Nada selecionado. Toque num objeto no mapa.");
        } else {
            editInfo.setText(Readable.nameOf(o.id) + "   X " + Level.fmt(o.x) + "  Y " + Level.fmt(o.y)
                    + "  rot " + Level.fmt(o.rot));
        }
    }

    void refreshModePane() {
        modePane.removeAllViews();
        if (tab == 0) {
            modePane.addView(paletteView);
        } else if (tab == 1) {
            modePane.addView(editBar);
        } else {
            modePane.addView(deleteHint);
        }
    }

    LinearLayout buildCode() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView tb = new HorizontalScrollView(this);
        tb.setBackgroundColor(Ui.panel);
        tb.setHorizontalScrollBarEnabled(false);
        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(6), dp(4), dp(6), dp(4));
        row.addView(flat("Desfazer", new View.OnClickListener() {
            public void onClick(View v) { if (!editor.undo()) toast("Nada para desfazer"); }
        }), lpm(WRAP, WRAP, 3, 0, 3, 0));
        row.addView(flat("Refazer", new View.OnClickListener() {
            public void onClick(View v) { if (!editor.redo()) toast("Nada para refazer"); }
        }), lpm(WRAP, WRAP, 3, 0, 3, 0));
        row.addView(flat("Buscar", new View.OnClickListener() {
            public void onClick(View v) { findDialog(); }
        }), lpm(WRAP, WRAP, 3, 0, 3, 0));
        row.addView(flat("Atualizar do mapa", new View.OnClickListener() {
            public void onClick(View v) { regenerateCode(); toast("Codigo atualizado a partir do mapa"); }
        }), lpm(WRAP, WRAP, 3, 0, 3, 0));
        row.addView(flat("Aplicar no mapa", new View.OnClickListener() {
            public void onClick(View v) { if (applyCode()) toast("Codigo aplicado no mapa"); }
        }), lpm(WRAP, WRAP, 3, 0, 3, 0));
        tb.addView(row);
        box.addView(tb, lp(MATCH, WRAP));

        editor = new CodeEditor(this);
        editor.setHint("Escreva uma linha por objeto. Ex.:  Bloco pos(15,15)");
        editor.onUserChange = new Runnable() {
            public void run() { codeDirty = true; }
        };
        box.addView(editor, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        HorizontalScrollView sb = new HorizontalScrollView(this);
        sb.setBackgroundColor(Ui.panel);
        sb.setHorizontalScrollBarEnabled(false);
        LinearLayout sr = new LinearLayout(this);
        sr.setPadding(dp(4), dp(4), dp(4), dp(4));
        String[][] syms = {
            {"Objeto", "Bloco pos(15,15)\n", "0"}, {"pos()", "pos()", "1"}, {"( )", "()", "1"},
            {",", ",", "0"}, {"\"\"", "\"\"", "1"}, {"zlayer", " zlayer()", "1"},
            {"zorder", " zorder()", "1"}, {"rgb", " rgb(255,255,255)", "0"}, {"Trigger", "Trigger(Cor) pos(300,15)\n", "0"},
            {"//", "// ", "0"}, {"Tab", "  ", "0"}
        };
        for (int i = 0; i < syms.length; i++) {
            final String ins = syms[i][1];
            final int caret = Integer.parseInt(syms[i][2]);
            sr.addView(flat(syms[i][0], new View.OnClickListener() {
                public void onClick(View v) { editor.insert(ins, caret); }
            }), lpm(WRAP, WRAP, 2, 0, 2, 0));
        }
        sb.addView(sr);
        box.addView(sb, lp(MATCH, WRAP));
        return box;
    }

    LinearLayout buildAi() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackgroundColor(Ui.bg);
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setBackgroundColor(Ui.panel);
        head.setPadding(dp(12), dp(6), dp(6), dp(6));
        aiInfo = new TextView(this);
        aiInfo.setTextColor(Ui.dim);
        aiInfo.setTextSize(12);
        head.addView(aiInfo, new LinearLayout.LayoutParams(0, WRAP, 1f));
        head.addView(flat("Configurar", new View.OnClickListener() {
            public void onClick(View v) { aiSettings(); }
        }), lp(WRAP, WRAP));
        box.addView(head, lp(MATCH, WRAP));

        chatScroll = new ScrollView(this);
        chatView = new TextView(this);
        chatView.setTextColor(Ui.text);
        chatView.setTextSize(14);
        chatView.setTextIsSelectable(true);
        chatView.setPadding(dp(14), dp(10), dp(14), dp(10));
        chatView.setText("Peca ajuda com niveis, triggers, IDs ou pecas prontas.\n"
                + "Ex.: \"monte uma escada de 5 blocos com espinhos\".\n"
                + "A resposta com codigo pode ser inserida no editor.\n");
        chatScroll.addView(chatView);
        box.addView(chatScroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout opts = new LinearLayout(this);
        opts.setGravity(Gravity.CENTER_VERTICAL);
        opts.setPadding(dp(10), dp(2), dp(6), dp(2));
        ctxBox = new CheckBox(this);
        ctxBox.setText("Enviar o codigo do editor");
        ctxBox.setTextColor(Ui.dim);
        ctxBox.setTextSize(12);
        opts.addView(ctxBox, new LinearLayout.LayoutParams(0, WRAP, 1f));
        opts.addView(flat("Inserir no editor", new View.OnClickListener() {
            public void onClick(View v) { insertAnswer(); }
        }), lpm(WRAP, WRAP, 2, 0, 2, 0));
        opts.addView(flat("Limpar", new View.OnClickListener() {
            public void onClick(View v) {
                chat.clear();
                lastAnswer = "";
                chatView.setText("");
            }
        }), lpm(WRAP, WRAP, 2, 0, 2, 0));
        box.addView(opts, lp(MATCH, WRAP));

        LinearLayout inRow = new LinearLayout(this);
        inRow.setGravity(Gravity.CENTER_VERTICAL);
        inRow.setBackgroundColor(Ui.panel);
        inRow.setPadding(dp(8), dp(6), dp(8), dp(6));
        aiInput = new EditText(this);
        aiInput.setHint("Pergunte ou peca algo...");
        aiInput.setTextColor(Ui.text);
        aiInput.setHintTextColor(Ui.dim);
        aiInput.setMaxLines(4);
        inRow.addView(aiInput, new LinearLayout.LayoutParams(0, WRAP, 1f));
        Button send = Ui.gdButton(this, "Enviar", false);
        send.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { sendAi(); }
        });
        inRow.addView(send, lpm(WRAP, WRAP, 6, 0, 0, 0));
        box.addView(inRow, lp(MATCH, WRAP));
        return box;
    }

    // ------------------------------------------------------------ abas

    void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null && getCurrentFocus() != null) {
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
    }

    void selectTab(int t) {
        if (tab == 3 && t != 3 && codeDirty) {
            pendingTab = t;
            if (!applyCode()) {
                return;
            }
        }
        hideKeyboard();
        tab = t;
        mapBox.setVisibility(t <= 2 ? View.VISIBLE : View.GONE);
        codeBox.setVisibility(t == 3 ? View.VISIBLE : View.GONE);
        aiBox.setVisibility(t == 4 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < tabBtns.length; i++) {
            Ui.styleGd(tabBtns[i], i == t);
        }
        if (t <= 2) {
            levelView.setMode(t);
            refreshModePane();
            updateEditInfo();
        }
        if (t == 3 && mapDirty && !codeDirty) {
            regenerateCode();
        }
        if (t == 4) {
            updateAiInfo();
        }
    }

    // ------------------------------------------------------------ modelo / desfazer

    @Override
    public void beforeChange() {
        pushUndo();
    }

    @Override
    public void changed() {
        mapDirty = true;
        updateEditInfo();
    }

    @Override
    public void selectionChanged(Level.GObj o) {
        updateEditInfo();
    }

    void pushUndo() {
        String s = level.toLevelString();
        if (s.length() > 4000000) {
            mapUndo.clear();
            return;
        }
        mapUndo.add(s);
        if (mapUndo.size() > 25) {
            mapUndo.remove(0);
        }
        mapRedo.clear();
    }

    void doUndo() {
        if (tab == 3) {
            if (!editor.undo()) {
                toast("Nada para desfazer");
            }
            return;
        }
        if (tab == 4) {
            toast("Desfazer vale no mapa e no codigo");
            return;
        }
        if (mapUndo.isEmpty()) {
            toast("Nada para desfazer");
            return;
        }
        mapRedo.add(level.toLevelString());
        level = Level.parse(mapUndo.remove(mapUndo.size() - 1));
        levelView.setLevel(level);
        mapDirty = true;
        updateEditInfo();
    }

    void doRedo() {
        if (tab == 3) {
            if (!editor.redo()) {
                toast("Nada para refazer");
            }
            return;
        }
        if (tab == 4 || mapRedo.isEmpty()) {
            toast("Nada para refazer");
            return;
        }
        mapUndo.add(level.toLevelString());
        level = Level.parse(mapRedo.remove(mapRedo.size() - 1));
        levelView.setLevel(level);
        mapDirty = true;
        updateEditInfo();
    }

    boolean applyCode() {
        String text = editor.getText().toString();
        try {
            Level nl = text.trim().length() == 0 ? Level.blank() : Readable.fromCode(text);
            pushUndo();
            level = nl;
            levelView.setLevel(level);
            codeDirty = false;
            mapDirty = false;
            return true;
        } catch (Exception e) {
            new AlertDialog.Builder(this).setTitle("Codigo com erro").setMessage(msg(e))
                    .setPositiveButton("Corrigir", null)
                    .setNegativeButton("Descartar e sair", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface d, int w) {
                            codeDirty = false;
                            mapDirty = true;
                            selectTab(pendingTab);
                        }
                    }).show();
            return false;
        }
    }

    void regenerateCode() {
        try {
            editor.loadText(Readable.toCode(level));
            mapDirty = false;
            codeDirty = false;
        } catch (Exception e) {
            toast("Erro ao gerar codigo: " + msg(e));
        }
    }

    // ------------------------------------------------------------ propriedades

    void editProps() {
        final Level.GObj o = levelView.selected;
        if (o == null) {
            toast("Selecione um objeto primeiro (toque nele)");
            return;
        }
        ScrollView sv = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(8));
        sv.addView(box);
        final ArrayList<String> keys = new ArrayList<String>(o.p.keySet());
        final ArrayList<EditText> vals = new ArrayList<EditText>();
        for (int i = 0; i < keys.size(); i++) {
            String k = keys.get(i);
            String al = Readable.ID2ALIAS.get(k);
            String nm = al != null ? al : (Codec.NAMES.get(k) != null ? Codec.NAMES.get(k).toLowerCase() : "P" + k);
            TextView tv = new TextView(this);
            tv.setText(nm + "  (chave " + k + ")");
            tv.setTextSize(12);
            tv.setPadding(0, dp(8), 0, 0);
            EditText et = new EditText(this);
            et.setText(o.p.get(k));
            et.setSingleLine(true);
            box.addView(tv);
            box.addView(et);
            vals.add(et);
        }
        TextView nt = new TextView(this);
        nt.setText("Nova propriedade (nome ou numero da chave)");
        nt.setTextSize(12);
        nt.setPadding(0, dp(14), 0, 0);
        final EditText nk = new EditText(this);
        nk.setHint("ex.: zorder  ou  57");
        nk.setSingleLine(true);
        final EditText nv = new EditText(this);
        nv.setHint("valor");
        nv.setSingleLine(true);
        box.addView(nt);
        box.addView(nk);
        box.addView(nv);
        new AlertDialog.Builder(this).setTitle("Propriedades").setView(sv)
                .setPositiveButton("Aplicar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        pushUndo();
                        for (int i = 0; i < keys.size(); i++) {
                            String v = vals.get(i).getText().toString().trim();
                            if (v.length() == 0) {
                                o.p.remove(keys.get(i));
                            } else {
                                o.p.put(keys.get(i), v);
                            }
                        }
                        String k = nk.getText().toString().trim();
                        if (k.length() > 0) {
                            String id = Readable.ALIAS2ID.get(Readable.norm(k));
                            if (id == null) {
                                id = Codec.IDS.get(k.toUpperCase());
                            }
                            if (id == null && k.matches("[Pp]?\\d+")) {
                                id = k.replaceAll("^[Pp]", "");
                            }
                            if (id != null) {
                                o.p.put(id, nv.getText().toString().trim());
                            } else {
                                toast("Propriedade desconhecida: " + k);
                            }
                        }
                        o.sync();
                        levelView.invalidate();
                        changed();
                    }
                }).setNegativeButton("Cancelar", null).show();
    }

    // ------------------------------------------------------------ menu

    void showMenu() {
        String[] items = {
            "Novo nivel", "Importar arquivo", "Exportar...", "Ferramentas (base64, gzip, zlib)",
            "Centralizar mapa", Ui.dark ? "Tema claro" : "Tema escuro", "Configurar IA", "Formatos aceitos"
        };
        new AlertDialog.Builder(this).setTitle("Menu").setItems(items, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface d, int which) {
                switch (which) {
                    case 0: newLevel(); break;
                    case 1: doImport(); break;
                    case 2: chooseExport(); break;
                    case 3: toolsDialog(); break;
                    case 4: levelView.fit(); selectTabIfCode(); break;
                    case 5:
                        if (tab == 3 && codeDirty && !applyCode()) {
                            return;
                        }
                        prefs.edit().putBoolean("dark", !Ui.dark).apply();
                        saveState();
                        recreate();
                        break;
                    case 6: aiSettings(); break;
                    default: formatsInfo(); break;
                }
            }
        }).show();
    }

    void selectTabIfCode() {
        if (tab == 3 || tab == 4) {
            selectTab(1);
        }
    }

    void newLevel() {
        pushUndo();
        level = Level.blank();
        levelView.setLevel(level);
        levelView.resetCamera();
        mapDirty = true;
        codeDirty = false;
        if (tab == 3) {
            regenerateCode();
        }
        toast("Novo nivel criado (Desfazer volta)");
    }

    void formatsInfo() {
        new AlertDialog.Builder(this).setTitle("Formatos aceitos")
                .setMessage("Importa:\n"
                        + "- .gdlvl, .gmd, .lvl (GDShare)\n"
                        + "- .gmd2 (zip)\n"
                        + "- CCLocalLevels.dat e outros saves (XOR + gzip), com varios niveis\n"
                        + "- nivel em base64+gzip (H4sI...), gzip/zlib cru\n"
                        + "- nivel decodificado (kS38,...;)\n"
                        + "- codigo .gdc e .spwn ($.add(obj{...}))\n\n"
                        + "Exporta: .gdlvl, .gmd, .lvl, texto codificado, .gdc, .spwn.\n\n"
                        + "Observacao: o proprio GD nao abre arquivos; para importar no jogo use o mod GDShare/Geode "
                        + "(que le .gmd/.lvl). O .gdlvl daqui usa o mesmo conteudo do .gmd.")
                .setPositiveButton("OK", null).show();
    }

    // ------------------------------------------------------------ importar / exportar

    void doImport() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, REQ_IMPORT);
    }

    void chooseExport() {
        new AlertDialog.Builder(this).setTitle("Exportar como").setItems(EXPORT_NAMES,
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) { exportAs(which); }
                }).show();
    }

    void exportAs(final int kind) {
        if (tab == 3 && codeDirty && !applyCode()) {
            return;
        }
        final Level snapshot = level;
        toast("Gerando arquivo...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    byte[] data;
                    String ls = snapshot.toLevelString();
                    if (kind <= 2) {
                        data = Codec.wrapGmd("GDAC", Codec.encodeLevel(ls)).getBytes(StandardCharsets.UTF_8);
                    } else if (kind == 3) {
                        data = Codec.encodeLevel(ls).getBytes(StandardCharsets.UTF_8);
                    } else if (kind == 4) {
                        data = Readable.toCode(snapshot).getBytes(StandardCharsets.UTF_8);
                    } else {
                        data = Readable.toSpwn(snapshot).getBytes(StandardCharsets.UTF_8);
                    }
                    pendingExport = data;
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                            i.addCategory(Intent.CATEGORY_OPENABLE);
                            i.setType("*/*");
                            i.putExtra(Intent.EXTRA_TITLE, "nivel." + EXPORT_EXT[kind]);
                            startActivityForResult(i, REQ_EXPORT);
                        }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        public void run() { showError(e); }
                    });
                }
            }
        }).start();
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri u = data.getData();
        if (req == REQ_IMPORT) {
            toast("Lendo arquivo...");
            new Thread(new Runnable() {
                public void run() {
                    try {
                        InputStream in = getContentResolver().openInputStream(u);
                        byte[] b;
                        try {
                            b = Codec.readAll(in);
                        } finally {
                            in.close();
                        }
                        final Codec.Imported imp = Codec.loadAny(b);
                        runOnUiThread(new Runnable() {
                            public void run() { handleImported(imp); }
                        });
                    } catch (final Throwable e) {
                        runOnUiThread(new Runnable() {
                            public void run() { showError(e); }
                        });
                    }
                }
            }).start();
        } else if (req == REQ_EXPORT && pendingExport != null) {
            try {
                OutputStream os = getContentResolver().openOutputStream(u, "wt");
                try {
                    os.write(pendingExport);
                } finally {
                    os.close();
                }
                toast("Arquivo salvo");
            } catch (Exception e) {
                showError(e);
            }
        }
    }

    void handleImported(Codec.Imported imp) {
        int n = imp.levels.size();
        if (n == 1) {
            openLevel(imp.levels.get(0), imp.kind);
        } else if (n > 1) {
            pickLevel(imp);
        } else if (imp.text != null) {
            openText(imp.text);
        } else {
            toast("Arquivo vazio ou formato nao reconhecido");
        }
    }

    void pickLevel(final Codec.Imported imp) {
        String[] names = new String[imp.levels.size()];
        for (int i = 0; i < names.length; i++) {
            String nm = imp.levels.get(i).name;
            names[i] = (i + 1) + ". " + (nm == null || nm.length() == 0 ? "Nivel sem nome" : nm);
        }
        new AlertDialog.Builder(this).setTitle(names.length + " niveis (" + imp.kind + ")")
                .setItems(names, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { openLevel(imp.levels.get(w), imp.kind); }
                }).show();
    }

    void openLevel(final Codec.Entry en, final String kind) {
        toast("Carregando nivel...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    final Level nl = Level.parse(en.level);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            mapUndo.clear();
                            mapRedo.clear();
                            level = nl;
                            levelView.setLevel(nl);
                            levelView.fit();
                            mapDirty = true;
                            codeDirty = false;
                            if (tab == 3) {
                                regenerateCode();
                            }
                            toast(nl.objs.size() + " objetos carregados (" + kind + ")");
                        }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        public void run() { showError(e); }
                    });
                }
            }
        }).start();
    }

    void openText(String text) {
        boolean code = text.contains("$.add(") || text.contains("obj{") || text.trim().toLowerCase().startsWith("header")
                || text.contains(" pos(");
        editor.loadText(text);
        codeDirty = code;
        mapDirty = false;
        selectTab(3);
        toast(code ? "Codigo aberto. Va para outra aba para aplicar no mapa."
                : "Formato nao reconhecido como nivel; aberto como texto.");
    }

    // ------------------------------------------------------------ ferramentas

    void toolsDialog() {
        new AlertDialog.Builder(this).setTitle("Ferramentas (aplicam ao editor de codigo)")
                .setItems(Tools.NAMES, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int which) { runTool(which); }
                }).show();
    }

    void runTool(int which) {
        if (tab != 3) {
            selectTab(3);
        }
        int a = editor.getSelectionStart();
        int b = editor.getSelectionEnd();
        String all = editor.getText().toString();
        boolean sel = a >= 0 && b >= 0 && a != b;
        String in = sel ? all.substring(Math.min(a, b), Math.max(a, b)) : all;
        try {
            String out = Tools.run(which, in);
            if (sel) {
                editor.getText().replace(Math.min(a, b), Math.max(a, b), out);
            } else {
                editor.getText().replace(0, editor.length(), out);
            }
            toast("Pronto");
        } catch (Exception e) {
            showError(e);
        }
    }

    void findDialog() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(4));
        final EditText f = new EditText(this);
        f.setHint("Buscar");
        f.setSingleLine(true);
        final EditText r = new EditText(this);
        r.setHint("Substituir por");
        r.setSingleLine(true);
        box.addView(f);
        box.addView(r);
        final AlertDialog dlg = new AlertDialog.Builder(this).setTitle("Buscar e substituir").setView(box)
                .setPositiveButton("Proximo", null)
                .setNeutralButton("Substituir tudo", null)
                .setNegativeButton("Fechar", null).create();
        dlg.show();
        dlg.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String t = editor.getText().toString();
                String q = f.getText().toString();
                if (q.length() == 0) {
                    return;
                }
                int from = Math.max(editor.getSelectionEnd(), 0);
                int i = t.indexOf(q, from);
                if (i < 0) {
                    i = t.indexOf(q);
                }
                if (i < 0) {
                    toast("Nao encontrado");
                } else {
                    editor.setSelection(i, i + q.length());
                    editor.bringPointIntoView(i);
                }
            }
        });
        dlg.getButton(DialogInterface.BUTTON_NEUTRAL).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                String t = editor.getText().toString();
                String q = f.getText().toString();
                if (q.length() == 0 || !t.contains(q)) {
                    toast("Nao encontrado");
                    return;
                }
                int count = 0;
                int idx = 0;
                while ((idx = t.indexOf(q, idx)) >= 0) {
                    count++;
                    idx += q.length();
                }
                editor.getText().replace(0, editor.length(), t.replace(q, r.getText().toString()));
                toast(count + " substituicoes");
            }
        });
    }

    // ------------------------------------------------------------ IA

    void updateAiInfo() {
        int idx = prefs.getInt("ai_idx", 0);
        Ai.Provider p = Ai.LIST[idx];
        aiInfo.setText(p.name + "  -  " + prefs.getString("ai_model_" + idx, p.model));
    }

    void aiSettings() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(8), dp(18), dp(4));
        final Spinner sp = new Spinner(this);
        sp.setAdapter(new ArrayAdapter<Ai.Provider>(this, android.R.layout.simple_spinner_dropdown_item, Ai.LIST));
        final EditText url = new EditText(this);
        url.setHint("URL base (ex.: https://openrouter.ai/api/v1)");
        url.setSingleLine(true);
        final EditText model = new EditText(this);
        model.setHint("Modelo (ex.: openrouter/auto)");
        model.setSingleLine(true);
        final EditText key = new EditText(this);
        key.setHint("Chave da API");
        key.setSingleLine(true);
        key.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        box.addView(sp);
        box.addView(url);
        box.addView(model);
        box.addView(key);
        TextView note = new TextView(this);
        note.setText("Qualquer servico compativel com OpenAI funciona: escolha \"Personalizado\" e informe a URL base.");
        note.setTextSize(11);
        note.setPadding(0, dp(8), 0, 0);
        box.addView(note);
        sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(AdapterView<?> parent, View v, int pos, long id) {
                Ai.Provider p = Ai.LIST[pos];
                url.setText(prefs.getString("ai_url_" + pos, p.url));
                model.setText(prefs.getString("ai_model_" + pos, p.model));
                key.setText(prefs.getString("ai_key_" + pos, ""));
            }

            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        sp.setSelection(prefs.getInt("ai_idx", 0));
        new AlertDialog.Builder(this).setTitle("Configurar IA").setView(box)
                .setPositiveButton("Salvar", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        int i = sp.getSelectedItemPosition();
                        prefs.edit().putInt("ai_idx", i)
                                .putString("ai_url_" + i, url.getText().toString().trim())
                                .putString("ai_model_" + i, model.getText().toString().trim())
                                .putString("ai_key_" + i, key.getText().toString().trim()).apply();
                        if (aiInfo != null) {
                            updateAiInfo();
                        }
                        toast("IA configurada");
                    }
                }).setNegativeButton("Cancelar", null).show();
    }

    void appendChat(String who, String text) {
        chatView.append("\n" + who + ":\n" + text + "\n");
        chatScroll.post(new Runnable() {
            public void run() { chatScroll.fullScroll(View.FOCUS_DOWN); }
        });
    }

    String codeForAi() {
        String code;
        if (codeDirty || (!mapDirty && editor.length() > 0)) {
            code = editor.getText().toString();
        } else {
            code = Readable.toCode(level);
        }
        if (code.length() > 12000) {
            code = code.substring(0, 12000) + "\n// ...cortado";
        }
        return code;
    }

    void sendAi() {
        final String q = aiInput.getText().toString().trim();
        if (q.length() == 0) {
            return;
        }
        final int idx = prefs.getInt("ai_idx", 0);
        final Ai.Provider pr = Ai.LIST[idx];
        final String url = prefs.getString("ai_url_" + idx, pr.url);
        final String model = prefs.getString("ai_model_" + idx, pr.model);
        final String key = prefs.getString("ai_key_" + idx, "");
        if (pr.needsKey && key.length() == 0) {
            toast("Configure a chave da API primeiro");
            aiSettings();
            return;
        }
        aiInput.setText("");
        hideKeyboard();
        appendChat("Voce", q);
        chat.add(new String[] {"user", q});
        final String ctx = ctxBox.isChecked() ? codeForAi() : null;
        final ArrayList<String[]> msgs = new ArrayList<String[]>(chat);
        if (ctx != null) {
            msgs.set(msgs.size() - 1, new String[] {"user", q + "\n\n--- codigo atual do editor ---\n" + ctx});
        }
        aiInfo.setText("Consultando " + pr.name + "...");
        new Thread(new Runnable() {
            public void run() {
                try {
                    final String ans = Ai.chat(url, key, model, pr.anthropic, msgs, Ai.SYSTEM);
                    runOnUiThread(new Runnable() {
                        public void run() {
                            chat.add(new String[] {"assistant", ans});
                            lastAnswer = ans;
                            appendChat("IA", ans);
                            updateAiInfo();
                        }
                    });
                } catch (final Throwable e) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            if (!chat.isEmpty()) {
                                chat.remove(chat.size() - 1);
                            }
                            appendChat("Erro", msg(e));
                            updateAiInfo();
                        }
                    });
                }
            }
        }).start();
    }

    void insertAnswer() {
        if (lastAnswer.length() == 0) {
            toast("Nenhuma resposta da IA ainda");
            return;
        }
        String code = lastAnswer;
        int a = code.indexOf("```");
        if (a >= 0) {
            int nl = code.indexOf('\n', a);
            int b = code.indexOf("```", a + 3);
            if (nl >= 0 && b > nl) {
                code = code.substring(nl + 1, b);
            }
        }
        selectTab(3);
        editor.setSelection(editor.length());
        editor.insert("\n" + code.trim() + "\n", 0);
        codeDirty = true;
        toast("Inserido. Ao sair do Codigo, vai para o mapa.");
    }
}
