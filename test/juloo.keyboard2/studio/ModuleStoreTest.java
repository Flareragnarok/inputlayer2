package juloo.keyboard2.studio;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleStoreTest
{
  public ModuleStoreTest() {}

  static Module mod(String type, String name, int v) throws Exception
  {
    return Module.parse("{\"module\":\"" + type + "\",\"name\":\"" + name
        + "\",\"v\":" + v + "}");
  }

  @Test
  public void save_list_replace()
  throws Exception
  {
    ModuleStore s = new ModuleStore(new FakePrefs());
    assertTrue(s.list("theme").isEmpty());
    s.save(mod("theme", "night", 1));
    s.save(mod("theme", "day", 1));
    s.save(mod("changeheight", "compact", 1));
    s.save(mod("theme", "night", 2));
    List<Module> ts = s.list("theme");
    assertEquals(2, ts.size());
    assertEquals("day", ts.get(0).name);
    assertEquals("night", ts.get(1).name);
    assertEquals(2, ts.get(1).json.getInt("v"));
    assertEquals(1, s.list("changeheight").size());
  }

  @Test
  public void active_and_delete()
  throws Exception
  {
    ModuleStore s = new ModuleStore(new FakePrefs());
    s.save(mod("theme", "night", 1));
    assertNull(s.active("theme"));
    s.set_active("theme", "night");
    assertEquals("night", s.active("theme").name);
    assertTrue(s.is_active(s.get("theme", "night")));
    s.delete("theme", "night");
    assertNull(s.active("theme"));
    assertNull(s.get("theme", "night"));
    // Re-adding a module with the same name doesn't make it active again.
    s.save(mod("theme", "night", 1));
    assertNull(s.active("theme"));
  }

  @Test
  public void corrupt_storage()
  {
    FakePrefs p = new FakePrefs();
    p.edit().putString(ModuleStore.KEY_MODULES, "garbage").apply();
    assertTrue(new ModuleStore(p).list("theme").isEmpty());
  }
}
