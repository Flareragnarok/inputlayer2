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
    assertTrue(Float.isNaN(c.bar_dp));
    assertTrue(Float.isNaN(c.margin_bottom_dp));
    assertEquals(StudioConfig.DEFAULT_COMFORT_BAR_DP, c.comfort_bar_dp, 0f);
    assertEquals(1f, c.row_factor(0), 0f);
    assertEquals(StudioConfig.DEFAULT_APPLETS, c.applets);
  }

  @Test
  public void heights()
  throws Exception
  {
    StudioConfig c = with("changeheight",
        "\"bar_dp\":26,\"keyboard_percent\":{\"portrait\":30,\"landscape\":45},"
        + "\"rows\":[0.8,1.0,\"x\",9],\"margin_bottom_dp\":4,\"comfort_bar_dp\":30");
    assertEquals(30, c.keyboard_percent(false, 35));
    assertEquals(45, c.keyboard_percent(true, 50));
    assertEquals(26f, c.bar_dp, 0f);
    assertEquals(4f, c.margin_bottom_dp, 0f);
    assertEquals(30f, c.comfort_bar_dp, 0f);
    assertEquals(0.8f, c.row_factor(0), 0.001f);
    assertEquals(1f, c.row_factor(2), 0f); // Not a number
    assertEquals(3f, c.row_factor(3), 0f); // Clamped
    assertEquals(1f, c.row_factor(10), 0f); // Past the list
  }

  @Test
  public void clamping_and_hiding()
  throws Exception
  {
    StudioConfig c = with("changeheight",
        "\"bar_dp\":2,\"keyboard_percent\":{\"portrait\":500},\"comfort_bar_dp\":0");
    assertEquals(StudioConfig.KEYBOARD_PERCENT_MAX, c.keyboard_percent(false, 35));
    assertEquals(50, c.keyboard_percent(true, 50)); // Not set
    assertEquals(12f, c.bar_dp, 0f);
    assertEquals(0f, c.comfort_bar_dp, 0f);
  }

  @Test
  public void comfortbar()
  throws Exception
  {
    StudioConfig c = with("comfortbar", "\"applets\":[\"copy\",\"\",\"paste\"]");
    assertEquals(java.util.Arrays.asList("copy", "paste"), c.applets);
  }

  @Test
  public void applet_keys_exist()
  {
    for (String a : StudioConfig.DEFAULT_APPLETS)
    {
      String[] k = ComfortBar.editing_applet(a);
      if (k != null)
        assertNotNull(a, KeyValue.getKeyByName(k[1]));
    }
    for (String name : new String[]{ "switch_forward", "change_method", "switch_clipboard" })
      assertNotNull(name, KeyValue.getKeyByName(name));
  }
}
