package juloo.keyboard2.studio;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

/** il-007: settings saved under "comfort bar" names keep working. */
public class RenamesTest
{
  static final String OLD = "comfort" + "bar";

  @Test public void saved_settings_move_to_the_new_names() throws Exception
  {
    FakePrefs p = new FakePrefs();
    JSONObject all = new JSONObject()
      .put(OLD, new JSONObject().put("mine", new JSONObject()
          .put("module", OLD).put("name", "mine").put("version", 1).put("keys", new org.json.JSONArray())))
      .put("changeheight", new JSONObject().put("h", new JSONObject()
          .put("module", "changeheight").put("name", "h").put("version", 1)
          .put("comfort" + "_bar_dp", 20)
          .put("order", new org.json.JSONArray().put("comfort").put("selection").put("keyboard"))));
    p.edit().putString(ModuleStore.KEY_MODULES, all.toString())
      .putString(ModuleStore.active_key(OLD), "mine")
      .putString(Heights.KEY_LAST_GOOD, new JSONObject().put("h", new JSONObject()
          .put("module", "changeheight").put("name", "h").put("comfort" + "_bar_dp", 20)).toString())
      .apply();

    Renames.migrate(p);
    Renames.migrate(p); // twice is the same as once

    ModuleStore s = new ModuleStore(p);
    assertEquals("the quicksettings layout in use stays in use", "mine", s.active("quicksettings").name);
    assertNull(p.getString(ModuleStore.active_key(OLD), null));
    JSONObject h = s.active("changeheight") == null ? s.get("changeheight", "h").json : s.active("changeheight").json;
    assertEquals("its height is kept", 20, h.getInt("quicksettings_dp"));
    assertFalse(h.has("comfort" + "_bar_dp"));
    assertEquals("quicksettings", h.getJSONArray("order").getString(0));
    String stored = p.getString(ModuleStore.KEY_MODULES, "");
    assertFalse("nothing written under the old names: " + stored, stored.contains("comfort"));
    assertFalse(p.getString(Heights.KEY_LAST_GOOD, "").contains("comfort"));
  }

  /** A module file exported before the rename imports under the new name. */
  @Test public void old_module_files_still_import() throws Exception
  {
    Module m = Module.parse("{\"module\":\"" + OLD + "\",\"name\":\"old\",\"version\":1,\"keys\":[]}");
    assertEquals("quicksettings", m.type);
  }
}
