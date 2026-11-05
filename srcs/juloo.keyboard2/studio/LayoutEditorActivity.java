package juloo.keyboard2.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.DirectBootAwarePreferences;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.prefs.LayoutsPreference;
import org.json.JSONObject;

/** Studio → Layout editor (il-006, owner 2026-11-06): a whole keyboard,
    keys added, removed, moved and resized freely, the nine names of each
    key set from the key list; saved as named layouts. A key
    `layout:<name>` (with the behaviours of a page, Studio → Keys) shows a
    saved layout; "Add to my layouts" puts it in the quick language's list. */
public class LayoutEditorActivity extends Activity implements LayoutCanvas.Listener
{
  SharedPreferences _prefs;
  ModuleStore _store;
  LayoutDoc _doc;
  LayoutCanvas _canvas;
  EditText _name;
  TextView _info;
  /** The name it was opened or last saved under (null: never saved). */
  String _saved_as = null;
  boolean _dirty = false;
  final ArrayDeque<String> _undo = new ArrayDeque<String>();

  @Override public void onCreate(Bundle b)
  {
    super.onCreate(b);
    _prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    _store = new ModuleStore(_prefs);
    setTitle("Layout editor");
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(12);
    root.setPadding(pad, pad, pad, pad);

    _name = new EditText(this);
    _name.setSingleLine(true);
    _name.setHint("Layout name");
    _name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    root.addView(_name);
    root.addView(bar(
        btn("Open…", v -> open_menu()),
        btn("Save", v -> save(false)),
        btn("Save as…", v -> save(true)),
        btn("Delete…", v -> delete()),
        btn("Undo", v -> undo())));
    _canvas = new LayoutCanvas(this);
    root.addView(_canvas);
    _info = new TextView(this);
    _info.setPadding(0, dp(6), 0, dp(6));
    root.addView(_info);
    root.addView(label("Key"));
    root.addView(bar(
        btn("Names…", v -> slots()),
        btn("◀", v -> move(-1)),
        btn("▶", v -> move(1)),
        btn("+ before", v -> add_key(0)),
        btn("+ after", v -> add_key(1)),
        btn("✕ key", v -> remove_key()),
        btn("width −", v -> width(-0.25f)),
        btn("width +", v -> width(0.25f)),
        btn("gap −", v -> gap(-0.25f)),
        btn("gap +", v -> gap(0.25f))));
    root.addView(label("Row"));
    root.addView(bar(
        btn("+ row above", v -> add_row(0)),
        btn("+ row below", v -> add_row(1)),
        btn("✕ row", v -> remove_row()),
        btn("height −", v -> height(-0.1f)),
        btn("height +", v -> height(0.1f)),
        btn("+ key at end", v -> add_key_end())));
    root.addView(label("Use it"));
    root.addView(bar(
        btn("Copy its key name", v -> copy_key_name()),
        btn("Add to my layouts", v -> add_to_layouts()),
        btn("Show XML", v -> show_xml())));
    TextView help = new TextView(this);
    help.setText("Tap a key to select it, drag its right edge to resize it. Tap a row's free space to select the row, "
        + "drag its bottom edge for its height. A key `layout:<name>` shows a saved layout "
        + "(behaviours in Studio → Keys: back after one key, at the end of the word…). "
        + "Saved layouts are drawn as they are: no bottom row or number row is added.");
    help.setPadding(0, dp(12), 0, 0);
    root.addView(help);
    ScrollView sv = new ScrollView(this);
    sv.addView(root);
    setContentView(sv);
    start_from_snapshot_or_blank();
    // targetSdk 36: the back gesture no longer calls onBackPressed.
    if (android.os.Build.VERSION.SDK_INT >= 33)
      getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
          android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::leave);
  }

  // ---- building the screen ----

  View bar(View... vs)
  {
    LinearLayout l = new LinearLayout(this);
    for (View v : vs) l.addView(v);
    HorizontalScrollView h = new HorizontalScrollView(this);
    h.addView(l);
    return h;
  }

  Button btn(String s, View.OnClickListener l)
  {
    Button b = new Button(this);
    b.setText(s);
    b.setAllCaps(false);
    b.setOnClickListener(l);
    return b;
  }

  TextView label(String s)
  {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
    t.setPadding(0, dp(8), 0, 0);
    return t;
  }

  // ---- the document ----

  void load(LayoutDoc d, String saved_as)
  {
    _doc = d;
    _saved_as = saved_as;
    _dirty = false;
    _undo.clear();
    _name.setText(d.name == null ? "" : d.name);
    _canvas.sel_row = -1;
    _canvas.sel_key = -1;
    _canvas.set(_doc, this);
    info();
  }

  void start_from_snapshot_or_blank()
  {
    LayoutDoc d = snapshot();
    load(d != null ? d : LayoutDoc.blank("My layout"), null);
  }

  /** The keyboard as last shown (the keyboard writes it when it opens). */
  LayoutDoc snapshot()
  {
    String j = _prefs.getString(juloo.keyboard2.Keyboard2.SNAPSHOT_PREF, "");
    if (j.isEmpty())
      return null;
    try { LayoutDoc d = LayoutDoc.of_json("My layout", new JSONObject(j)); return d.rows.isEmpty() ? null : d; }
    catch (Exception e) { return null; }
  }

  @Override public void will_change()
  {
    try { _undo.push(_doc.json().toString()); } catch (Exception e) {}
    while (_undo.size() > 100) _undo.removeLast();
    _dirty = true;
  }

  @Override public void changed() { info(); }

  @Override public void selected(int row, int key) { info(); }

  void edited()
  {
    _canvas.set(_doc, this);
    info();
  }

  void undo()
  {
    if (_undo.isEmpty()) { toast("Nothing to undo"); return; }
    try
    {
      String n = _doc.name;
      _doc = LayoutDoc.of_json(n, new JSONObject(_undo.pop()));
      edited();
    }
    catch (Exception e) { toast(String.valueOf(e)); }
  }

  void info()
  {
    int r = _canvas.sel_row, k = _canvas.sel_key;
    String s = String.format(java.util.Locale.ROOT, "%d rows · widest row %.2f keys%s", _doc.rows.size(), _doc.width(),
        _saved_as == null ? " · not saved" : " · saved as \"" + _saved_as + "\"" + (_dirty ? " (changed)" : ""));
    if (r >= 0)
    {
      LayoutDoc.Row row = _doc.rows.get(r);
      s += String.format(java.util.Locale.ROOT, "\nRow %d: %d keys, %.2f wide, height %.2f", r + 1, row.keys.size(), row.width(), row.height);
      if (k >= 0)
      {
        LayoutDoc.Key key = row.keys.get(k);
        StringBuilder names = new StringBuilder();
        for (int i = 0; i < 9; i++)
          if (key.names[i] != null)
            names.append(names.length() == 0 ? "" : "\n").append(LayoutDoc.SLOTS[i]).append("  ").append(QuickNames.of_key(key.names[i]));
        s += String.format(java.util.Locale.ROOT, "\nKey %d: width %.2f, gap %.2f\n%s", k + 1, key.width, key.shift,
            names.length() == 0 ? "(empty)" : names.toString());
      }
    }
    else
      s += "\nTap a key (or a row's free space).";
    _info.setText(s);
  }

  boolean need_key()
  {
    if (_canvas.sel_row >= 0 && _canvas.sel_key >= 0) return true;
    toast("Select a key first");
    return false;
  }

  boolean need_row()
  {
    if (_canvas.sel_row >= 0) return true;
    toast("Select a row (or a key in it) first");
    return false;
  }

  // ---- key and row tools ----

  void move(int d)
  {
    if (!need_key()) return;
    will_change();
    int[] at = _doc.move_key(_canvas.sel_row, _canvas.sel_key, d);
    _canvas.sel_row = at[0]; _canvas.sel_key = at[1];
    edited();
  }

  void add_key(int after)
  {
    if (!need_key()) return;
    will_change();
    _doc.add_key(_canvas.sel_row, _canvas.sel_key + after);
    _canvas.sel_key = _canvas.sel_key + after;
    edited();
    slots();
  }

  void add_key_end()
  {
    if (!need_row()) return;
    will_change();
    int n = _doc.rows.get(_canvas.sel_row).keys.size();
    _doc.add_key(_canvas.sel_row, n);
    _canvas.sel_key = n;
    edited();
    slots();
  }

  void remove_key()
  {
    if (!need_key()) return;
    will_change();
    _doc.remove_key(_canvas.sel_row, _canvas.sel_key);
    _canvas.sel_key = -1;
    edited();
  }

  void width(float d)
  {
    if (!need_key()) return;
    will_change();
    _doc.set_width(_canvas.sel_row, _canvas.sel_key, _doc.key(_canvas.sel_row, _canvas.sel_key).width + d);
    edited();
  }

  void gap(float d)
  {
    if (!need_key()) return;
    will_change();
    _doc.set_shift(_canvas.sel_row, _canvas.sel_key, _doc.key(_canvas.sel_row, _canvas.sel_key).shift + d);
    edited();
  }

  void add_row(int below)
  {
    will_change();
    int at = (_canvas.sel_row < 0) ? _doc.rows.size() : _canvas.sel_row + below;
    _doc.add_row(at);
    _canvas.sel_row = at;
    _canvas.sel_key = 0;
    edited();
    slots();
  }

  void remove_row()
  {
    if (!need_row()) return;
    will_change();
    _doc.remove_row(_canvas.sel_row);
    _canvas.sel_row = -1;
    _canvas.sel_key = -1;
    edited();
  }

  void height(float d)
  {
    if (!need_row()) return;
    will_change();
    _doc.set_height(_canvas.sel_row, _doc.rows.get(_canvas.sel_row).height + d);
    edited();
  }

  // ---- the nine names of a key ----

  void slots()
  {
    if (!need_key()) return;
    final LayoutDoc.Key k = _doc.key(_canvas.sel_row, _canvas.sel_key);
    String[] items = new String[9];
    for (int i = 0; i < 9; i++)
      items[i] = LayoutDoc.SLOTS[i] + "    " + (k.names[i] == null ? "–" : LayoutDoc.label(k.names[i]) + "   " + QuickNames.of_key(k.names[i]));
    new AlertDialog.Builder(this).setTitle("Key names (tap, then the swipes)")
      .setItems(items, (di, i) -> slot(k, i))
      .setPositiveButton("Done", null).show();
  }

  void slot(final LayoutDoc.Key k, final int i)
  {
    String[] items = { "Pick a quick…", "quickapp (quickopen : app)…", "Type text or emoji…", "Type a key name…", "Empty" };
    new AlertDialog.Builder(this).setTitle(LayoutDoc.SLOTS[i])
      .setItems(items, (di, w) -> {
        switch (w)
        {
          case 0:
            KeyEditor.pick(this, _prefs, "Quick for " + LayoutDoc.SLOTS[i],
                n -> set_name(k, i, n), this::slots);
            break;
          case 1:
            AppPick.show(this, LayoutDoc.SLOTS[i] + " : quickapp",
                n -> set_name(k, i, n), this::slots);
            break;
          case 2: ask_text(k, i, false); break;
          case 3: ask_text(k, i, true); break;
          default: set_name(k, i, null); break;
        }
      })
      .setNegativeButton(android.R.string.cancel, (di, w) -> slots()).show();
  }

  /** [raw]: a key name as is (app:…, layout:…~once, any name); otherwise
      text the key types. */
  void ask_text(final LayoutDoc.Key k, final int i, final boolean raw)
  {
    final EditText t = new EditText(this);
    t.setSingleLine(true);
    t.setHint(raw ? "a quick name or a legacy name, e.g. quickopen:app:com.termux, pageswitch:symbols~once, copy" : "e.g. ♪  🎶  ♪~(´ε` )");
    new AlertDialog.Builder(this).setTitle(raw ? "Key name" : "Text").setView(t)
      .setPositiveButton(android.R.string.ok, (di, w) -> {
        String s = t.getText().toString();
        String n = raw ? s.trim() : KeyMap.name_for_text(s);
        if (n == null || n.isEmpty() || KeyMap.key_of_name(n) == null)
        {
          toast(raw ? "Not a key name" : "Can't put that text on a key");
          slot(k, i);
          return;
        }
        set_name(k, i, n);
      })
      .setNegativeButton(android.R.string.cancel, (di, w) -> slot(k, i)).show();
  }

  void set_name(LayoutDoc.Key k, int i, String n)
  {
    will_change();
    k.names[i] = n;
    edited();
    slots();
  }

  // ---- saving and opening ----

  String name_field()
  {
    return _name.getText().toString().trim();
  }

  void save(boolean as_new)
  {
    final String n = name_field();
    if (as_new || _saved_as == null || !n.equals(_saved_as))
    {
      if (!LayoutDoc.valid_name(n))
      {
        toast("Name: letters, digits, space . _ - (up to 24)");
        return;
      }
      if (_store.get("layout", n) != null && !n.equals(_saved_as))
      {
        new AlertDialog.Builder(this).setTitle("Replace \"" + n + "\"?")
          .setPositiveButton("Replace", (di, w) -> write(n))
          .setNegativeButton(android.R.string.cancel, null).show();
        return;
      }
    }
    write(n);
  }

  void write(String n)
  {
    try
    {
      if (_doc.rows.isEmpty()) { toast("A layout needs a row"); return; }
      _doc.name = n;
      // The keyboard must be able to load it: check the XML parses.
      juloo.keyboard2.KeyboardData kd = juloo.keyboard2.KeyboardData.load_string_exn(_doc.xml());
      _store.save(Module.create("layout", n, _doc.json()), false);
      _saved_as = n;
      _dirty = false;
      info();
      toast("Saved \"" + n + "\" (" + kd.rows.size() + " rows). Its key: layout:" + n);
    }
    catch (Throwable e)
    {
      toast("Not saved: " + e.getMessage());
    }
  }

  void delete()
  {
    if (_saved_as == null) { toast("Not saved yet"); return; }
    final String n = _saved_as;
    new AlertDialog.Builder(this).setTitle("Delete layout \"" + n + "\"?")
      .setMessage("Keys layout:" + n + " will show a message instead.")
      .setPositiveButton("Delete", (di, w) -> {
        _store.delete("layout", n);
        _saved_as = null;
        info();
        toast("Deleted");
      })
      .setNegativeButton(android.R.string.cancel, null).show();
  }

  void open_menu()
  {
    final List<Module> saved = _store.list("layout");
    final List<String> items = new ArrayList<String>();
    items.add("The keyboard as you use it");
    items.add("A blank keyboard");
    items.add("Convert a built-in layout to quick names…");
    for (Module m : saved) items.add("▦ " + m.name);
    Runnable go = () -> new AlertDialog.Builder(this).setTitle("Open")
      .setItems(items.toArray(new String[0]), (di, i) -> {
        if (i == 0)
        {
          LayoutDoc d = snapshot();
          if (d == null) toast("Open the keyboard once first (in any text field)");
          else load(d, null);
        }
        else if (i == 1)
          load(LayoutDoc.blank("My layout"), null);
        else if (i == 2)
          convert_menu();
        else
        {
          Module m = saved.get(i - 3);
          try { load(LayoutDoc.of_json(m.name, m.json), m.name); }
          catch (Exception e) { toast("Can't open: " + e.getMessage()); }
        }
      }).setNegativeButton(android.R.string.cancel, null).show();
    if (_dirty)
      new AlertDialog.Builder(this).setTitle("Leave the changes unsaved?")
        .setPositiveButton("Leave them", (di, w) -> go.run())
        .setNegativeButton(android.R.string.cancel, null).show();
    else
      go.run();
  }

  /** Android 8-12; 13+ goes through the OnBackInvokedCallback. */
  @SuppressWarnings("deprecation")
  @android.annotation.SuppressLint("GestureBackNavigation")
  @Override public void onBackPressed() { leave(); }

  void leave()
  {
    if (!_dirty) { finish(); return; }
    new AlertDialog.Builder(this).setTitle("Leave the changes unsaved?")
      .setPositiveButton("Leave them", (di, w) -> finish())
      .setNegativeButton("Stay", null).show();
  }

  // ---- converting a built-in layout (il-008) ----

  /** Owner: "upstream layouts can be converted in settings to quick names,
      leaving a replica of the legacy keys". */
  void convert_menu()
  {
    final String[] ids = getResources().getStringArray(juloo.keyboard2.R.array.pref_layout_values);
    final String[] labels = getResources().getStringArray(juloo.keyboard2.R.array.pref_layout_entries);
    final List<Integer> idx = new ArrayList<Integer>();
    List<String> shown = new ArrayList<String>();
    for (int i = 0; i < ids.length && i < labels.length; i++)
      if (LayoutsPreference.layout_id_of_name(getResources(), ids[i]) > 0)
      {
        idx.add(i);
        shown.add(labels[i] + "   (" + ids[i] + ")");
      }
    new AlertDialog.Builder(this).setTitle("Convert which layout?")
      .setItems(shown.toArray(new String[0]), (di, w) -> convert(ids[idx.get(w)], labels[idx.get(w)]))
      .setNegativeButton(android.R.string.cancel, null).show();
  }

  /** Saves "<name>" in quick names and "<name> legacy", the replica, both
      with the bottom row the keyboard adds to the built-in one. */
  void convert(String id, String label)
  {
    try
    {
      int res = LayoutsPreference.layout_id_of_name(getResources(), id);
      juloo.keyboard2.KeyboardData kd = juloo.keyboard2.KeyboardData.load(getResources(), res);
      if (kd.bottom_row)
        kd = kd.insert_row(juloo.keyboard2.KeyboardData.load_row(getResources(), juloo.keyboard2.R.xml.bottom_row), kd.rows.size());
      String base = label.replaceAll("[^A-Za-z0-9 _.-]", " ").replaceAll(" +", " ").trim();
      if (base.isEmpty() || !Character.isLetterOrDigit(base.charAt(0))) base = id;
      if (base.length() > 17) base = base.substring(0, 17).trim();
      final LayoutDoc legacy = LayoutDoc.of_keyboard(kd, base + " legacy");
      final LayoutDoc quick = legacy.with_quick_names(base);
      // The quick one must load to the same keys as the replica.
      juloo.keyboard2.KeyboardData a = juloo.keyboard2.KeyboardData.load_string_exn(legacy.xml());
      juloo.keyboard2.KeyboardData b = juloo.keyboard2.KeyboardData.load_string_exn(quick.xml());
      for (int r = 0; r < a.rows.size(); r++)
        for (int k = 0; k < a.rows.get(r).keys.size(); k++)
          if (!java.util.Arrays.equals(a.rows.get(r).keys.get(k).keys, b.rows.get(r).keys.get(k).keys))
            throw new IllegalStateException("row " + (r + 1) + " key " + (k + 1) + " differs");
      final String qn = base, ln = legacy.name;
      Runnable save = () -> {
        try
        {
          _store.save(Module.create("layout", ln, legacy.json()), false);
          _store.save(Module.create("layout", qn, quick.json()), false);
          load(quick, qn);
          toast("Saved \"" + qn + "\" in quick names and \"" + ln + "\" as it was");
        }
        catch (Throwable e) { toast("Not saved: " + e.getMessage()); }
      };
      if (_store.get("layout", qn) != null || _store.get("layout", ln) != null)
        new AlertDialog.Builder(this).setTitle("Replace \"" + qn + "\" and \"" + ln + "\"?")
          .setPositiveButton("Replace", (di, w) -> save.run())
          .setNegativeButton(android.R.string.cancel, null).show();
      else
        save.run();
    }
    catch (Throwable e)
    {
      toast("Can't convert " + id + ": " + e.getMessage());
    }
  }

  // ---- using it ----

  void copy_key_name()
  {
    if (_saved_as == null) { toast("Save it first"); return; }
    copy(Behaviours.LAYOUT_PREFIX + _saved_as);
    toast("Copied layout:" + _saved_as + " — place it with edit mode → Replace with… (search \"Your layouts\")");
  }

  /** Into the quick language's list (Studio → Languages), as a custom layout. */
  void add_to_layouts()
  {
    try
    {
      if (_doc.rows.isEmpty()) { toast("A layout needs a row"); return; }
      _doc.name = name_field().isEmpty() ? "My layout" : name_field();
      String xml = _doc.xml();
      juloo.keyboard2.KeyboardData.load_string_exn(xml);
      List<LayoutsPreference.Layout> ls = LayoutsPreference.load_layouts(_prefs);
      ls.add(LayoutsPreference.CustomLayout.parse(xml));
      SharedPreferences.Editor e = _prefs.edit();
      LayoutsPreference.save_to_preferences(e, ls);
      e.apply();
      toast("Added: the quick language switches to it (Studio → Languages lists it)");
    }
    catch (Throwable e)
    {
      toast("Not added: " + e.getMessage());
    }
  }

  void show_xml()
  {
    _doc.name = name_field();
    final String xml = _doc.xml();
    TextView t = new TextView(this);
    t.setText(xml);
    t.setTextIsSelectable(true);
    t.setTypeface(android.graphics.Typeface.MONOSPACE);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
    t.setPadding(dp(16), dp(8), dp(16), dp(8));
    ScrollView sv = new ScrollView(this);
    sv.addView(t);
    new AlertDialog.Builder(this).setTitle("Layout XML").setView(sv)
      .setPositiveButton("Copy", (di, w) -> copy(xml))
      .setNegativeButton(android.R.string.cancel, null).show();
  }

  void copy(String s)
  {
    ((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("Input Layer", s));
  }

  void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_LONG).show(); }

  int dp(int v)
  {
    return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
  }
}
