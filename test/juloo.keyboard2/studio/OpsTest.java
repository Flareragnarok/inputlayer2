package juloo.keyboard2.studio;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpsTest
{
  public OpsTest() {}

  static List<String> l(String... ops) { return Arrays.asList(ops); }

  @Test
  public void spec_settings()
  {
    // Runs of the same setting: the last; settings already in effect go.
    assertEquals(l("dir:back", "step"),
        Ops.simplify_spec(l("dir:back", "dir:fwd", "dir:back", "step")));
    assertEquals(l("step"), Ops.simplify_spec(l("dir:fwd", "mode:excl", "step")));
    assertEquals(l("mode:incl", "step"),
        Ops.simplify_spec(l("mode:desel", "mode:incl", "step")));
    // Settings at the end do nothing.
    assertEquals(l("step"), Ops.simplify_spec(l("step", "mode:incl", "dir:back")));
    assertEquals(l(), Ops.simplify_spec(l("scope:Word", "mode:incl")));
  }

  @Test
  public void spec_back_and_forth()
  {
    // Inclusive step → then deselect with the arrow ←: the same unit.
    assertEquals(l("step"),
        Ops.simplify_spec(l("step", "mode:incl", "step", "mode:desel", "dir:back", "step")));
    // Repeated back and forth terminates.
    assertEquals(l("step"),
        Ops.simplify_spec(l("step", "mode:incl", "step", "mode:desel", "dir:back", "step",
            "mode:incl", "dir:fwd", "step", "mode:desel", "dir:back", "step")));
    // Same direction: not undone, kept.
    List<String> keep = l("step", "mode:incl", "step", "mode:desel", "step");
    assertEquals(keep, Ops.simplify_spec(keep));
    // A unit operation in between: kept.
    List<String> keep2 = l("mode:incl", "step", "unit", "mode:desel", "dir:back", "step");
    assertEquals(keep2, Ops.simplify_spec(keep2));
    assertEquals(l("step"), Ops.simplify_spec(l("move:next", "move:prev", "unit", "unit", "step")));
  }

  @Test
  public void vert_merge()
  {
    assertEquals(l("back:5", "insert:oBAmA"),
        Ops.simplify_vert(l("back:2", "back:3", "insert:oB", "insert:Ax", "back:1", "insert:mA")));
    assertEquals(l("back:2"), Ops.simplify_vert(l("insert:a", "back:3")));
    assertEquals(l("wrap:**|**", "look:bold"),
        Ops.simplify_vert(l("wrap:**|**", "look:italic", "look:bold")));
    assertEquals(l("del:3", "case:upper"), Ops.simplify_vert(l("del:1", "del:2", "case:lower", "case:upper")));
  }

  @Test
  public void describe()
  {
    assertEquals("type \"hi\"", Ops.describe("insert:hi"));
    assertEquals("wrap ** … **", Ops.describe("wrap:**|**"));
    assertEquals("direction ←", Ops.describe("dir:back"));
    assertTrue(Ops.is_spec_op("move:next"));
    assertFalse(Ops.is_spec_op("insert:x"));
  }
}
