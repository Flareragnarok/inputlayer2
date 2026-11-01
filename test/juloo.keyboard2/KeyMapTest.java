package juloo.keyboard2;

import java.util.ArrayList;
import java.util.Arrays;
import juloo.keyboard2.studio.KeyMap;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

/** Layout mode's key changes ([keymap] modules). In this package to build
    layouts without resources. */
public class KeyMapTest
{
  public KeyMapTest() {}

  static final int C = 0, N = 1, NE = 2, S = 5;

  static KeyboardData.Key key(String c, String ne)
  {
    KeyValue[] ks = new KeyValue[9];
    ks[0] = KeyValue.getKeyByName(c);
    if (ne != null)
      ks[2] = KeyValue.getKeyByName(ne);
    return new KeyboardData.Key(ks, null, 0, 1f, 0f, null, KeyboardData.Key.Role.Normal);
  }

  static KeyboardData layout(String name)
  {
    KeyboardData.Row r = new KeyboardData.Row(new ArrayList<KeyboardData.Key>(
          Arrays.asList(key("q", "config"), key("w", "2"), key("enter", null))), 1f, 0f);
    return new KeyboardData(new ArrayList<KeyboardData.Row>(Arrays.asList(r)),
        3f, null, "latin", null, name, false, false, false);
  }

  static KeyboardData.Key at(KeyboardData kw, int i) { return kw.rows.get(0).keys.get(i); }

  /** The owner's example: ⚙ from Q's corner to Enter's bottom swipe. */
  @Test
  public void move_settings_to_enter() throws Exception
  {
    JSONObject j = KeyMap.set(new JSONObject(), "IL", "enter", S, "config");
    j = KeyMap.set(j, "IL", "q", NE, "");
    KeyMap km = KeyMap.of_json(j);
    KeyboardData kw = km.apply(layout("IL"));
    assertNull(at(kw, 0).keys[2]);
    assertEquals(KeyValue.getKeyByName("q"), at(kw, 0).keys[0]);
    assertEquals(KeyValue.CONFIG, at(kw, 2).keys[8]);
    assertEquals(KeyValue.getKeyByName("enter"), at(kw, 2).keys[0]);
    assertEquals(KeyValue.getKeyByName("2"), at(kw, 1).keys[2]); // Untouched.
    assertTrue(km.is_changed("IL", "enter", S));
    assertFalse(km.is_changed("IL", "enter", N));
  }

  @Test
  public void only_the_layout_named() throws Exception
  {
    KeyMap km = KeyMap.of_json(KeyMap.set(new JSONObject(), "IL", "q", NE, ""));
    KeyboardData other = layout("Other");
    assertSame(other, km.apply(other));
  }

  @Test
  public void back_to_the_layout() throws Exception
  {
    JSONObject j = KeyMap.set(new JSONObject(), "IL", "q", NE, "");
    j = KeyMap.set(j, "IL", "q", NE, null);
    assertFalse(j.getJSONObject("layouts").has("IL")); // Nothing left.
    assertTrue(KeyMap.of_json(j).is_empty());
  }

  /** A changed tap keeps the key's id, so it can be edited again. */
  @Test
  public void changed_tap_keeps_its_id() throws Exception
  {
    KeyMap km = KeyMap.of_json(KeyMap.set(new JSONObject(), "IL", "q", C, "ü"));
    KeyboardData kw = km.apply(layout("IL"));
    assertEquals(KeyValue.getKeyByName("ü"), at(kw, 0).keys[0]);
    assertEquals("q", km.id_of("IL", at(kw, 0)));
    assertEquals("w", km.id_of("IL", at(kw, 1)));
  }

  @Test
  public void names_round_trip()
  {
    String[] ns = { "q", "ё", "config", "enter", "shift", "backspace", ",", ".", "?", "1", "switch_emoji" };
    for (String n : ns)
    {
      KeyValue kv = KeyValue.getKeyByName(n);
      assertEquals(n, kv, KeyValue.getKeyByName(KeyMap.name_of(kv)));
    }
    String[] d = KeyMap.names_of(at(layout("IL"), 0));
    assertEquals("q", d[C]);
    assertEquals("config", d[NE]);
    assertEquals("", d[S]);
  }

  /** Any text on a key: emoji, notes, kaomoji (with ':' and quotes). */
  @Test
  public void text_keys() throws Exception
  {
    String[] ts = { "♪", "🎶", "😶‍🌫️", "♪~(´ε` )", "a:b", "it's: ok", "enter", "hello" };
    for (String t : ts)
    {
      String n = KeyMap.name_for_text(t);
      assertNotNull(t, n);
      KeyValue kv = KeyValue.getKeyByName(n);
      assertEquals(t, t, kv.getString());
    }
    assertNull(KeyMap.name_for_text(""));
    // On a key, through a keymap.
    JSONObject j = KeyMap.set(new JSONObject(), "IL", "w", S, KeyMap.name_for_text("♪~(´ε` )"));
    KeyboardData kw = KeyMap.of_json(j).apply(layout("IL"));
    assertEquals("♪~(´ε` )", at(kw, 1).keys[8].getString());
  }

  /** Layout mode's swap: two keys trade everything they hold. */
  @Test
  public void swap_keys() throws Exception
  {
    KeyboardData kw = layout("IL");
    JSONObject j = KeyMap.swap(new JSONObject(), "IL", "q", KeyMap.names_of(at(kw, 0)),
        "enter", KeyMap.names_of(at(kw, 2)));
    KeyMap km = KeyMap.of_json(j);
    KeyboardData r = km.apply(kw);
    assertEquals(KeyValue.getKeyByName("enter"), at(r, 0).keys[0]);
    assertNull(at(r, 0).keys[2]);
    assertEquals(KeyValue.getKeyByName("q"), at(r, 2).keys[0]);
    assertEquals(KeyValue.CONFIG, at(r, 2).keys[2]);
    // Each still knows which key it is.
    assertEquals("q", km.id_of("IL", at(r, 0)));
    assertEquals("enter", km.id_of("IL", at(r, 2)));
  }

  /** Owner's default (build 67): ?123 and Shift trade places, Ctrl on
      ?123's down. */
  @Test
  public void symbols_and_shift_swap()
  {
    KeyboardData.Row r1 = new KeyboardData.Row(new ArrayList<KeyboardData.Key>(
          Arrays.asList(key("shift", null), key("z", null))), 1f, 0f);
    KeyboardData.Row r2 = new KeyboardData.Row(new ArrayList<KeyboardData.Key>(
          Arrays.asList(key("il_symbols", "studio_redo"), key("space", null))), 1f, 0f);
    KeyboardData kw = new KeyboardData(new ArrayList<KeyboardData.Row>(Arrays.asList(r1, r2)),
        2f, null, "latin", null, "IL", false, false, false);
    KeyboardData s = LayoutModifier.swap_symbols_and_shift(kw);
    KeyboardData.Key top = s.rows.get(0).keys.get(0), bottom = s.rows.get(1).keys.get(0);
    assertEquals(KeyValue.getKeyByName("il_symbols"), top.keys[0]);
    assertEquals(KeyValue.getKeyByName("studio_redo"), top.keys[2]);
    assertEquals(KeyValue.getKeyByName("ctrl"), top.keys[8]);
    assertEquals(KeyValue.getKeyByName("shift"), bottom.keys[0]);
    assertNull(bottom.keys[2]);
    // A layout without both is left alone.
    KeyboardData one = new KeyboardData(new ArrayList<KeyboardData.Row>(Arrays.asList(r1)),
        2f, null, "latin", null, "IL", false, false, false);
    assertEquals(KeyValue.getKeyByName("shift"),
        LayoutModifier.swap_keys(one, "shift", "il_symbols").rows.get(0).keys.get(0).keys[0]);
  }
}
