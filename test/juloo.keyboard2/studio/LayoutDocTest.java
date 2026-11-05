package juloo.keyboard2.studio;

import static org.junit.Assert.*;

import java.io.StringReader;
import java.lang.reflect.Method;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;
import org.json.JSONObject;
import org.junit.Test;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParser;

/** il-006: the layout editor's documents. */
public class LayoutDocTest
{
  /** What the keyboard does with a saved layout: parse its XML. */
  static KeyboardData load(String xml) throws Exception
  {
    XmlPullParser p = new KXmlParser();
    p.setInput(new StringReader(xml));
    Method m = KeyboardData.class.getDeclaredMethod("parse_keyboard", XmlPullParser.class);
    m.setAccessible(true);
    return (KeyboardData)m.invoke(null, p);
  }

  @Test public void blank_loads_as_drawn() throws Exception
  {
    LayoutDoc d = LayoutDoc.blank("Test");
    KeyboardData kd = load(d.xml());
    assertEquals("no bottom row added", 4, kd.rows.size());
    assertEquals(10, kd.rows.get(0).keys.size());
    assertEquals("q", kd.rows.get(0).keys.get(0).keys[0].getString());
    assertEquals(5f, kd.rows.get(3).keys.get(1).width, 0.001f);
  }

  @Test public void editing()
  {
    LayoutDoc d = LayoutDoc.blank("T");
    d.add_key(1, 0);
    assertEquals(10, d.rows.get(1).keys.size());
    d.key(1, 0).names[0] = "tab";
    int[] at = d.move_key(1, 0, -1);
    assertArrayEquals("moving left of the row's start goes to the row above's end", new int[]{ 0, 10 }, at);
    assertEquals("tab", d.key(0, 10).names[0]);
    d.set_width(0, 10, 0.01f);
    assertEquals(LayoutDoc.WIDTH_MIN, d.key(0, 10).width, 0.001f);
    d.set_width(0, 10, 1.234f);
    assertEquals("steps of 0.05", 1.25f, d.key(0, 10).width, 0.001f);
    d.set_height(0, 9f);
    assertEquals(LayoutDoc.HEIGHT_MAX, d.rows.get(0).height, 0.001f);
    d.add_row(0);
    assertEquals(5, d.rows.size());
    d.remove_row(0);
    d.remove_key(0, 10);
    assertEquals(10f, d.width(), 0.001f);
  }

  @Test public void every_slot_gap_and_escaping_survive() throws Exception
  {
    LayoutDoc d = LayoutDoc.blank("Esc & co");
    LayoutDoc.Key k = d.key(0, 0);
    k.shift = 0.5f;
    k.names[2] = "\"";
    k.names[7] = "&";
    k.names[8] = "<";
    k.names[5] = "studio_toggle_repeat~once";
    k.names[6] = "layout:Gaming";
    KeyboardData kd = load(d.xml());
    KeyboardData.Key key = kd.rows.get(0).keys.get(0);
    assertEquals(0.5f, key.shift, 0.001f);
    assertEquals("\"", key.keys[2].getString());
    assertEquals("&", key.keys[7].getString());
    assertEquals("<", key.keys[8].getString());
    assertEquals(KeyValue.Kind.Behaved, key.keys[5].getKind());
    assertEquals("layout:Gaming", KeyMap.name_of(key.keys[6]));
    // and back: the keyboard's layout as the editor opens it
    LayoutDoc again = LayoutDoc.of_keyboard(kd, "again");
    assertEquals("studio_toggle_repeat~once", again.key(0, 0).names[5]);
    assertEquals(d.xml().replace("Esc &amp; co", "again"), again.xml());
  }

  @Test public void json_round_trip() throws Exception
  {
    LayoutDoc d = LayoutDoc.blank("J");
    d.key(2, 3).names[4] = "app:com.termux";
    d.set_height(1, 1.3f);
    LayoutDoc e = LayoutDoc.of_json("J", new JSONObject(d.json().toString()));
    assertEquals(d.xml(), e.xml());
  }

  @Test public void names_and_layout_keys()
  {
    assertTrue(LayoutDoc.valid_name("Gaming 2"));
    assertFalse(LayoutDoc.valid_name("a~b"));
    assertFalse(LayoutDoc.valid_name("<x>"));
    assertFalse(LayoutDoc.valid_name(""));
    KeyValue k = KeyValue.getKeyByName("layout:Gaming");
    assertEquals(KeyValue.Kind.Behaved, k.getKind());
    assertEquals("▦Gami", k.getString());
    assertEquals("layout:Gaming", KeyMap.name_of(k));
    KeyValue o = KeyValue.getKeyByName("layout:Gaming~once");
    assertEquals("layout:Gaming~once", KeyMap.name_of(o));
    assertTrue(Behaviours.capable("layout:Gaming").page);
    assertNotEquals(KeyValue.Kind.Behaved, KeyValue.getKeyByName("layout:a~b~c").getKind());
  }
}
