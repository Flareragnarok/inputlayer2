package juloo.keyboard2.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Logs;
import org.json.JSONObject;

/** Studio's editor for one key, opened from layout mode: its 9 directions,
    each can get any key (from the picker), be emptied, or go back to the
    layout's own. Saved in the active [keymap] module, or a new one named
    "mine". The changes apply to that layout only. */
public final class KeyEditor
{
  static final String[] DIR_LABELS = { "Tap", "↑", "↗", "→", "↘", "↓", "↙", "←", "↖" };
  static final String NEW_MODULE = "mine";

  final Activity _act;
  final SharedPreferences _prefs;
  final String _layout;
  final String _id;
  /** What each direction holds now, "" when empty, [null] when back to the
      layout's own (not known here). */
  final String[] _names;

  /** The layout's other keys, for swapping: ids and 9 names each. */
  final String[] _other_ids;
  final String[] _other_names;

  public KeyEditor(Activity act, SharedPreferences prefs, String layout,
      String id, String[] names, String[] other_ids, String[] other_names)
  {
    boolean others_ok = other_ids != null && other_names != null
      && other_names.length == other_ids.length * DIR_LABELS.length;
    _other_ids = others_ok ? other_ids : new String[0];
    _other_names = others_ok ? other_names : new String[0];
    _act = act;
    _prefs = prefs;
    _layout = (layout == null) ? "" : layout;
    _id = id;
    _names = (names != null && names.length == DIR_LABELS.length)
      ? names.clone() : new String[DIR_LABELS.length];
  }

  KeyMap keymap()
  {
    Module m = new ModuleStore(_prefs).active("keymap");
    return KeyMap.of_json(m == null ? null : m.json);
  }

  static String glyph(String name)
  {
    if (name == null)
      return "as in the layout";
    if (name.isEmpty())
      return "—";
    KeyValue kv = KeyMap.key_of_name(name);
    String g = (kv == null || kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT)) ? name : kv.getString();
    return (g.equals(name) || g.isEmpty()) ? name : g + "  (" + name + ")";
  }

  public void show()
  {
    KeyMap km = keymap();
    // The 9 directions, then swapping the whole key.
    final boolean can_swap = _other_ids.length > 0 && known(_names);
    String[] rows = new String[DIR_LABELS.length + (can_swap ? 1 : 0)];
    for (int d = 0; d < DIR_LABELS.length; d++)
      rows[d] = DIR_LABELS[d] + "    " + glyph(_names[d])
        + (km.is_changed(_layout, _id, d) ? "    • changed" : "");
    if (can_swap)
      rows[DIR_LABELS.length] = "⇄  Swap the whole key with…";
    new AlertDialog.Builder(_act)
      .setTitle("Key " + glyph(_id) + " · " + (_layout.isEmpty() ? "layout" : _layout))
      .setItems(rows, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int d)
        {
          if (d < DIR_LABELS.length)
            direction(d);
          else
            pick_swap();
        }
      })
      .setNegativeButton("Done", null)
      .show();
  }

  void direction(final int d)
  {
    final boolean changed = keymap().is_changed(_layout, _id, d);
    final boolean empty = "".equals(_names[d]);
    final List<String> labels = new ArrayList<String>();
    final List<Runnable> acts = new ArrayList<Runnable>();
    labels.add(empty ? "Add key…" : "Replace with…");
    acts.add(new Runnable() { public void run() { pick(d); } });
    labels.add("Type text or emoji…");
    acts.add(new Runnable() { public void run() { type_text(d); } });
    if (!empty)
    {
      labels.add("Remove");
      acts.add(new Runnable() { public void run() { save(d, ""); } });
    }
    if (changed)
    {
      labels.add("Back to the layout's own");
      acts.add(new Runnable() { public void run() { save(d, null); } });
    }
    new AlertDialog.Builder(_act)
      .setTitle(DIR_LABELS[d] + "    " + glyph(_names[d]))
      .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int i) { acts.get(i).run(); }
      })
      .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int i) { show(); }
      })
      .show();
  }

  /** The list of every key, with a filter. */
  void pick(final int d)
  {
    final List<KeyCatalog.Entry> all;
    try { all = KeyCatalog.all(_act.getResources()); }
    catch (Throwable e)
    {
      Toast.makeText(_act, "Can't list the keys: " + e, Toast.LENGTH_LONG).show();
      return;
    }
    final List<KeyCatalog.Entry> shown = new ArrayList<KeyCatalog.Entry>(all);
    final ArrayAdapter<String> adapter = new ArrayAdapter<String>(_act,
        android.R.layout.simple_list_item_1, labels(shown));
    LinearLayout box = new LinearLayout(_act);
    box.setOrientation(LinearLayout.VERTICAL);
    final EditText filter = new EditText(_act);
    filter.setHint("Search: letter, name or group (ё, config, greek, music, emoji)");
    filter.setSingleLine(true);
    final ListView list = new ListView(_act);
    list.setAdapter(adapter);
    box.addView(filter);
    box.addView(list, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    final AlertDialog dlg = new AlertDialog.Builder(_act)
      .setTitle("Key for " + DIR_LABELS[d])
      .setView(box)
      .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int i) { direction(d); }
      })
      .create();
    filter.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(Editable s)
      {
        shown.clear();
        shown.addAll(KeyCatalog.filter(all, s.toString()));
        adapter.clear();
        adapter.addAll(labels(shown));
      }
    });
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> p, View v, int i, long id)
      {
        dlg.dismiss();
        save(d, shown.get(i).name);
      }
    });
    dlg.show();
  }

  /** Whether every direction is known (none "back to the layout's own",
      which isn't known here). */
  static boolean known(String[] names)
  {
    for (String n : names)
      if (n == null)
        return false;
    return true;
  }

  String[] other_names(int i)
  {
    String[] r = new String[DIR_LABELS.length];
    System.arraycopy(_other_names, i * r.length, r, 0, r.length);
    return r;
  }

  /** Swap with another key of the layout: everything it holds, tap and
      swipes. */
  void pick_swap()
  {
    String[] labels = new String[_other_ids.length];
    for (int i = 0; i < labels.length; i++)
      labels[i] = glyph(other_names(i)[0]);
    new AlertDialog.Builder(_act)
      .setTitle("Swap " + glyph(_names[0]) + " with")
      .setItems(labels, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int i) { swap(i); }
      })
      .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int i) { show(); }
      })
      .show();
  }

  void swap(int i)
  {
    String[] theirs = other_names(i);
    try
    {
      ModuleStore store = new ModuleStore(_prefs);
      Module m = store.active("keymap");
      if (m == null)
        m = store.get("keymap", NEW_MODULE);
      JSONObject j = KeyMap.swap(m == null ? new JSONObject() : m.json,
          _layout, _id, _names, _other_ids[i], theirs);
      Module nm = (m == null) ? Module.create("keymap", NEW_MODULE, j) : new Module(j);
      store.save(nm, true);
      String[] mine = _names.clone();
      System.arraycopy(theirs, 0, _names, 0, _names.length);
      System.arraycopy(mine, 0, _other_names, i * mine.length, mine.length);
      Toast.makeText(_act, "Swapped", Toast.LENGTH_SHORT).show();
    }
    catch (Exception e)
    {
      Logs.exn("Key editor swap", e);
      Toast.makeText(_act, "Not swapped: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
    show();
  }

  /** Any text on the key: an emoji, ♪, a kaomoji, a word. */
  void type_text(final int d)
  {
    final EditText t = new EditText(_act);
    t.setHint("e.g. ♪  🎶  ♪~(´ε` )");
    KeyValue cur = "".equals(_names[d]) || _names[d] == null ? null
      : KeyMap.key_of_name(_names[d]);
    if (cur != null && cur.getKind() == KeyValue.Kind.String)
      t.setText(cur.getString());
    new AlertDialog.Builder(_act)
      .setTitle("Text for " + DIR_LABELS[d])
      .setView(t)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int i)
        {
          String n = KeyMap.name_for_text(t.getText().toString());
          if (n == null)
          {
            Toast.makeText(_act, "Can't put that text on a key (empty, or a \\ with a :)",
                Toast.LENGTH_LONG).show();
            direction(d);
          }
          else
            save(d, n);
        }
      })
      .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int i) { direction(d); }
      })
      .show();
  }

  static List<String> labels(List<KeyCatalog.Entry> es)
  {
    List<String> l = new ArrayList<String>(es.size());
    for (KeyCatalog.Entry e : es)
      l.add(e.label());
    return l;
  }

  /** Set direction [d] to key [name] ("" empty, [null] the layout's own). */
  void save(int d, String name)
  {
    try
    {
      ModuleStore store = new ModuleStore(_prefs);
      Module m = store.active("keymap");
      if (m == null)
        m = store.get("keymap", NEW_MODULE);
      JSONObject j = KeyMap.set(m == null ? new JSONObject() : m.json,
          _layout, _id, d, name);
      Module nm = (m == null) ? Module.create("keymap", NEW_MODULE, j) : new Module(j);
      store.save(nm, true);
      _names[d] = name;
    }
    catch (Exception e)
    {
      Logs.exn("Key editor", e);
      Toast.makeText(_act, "Not saved: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
    show();
  }
}
