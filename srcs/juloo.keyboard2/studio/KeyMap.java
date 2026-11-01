package juloo.keyboard2.studio;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;
import org.json.JSONException;
import org.json.JSONObject;

/** The owner's key changes, from the active [keymap] module, made in layout
    mode: per layout (by its name), per key (by the name of its tap before
    any change), what each direction holds. "" removes what is there.

    { "module": "keymap", "name": "mine", "version": 1,
      "layouts": { "Input Layer QWERTZ": {
          "q": { "ne": "" },
          "enter": { "s": "config" } } } } */
public final class KeyMap
{
  /** Directions as in [ComfortKeySpec.DIRECTIONS], to the index in
      [KeyboardData.Key.keys]. */
  static final int[] KEY_INDEX = { 0, 7, 2, 6, 4, 8, 3, 5, 1 };

  /** layout -> key id -> per direction ([ComfortKeySpec.DIRECTIONS] order):
      the key's name, "" for none, [null] for unchanged. */
  final Map<String, Map<String, String[]>> _layouts =
    new HashMap<String, Map<String, String[]>>();

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
      Map<String, String[]> keys = new HashMap<String, String[]>();
      Iterator<String> ki = ks.keys();
      while (ki.hasNext())
      {
        String id = ki.next();
        JSONObject ds = ks.optJSONObject(id);
        if (ds == null)
          continue;
        String[] v = new String[KEY_INDEX.length];
        boolean any = false;
        for (int d = 0; d < v.length; d++)
          if (ds.has(ComfortKeySpec.DIRECTIONS[d]))
          {
            v[d] = ds.optString(ComfortKeySpec.DIRECTIONS[d], "");
            any = true;
          }
        if (any)
          keys.put(id, v);
      }
      if (!keys.isEmpty())
        m._layouts.put(layout, keys);
    }
    return m;
  }

  public boolean is_empty() { return _layouts.isEmpty(); }

  public static String layout_key(KeyboardData kw)
  {
    return (kw.name == null) ? "" : kw.name;
  }

  /** [kw] with the changes for its layout. */
  public KeyboardData apply(KeyboardData kw)
  {
    final Map<String, String[]> keys = _layouts.get(layout_key(kw));
    if (keys == null)
      return kw;
    final Map<KeyValue, String[]> by_tap = new HashMap<KeyValue, String[]>();
    for (Map.Entry<String, String[]> e : keys.entrySet())
    {
      KeyValue tap = key_of_name(e.getKey());
      if (tap != null)
        by_tap.put(tap, e.getValue());
    }
    return kw.mapKeys(new KeyboardData.MapKey() {
      public KeyboardData.Key apply(KeyboardData.Key k)
      {
        String[] v = (k.keys[0] == null) ? null : by_tap.get(k.keys[0]);
        if (v == null)
          return k;
        for (int d = 0; d < v.length; d++)
          if (v[d] != null)
            k = k.withKeyValue(KEY_INDEX[d], v[d].isEmpty() ? null : key_of_name(v[d]));
        return k;
      }
    });
  }

  /** The id of the key [k] shown in [layout]: the name of its tap before
      the changes. */
  public String id_of(String layout, KeyboardData.Key k)
  {
    String tap = (k.keys[0] == null) ? "" : name_of(k.keys[0]);
    Map<String, String[]> keys = _layouts.get(layout);
    if (keys != null)
      for (Map.Entry<String, String[]> e : keys.entrySet())
        if (tap.equals(e.getValue()[0]))
          return e.getKey(); // Its tap was changed.
    return tap;
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
    if (ds.length() == 0)
      ks.remove(id);
    if (ks.length() == 0)
      ls.remove(layout);
    return r;
  }

  /** Whether direction [dir] of key [id] in [layout] is changed. */
  public boolean is_changed(String layout, String id, int dir)
  {
    Map<String, String[]> keys = _layouts.get(layout);
    String[] v = (keys == null) ? null : keys.get(id);
    return v != null && v[dir] != null;
  }
}
