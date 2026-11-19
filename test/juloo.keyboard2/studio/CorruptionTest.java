package juloo.keyboard2.studio;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import org.junit.After;
import org.junit.Test;

/** il-010: corruptions, saved piles of marks, as Verse looks. */
public class CorruptionTest
{
  static final String SLASH = "̸⃫̣";

  @After public void clear()
  {
    UnicodeStyles.set_corruptions(Collections.<UnicodeStyles.Corruption>emptyList());
  }

  @Test public void only_marks_and_a_free_name()
  {
    assertNotNull(UnicodeStyles.corruption("slashed", SLASH));
    assertNull(UnicodeStyles.corruption("slashed", "̸x"));
    assertNull(UnicodeStyles.corruption("slashed", ""));
    assertNull(UnicodeStyles.corruption("", SLASH));
    assertNull(UnicodeStyles.corruption("zalgo", SLASH));
  }

  @Test public void applied_to_every_character_and_back()
  {
    UnicodeStyles.set_corruptions(Arrays.asList(
          UnicodeStyles.corruption("slashed", SLASH),
          UnicodeStyles.corruption("one", "̷")));
    String w = UnicodeStyles.apply("word ok", "slashed");
    assertEquals("w" + SLASH + "o" + SLASH + "r" + SLASH + "d" + SLASH + " o" + SLASH + "k" + SLASH, w);
    assertEquals("slashed", UnicodeStyles.style_of(w));
    assertEquals("word ok", UnicodeStyles.to_plain(w));
    String one = UnicodeStyles.apply("café", "one");
    assertEquals("one", UnicodeStyles.style_of(one));
    assertEquals("café", UnicodeStyles.to_plain(one));
    // From one look to another, and from bold.
    assertEquals(UnicodeStyles.apply("word", "one"),
        UnicodeStyles.apply(UnicodeStyles.apply("word", "slashed"), "one"));
    assertEquals(w, UnicodeStyles.apply(UnicodeStyles.apply("word ok", "bold"), "slashed"));
    // Accents stay plain.
    assertEquals("e\u0301", UnicodeStyles.to_plain("e\u0301"));
  }

  @Test public void listed_in_the_marks_nest() throws Exception
  {
    UnicodeStyles.set_corruptions(Arrays.asList(UnicodeStyles.corruption("slashed", SLASH)));
    String[] marks = null;
    for (String[] n : UnicodeStyles.nests())
      if (n[0].equals("Marks"))
        marks = n;
    assertEquals("slashed", marks[marks.length - 1]);
    assertTrue(UnicodeStyles.styles().contains("slashed"));
    Module m = Module.create("corruption", "slashed", new org.json.JSONObject());
    assertNull(CustomTools.corruption(m));
  }
}
