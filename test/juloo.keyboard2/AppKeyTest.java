package juloo.keyboard2;

import static org.junit.Assert.*;

import juloo.keyboard2.studio.KeyMap;
import org.junit.Test;

/** il-004: app keys. */
public class AppKeyTest
{
  @Test public void plain_package()
  {
    KeyValue k = KeyValue.getKeyByName("app:com.termux");
    assertEquals(KeyValue.Kind.App, k.getKind());
    assertEquals("com.termux", k.getApp().package_name());
    assertTrue(k.getApp().extras().isEmpty());
    assertEquals("↗term", k.getString());
  }

  @Test public void extras_in_order()
  {
    KeyValue k = KeyValue.getKeyByName("app:io.github.flareragnarok.remoteclient?tab=roadmap&x=1");
    assertEquals("io.github.flareragnarok.remoteclient", k.getApp().package_name());
    assertEquals("{tab=roadmap, x=1}", k.getApp().extras().toString());
    assertEquals("↗remo", k.getString());
  }

  @Test public void name_round_trip()
  {
    for (String n : new String[]{ "app:com.termux", "app:a.b?tab=doc" })
      assertEquals(n, KeyMap.name_of(KeyValue.getKeyByName(n)));
  }

  @Test public void not_app_keys()
  {
    for (String n : new String[]{ "app:termux", "app:com.x?bad", "app:com.x?a=b c", "app:" })
      assertNotEquals(n, KeyValue.Kind.App, KeyValue.getKeyByName(n).getKind());
  }
}
