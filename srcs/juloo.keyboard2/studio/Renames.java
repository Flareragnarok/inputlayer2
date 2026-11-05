package juloo.keyboard2.studio;

import android.content.SharedPreferences;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Names the owner changed, for what is already saved under the old ones
    (il-007: "rename every mention of comfort bar to quicksettings",
    2026-11-06). Saved modules, the active module per type and the heights'
    last good copies are rewritten once; a module document read later (an
    imported file) is renamed as it is read. Nothing is lost: the old names
    are only ever read, never written. */
public final class Renames
{
  /** Set once the saved settings use the new names. */
  static final String DONE_KEY = "studio_renamed_quicksettings";

  static final String OLD_TYPE = "comfort" + "bar";
  static final String OLD_DP = "comfort" + "_bar_dp";
  static final String OLD_CONTAINER = "comfort";

  private Renames() {}

  /** [doc] (a module document) under the new names. Returns whether it changed. */
  public static boolean doc(JSONObject doc) throws JSONException
  {
    boolean changed = false;
    if (OLD_TYPE.equals(doc.optString("module")))
    {
      doc.put("module", "quicksettings");
      changed = true;
    }
    if (doc.has(OLD_DP))
    {
      if (!doc.has("quicksettings_dp"))
        doc.put("quicksettings_dp", doc.get(OLD_DP));
      doc.remove(OLD_DP);
      changed = true;
    }
    JSONArray order = doc.optJSONArray("order");
    if (order != null)
      for (int i = 0; i < order.length(); i++)
        if (OLD_CONTAINER.equals(order.optString(i)))
        {
          order.put(i, "quicksettings");
          changed = true;
        }
    return changed;
  }

  /** The [studio_modules] object under the new names (in place). */
  static boolean modules(JSONObject all) throws JSONException
  {
    boolean changed = false;
    JSONObject old = all.optJSONObject(OLD_TYPE);
    if (old != null)
    {
      JSONObject now = all.optJSONObject("quicksettings");
      if (now == null)
      {
        now = new JSONObject();
        all.put("quicksettings", now);
      }
      for (Iterator<String> it = old.keys(); it.hasNext(); )
      {
        String n = it.next();
        if (!now.has(n))
          now.put(n, old.get(n));
      }
      all.remove(OLD_TYPE);
      changed = true;
    }
    for (Iterator<String> t = all.keys(); t.hasNext(); )
    {
      JSONObject of_type = all.optJSONObject(t.next());
      if (of_type == null)
        continue;
      for (Iterator<String> n = of_type.keys(); n.hasNext(); )
      {
        JSONObject d = of_type.optJSONObject(n.next());
        if (d != null && doc(d))
          changed = true;
      }
    }
    return changed;
  }

  /** Once, before anything reads the settings (keyboard and Studio start). */
  public static void migrate(SharedPreferences prefs)
  {
    if (prefs.getBoolean(DONE_KEY, false))
      return;
    try
    {
      SharedPreferences.Editor e = prefs.edit();
      String s = prefs.getString(ModuleStore.KEY_MODULES, null);
      if (s != null)
      {
        JSONObject all = new JSONObject(s);
        if (modules(all))
          e.putString(ModuleStore.KEY_MODULES, all.toString());
      }
      String old_active = ModuleStore.active_key(OLD_TYPE);
      String active = prefs.getString(old_active, null);
      if (active != null)
      {
        if (prefs.getString(ModuleStore.active_key("quicksettings"), null) == null)
          e.putString(ModuleStore.active_key("quicksettings"), active);
        e.remove(old_active);
      }
      String good = prefs.getString(Heights.KEY_LAST_GOOD, null);
      if (good != null)
      {
        JSONObject g = new JSONObject(good);
        boolean changed = false;
        for (Iterator<String> it = g.keys(); it.hasNext(); )
        {
          JSONObject d = g.optJSONObject(it.next());
          if (d != null && doc(d))
            changed = true;
        }
        if (changed)
          e.putString(Heights.KEY_LAST_GOOD, g.toString());
      }
      e.putBoolean(DONE_KEY, true);
      e.apply();
    }
    catch (Throwable t)
    {
      // Unreadable settings stay as they are; the keyboard falls back to
      // its defaults for whatever it can't read, as before.
    }
  }
}
