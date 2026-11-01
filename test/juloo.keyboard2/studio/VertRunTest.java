package juloo.keyboard2.studio;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class VertRunTest
{
  public VertRunTest() {}

  static List<String[]> md()
  {
    return TextFormats.cycle_list(TextFormats.syntax("Markdown"), new java.util.ArrayList<String[]>());
  }

  static VertRun run(String t, int s, int e, String... ops)
  {
    return VertRun.run(t, s, e, Arrays.asList(ops), md());
  }

  @Test
  public void keys_faithfully()
  {
    // "obama" selected, ⌫5 then typed: transformed into oBAmA.
    VertRun r = run("say obama now", 4, 9, "back:5", "insert:oBAmA");
    assertEquals("say oBAmA now", r.text);
    // An empty selection still gets the insertion.
    assertEquals("say oBAmA now", run("say  now", 4, 4, "insert:oBAmA").text);
    // ⌫ more than there is before the cursor: what there is.
    assertEquals("oBAmA now", run("say  now", 4, 4, "back:9", "insert:oBAmA").text);
    assertEquals("ac", run("abc", 1, 1, "del:1").text);
    assertEquals("x  y", run("x hi y", 2, 4, "clear").text);
  }

  @Test
  public void wrap_and_looks()
  {
    VertRun r = run("a word b", 2, 6, "look:bold", "wrap:**|**");
    assertEquals("a **" + UnicodeStyles.apply("word", "bold") + "** b", r.text);
    // The scope follows: inside the markers.
    assertEquals(UnicodeStyles.apply("word", "bold"), r.text.substring(r.start, r.end));
    assertEquals("a word b", run("a **word** b", 4, 8, "unwrap").text);
    assertEquals("helloWorld", run("hello world", 0, 11, "case:camel").text);
    // Transforming nothing does nothing; inserting around nothing still inserts.
    assertEquals("x", run("x", 1, 1, "look:bold", "case:upper", "unwrap").text);
    assertEquals("x****", run("x", 1, 1, "wrap:**|**").text);
  }
}
