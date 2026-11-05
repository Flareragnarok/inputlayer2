package juloo.keyboard2.studio;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** What the active modules change in the keyboard, read with the rest of the
    config. Values that are not set keep upstream's behavior. */
public final class StudioConfig
{
  public static final float DEFAULT_QUICKSETTINGS_DP = 26f;
  /** Upstream makes the suggestion bar a full key row high, too big. */
  public static final float DEFAULT_BAR_DP = 28f;

  /** Keys shown when no [quicksettings] module is active, left to right
      (owner's names, build 73): layout key, suggestions key, cursor key
      (center), language key, edit key. */
  public static final List<QuicksettingsKeySpec> DEFAULT_KEYS = Arrays.asList(
      // Layout key: edit mode, ↑ copy, ↓ key repeat on/off, ↗ split
      // backspace on/off. Cut is backspace ↓, select all ⌦ ←.
      QuicksettingsKeySpec.of("c", "layout_mode", "n", "copy", "s", "toggle_repeat",
          "ne", "split_backspace"),
      // Suggestions key: the suggestions' language.
      QuicksettingsKeySpec.of("c", "dictionary"),
      // Cursor key.
      QuicksettingsKeySpec.of("w", "left", "e", "right", "n", "up", "s", "down"),
      // Language key: tap next language, ← previous, ↑ list; ↓ saves the key
      // arrangement as a layout, → picks a saved one.
      QuicksettingsKeySpec.of("c", "layout", "w", "layout_prev", "n", "layout_menu",
          "s", "layouts_save", "e", "layouts_pick"),
      // Edit key (right end): tap cycles the layer of focus (whole, bars,
      // rows, keys); tap an element to select it; ↑ ↓ size, ← → order,
      // ↗ more, ↖ undo (like Back), ↙ undo all since shown.
      QuicksettingsKeySpec.of("c", "select_container", "n", "height_up", "s", "height_down",
          "w", "move_up", "e", "move_down", "nw", "height_undo", "sw", "height_revert",
          "ne", "edit_menu"));

  /** [-1] when not set. */
  public int keyboard_percent_portrait = -1;
  public int keyboard_percent_landscape = -1;
  public float bar_dp = DEFAULT_BAR_DP;
  /** [NaN] when not set. */
  public float margin_bottom_dp = Float.NaN;
  public float quicksettings_dp = DEFAULT_QUICKSETTINGS_DP;
  public static final float DEFAULT_EDIT_STRIP_DP = 28f;
  public static final float DEFAULT_SELECTION_BAR_DP = 26f;
  /** The selection bar, [0] hides it. */
  public float selection_bar_dp = DEFAULT_SELECTION_BAR_DP;
  /** Containers top to bottom; the height key moves them. */
  public static final List<String> DEFAULT_ORDER =
    Arrays.asList("quicksettings", "selection", "suggestions", "keyboard", "edit");
  public List<String> order = DEFAULT_ORDER;
  /** The edit strip under the space bar, [0] hides it. */
  public float edit_strip_dp = DEFAULT_EDIT_STRIP_DP;
  /** Height factor for each row, top to bottom. [null] when not set. */
  public float[] rows = null;
  public List<QuicksettingsKeySpec> keys = DEFAULT_KEYS;
  /** Key changes made in layout mode, [null] when none. */
  public KeyMap keymap = null;
  public static final String KEY_SPLIT_BACKSPACE = "studio_split_backspace";
  /** A wide backspace is cut in two (the layout key's ↗). */
  public boolean split_backspace = true;

  public static StudioConfig load(SharedPreferences prefs)
  {
    ModuleStore store = new ModuleStore(prefs);
    StudioConfig c = new StudioConfig();
    Module h = store.active("changeheight");
    if (h != null)
      c.read_heights(h.json);
    c.split_backspace = prefs.getBoolean(KEY_SPLIT_BACKSPACE, true);
    Module km = store.active("keymap");
    if (km != null)
      c.keymap = KeyMap.of_json(km.json);
    Module b = store.active("quicksettings");
    if (b != null)
      c.read_quicksettings(b.json);
    return c;
  }

  void read_heights(JSONObject j)
  {
    JSONObject kp = j.optJSONObject("keyboard_percent");
    if (kp != null)
    {
      keyboard_percent_portrait = clamp_percent(false, kp.optInt("portrait", -1));
      keyboard_percent_landscape = clamp_percent(true, kp.optInt("landscape", -1));
    }
    // Ranges are in [Heights]; values past them are repaired on start.
    float b = clamp_dp(j.optDouble("bar_dp", Double.NaN), Heights.BAR_MIN, Heights.BAR_MAX);
    if (!Float.isNaN(b))
      bar_dp = b;
    margin_bottom_dp = clamp_dp(j.optDouble("margin_bottom_dp", Double.NaN),
        Heights.MARGIN_MIN, Heights.MARGIN_MAX);
    // 0 hides the quicksettings.
    float cb = clamp_dp(j.optDouble("quicksettings_dp", Double.NaN), 0, Heights.BAR_MAX);
    if (!Float.isNaN(cb))
      quicksettings_dp = (cb < 12) ? 0 : cb;
    float es = clamp_dp(j.optDouble("edit_strip_dp", Double.NaN), 0, Heights.BAR_MAX);
    if (!Float.isNaN(es))
      edit_strip_dp = (es < 12) ? 0 : es;
    float sb = clamp_dp(j.optDouble("selection_bar_dp", Double.NaN), 0, Heights.BAR_MAX);
    if (!Float.isNaN(sb))
      selection_bar_dp = (sb < 12) ? 0 : sb;
    JSONArray os = j.optJSONArray("order");
    if (os != null)
      order = read_order(os);
    JSONArray rs = j.optJSONArray("rows");
    if (rs != null && rs.length() > 0)
    {
      rows = Heights.row_factors(rs, rs.length());
    }
  }

  /** [keys]: one object per key, mapping "c" (tap) and the directions "n",
      "ne", "e", "se", "s", "sw", "w", "nw" (swipes) to actions. The older
      [applets] list of single actions is still read. */
  void read_quicksettings(JSONObject j)
  {
    List<QuicksettingsKeySpec> l = new ArrayList<QuicksettingsKeySpec>();
    JSONArray ks = j.optJSONArray("keys");
    JSONArray as = j.optJSONArray("applets");
    if (ks != null)
    {
      for (int i = 0; i < ks.length(); i++)
      {
        JSONObject k = ks.optJSONObject(i);
        QuicksettingsKeySpec s = (k == null) ? null : QuicksettingsKeySpec.of_json(k);
        if (s != null)
          l.add(s);
      }
    }
    else if (as != null)
    {
      for (int i = 0; i < as.length(); i++)
      {
        String a = as.optString(i, "");
        if (a.equals("height"))
          l.add(QuicksettingsKeySpec.of("c", "height_up", "s", "height_down"));
        else if (!a.isEmpty())
          l.add(QuicksettingsKeySpec.of("c", a));
      }
    }
    else
      return;
    keys = l;
  }

  /** The containers named in [a], in its order, then the missing ones in
      their default order. Unknown names are ignored. */
  static List<String> read_order(JSONArray a)
  {
    List<String> l = new ArrayList<String>();
    for (int i = 0; i < a.length(); i++)
    {
      String n = a.optString(i, "");
      if (DEFAULT_ORDER.contains(n) && !l.contains(n))
        l.add(n);
    }
    for (String n : DEFAULT_ORDER)
      if (!l.contains(n))
        l.add(n);
    return l;
  }

  /** [current] if the active module doesn't set it. */
  public int keyboard_percent(boolean landscape, int current)
  {
    int p = landscape ? keyboard_percent_landscape : keyboard_percent_portrait;
    return (p < 0) ? current : p;
  }

  /** Row height factor for row [i] of a layout with [n] rows. */
  public float row_factor(int i)
  {
    return (rows == null || i >= rows.length) ? 1f : rows[i];
  }

  static int clamp_percent(boolean landscape, int p)
  {
    return (p < 0) ? -1 : Heights.clamp_percent(landscape, p);
  }

  static float clamp_dp(double v, float min, float max)
  {
    if (Double.isNaN(v))
      return Float.NaN;
    return (float)Math.max(min, Math.min(max, v));
  }
}
