package juloo.keyboard2.studio;

import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.KeyValue;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** A whole keyboard as the layout editor edits it (il-006, owner
    2026-11-06: "a full scale layout editor where we can add and remove and
    resize buttons liberally, save those layouts, switch to them via an
    action key"). Rows of keys; a key has a width, a gap before it and up to
    nine names (index as in [KeyboardData.Key.keys]: 0 the tap, then nw ne
    sw se w e n s). Saved as a [layout] module; the keyboard loads it as
    layout XML, exactly as drawn: no bottom row or number row added. */
public final class LayoutDoc
{
  public static final float WIDTH_MIN = 0.25f, WIDTH_MAX = 10f;
  public static final float HEIGHT_MIN = 0.4f, HEIGHT_MAX = 3f;
  /** Slot names in index order, for the editor's lists. */
  public static final String[] SLOTS = { "tap", "↖", "↗", "↙", "↘", "←", "→", "↑", "↓" };

  public static final class Key
  {
    public float width = 1f, shift = 0f;
    public final String[] names = new String[9];

    Key copy()
    {
      Key k = new Key();
      k.width = width; k.shift = shift;
      System.arraycopy(names, 0, k.names, 0, 9);
      return k;
    }
  }

  public static final class Row
  {
    public float height = 1f;
    public final List<Key> keys = new ArrayList<Key>();

    public float width()
    {
      float w = 0;
      for (Key k : keys) w += k.width + k.shift;
      return w;
    }
  }

  public String name;
  public final List<Row> rows = new ArrayList<Row>();

  /** Layout names: key names refer to them (`layout:<name>`), so no `~`
      (behaviours) and nothing XML would need escaped. */
  public static boolean valid_name(String n)
  {
    return n != null && n.matches("[A-Za-z0-9][A-Za-z0-9 _.-]{0,23}");
  }

  // ---- from what the keyboard shows ----

  /** [kw] as drawn (its rows, widths, gaps, the names on each key). */
  public static LayoutDoc of_keyboard(KeyboardData kw, String name)
  {
    LayoutDoc d = new LayoutDoc();
    d.name = name;
    for (KeyboardData.Row r : kw.rows)
    {
      Row row = new Row();
      row.height = r.height;
      for (KeyboardData.Key k : r.keys)
      {
        Key key = new Key();
        key.width = k.width;
        key.shift = k.shift;
        for (int i = 0; i < 9; i++)
        {
          String n = (k.keys[i] == null) ? null : KeyMap.name_of(k.keys[i]);
          key.names[i] = (n == null || n.isEmpty()) ? null : n;
        }
        row.keys.add(key);
      }
      d.rows.add(row);
    }
    return d;
  }

  /** A plain 3-row grid to start from nothing. */
  public static LayoutDoc blank(String name)
  {
    LayoutDoc d = new LayoutDoc();
    d.name = name;
    String[] rows = { "qwertyuiop", "asdfghjkl", "zxcvbnm" };
    for (String r : rows)
    {
      Row row = new Row();
      for (char c : r.toCharArray())
      {
        Key k = new Key();
        k.names[0] = String.valueOf(c);
        row.keys.add(k);
      }
      d.rows.add(row);
    }
    Row bottom = new Row();
    for (String n : new String[]{ "il_symbols", "space", "backspace", "enter" })
    {
      Key k = new Key();
      k.names[0] = n;
      k.width = n.equals("space") ? 5f : 1.5f;
      bottom.keys.add(k);
    }
    bottom.keys.get(3).width = 2f;
    d.rows.add(bottom);
    return d;
  }

  // ---- editing ----

  public Key key(int r, int i) { return rows.get(r).keys.get(i); }

  public void add_key(int r, int at)
  {
    Key k = new Key();
    List<Key> ks = rows.get(r).keys;
    ks.add(Math.max(0, Math.min(at, ks.size())), k);
  }

  public void remove_key(int r, int i)
  {
    rows.get(r).keys.remove(i);
  }

  /** Move key [i] of row [r] by [delta] places; it crosses into the row
      above/below when it moves past an end. Returns {row, index} after. */
  public int[] move_key(int r, int i, int delta)
  {
    List<Key> ks = rows.get(r).keys;
    Key k = ks.get(i);
    int j = i + delta;
    if (j >= 0 && j < ks.size())
    {
      ks.remove(i);
      ks.add(j, k);
      return new int[]{ r, j };
    }
    int r2 = r + (delta < 0 ? -1 : 1);
    if (r2 < 0 || r2 >= rows.size())
      return new int[]{ r, i };
    ks.remove(i);
    List<Key> to = rows.get(r2).keys;
    int at = delta < 0 ? to.size() : 0;
    to.add(at, k);
    return new int[]{ r2, at };
  }

  public void add_row(int at)
  {
    Row row = new Row();
    row.keys.add(new Key());
    rows.add(Math.max(0, Math.min(at, rows.size())), row);
  }

  public void remove_row(int r)
  {
    rows.remove(r);
  }

  public static float clamp(float v, float lo, float hi)
  {
    return Math.max(lo, Math.min(hi, Math.round(v * 20f) / 20f)); // steps of 0.05
  }

  public void set_width(int r, int i, float w) { key(r, i).width = clamp(w, WIDTH_MIN, WIDTH_MAX); }
  public void set_shift(int r, int i, float s) { key(r, i).shift = clamp(s, 0f, WIDTH_MAX); }
  public void set_height(int r, float h) { rows.get(r).height = clamp(h, HEIGHT_MIN, HEIGHT_MAX); }

  /** The widest row: the keyboard's width in key units. */
  public float width()
  {
    float w = 0;
    for (Row r : rows) w = Math.max(w, r.width());
    return w;
  }

  // ---- layout XML (what the keyboard loads) ----

  public String xml()
  {
    StringBuilder b = new StringBuilder();
    b.append("<keyboard name=\"").append(esc(name)).append("\" bottom_row=\"false\" locale_extra_keys=\"false\">\n");
    for (Row r : rows)
    {
      b.append("  <row height=\"").append(num(r.height)).append("\">\n");
      for (Key k : r.keys)
      {
        b.append("    <key");
        if (k.width != 1f) b.append(" width=\"").append(num(k.width)).append('"');
        if (k.shift != 0f) b.append(" shift=\"").append(num(k.shift)).append('"');
        for (int i = 0; i < 9; i++)
          if (k.names[i] != null && !k.names[i].isEmpty())
            b.append(" key").append(i).append("=\"").append(esc(k.names[i])).append('"');
        b.append("/>\n");
      }
      b.append("  </row>\n");
    }
    b.append("</keyboard>\n");
    return b.toString();
  }

  static String num(float f)
  {
    return (f == Math.rint(f)) ? String.valueOf((int)f) : String.valueOf(Math.round(f * 100f) / 100f);
  }

  static String esc(String s)
  {
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray())
      switch (c)
      {
        case '&': b.append("&amp;"); break;
        case '<': b.append("&lt;"); break;
        case '>': b.append("&gt;"); break;
        case '"': b.append("&quot;"); break;
        case '\'': b.append("&apos;"); break;
        default: b.append(c);
      }
    return b.toString();
  }

  // ---- storage ([layout] module fields) ----

  public JSONObject json() throws JSONException
  {
    JSONArray rs = new JSONArray();
    for (Row r : rows)
    {
      JSONArray ks = new JSONArray();
      for (Key k : r.keys)
      {
        JSONObject o = new JSONObject();
        if (k.width != 1f) o.put("w", k.width);
        if (k.shift != 0f) o.put("gap", k.shift);
        JSONArray ns = new JSONArray();
        int last = -1;
        for (int i = 0; i < 9; i++) if (k.names[i] != null) last = i;
        for (int i = 0; i <= last; i++) ns.put(k.names[i] == null ? "" : k.names[i]);
        o.put("k", ns);
        ks.put(o);
      }
      rs.put(new JSONObject().put("h", r.height).put("keys", ks));
    }
    return new JSONObject().put("rows", rs);
  }

  public static LayoutDoc of_json(String name, JSONObject j) throws JSONException
  {
    LayoutDoc d = new LayoutDoc();
    d.name = name;
    JSONArray rs = j.getJSONArray("rows");
    for (int r = 0; r < rs.length(); r++)
    {
      JSONObject ro = rs.getJSONObject(r);
      Row row = new Row();
      row.height = (float)ro.optDouble("h", 1);
      JSONArray ks = ro.getJSONArray("keys");
      for (int i = 0; i < ks.length(); i++)
      {
        JSONObject ko = ks.getJSONObject(i);
        Key k = new Key();
        k.width = (float)ko.optDouble("w", 1);
        k.shift = (float)ko.optDouble("gap", 0);
        JSONArray ns = ko.optJSONArray("k");
        for (int n = 0; ns != null && n < Math.min(9, ns.length()); n++)
          k.names[n] = ns.getString(n).isEmpty() ? null : ns.getString(n);
        row.keys.add(k);
      }
      d.rows.add(row);
    }
    return d;
  }

  public LayoutDoc copy()
  {
    try { return of_json(name, json()); }
    catch (JSONException e) { throw new IllegalStateException(e); }
  }

  /** What a key shows in the editor: its tap's label. */
  public static String label(String name)
  {
    if (name == null || name.isEmpty())
      return "";
    KeyValue kv = KeyMap.key_of_name(name);
    if (kv == null)
      return name;
    String s = kv.getString();
    return (kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT) || s.isEmpty()) ? short_name(name) : s;
  }

  static String short_name(String n)
  {
    return n.length() <= 5 ? n : n.substring(0, 5);
  }
}
