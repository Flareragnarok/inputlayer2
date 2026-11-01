package juloo.keyboard2.studio;

import java.util.Iterator;
import java.util.List;
import org.json.JSONObject;

/** Saved layouts: key arrangements made in edit mode, kept as [keymap]
    modules. The language key saves the current one (↓) and picks between
    them (→). The active [keymap] module is the one edit mode changes, so
    saving copies it under a new name and goes on with the copy: the saved
    one stays as it was. */
public final class SavedLayouts
{
  static final String PREFIX = "Layout ";

  private SavedLayouts() {}

  /** "Layout N", N one more than the highest saved. */
  static String next_name(List<Module> saved)
  {
    int max = 0;
    for (Module m : saved)
      if (m.name.startsWith(PREFIX))
      {
        try { max = Math.max(max, Integer.parseInt(m.name.substring(PREFIX.length()))); }
        catch (NumberFormatException _e) {}
      }
    return PREFIX + (max + 1);
  }

  /** Save the current arrangement under a new name, which becomes the one
      in use. Returns that name. */
  public static String save_current(ModuleStore store) throws Exception
  {
    Module cur = store.active("keymap");
    String name = next_name(store.list("keymap"));
    JSONObject fields = new JSONObject();
    if (cur != null)
    {
      Iterator<String> it = cur.json.keys();
      while (it.hasNext())
      {
        String k = it.next();
        if (!k.equals("module") && !k.equals("name") && !k.equals("version"))
          fields.put(k, cur.json.get(k));
      }
    }
    store.save(Module.create("keymap", name, new JSONObject(fields.toString())), true);
    return name;
  }

  public static List<Module> list(ModuleStore store)
  {
    return store.list("keymap");
  }

  /** Use saved layout [name], or the layouts' own arrangement if [null]. */
  public static void pick(ModuleStore store, String name)
  {
    store.set_active("keymap", name);
  }
}
