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
  public static final int KEYBOARD_PERCENT_MIN = 10;
  public static final int KEYBOARD_PERCENT_MAX = 100;
  public static final float DEFAULT_COMFORT_BAR_DP = 26f;
  /** Upstream makes the suggestion bar a full key row high, too big. */
  public static final float DEFAULT_BAR_DP = 28f;

  /** Keys shown when no [comfortbar] module is active. */
  public static final List<ComfortKeySpec> DEFAULT_KEYS = Arrays.asList(
      ComfortKeySpec.of("c", "copy", "nw", "cut", "ne", "paste", "sw", "select_all"),
      ComfortKeySpec.of("c", "undo", "e", "redo"),
      ComfortKeySpec.of("w", "left", "e", "right", "n", "up", "s", "down"),
      ComfortKeySpec.of("c", "select_container", "n", "height_up", "s", "height_down"),
      ComfortKeySpec.of("c", "dictionary", "sw", "layout", "se", "keyboard_picker"));

  /** [-1] when not set. */
  public int keyboard_percent_portrait = -1;
  public int keyboard_percent_landscape = -1;
  public float bar_dp = DEFAULT_BAR_DP;
  /** [NaN] when not set. */
  public float margin_bottom_dp = Float.NaN;
  public float comfort_bar_dp = DEFAULT_COMFORT_BAR_DP;
  public static final float DEFAULT_EDIT_STRIP_DP = 28f;
  /** The edit strip under the space bar, [0] hides it. */
  public float edit_strip_dp = DEFAULT_EDIT_STRIP_DP;
  /** Height factor for each row, top to bottom. [null] when not set. */
  public float[] rows = null;
  public List<ComfortKeySpec> keys = DEFAULT_KEYS;

  public static StudioConfig load(SharedPreferences prefs)
  {
    ModuleStore store = new ModuleStore(prefs);
    StudioConfig c = new StudioConfig();
    Module h = store.active("changeheight");
    if (h != null)
      c.read_heights(h.json);
    Module b = store.active("comfortbar");
    if (b != null)
      c.read_comfortbar(b.json);
    return c;
  }

  void read_heights(JSONObject j)
  {
    JSONObject kp = j.optJSONObject("keyboard_percent");
    if (kp != null)
    {
      keyboard_percent_portrait = clamp_percent(kp.optInt("portrait", -1));
      keyboard_percent_landscape = clamp_percent(kp.optInt("landscape", -1));
    }
    float b = clamp_dp(j.optDouble("bar_dp", Double.NaN), 12, 96);
    if (!Float.isNaN(b))
      bar_dp = b;
    margin_bottom_dp = clamp_dp(j.optDouble("margin_bottom_dp", Double.NaN), 0, 64);
    // 0 hides the comfort bar.
    float cb = clamp_dp(j.optDouble("comfort_bar_dp", Double.NaN), 0, 96);
    if (!Float.isNaN(cb))
      comfort_bar_dp = (cb < 12) ? 0 : cb;
    float es = clamp_dp(j.optDouble("edit_strip_dp", Double.NaN), 0, 96);
    if (!Float.isNaN(es))
      edit_strip_dp = (es < 12) ? 0 : es;
    JSONArray rs = j.optJSONArray("rows");
    if (rs != null && rs.length() > 0)
    {
      rows = new float[rs.length()];
      for (int i = 0; i < rows.length; i++)
      {
        float f = (float)rs.optDouble(i, 1.0);
        rows[i] = Float.isNaN(f) ? 1f : Math.max(0.3f, Math.min(3f, f));
      }
    }
  }

  /** [keys]: one object per key, mapping "c" (tap) and the directions "n",
      "ne", "e", "se", "s", "sw", "w", "nw" (swipes) to actions. The older
      [applets] list of single actions is still read. */
  void read_comfortbar(JSONObject j)
  {
    List<ComfortKeySpec> l = new ArrayList<ComfortKeySpec>();
    JSONArray ks = j.optJSONArray("keys");
    JSONArray as = j.optJSONArray("applets");
    if (ks != null)
    {
      for (int i = 0; i < ks.length(); i++)
      {
        JSONObject k = ks.optJSONObject(i);
        ComfortKeySpec s = (k == null) ? null : ComfortKeySpec.of_json(k);
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
          l.add(ComfortKeySpec.of("c", "height_up", "s", "height_down"));
        else if (!a.isEmpty())
          l.add(ComfortKeySpec.of("c", a));
      }
    }
    else
      return;
    keys = l;
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

  static int clamp_percent(int p)
  {
    if (p < 0)
      return -1;
    return Math.max(KEYBOARD_PERCENT_MIN, Math.min(KEYBOARD_PERCENT_MAX, p));
  }

  static float clamp_dp(double v, float min, float max)
  {
    if (Double.isNaN(v))
      return Float.NaN;
    return (float)Math.max(min, Math.min(max, v));
  }
}
