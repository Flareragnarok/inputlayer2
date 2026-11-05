package juloo.keyboard2.studio;

import android.content.SharedPreferences;
import java.util.Iterator;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Names the owner changed, for what is already saved under the old ones.
    The bar above the suggestions was the "comfort bar" until build 79,
    "quicksettings" in build 80 (il-007), and is the "quicksbar" since
    build 81; its buttons are "quicks" (owner, 2026-11-06). Saved modules,
    the active module per type and the heights' last good copies are
    rewritten once; a module document read later (an imported file) is
    renamed as it is read. The old names are only ever read, never written. */
public final class Renames
{
  /** Set once the saved settings use the newest names. A new rename gets a
      new key, so phones that ran an earlier one run this one too. */
  static final String DONE_KEY = "studio_renamed_quicksbar";

  static final String TYPE = "quicksbar", DP = "quicksbar_dp", CONTAINER = "quicksbar";
  /** Earlier names, oldest first. */
  static final String[] OLD_TYPES = { "comfort" + "bar", "quick" + "settings" };
  static final String[] OLD_DPS = { "comfort" + "_bar_dp", "quick" + "settings_dp" };
  static final String[] OLD_CONTAINERS = { "comfort", "quick" + "settings" };

  private Renames() {}

  static boolean is_in(String s, String[] l)
  {
    for (String x : l)
      if (x.equals(s))
        return true;
    return false;
  }

  /** [doc] (a module document) under the newest names. Returns whether it changed. */
  public static boolean doc(JSONObject doc) throws JSONException
  {
    boolean changed = false;
    if (is_in(doc.optString("module"), OLD_TYPES))
    {
      doc.put("module", TYPE);
      changed = true;
    }
    // The newest value wins: walk from the newest old name to the oldest.
    for (int i = OLD_DPS.length - 1; i >= 0; i--)
      if (doc.has(OLD_DPS[i]))
      {
        if (!doc.has(DP))
          doc.put(DP, doc.get(OLD_DPS[i]));
        doc.remove(OLD_DPS[i]);
        changed = true;
      }
    JSONArray order = doc.optJSONArray("order");
    if (order != null)
      for (int i = 0; i < order.length(); i++)
        if (is_in(order.optString(i), OLD_CONTAINERS))
        {
          order.put(i, CONTAINER);
          changed = true;
        }
    // quicks that cycle the bar's modules: "switch:<old type>"
    if (switch_actions(doc))
      changed = true;
    return changed;
  }

  /** Every string value "switch:<old type>" anywhere in [j] (quicksbar
      modules keep their quicks' actions in nested arrays and objects). */
  static boolean switch_actions(Object j) throws JSONException
  {
    boolean changed = false;
    if (j instanceof JSONObject)
    {
      JSONObject o = (JSONObject)j;
      for (Iterator<String> it = o.keys(); it.hasNext(); )
      {
        String k = it.next();
        Object v = o.get(k);
        String r = renamed_switch(v);
        if (r != null) { o.put(k, r); changed = true; }
        else if (switch_actions(v)) changed = true;
      }
    }
    else if (j instanceof JSONArray)
    {
      JSONArray a = (JSONArray)j;
      for (int i = 0; i < a.length(); i++)
      {
        Object v = a.get(i);
        String r = renamed_switch(v);
        if (r != null) { a.put(i, r); changed = true; }
        else if (switch_actions(v)) changed = true;
      }
    }
    return changed;
  }

  static String renamed_switch(Object v)
  {
    if (!(v instanceof String) || !((String)v).startsWith("switch:"))
      return null;
    String t = ((String)v).substring(7);
    return is_in(t, OLD_TYPES) ? "switch:" + TYPE : null;
  }

  /** The [studio_modules] object under the newest names (in place). */
  static boolean modules(JSONObject all) throws JSONException
  {
    boolean changed = false;
    for (int i = OLD_TYPES.length - 1; i >= 0; i--)
    {
      JSONObject old = all.optJSONObject(OLD_TYPES[i]);
      if (old == null)
        continue;
      JSONObject now = all.optJSONObject(TYPE);
      if (now == null)
      {
        now = new JSONObject();
        all.put(TYPE, now);
      }
      for (Iterator<String> it = old.keys(); it.hasNext(); )
      {
        String n = it.next();
        if (!now.has(n))
          now.put(n, old.get(n));
      }
      all.remove(OLD_TYPES[i]);
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
      // The newest active one wins.
      for (int i = OLD_TYPES.length - 1; i >= 0; i--)
      {
        String old_active = ModuleStore.active_key(OLD_TYPES[i]);
        String active = prefs.getString(old_active, null);
        if (active == null)
          continue;
        if (prefs.getString(ModuleStore.active_key(TYPE), null) == null)
        {
          e.putString(ModuleStore.active_key(TYPE), active);
          // older names must not override this one
          for (int j = i - 1; j >= 0; j--)
            e.remove(ModuleStore.active_key(OLD_TYPES[j]));
          e.remove(old_active);
          break;
        }
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
