package juloo.keyboard2.studio;

import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ModuleTest
{
  public ModuleTest() {}

  @Test
  public void parse()
  throws Exception
  {
    Module m = Module.parse("{\"module\":\"changeheight\",\"name\":\"compact\",\"version\":1,\"bar_dp\":26}");
    assertEquals("changeheight", m.type);
    assertEquals("compact", m.name);
    assertEquals(1, m.version);
    assertEquals(26, m.json.getInt("bar_dp"));
    assertEquals("changeheight-compact.json", m.file_name());
    // Round trip.
    assertEquals(26, Module.parse(m.to_json()).json.getInt("bar_dp"));
  }

  @Test
  public void refused()
  {
    String[] bad = {
      "not json",
      "[1,2]",
      "{\"name\":\"x\"}",
      "{\"module\":\"script\",\"name\":\"x\"}",
      "{\"module\":\"theme\"}",
      "{\"module\":\"theme\",\"name\":\"../evil\"}",
      "{\"module\":\"theme\",\"name\":\" leading space\"}",
    };
    for (String s : bad)
    {
      try
      {
        Module.parse(s);
        fail("accepted: " + s);
      }
      catch (Module.InvalidModule _e) {}
    }
  }

  @Test
  public void too_large()
  {
    StringBuilder b = new StringBuilder("{\"module\":\"theme\",\"name\":\"x\",\"pad\":\"");
    while (b.length() <= Module.MAX_JSON_LENGTH)
      b.append("aaaaaaaaaaaaaaaa");
    b.append("\"}");
    try
    {
      Module.parse(b.toString());
      fail("accepted a too large module");
    }
    catch (Module.InvalidModule _e) {}
  }

  @Test
  public void create()
  throws Exception
  {
    JSONObject f = new JSONObject();
    f.put("bar_dp", 30);
    f.put("module", "theme"); // Can't override the type.
    Module m = Module.create("changeheight", "big", f);
    assertEquals("changeheight", m.type);
    assertEquals("changeheight", m.json.getString("module"));
    assertEquals(30, m.json.getInt("bar_dp"));
  }
}
