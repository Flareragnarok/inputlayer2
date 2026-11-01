package juloo.keyboard2.studio;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class SavedLayoutsTest
{
  public SavedLayoutsTest() {}

  @Test
  public void save_and_pick() throws Exception
  {
    FakePrefs p = new FakePrefs();
    ModuleStore s = new ModuleStore(p);
    // Edits so far, in the module edit mode made.
    s.save(Module.create("keymap", "mine",
          KeyMap.set(new JSONObject(), "IL", "enter", 5, "config")), true);
    assertEquals("Layout 1", SavedLayouts.save_current(s));
    assertEquals("Layout 1", s.active("keymap").name);
    // Edit mode now changes the copy; the original stays.
    Module l1 = s.active("keymap");
    s.save(Module.create("keymap", "Layout 1",
          KeyMap.set(l1.json, "IL", "q", 2, "")), true);
    assertTrue(KeyMap.of_json(s.get("keymap", "mine").json).is_changed("IL", "enter", 5));
    assertFalse(KeyMap.of_json(s.get("keymap", "mine").json).is_changed("IL", "q", 2));
    assertTrue(KeyMap.of_json(s.active("keymap").json).is_changed("IL", "q", 2));
    assertEquals("Layout 2", SavedLayouts.save_current(s));
    assertEquals(3, SavedLayouts.list(s).size());
    SavedLayouts.pick(s, "mine");
    assertEquals("mine", s.active("keymap").name);
    SavedLayouts.pick(s, null);
    assertNull(s.active("keymap"));
    // Nothing changed yet: an empty arrangement can be saved too.
    assertEquals("Layout 3", SavedLayouts.save_current(s));
    assertTrue(KeyMap.of_json(s.active("keymap").json).is_empty());
  }
}
