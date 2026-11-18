package juloo.keyboard2.studio;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

/** il-009: the code table's blocks and search. */
public class CodeTableTest
{
  static CodeTable.Block block_of(int cp)
  {
    for (CodeTable.Block b : CodeTable.blocks())
      if (cp >= b.first && cp <= b.last)
        return b;
    return null;
  }

  @Test public void blocks_in_code_order_with_usable_characters()
  {
    List<CodeTable.Block> bs = CodeTable.blocks();
    assertTrue(bs.size() > 200);
    for (int i = 1; i < bs.size(); i++)
      assertTrue(bs.get(i - 1).last < bs.get(i).first);
    assertEquals("Basic latin", bs.get(0).name);
    assertEquals(' ', bs.get(0).cps[0]); // controls left out
    assertEquals("Currency symbols", block_of(0x20AA).name);
    assertEquals("Latin 1 supplement", block_of('è').name);
    assertEquals("Miscellaneous symbols", block_of('♪').name);
    for (CodeTable.Block b : bs)
      for (int cp : b.cps)
        assertTrue(CodeTable.usable(cp));
  }

  @Test public void search_by_code_character_and_name()
  {
    CodeTable.Names ns = CodeTable.names();
    assertEquals(0x20AA, (int)CodeTable.search("20AA", ns).get(0));
    assertEquals(0x20AA, (int)CodeTable.search("u+20aa", ns).get(0));
    assertEquals(0x20AA, (int)CodeTable.search("0x20AA", ns).get(0));
    assertTrue(CodeTable.search("sheqel", ns).contains(0x20AA));
    assertTrue(CodeTable.search("e grave", ns).contains((int)'è'));
    assertTrue(CodeTable.search("grave", ns).contains((int)'è'));
    List<Integer> pasted = CodeTable.search("₪ è ♪", ns);
    assertEquals(0x20AA, (int)pasted.get(0));
    assertEquals('è', (int)pasted.get(1));
    assertEquals('♪', (int)pasted.get(2));
    // Before the names are loaded, codes and pasted characters still work.
    assertEquals(0x20AA, (int)CodeTable.search("₪", null).get(0));
    assertTrue(CodeTable.search("sheqel", null).isEmpty());
    assertTrue(CodeTable.search("zzzzqqq", ns).isEmpty());
    assertTrue(CodeTable.search_blocks("currency").get(0).name.equals("Currency symbols"));
  }

  @Test public void shown_and_put_on_keys()
  {
    assertEquals("◌̀", CodeTable.shown(0x300));
    assertEquals("₪", CodeTable.shown(0x20AA));
    assertEquals("U+20AA", CodeTable.hex(0x20AA));
    assertEquals("₪", CodeTable.key_name(0x20AA));
    assertEquals(":str:':'", CodeTable.key_name(':'));
  }
}
