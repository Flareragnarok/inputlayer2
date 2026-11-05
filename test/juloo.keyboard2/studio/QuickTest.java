package juloo.keyboard2.studio;

import org.junit.Test;
import static org.junit.Assert.*;

public class QuickTest
{
  public QuickTest() {}

  static int dir(QuickSpec s, float dx, float dy)
  {
    return Quick.direction(s, dx, dy, 10f);
  }

  static int idx(String d) { return QuickSpec.index_of(d); }

  @Test
  public void directions()
  {
    QuickSpec all = QuickSpec.of("c", "a", "n", "a", "ne", "a",
        "e", "a", "se", "a", "s", "a", "sw", "a", "w", "a", "nw", "a");
    assertEquals(0, dir(all, 3, 3));        // Too short: tap
    assertEquals(idx("n"), dir(all, 0, -20));
    assertEquals(idx("e"), dir(all, 20, 0));
    assertEquals(idx("s"), dir(all, 0, 20));
    assertEquals(idx("w"), dir(all, -20, 0));
    assertEquals(idx("ne"), dir(all, 20, -20));
    assertEquals(idx("sw"), dir(all, -20, 20));
    assertEquals(idx("nw"), dir(all, -20, -20));
  }

  @Test
  public void brighter()
  {
    assertEquals(0xff434343, Quick.brighter(0xff333333, 0.08f));
    assertEquals(0xffffffff, Quick.brighter(0xffffffff, 0.08f));
    assertEquals(0x80000000 | 0x141414, Quick.brighter(0x80000000, 0.08f));
  }

  @Test
  public void nearest_set_direction()
  {
    // Only the bottom corners are set: a swipe down-left-ish lands on "sw".
    QuickSpec s = QuickSpec.of("c", "a", "sw", "b", "se", "b");
    assertEquals(idx("sw"), dir(s, -5, 20));   // Mostly down, a bit left
    assertEquals(idx("se"), dir(s, 5, 20));    // Mostly down, a bit right
    assertEquals(idx("sw"), dir(s, -20, 3));   // Mostly left, a bit down
    assertEquals(-1, dir(s, 0, -20));          // Up: nothing there
  }
}
