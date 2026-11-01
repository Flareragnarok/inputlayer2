package juloo.keyboard2.studio;

import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.json.JSONException;
import org.json.JSONObject;

/** Saved modules, kept in the keyboard's preferences like its other settings
    so that the keyboard can read the active ones in direct-boot mode and is
    notified when they change. */
public final class ModuleStore
{
  /** JSON object: type -> name -> module document. */
  static final String KEY_MODULES = "studio_modules";

  static String active_key(String type) { return "studio_active_" + type; }

  final SharedPreferences _prefs;

  public ModuleStore(SharedPreferences prefs)
  {
    _prefs = prefs;
  }

  /** Modules of type [type], sorted by name. Unreadable entries are skipped. */
  public List<Module> list(String type)
  {
    List<Module> ms = new ArrayList<Module>();
    JSONObject of_type = load().optJSONObject(type);
    if (of_type == null)
      return ms;
    Iterator<String> it = of_type.keys();
    while (it.hasNext())
    {
      JSONObject j = of_type.optJSONObject(it.next());
      if (j == null)
        continue;
      try { ms.add(new Module(j)); }
      catch (Module.InvalidModule _e) {}
    }
    Collections.sort(ms, new Comparator<Module>() {
      public int compare(Module a, Module b) { return a.name.compareTo(b.name); }
    });
    return ms;
  }

  public Module get(String type, String name)
  {
    for (Module m : list(type))
      if (m.name.equals(name))
        return m;
    return null;
  }

  /** The module of type [type] in use, or [null]. */
  public Module active(String type)
  {
    String name = _prefs.getString(active_key(type), null);
    return (name == null) ? null : get(type, name);
  }

  public boolean is_active(Module m)
  {
    return m.name.equals(_prefs.getString(active_key(m.type), null));
  }

  /** Add [m], replacing a module of the same type and name. */
  public void save(Module m)
  {
    save(m, false);
  }

  /** Like [save], and make [m] the active module of its type if [activate],
      in a single preference change. */
  public void save(Module m, boolean activate)
  {
    JSONObject all = load();
    try
    {
      JSONObject of_type = all.optJSONObject(m.type);
      if (of_type == null)
      {
        of_type = new JSONObject();
        all.put(m.type, of_type);
      }
      of_type.put(m.name, m.json);
    }
    catch (JSONException _e) { return; }
    SharedPreferences.Editor e = _prefs.edit();
    e.putString(KEY_MODULES, all.toString());
    if (activate)
      e.putString(active_key(m.type), m.name);
    e.apply();
  }

  public void delete(String type, String name)
  {
    JSONObject all = load();
    JSONObject of_type = all.optJSONObject(type);
    if (of_type != null)
      of_type.remove(name);
    SharedPreferences.Editor e = _prefs.edit();
    e.putString(KEY_MODULES, all.toString());
    if (name.equals(_prefs.getString(active_key(type), null)))
      e.remove(active_key(type));
    e.apply();
  }

  /** Use module [name] for its type, or none if [name] is [null]. */
  public void set_active(String type, String name)
  {
    SharedPreferences.Editor e = _prefs.edit();
    if (name == null)
      e.remove(active_key(type));
    else
      e.putString(active_key(type), name);
    e.apply();
  }

  /** The saved modules of one type and which one is active, to be put back
      with [restore]. */
  public static final class Snapshot
  {
    final String type;
    /** [null] when there were none. */
    final String modules;
    final String active;

    Snapshot(String t, String m, String a) { type = t; modules = m; active = a; }
  }

  public Snapshot snapshot(String type)
  {
    JSONObject of_type = load().optJSONObject(type);
    return new Snapshot(type, (of_type == null) ? null : of_type.toString(),
        _prefs.getString(active_key(type), null));
  }

  /** Put the modules of [s]'s type back as they were, in a single
      preference change. The other types are not touched. */
  public void restore(Snapshot s)
  {
    JSONObject all = load();
    try
    {
      if (s.modules == null)
        all.remove(s.type);
      else
        all.put(s.type, new JSONObject(s.modules));
    }
    catch (JSONException _e) { return; }
    SharedPreferences.Editor e = _prefs.edit();
    e.putString(KEY_MODULES, all.toString());
    if (s.active == null)
      e.remove(active_key(s.type));
    else
      e.putString(active_key(s.type), s.active);
    e.apply();
  }

  JSONObject load()
  {
    try
    {
      return new JSONObject(_prefs.getString(KEY_MODULES, "{}"));
    }
    catch (JSONException _e)
    {
      return new JSONObject();
    }
  }
}
