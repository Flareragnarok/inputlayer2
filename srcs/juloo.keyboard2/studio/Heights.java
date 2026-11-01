package juloo.keyboard2.studio;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.Logs;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** The height math of [changeheight] modules, in one place: the sane range
    of every value, one step of the height key, and the repair of stored
    values that left their range.

    A step only reads the module itself (never a view's measured height), so
    the same step from the same module always gives the same result, and a
    step at the edge of a range changes nothing. */
public final class Heights
{
  /** Height factor of a keyboard row. */
  public static final float ROW_MIN = 0.5f;
  public static final float ROW_MAX = 2f;
  public static final float ROW_STEP = 0.1f;
  /** Keyboard height, in % of upstream's base height. */
  public static final int PORTRAIT_MIN = 10;
  public static final int PORTRAIT_MAX = 60;
  public static final int LANDSCAPE_MIN = 20;
  public static final int LANDSCAPE_MAX = 65;
  public static final int PERCENT_STEP = 2;
  /** Bars, in dp. The comfort bar, edit strip and selection bar can also be
      [0], hidden. */
  public static final float BAR_MIN = 12f;
  public static final float BAR_MAX = 64f;
  public static final float BAR_STEP = 2f;
  public static final float MARGIN_MIN = 0f;
  public static final float MARGIN_MAX = 48f;

  /** Copies of the modules as they were last seen sane: module name ->
      module document. */
  public static final String KEY_LAST_GOOD = "studio_heights_last_good";

  /** Fields in dp: name, default, whether [0] (hidden) is allowed. */
  static final String[] DP_FIELDS =
    { "bar_dp", "comfort_bar_dp", "edit_strip_dp", "selection_bar_dp" };
  static final float[] DP_DEFAULTS = {
    StudioConfig.DEFAULT_BAR_DP, StudioConfig.DEFAULT_COMFORT_BAR_DP,
    StudioConfig.DEFAULT_EDIT_STRIP_DP, StudioConfig.DEFAULT_SELECTION_BAR_DP };
  static final boolean[] DP_HIDEABLE = { false, true, true, true };

  private Heights() {}

  public static float clamp(float v, float min, float max)
  {
    return Math.max(min, Math.min(max, v));
  }

  public static int clamp_percent(boolean landscape, int p)
  {
    return landscape ? Math.max(LANDSCAPE_MIN, Math.min(LANDSCAPE_MAX, p))
      : Math.max(PORTRAIT_MIN, Math.min(PORTRAIT_MAX, p));
  }

  static float round2(float f)
  {
    return Math.round(f * 100) / 100f;
  }

  /* Steps */

  /** Fields of [j] (a copy) after one step of the height key on [target]
      ([ComfortBar.TARGET_*], or [-1] for the keyboard), bigger if [bigger].
      [rows] is the number of rows shown, [percent] the keyboard height used
      when [j] doesn't set it. [null] when the step changes nothing, at the
      edge of the range. */
  public static JSONObject step(JSONObject j, int target, boolean bigger,
      int rows, boolean landscape, int percent) throws JSONException
  {
    JSONObject r = new JSONObject(j.toString());
    int sign = bigger ? 1 : -1;
    switch (target)
    {
      case ComfortBar.TARGET_COMFORT_BAR: return step_dp(r, 1, sign);
      case ComfortBar.TARGET_SUGGESTIONS: return step_dp(r, 0, sign);
      case ComfortBar.TARGET_EDIT_STRIP: return step_dp(r, 2, sign);
      case ComfortBar.TARGET_SELECTION_BAR: return step_dp(r, 3, sign);
      case -1: case ComfortBar.TARGET_KEYBOARD:
      {
        String o = landscape ? "landscape" : "portrait";
        JSONObject kp = r.optJSONObject("keyboard_percent");
        if (kp == null)
          kp = new JSONObject();
        int cur = clamp_percent(landscape, kp.optInt(o, percent));
        int p = clamp_percent(landscape, cur + sign * PERCENT_STEP);
        if (p == cur && kp.has(o))
          return null;
        kp.put(o, p);
        r.put("keyboard_percent", kp);
        return r;
      }
      default:
      {
        int row = target - ComfortBar.TARGET_ROWS;
        if (row < 0)
          return null;
        float[] fs = row_factors(r.optJSONArray("rows"), Math.max(row + 1, rows));
        float f = round2(clamp(fs[row] + sign * ROW_STEP, ROW_MIN, ROW_MAX));
        if (f == fs[row])
          return null;
        fs[row] = f;
        JSONArray a = new JSONArray();
        for (float x : fs)
          a.put((double)x);
        r.put("rows", a);
        return r;
      }
    }
  }

  static JSONObject step_dp(JSONObject r, int field, int sign) throws JSONException
  {
    String n = DP_FIELDS[field];
    double v = r.optDouble(n, DP_DEFAULTS[field]);
    float cur = Double.isNaN(v) ? DP_DEFAULTS[field] : (float)v;
    // A hidden bar comes back at its smallest size.
    float d = clamp(cur + sign * BAR_STEP, BAR_MIN, BAR_MAX);
    if (d == cur)
      return null;
    r.put(n, (double)d);
    return r;
  }

  /** [n] row factors from [a], each in range ([1] where unset or wrong). */
  public static float[] row_factors(JSONArray a, int n)
  {
    float[] fs = new float[n];
    for (int i = 0; i < n; i++)
    {
      double v = (a == null) ? Double.NaN : a.optDouble(i, Double.NaN);
      fs[i] = Double.isNaN(v) ? 1f : round2(clamp((float)v, ROW_MIN, ROW_MAX));
    }
    return fs;
  }

  /* Repair */

  /** Put the values of [j] that are out of their range back to their value
      in [good] if that one is in range, else remove them (or [1] for a row)
      so that the default applies. Other fields are kept. Returns the names
      of the fields changed, empty when [j] was sane. */
  public static List<String> repair(JSONObject j, JSONObject good)
  {
    List<String> changed = new ArrayList<String>();
    try
    {
      for (int i = 0; i < DP_FIELDS.length; i++)
      {
        boolean h = DP_HIDEABLE[i];
        if (repair_number(j, j, good, DP_FIELDS[i], BAR_MIN, BAR_MAX, h))
          changed.add(DP_FIELDS[i]);
      }
      if (repair_number(j, j, good, "margin_bottom_dp", MARGIN_MIN, MARGIN_MAX, false))
        changed.add("margin_bottom_dp");
      if (j.has("keyboard_percent"))
      {
        JSONObject kp = j.optJSONObject("keyboard_percent");
        JSONObject gkp = (good == null) ? null : good.optJSONObject("keyboard_percent");
        if (kp == null)
        {
          j.remove("keyboard_percent");
          changed.add("keyboard_percent");
        }
        else
        {
          if (repair_number(j, kp, gkp, "portrait", PORTRAIT_MIN, PORTRAIT_MAX, false))
            changed.add("keyboard_percent.portrait");
          if (repair_number(j, kp, gkp, "landscape", LANDSCAPE_MIN, LANDSCAPE_MAX, false))
            changed.add("keyboard_percent.landscape");
        }
      }
      if (j.has("rows"))
      {
        JSONArray rs = j.optJSONArray("rows");
        JSONArray grs = (good == null) ? null : good.optJSONArray("rows");
        if (rs == null)
        {
          j.remove("rows");
          changed.add("rows");
        }
        else
          for (int i = 0; i < rs.length(); i++)
          {
            double v = rs.optDouble(i, Double.NaN);
            if (in_range(v, ROW_MIN, ROW_MAX, false))
              continue;
            double g = (grs == null) ? Double.NaN : grs.optDouble(i, Double.NaN);
            rs.put(i, in_range(g, ROW_MIN, ROW_MAX, false) ? g : 1.0);
            changed.add("rows[" + i + "]");
          }
      }
    }
    catch (JSONException _e) {}
    return changed;
  }

  static boolean in_range(double v, float min, float max, boolean zero_ok)
  {
    if (Double.isNaN(v) || Double.isInfinite(v))
      return false;
    return (zero_ok && v == 0) || (v >= min && v <= max);
  }

  /** Repair field [n] of [o] (part of the module [j]), see [repair]. */
  static boolean repair_number(JSONObject j, JSONObject o, JSONObject good,
      String n, float min, float max, boolean zero_ok) throws JSONException
  {
    if (!o.has(n))
      return false;
    if (in_range(o.optDouble(n, Double.NaN), min, max, zero_ok))
      return false;
    double g = (good == null || !good.has(n)) ? Double.NaN : good.optDouble(n, Double.NaN);
    if (in_range(g, min, max, zero_ok))
      o.put(n, g);
    else
      o.remove(n);
    return true;
  }

  /** Check every saved [changeheight] module: repair the ones out of range
      (from their last sane copy, or the defaults) and remember the sane
      ones. Only height values change; everything else is kept. Returns the
      number of modules repaired. Run when the keyboard starts, when Studio
      opens and when the keyboard is hidden. */
  public static int check(SharedPreferences prefs)
  {
    ModuleStore store = new ModuleStore(prefs);
    JSONObject last_good;
    try { last_good = new JSONObject(prefs.getString(KEY_LAST_GOOD, "{}")); }
    catch (JSONException _e) { last_good = new JSONObject(); }
    int repaired = 0;
    boolean good_changed = false;
    for (Module m : store.list("changeheight"))
    {
      try
      {
        JSONObject j = new JSONObject(m.json.toString());
        List<String> fixed = repair(j, last_good.optJSONObject(m.name));
        if (fixed.isEmpty())
        {
          if (!m.json.toString().equals(String.valueOf(last_good.optJSONObject(m.name))))
          {
            last_good.put(m.name, m.json);
            good_changed = true;
          }
          continue;
        }
        Logs.debug("Heights: repaired module \"" + m.name + "\": " + fixed);
        store.save(new Module(j));
        repaired++;
      }
      catch (Exception e)
      {
        Logs.exn("Heights: check", e);
      }
    }
    if (good_changed)
      prefs.edit().putString(KEY_LAST_GOOD, last_good.toString()).apply();
    return repaired;
  }
}
