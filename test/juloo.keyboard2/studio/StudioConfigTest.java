package juloo.keyboard2.studio;

import juloo.keyboard2.KeyValue;
import org.junit.Test;
import static org.junit.Assert.*;

public class StudioConfigTest
{
  public StudioConfigTest() {}

  static StudioConfig with(String type, String fields) throws Exception
  {
    FakePrefs p = new FakePrefs();
    ModuleStore s = new ModuleStore(p);
    s.save(Module.parse("{\"module\":\"" + type + "\",\"name\":\"t\"," + fields + "}"), true);
    return StudioConfig.load(p);
  }

  @Test
  public void defaults()
  {
    StudioConfig c = StudioConfig.load(new FakePrefs());
    assertEquals(35, c.keyboard_percent(false, 35));
    assertEquals(StudioConfig.DEFAULT_BAR_DP, c.bar_dp, 0f);
    assertTrue(Float.isNaN(c.margin_bottom_dp));
    assertEquals(StudioConfig.DEFAULT_QUICKSBAR_DP, c.quicksbar_dp, 0f);
    assertEquals(1f, c.row_factor(0), 0f);
    assertEquals(StudioConfig.DEFAULT_KEYS, c.keys);
  }

  @Test
  public void heights()
  throws Exception
  {
    StudioConfig c = with("changeheight",
        "\"bar_dp\":26,\"keyboard_percent\":{\"portrait\":30,\"landscape\":45},"
        + "\"rows\":[0.8,1.0,\"x\",9],\"margin_bottom_dp\":4,\"quicksbar_dp\":30");
    assertEquals(30, c.keyboard_percent(false, 35));
    assertEquals(45, c.keyboard_percent(true, 50));
    assertEquals(26f, c.bar_dp, 0f);
    assertEquals(4f, c.margin_bottom_dp, 0f);
    assertEquals(30f, c.quicksbar_dp, 0f);
    assertEquals(0.8f, c.row_factor(0), 0.001f);
    assertEquals(1f, c.row_factor(2), 0f); // Not a number
    assertEquals(Heights.ROW_MAX, c.row_factor(3), 0f); // Clamped
    assertEquals(1f, c.row_factor(10), 0f); // Past the list
  }

  @Test
  public void clamping_and_hiding()
  throws Exception
  {
    StudioConfig c = with("changeheight",
        "\"bar_dp\":2,\"keyboard_percent\":{\"portrait\":500},\"quicksbar_dp\":0");
    assertEquals(Heights.PORTRAIT_MAX, c.keyboard_percent(false, 35));
    assertEquals(50, c.keyboard_percent(true, 50)); // Not set
    assertEquals(12f, c.bar_dp, 0f);
    assertEquals(0f, c.quicksbar_dp, 0f);
  }

  @Test
  public void container_order()
  throws Exception
  {
    assertEquals(StudioConfig.DEFAULT_ORDER, StudioConfig.load(new FakePrefs()).order);
    StudioConfig c = with("changeheight", "\"order\":[\"keyboard\",\"quicksbar\",\"bogus\",\"keyboard\"]");
    assertEquals(java.util.Arrays.asList("keyboard", "quicksbar", "selection", "suggestions", "edit"), c.order);
  }

  @Test
  public void quicksbar_keys()
  throws Exception
  {
    StudioConfig c = with("quicksbar",
        "\"keys\":[{\"c\":\"copy\",\"sw\":\"cut\",\"x\":\"paste\"},{},{\"e\":\"redo\"}]");
    assertEquals(2, c.keys.size());
    assertEquals("copy", c.keys.get(0).get(0));
    assertEquals("cut", c.keys.get(0).get(QuickSpec.index_of("sw")));
    assertNull(c.keys.get(0).get(QuickSpec.index_of("ne")));
    assertNull(c.keys.get(1).get(0));
    assertEquals("redo", c.keys.get(1).get(QuickSpec.index_of("e")));
  }

  @Test
  public void quicksbar_applets_still_read()
  throws Exception
  {
    StudioConfig c = with("quicksbar", "\"applets\":[\"copy\",\"\",\"height\"]");
    assertEquals(2, c.keys.size());
    assertEquals("copy", c.keys.get(0).get(0));
    assertEquals("height_up", c.keys.get(1).get(0));
  }

  @Test
  public void applet_keys_exist()
  {
    String[] actions = { "left", "right", "up", "down", "select_all", "copy",
      "cut", "paste", "undo", "redo", "layout", "keyboard_picker", "clipboard",
      "app:com.termux" };
    for (String a : actions)
      assertNotNull(a, KeyValue.getKeyByName(Quicksbar.key_name(a)));
    for (QuickSpec s : StudioConfig.DEFAULT_KEYS)
      for (String a : s.actions)
        if (a != null && Quicksbar.key_name(a) != null)
          assertNotNull(a, KeyValue.getKeyByName(Quicksbar.key_name(a)));
    for (String name : new String[]{ "change_dictionary", "switch_forward", "change_method", "switch_clipboard" })
      assertNotNull(name, KeyValue.getKeyByName(name));
  }
}
