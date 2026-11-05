package juloo.keyboard2.studio;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class HeightsTest
{
  public HeightsTest() {}

  static final int ROW = Quicksettings.TARGET_ROWS;

  static JSONObject step(JSONObject j, int target, boolean bigger) throws Exception
  {
    return Heights.step(j, target, bigger, 4, false, 35);
  }

  /** Steps of [target] until nothing changes; fails if that never happens. */
  static JSONObject settle(JSONObject j, int target, boolean bigger, int max_steps)
    throws Exception
  {
    for (int i = 0; i < max_steps; i++)
    {
      JSONObject r = step(j, target, bigger);
      if (r == null)
        return j;
      j = r;
    }
    fail("Still changing after " + max_steps + " steps");
    return null;
  }

  @Test
  public void row_step() throws Exception
  {
    JSONObject r = step(new JSONObject(), ROW + 1, true);
    JSONArray rows = r.getJSONArray("rows");
    assertEquals(4, rows.length());
    assertEquals(1.0, rows.getDouble(0), 1e-6);
    assertEquals(1.1, rows.getDouble(1), 1e-6);
    assertEquals(1.0, rows.getDouble(2), 1e-6);
    // The other fields are kept, the input is not changed.
    JSONObject in = new JSONObject("{\"order\":[\"keyboard\"],\"rows\":[1,1,1,1]}");
    r = step(in, ROW + 1, false);
    assertEquals(0.9, r.getJSONArray("rows").getDouble(1), 1e-6);
    assertEquals("keyboard", r.getJSONArray("order").getString(0));
    assertEquals(1.0, in.getJSONArray("rows").getDouble(1), 1e-6);
  }

  @Test
  public void step_is_deterministic() throws Exception
  {
    JSONObject in = new JSONObject("{\"rows\":[1,1.3,1,1]}");
    assertEquals(step(in, ROW + 1, true).toString(), step(in, ROW + 1, true).toString());
  }

  /** The bug of build 61: holding + made a row grow forever. However many
      steps, a row settles at its maximum and further steps save nothing. */
  @Test
  public void rows_settle() throws Exception
  {
    JSONObject j = settle(new JSONObject(), ROW + 1, true, 50);
    assertEquals(Heights.ROW_MAX, j.getJSONArray("rows").getDouble(1), 1e-6);
    assertNull(step(j, ROW + 1, true));
    j = settle(j, ROW + 1, false, 50);
    assertEquals(Heights.ROW_MIN, j.getJSONArray("rows").getDouble(1), 1e-6);
    assertNull(step(j, ROW + 1, false));
  }

  @Test
  public void row_out_of_range_steps_from_the_clamp() throws Exception
  {
    // An old value past the range: one step down goes from the max.
    JSONObject r = step(new JSONObject("{\"rows\":[1,3,1,1]}"), ROW + 1, false);
    assertEquals(Heights.ROW_MAX - Heights.ROW_STEP,
        r.getJSONArray("rows").getDouble(1), 1e-6);
  }

  @Test
  public void keyboard_percent() throws Exception
  {
    JSONObject r = step(new JSONObject(), -1, true);
    assertEquals(37, r.getJSONObject("keyboard_percent").getInt("portrait"));
    r = Heights.step(new JSONObject(), Quicksettings.TARGET_KEYBOARD, false, 4, true, 50);
    assertEquals(48, r.getJSONObject("keyboard_percent").getInt("landscape"));
    JSONObject j = settle(new JSONObject(), -1, true, 50);
    assertEquals(Heights.PORTRAIT_MAX,
        j.getJSONObject("keyboard_percent").getInt("portrait"));
    j = settle(j, -1, false, 50);
    assertEquals(Heights.PORTRAIT_MIN,
        j.getJSONObject("keyboard_percent").getInt("portrait"));
  }

  @Test
  public void bars() throws Exception
  {
    JSONObject r = step(new JSONObject(), Quicksettings.TARGET_SUGGESTIONS, true);
    assertEquals(StudioConfig.DEFAULT_BAR_DP + 2, r.getDouble("bar_dp"), 1e-6);
    // A hidden bar comes back at its smallest.
    r = step(new JSONObject("{\"quicksettings_dp\":0}"), Quicksettings.TARGET_QUICKSETTINGS, true);
    assertEquals(Heights.BAR_MIN, r.getDouble("quicksettings_dp"), 1e-6);
    JSONObject j = settle(new JSONObject(), Quicksettings.TARGET_EDIT_STRIP, true, 50);
    assertEquals(Heights.BAR_MAX, j.getDouble("edit_strip_dp"), 1e-6);
  }

  /* Repair */

  /** What build 61 could leave: the asdf row and the keyboard at the old
      maximums. */
  static final String BROKEN =
    "{\"module\":\"changeheight\",\"name\":\"quick\",\"version\":1,"
    + "\"rows\":[1,3,1,1],\"keyboard_percent\":{\"portrait\":100,\"landscape\":45},"
    + "\"quicksettings_dp\":30,\"bar_dp\":96,\"order\":[\"keyboard\",\"quicksettings\"]}";

  @Test
  public void repair_to_defaults() throws Exception
  {
    JSONObject j = new JSONObject(BROKEN);
    List<String> fixed = Heights.repair(j, null);
    assertEquals(3, fixed.size());
    assertEquals(1.0, j.getJSONArray("rows").getDouble(1), 1e-6);
    assertFalse(j.getJSONObject("keyboard_percent").has("portrait"));
    assertFalse(j.has("bar_dp"));
    // The rest is kept.
    assertEquals(45, j.getJSONObject("keyboard_percent").getInt("landscape"));
    assertEquals(30, j.getInt("quicksettings_dp"));
    assertEquals("keyboard", j.getJSONArray("order").getString(0));
    assertEquals("quick", j.getString("name"));
    // Once repaired, nothing more to do.
    assertTrue(Heights.repair(j, null).isEmpty());
  }

  @Test
  public void repair_from_last_good() throws Exception
  {
    JSONObject good = new JSONObject(
        "{\"rows\":[1,1.4,1,1],\"keyboard_percent\":{\"portrait\":40},\"bar_dp\":200}");
    JSONObject j = new JSONObject(BROKEN);
    Heights.repair(j, good);
    assertEquals(1.4, j.getJSONArray("rows").getDouble(1), 1e-6);
    assertEquals(40, j.getJSONObject("keyboard_percent").getInt("portrait"));
    // The last good value is out of range too: the default.
    assertFalse(j.has("bar_dp"));
  }

  @Test
  public void repair_wrong_types() throws Exception
  {
    JSONObject j = new JSONObject(
        "{\"rows\":[\"x\",null,0.2],\"keyboard_percent\":3,\"edit_strip_dp\":0,\"margin_bottom_dp\":-1}");
    Heights.repair(j, null);
    assertEquals(1.0, j.getJSONArray("rows").getDouble(0), 1e-6);
    assertEquals(1.0, j.getJSONArray("rows").getDouble(1), 1e-6);
    assertEquals(1.0, j.getJSONArray("rows").getDouble(2), 1e-6);
    assertFalse(j.has("keyboard_percent"));
    assertEquals(0, j.getInt("edit_strip_dp")); // Hidden is fine.
    assertFalse(j.has("margin_bottom_dp"));
  }

  @Test
  public void sane_is_untouched() throws Exception
  {
    String s = "{\"rows\":[0.5,2,1.3],\"keyboard_percent\":{\"portrait\":60,\"landscape\":20},"
      + "\"bar_dp\":12,\"selection_bar_dp\":0,\"margin_bottom_dp\":0}";
    JSONObject j = new JSONObject(s);
    assertTrue(Heights.repair(j, null).isEmpty());
    assertEquals(new JSONObject(s).toString(), j.toString());
  }

  @Test
  public void check_repairs_stored_modules() throws Exception
  {
    FakePrefs p = new FakePrefs();
    ModuleStore s = new ModuleStore(p);
    s.save(Module.parse(BROKEN), true);
    s.save(Module.parse("{\"module\":\"changeheight\",\"name\":\"ok\",\"rows\":[1,1.2]}"));
    s.save(Module.parse("{\"module\":\"quicksettings\",\"name\":\"mine\",\"keys\":[{\"c\":\"copy\"}]}"), true);
    assertEquals(1, Heights.check(p));
    Module q = s.active("changeheight");
    assertEquals("quick", q.name); // Still active.
    assertEquals(1.0, q.json.getJSONArray("rows").getDouble(1), 1e-6);
    assertEquals("mine", s.active("quicksettings").name); // Other settings kept.
    StudioConfig c = StudioConfig.load(p);
    assertEquals(1f, c.row_factor(1), 0f);
    assertEquals(35, c.keyboard_percent(false, 35));
    // Nothing more to repair; both are now remembered as good.
    assertEquals(0, Heights.check(p));
    JSONObject good = new JSONObject(p.getString(Heights.KEY_LAST_GOOD, "{}"));
    assertTrue(good.has("quick"));
    assertEquals(1.2, good.getJSONObject("ok").getJSONArray("rows").getDouble(1), 1e-6);
  }

  @Test
  public void check_falls_back_to_last_good() throws Exception
  {
    FakePrefs p = new FakePrefs();
    ModuleStore s = new ModuleStore(p);
    s.save(Module.parse("{\"module\":\"changeheight\",\"name\":\"quick\","
          + "\"rows\":[1,1.5,1,1],\"keyboard_percent\":{\"portrait\":42}}"), true);
    Heights.check(p); // Remembered.
    s.save(Module.parse(BROKEN), true);
    assertEquals(1, Heights.check(p));
    Module q = s.active("changeheight");
    assertEquals(1.5, q.json.getJSONArray("rows").getDouble(1), 1e-6);
    assertEquals(42, q.json.getJSONObject("keyboard_percent").getInt("portrait"));
    assertEquals(30, q.json.getInt("quicksettings_dp"));
  }

  /* Undo (ModuleStore snapshots) */

  @Test
  public void snapshot_restore() throws Exception
  {
    FakePrefs p = new FakePrefs();
    ModuleStore s = new ModuleStore(p);
    s.save(Module.parse("{\"module\":\"quicksettings\",\"name\":\"mine\",\"keys\":[{\"c\":\"copy\"}]}"), true);
    ModuleStore.Snapshot none = s.snapshot("changeheight");
    s.save(Module.parse("{\"module\":\"changeheight\",\"name\":\"quick\",\"rows\":[1,1.1]}"), true);
    ModuleStore.Snapshot one = s.snapshot("changeheight");
    s.save(Module.parse("{\"module\":\"changeheight\",\"name\":\"quick\",\"rows\":[1,1.2]}"), true);
    s.restore(one);
    assertEquals(1.1, s.active("changeheight").json.getJSONArray("rows").getDouble(1), 1e-6);
    s.restore(none);
    assertNull(s.active("changeheight"));
    assertTrue(s.list("changeheight").isEmpty());
    assertEquals("mine", s.active("quicksettings").name);
  }
}
