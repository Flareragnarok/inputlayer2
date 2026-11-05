package juloo.keyboard2.studio;

import static org.junit.Assert.*;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

/** il-007: settings saved under the bar's earlier names ("comfort bar" up
    to build 79, "quicksettings" in build 80) keep working as "quicksbar". */
public class RenamesTest
{
  static final String COMFORT = "comfort" + "bar", QS = "quick" + "settings";

  /** Settings as a build using [type], [dp] and [container] saved them. */
  static FakePrefs saved(String type, String dp, String container) throws Exception
  {
    FakePrefs p = new FakePrefs();
    JSONObject all = new JSONObject()
      .put(type, new JSONObject().put("mine", new JSONObject()
          .put("module", type).put("name", "mine").put("version", 1).put("keys", new JSONArray())))
      .put("changeheight", new JSONObject().put("h", new JSONObject()
          .put("module", "changeheight").put("name", "h").put("version", 1).put(dp, 20)
          .put("order", new JSONArray().put(container).put("selection").put("keyboard"))));
    p.edit().putString(ModuleStore.KEY_MODULES, all.toString())
      .putString(ModuleStore.active_key(type), "mine")
      .putString(Heights.KEY_LAST_GOOD, new JSONObject().put("h", new JSONObject()
          .put("module", "changeheight").put("name", "h").put(dp, 20)).toString())
      .apply();
    return p;
  }

  static void check(FakePrefs p) throws Exception
  {
    Renames.migrate(p);
    Renames.migrate(p); // twice is the same as once
    ModuleStore s = new ModuleStore(p);
    assertEquals("the quicksbar layout in use stays in use", "mine", s.active("quicksbar").name);
    JSONObject h = s.get("changeheight", "h").json;
    assertEquals("its height is kept", 20, h.getInt("quicksbar_dp"));
    assertEquals("quicksbar", h.getJSONArray("order").getString(0));
    String stored = p.getString(ModuleStore.KEY_MODULES, "") + p.getString(Heights.KEY_LAST_GOOD, "");
    assertFalse("nothing under the old names: " + stored, stored.contains("comfort") || stored.contains(QS));
    assertNull(p.getString(ModuleStore.active_key(COMFORT), null));
    assertNull(p.getString(ModuleStore.active_key(QS), null));
  }

  @Test public void from_comfort_bar_names() throws Exception
  {
    check(saved(COMFORT, "comfort" + "_bar_dp", "comfort"));
  }

  /** A phone that ran build 80's rename. */
  @Test public void from_build_80_names() throws Exception
  {
    FakePrefs p = saved(QS, QS + "_dp", QS);
    p.edit().putBoolean("studio_renamed_" + QS, true).apply();
    check(p);
  }

  /** Quicks that cycle the bar's modules named the old type (review 2026-11-06). */
  @Test public void switch_actions_follow_the_type() throws Exception
  {
    JSONObject doc = new JSONObject("{\"module\":\"" + COMFORT + "\",\"name\":\"m\",\"keys\":[{\"c\":\"switch:" + COMFORT
        + "\",\"n\":\"copy\"}],\"applets\":[\"switch:" + QS + "\"]}");
    assertTrue(Renames.doc(doc));
    assertEquals("switch:quicksbar", doc.getJSONArray("keys").getJSONObject(0).getString("c"));
    assertEquals("copy", doc.getJSONArray("keys").getJSONObject(0).getString("n"));
    assertEquals("switch:quicksbar", doc.getJSONArray("applets").getString(0));
  }

  @Test public void old_module_files_still_import() throws Exception
  {
    for (String t : new String[]{ COMFORT, QS })
      assertEquals("quicksbar",
          Module.parse("{\"module\":\"" + t + "\",\"name\":\"old\",\"version\":1,\"keys\":[]}").type);
  }
}
