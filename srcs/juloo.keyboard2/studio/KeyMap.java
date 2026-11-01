package juloo.keyboard2.studio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Iterator;
import java.util.Map;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The owner's key changes, from the active [keymap] module, made in layout
    mode: per layout (by its name), per key (by the name of its tap before
    any change), what each direction holds ("" removes what is there), its
    [width] in key units, and [split]: the key is cut in two halves, the
    right one tapping that key (changed in turn under its own name).

    { "module": "keymap", "name": "mine", "version": 1,
      "layouts": { "Input Layer QWERTZ": {
          "q": { "ne": "" },
          "enter": { "s": "config" },
          "backspace": { "width": 2.25, "split": "delete" } } } } */
public final class KeyMap
{
  /** Directions as in [ComfortKeySpec.DIRECTIONS], to the index in
      [KeyboardData.Key.keys]. */
  static final int[] KEY_INDEX = { 0, 7, 2, 6, 4, 8, 3, 5, 1 };
  public static final float WIDTH_MIN = 0.5f;
  public static final float WIDTH_MAX = 5f;
  public static final float WIDTH_STEP = 0.25f;

  static final class Change
  {
    /** Per direction ([ComfortKeySpec.DIRECTIONS] order): the key's name,
        "" for none, [null] for unchanged. */
    final String[] dirs = new String[KEY_INDEX.length];
    /** [NaN] when unchanged. */
    float width = Float.NaN;
    /** Tap of the right half, [null] when not split. */
    String split = null;
  }

  /** layout -> key id -> its changes. */
  final Map<String, Map<String, Change>> _layouts =
    new HashMap<String, Map<String, Change>>();
  /** layout -> its rows as key ids, top to bottom, when the edit key has
      rearranged it ([ROWS] in the module). */
  final Map<String, List<List<String>>> _structures =
    new HashMap<String, List<List<String>>>();
  /** Field of a layout holding its rows, not a key id. */
  public static final String ROWS = "_rows";

  public static KeyMap of_json(JSONObject j)
  {
    KeyMap m = new KeyMap();
    JSONObject ls = (j == null) ? null : j.optJSONObject("layouts");
    if (ls == null)
      return m;
    Iterator<String> li = ls.keys();
    while (li.hasNext())
    {
      String layout = li.next();
      JSONObject ks = ls.optJSONObject(layout);
      if (ks == null)
        continue;
      Map<String, Change> keys = new HashMap<String, Change>();
      Iterator<String> ki = ks.keys();
      while (ki.hasNext())
      {
        String id = ki.next();
        JSONObject ds = ks.optJSONObject(id);
        if (ds == null)
          continue;
        Change c = new Change();
        boolean any = false;
        for (int d = 0; d < c.dirs.length; d++)
          if (ds.has(ComfortKeySpec.DIRECTIONS[d]))
          {
            c.dirs[d] = ds.optString(ComfortKeySpec.DIRECTIONS[d], "");
            any = true;
          }
        double w = ds.optDouble("width", Double.NaN);
        if (!Double.isNaN(w))
        {
          c.width = Heights.clamp((float)w, WIDTH_MIN, WIDTH_MAX);
          any = true;
        }
        String sp = ds.optString("split", "");
        if (!sp.isEmpty() && key_of_name(sp) != null)
        {
          c.split = sp;
          any = true;
        }
        if (any)
          keys.put(id, c);
      }
      if (!keys.isEmpty())
        m._layouts.put(layout, keys);
      List<List<String>> rows = rows_of(ks.optJSONArray(ROWS));
      if (rows != null)
        m._structures.put(layout, rows);
    }
    return m;
  }

  static List<List<String>> rows_of(JSONArray a)
  {
    if (a == null)
      return null;
    List<List<String>> rows = new ArrayList<List<String>>();
    for (int i = 0; i < a.length(); i++)
    {
      JSONArray r = a.optJSONArray(i);
      if (r == null)
        continue;
      List<String> row = new ArrayList<String>();
      for (int j = 0; j < r.length(); j++)
      {
        String id = r.optString(j, "");
        if (!id.isEmpty())
          row.add(id);
      }
      if (!row.isEmpty())
        rows.add(row);
    }
    return rows.isEmpty() ? null : rows;
  }

  public boolean is_empty() { return _layouts.isEmpty() && _structures.isEmpty(); }

  /** The rows of [layout] as the edit key arranged them, [null] if it
      didn't. */
  public List<List<String>> structure(String layout)
  {
    List<List<String>> r = _structures.get(layout);
    return (r == null) ? null : Arrange.copy(r);
  }

  /** The rows of [kw] (shown, with the changes) as key ids. */
  public List<List<String>> rows_now(KeyboardData kw)
  {
    String layout = layout_key(kw);
    List<List<String>> rows = new ArrayList<List<String>>();
    for (KeyboardData.Row r : kw.rows)
    {
      List<String> row = new ArrayList<String>();
      for (KeyboardData.Key k : r.keys)
        if (k.keys[0] != null)
          row.add(id_of(layout, k));
      rows.add(row);
    }
    return rows;
  }

  public static String layout_key(KeyboardData kw)
  {
    return (kw.name == null) ? "" : kw.name;
  }

  /** [kw] with the changes for its layout. */
  public KeyboardData apply(KeyboardData kw)
  {
    Map<String, Change> keys = _layouts.get(layout_key(kw));
    List<List<String>> structure = _structures.get(layout_key(kw));
    if (keys == null && structure == null)
      return kw;
    if (keys == null)
      keys = new HashMap<String, Change>();
    Map<KeyValue, Change> by_tap = new HashMap<KeyValue, Change>();
    for (Map.Entry<String, Change> e : keys.entrySet())
    {
      KeyValue tap = key_of_name(e.getKey());
      if (tap != null)
        by_tap.put(tap, e.getValue());
    }
    List<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    for (KeyboardData.Row r : kw.rows)
    {
      List<KeyboardData.Key> ks = new ArrayList<KeyboardData.Key>();
      for (KeyboardData.Key k : r.keys)
      {
        Change c = (k.keys[0] == null) ? null : by_tap.get(k.keys[0]);
        if (c == null)
        {
          ks.add(k);
          continue;
        }
        k = change(k, c);
        if (c.split == null)
        {
          ks.add(k);
          continue;
        }
        // Two halves; the right one taps [split] and has its own changes.
        float w = k.width / 2;
        ks.add(k.withWidth(w));
        KeyboardData.Key h = k.withWidthAndShift(w, 0f);
        for (int i = 0; i < h.keys.length; i++)
          h = h.withKeyValue(i, (i == 0) ? key_of_name(c.split) : null);
        Change hc = by_tap.get(h.keys[0]);
        ks.add((hc == null || hc == c) ? h : change(h, hc));
      }
      rows.add(r.with_keys(ks));
    }
    if (structure != null)
      rows = arrange(layout_key(kw), rows, structure, keys);
    return kw.with_rows(rows);
  }

  /** [rows] put in the order of [structure]. Keys named there but not in
      the layout are added (one key wide, then changed like the others);
      rows none of whose keys are named stay where they were (the layout
      gained them later); other keys not named were removed. A row takes
      the height of the row its first key came from. */
  List<KeyboardData.Row> arrange(String layout, List<KeyboardData.Row> rows,
      List<List<String>> structure, Map<String, Change> keys)
  {
    Map<String, KeyboardData.Key> by_id = new HashMap<String, KeyboardData.Key>();
    Map<String, KeyboardData.Row> row_of = new HashMap<String, KeyboardData.Row>();
    KeyboardData.Key template = null;
    for (KeyboardData.Row r : rows)
      for (KeyboardData.Key k : r.keys)
      {
        if (k.keys[0] == null)
          continue;
        String id = id_of(layout, k);
        if (!by_id.containsKey(id))
        {
          by_id.put(id, k);
          row_of.put(id, r);
        }
        if (template == null && k.role == KeyboardData.Key.Role.Normal)
          template = k;
      }
    java.util.Set<String> named = new java.util.HashSet<String>();
    for (List<String> sr : structure)
      named.addAll(sr);
    List<KeyboardData.Row> out = new ArrayList<KeyboardData.Row>();
    for (List<String> sr : structure)
    {
      List<KeyboardData.Key> ks = new ArrayList<KeyboardData.Key>();
      KeyboardData.Row from = null;
      for (String id : sr)
      {
        KeyboardData.Key k = by_id.get(id);
        if (k == null)
          k = new_key(id, template, keys.get(id));
        if (k == null)
          continue;
        ks.add(k);
        if (from == null)
          from = row_of.get(id);
      }
      if (ks.isEmpty())
        continue;
      if (from == null)
        from = rows.isEmpty() ? null : rows.get(0);
      if (from != null)
        out.add(from.with_keys(ks));
    }
    for (int i = 0; i < rows.size(); i++)
    {
      boolean any = false;
      for (KeyboardData.Key k : rows.get(i).keys)
        if (k.keys[0] != null && named.contains(id_of(layout, k)))
          any = true;
      if (!any)
        out.add(Math.min(i, out.size()), rows.get(i));
    }
    return out;
  }

  /** A key added by the edit key: one key wide, tapping [id]. */
  static KeyboardData.Key new_key(String id, KeyboardData.Key template, Change c)
  {
    KeyValue tap = key_of_name(id);
    if (tap == null || template == null)
      return null;
    KeyboardData.Key k = template.withWidthAndShift(1f, 0f);
    for (int i = 0; i < k.keys.length; i++)
      k = k.withKeyValue(i, (i == 0) ? tap : null);
    return (c == null) ? k : change(k, c);
  }

  /** Fields of a [keymap] module (a copy of [j]) with [rows] as the
      arrangement of [layout], or none if [null]. */
  public static JSONObject set_rows(JSONObject j, String layout,
      List<List<String>> rows) throws JSONException
  {
    JSONArray a = null;
    if (rows != null)
    {
      a = new JSONArray();
      for (List<String> r : rows)
        a.put(new JSONArray(r));
    }
    return set_field(j, layout, ROWS, null, a, true);
  }

  /** [k] with the directions and width of [c] (not its split). */
  static KeyboardData.Key change(KeyboardData.Key k, Change c)
  {
    for (int d = 0; d < c.dirs.length; d++)
      if (c.dirs[d] != null)
        k = k.withKeyValue(KEY_INDEX[d], c.dirs[d].isEmpty() ? null : key_of_name(c.dirs[d]));
    if (!Float.isNaN(c.width))
      k = k.withWidth(c.width);
    return k;
  }

  /** The id of the key [k] shown in [layout]: the name of its tap before
      the changes. */
  public String id_of(String layout, KeyboardData.Key k)
  {
    String tap = (k.keys[0] == null) ? "" : name_of(k.keys[0]);
    Map<String, Change> keys = _layouts.get(layout);
    if (keys != null)
      for (Map.Entry<String, Change> e : keys.entrySet())
        if (tap.equals(e.getValue().dirs[0]))
          return e.getKey(); // Its tap was changed.
    return tap;
  }

  /** The split of key [id], [null] if it isn't. */
  public String split_of(String layout, String id)
  {
    Change c = change_of(layout, id);
    return (c == null) ? null : c.split;
  }

  Change change_of(String layout, String id)
  {
    Map<String, Change> keys = _layouts.get(layout);
    return (keys == null) ? null : keys.get(id);
  }

  static KeyValue key_of_name(String n)
  {
    try { return KeyValue.getKeyByName(n); }
    catch (Exception _e) { return null; }
  }

  /** A name that gives [kv] back with [KeyValue.getKeyByName]. */
  public static String name_of(KeyValue kv)
  {
    if (kv == null)
      return "";
    String s = kv.getString();
    if (kv.equals(key_of_name(s)))
      return s;
    if (kv.getKind() == KeyValue.Kind.Char)
    {
      String c = String.valueOf(kv.getChar());
      if (kv.equals(key_of_name(c)))
        return c;
      if (kv.equals(key_of_name("\\" + c)))
        return "\\" + c;
    }
    for (String n : KeyNames.SPECIAL)
      if (kv.equals(key_of_name(n)))
        return n;
    return s;
  }

  /** A name for a key that types [text] (any text: emoji, ♪, a kaomoji),
      [null] if [text] can't be written as a key. */
  public static String name_for_text(String text)
  {
    if (text == null || text.isEmpty())
      return null;
    // Plain text is a string key, unless it has a ':' or is a key's name.
    if (text.indexOf(':') < 0 && KeyValue.getSpecialKeyByName(text) == null)
      return text;
    // Quoted; the quote is the only escape there.
    if (text.indexOf('\\') >= 0)
      return null;
    return ":str:'" + text.replace("'", "\\'") + "'";
  }

  /** The names of the 9 directions of [k], "" where empty. */
  public static String[] names_of(KeyboardData.Key k)
  {
    String[] r = new String[KEY_INDEX.length];
    for (int d = 0; d < r.length; d++)
      r[d] = name_of(k.keys[KEY_INDEX[d]]);
    return r;
  }

  /** Fields of a [keymap] module (a copy of [j]) with direction [dir] of
      key [id] in [layout] set to key [name] ("" to empty it), or back to the
      layout's own if [name] is [null]. */
  public static JSONObject set(JSONObject j, String layout, String id, int dir,
      String name) throws JSONException
  {
    JSONObject r = new JSONObject(j.toString());
    JSONObject ls = r.optJSONObject("layouts");
    if (ls == null)
      r.put("layouts", ls = new JSONObject());
    JSONObject ks = ls.optJSONObject(layout);
    if (ks == null)
      ls.put(layout, ks = new JSONObject());
    JSONObject ds = ks.optJSONObject(id);
    if (ds == null)
      ks.put(id, ds = new JSONObject());
    String d = ComfortKeySpec.DIRECTIONS[dir];
    if (name == null)
      ds.remove(d);
    else
      ds.put(d, name);
    return clean(r, ls, ks, ds, layout, id);
  }

  /** Fields of a [keymap] module (a copy of [j]) with the width of key [id]
      set to [width] (clamped), or back to the layout's if [NaN]. */
  public static JSONObject set_width(JSONObject j, String layout, String id,
      float width) throws JSONException
  {
    return set_field(j, layout, id, "width",
        Float.isNaN(width) ? null : (Object)(double)Heights.round2(
          Heights.clamp(width, WIDTH_MIN, WIDTH_MAX)));
  }

  /** Key [id] cut in two, the right half tapping [tap]; joined again if
      [tap] is [null]. */
  public static JSONObject set_split(JSONObject j, String layout, String id,
      String tap) throws JSONException
  {
    return set_field(j, layout, id, "split", tap);
  }

  static JSONObject set_field(JSONObject j, String layout, String id,
      String field, Object v) throws JSONException
  {
    return set_field(j, layout, id, field, v, false);
  }

  /** With [layout_level], [id] is a field of the layout itself ([field] is
      unused). */
  static JSONObject set_field(JSONObject j, String layout, String id,
      String field, Object v, boolean layout_level) throws JSONException
  {
    if (layout_level)
    {
      JSONObject r = new JSONObject(j.toString());
      JSONObject ls = r.optJSONObject("layouts");
      if (ls == null)
        r.put("layouts", ls = new JSONObject());
      JSONObject ks = ls.optJSONObject(layout);
      if (ks == null)
        ls.put(layout, ks = new JSONObject());
      if (v == null)
        ks.remove(id);
      else
        ks.put(id, v);
      if (ks.length() == 0)
        ls.remove(layout);
      return r;
    }
    JSONObject r = new JSONObject(j.toString());
    JSONObject ls = r.optJSONObject("layouts");
    if (ls == null)
      r.put("layouts", ls = new JSONObject());
    JSONObject ks = ls.optJSONObject(layout);
    if (ks == null)
      ls.put(layout, ks = new JSONObject());
    JSONObject ds = ks.optJSONObject(id);
    if (ds == null)
      ks.put(id, ds = new JSONObject());
    if (v == null)
      ds.remove(field);
    else
      ds.put(field, v);
    return clean(r, ls, ks, ds, layout, id);
  }

  /** Remove what became empty. */
  static JSONObject clean(JSONObject r, JSONObject ls, JSONObject ks,
      JSONObject ds, String layout, String id)
  {
    if (ds.length() == 0)
      ks.remove(id);
    if (ks.length() == 0)
      ls.remove(layout);
    return r;
  }

  /** Fields of a [keymap] module (a copy of [j]) with keys [a] and [b] of
      [layout] swapping what they hold: [names_a] and [names_b] are what
      they show now ([names_of]). */
  public static JSONObject swap(JSONObject j, String layout, String a,
      String[] names_a, String b, String[] names_b) throws JSONException
  {
    JSONObject r = j;
    for (int d = 0; d < KEY_INDEX.length; d++)
    {
      if (names_a[d].equals(names_b[d]))
        continue;
      r = set(r, layout, a, d, names_b[d]);
      r = set(r, layout, b, d, names_a[d]);
    }
    return new JSONObject(r.toString());
  }

  /** Whether direction [dir] of key [id] in [layout] is changed. */
  public boolean is_changed(String layout, String id, int dir)
  {
    Change c = change_of(layout, id);
    return c != null && c.dirs[dir] != null;
  }
}
