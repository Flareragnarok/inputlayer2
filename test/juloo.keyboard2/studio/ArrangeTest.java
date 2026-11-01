package juloo.keyboard2.studio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class ArrangeTest
{
  public ArrangeTest() {}

  static List<List<String>> rows(String... rs)
  {
    List<List<String>> r = new ArrayList<List<String>>();
    for (String s : rs)
      r.add(new ArrayList<String>(Arrays.asList(s.split(" "))));
    return r;
  }

  static String str(List<List<String>> rows)
  {
    StringBuilder b = new StringBuilder();
    for (List<String> r : rows)
      b.append(String.join(" ", r)).append("|");
    return b.toString();
  }

  @Test
  public void keys_and_rows() throws Exception
  {
    List<List<String>> r = rows("q w e", "a s d", "shift z backspace");
    assertTrue(Arrange.move_key(r, 0, 0, 1));
    assertEquals("w q e|a s d|shift z backspace|", str(r));
    assertFalse(Arrange.move_key(r, 0, 2, 1)); // Already at the end.
    assertTrue(Arrange.move_row(r, 2, -1));
    assertEquals("w q e|shift z backspace|a s d|", str(r));
    assertFalse(Arrange.move_row(r, 0, -1));
    assertTrue(Arrange.insert(r, 0, 99, "♪"));
    assertEquals("w q e ♪|shift z backspace|a s d|", str(r));
    assertTrue(Arrange.split_row(r, 1, 2));
    assertEquals("w q e ♪|shift z|backspace|a s d|", str(r));
    assertFalse(Arrange.split_row(r, 2, 0)); // Nothing before.
    assertTrue(Arrange.join(r, 1));
    assertEquals("w q e ♪|shift z backspace|a s d|", str(r));
    assertTrue(Arrange.remove(r, 0, 3));
    assertTrue(Arrange.remove(r, 2, 0) && Arrange.remove(r, 2, 0) && Arrange.remove(r, 2, 0));
    assertEquals("w q e|shift z backspace|", str(r)); // Emptied row gone.
  }

  /** The owner's case: a key made wider when it doesn't fit, the space
      taken from the key next to it or from the whole row. */
  @Test
  public void resize_keeps_the_row() throws Exception
  {
    float[] w = { 1.75f, 1f, 1f, 1f, 1.5f, 0.75f };
    float[] n = Arrange.resize(w, 4, 0.25f, false, 0.5f, 5f);
    assertArrayEquals(new float[]{ 1.75f, 1f, 1f, 1f, 1.75f, 0.5f }, n, 1e-6f);
    // The neighbour at its smallest: no room.
    assertNull(Arrange.resize(n, 4, 0.25f, false, 0.5f, 5f));
    // From the whole row, in proportion: the total stays.
    float[] r = Arrange.resize(n, 4, 0.25f, true, 0.5f, 5f);
    assertEquals(2f, r[4], 1e-6f);
    float sum = 0, before = 0;
    for (int i = 0; i < r.length; i++) { sum += r[i]; before += n[i]; }
    assertEquals(before, sum, 0.05f);
    assertTrue(r[0] < n[0] && r[1] < n[1]);
    // The last key takes from its left neighbour.
    float[] l = Arrange.resize(new float[]{ 1f, 1f }, 1, 0.25f, false, 0.5f, 5f);
    assertArrayEquals(new float[]{ 0.75f, 1.25f }, l, 1e-6f);
  }

  @Test
  public void scale_all_keeps_proportions() throws Exception
  {
    JSONObject j = new JSONObject("{\"bar_dp\":28,\"comfort_bar_dp\":26,\"edit_strip_dp\":0}");
    JSONObject r = Heights.scale_all(j, true, false, 35);
    assertEquals(37, r.getJSONObject("keyboard_percent").getInt("portrait"));
    assertEquals(28 * 37f / 35, r.getDouble("bar_dp"), 0.01);
    assertEquals(26 * 37f / 35, r.getDouble("comfort_bar_dp"), 0.01);
    assertEquals(0, r.getDouble("edit_strip_dp"), 0); // Hidden stays hidden.
    // At the top of the range: nothing.
    JSONObject top = new JSONObject("{\"keyboard_percent\":{\"portrait\":" + Heights.PORTRAIT_MAX + "}}");
    assertNull(Heights.scale_all(top, true, false, 35));
  }
}
