package juloo.keyboard2.studio;

import org.junit.Test;
import static org.junit.Assert.*;

public class EditToolsTest
{
  public EditToolsTest() {}

  static String sub(String t, int[] r) { return (r == null) ? null : t.substring(r[0], r[1]); }

  @Test
  public void words()
  {
    String t = "Hello, wonderful world! It's fine.";
    TextScope w = TextScope.WORD;
    assertEquals("Hello", sub(t, w.unit_at(t, 2)));
    assertEquals("Hello", sub(t, w.unit_at(t, 5)));      // Right after the word
    assertEquals("wonderful", sub(t, w.unit_at(t, 6)));  // On the space: next word
    assertEquals("It's", sub(t, w.unit_at(t, 25)));
    assertEquals("wonderful", sub(t, w.next_unit(t, 5)));
    assertEquals("Hello", sub(t, w.prev_unit(t, 7)));
    assertNull(w.prev_unit(t, 0));
    int[] sel = w.unit_at(t, 8);
    sel = w.extend(t, sel);
    assertEquals("wonderful world", sub(t, sel));
    sel = w.extend(t, sel);
    assertEquals("wonderful world! It's", sub(t, sel));
    sel = w.shrink(t, sel);
    assertEquals("wonderful world", sub(t, sel));
    sel = w.shrink(t, w.shrink(t, sel));
    assertEquals("wonderful", sub(t, sel));               // Never below one unit
  }

  @Test
  public void styled_words()
  {
    String t = "a " + UnicodeStyles.apply("bold", "bold") + " z";
    assertEquals(UnicodeStyles.apply("bold", "bold"), sub(t, TextScope.WORD.unit_at(t, 3)));
  }

  @Test
  public void sentences_lines_paragraphs()
  {
    String t = "One two. Three four?\nFive six\n\nSeven.  Eight";
    assertEquals("One two.", sub(t, TextScope.SENTENCE.unit_at(t, 1)));
    assertEquals("Three four?", sub(t, TextScope.SENTENCE.unit_at(t, 12)));
    assertEquals("Three four?", sub(t, TextScope.SENTENCE.next_unit(t, 8)));
    assertEquals("Five six", sub(t, TextScope.SENTENCE.unit_at(t, 23)));
    assertEquals("One two. Three four?", sub(t, TextScope.LINE.unit_at(t, 3)));
    assertEquals("Five six", sub(t, TextScope.LINE.next_unit(t, 20)));
    assertEquals("One two. Three four?\nFive six", sub(t, TextScope.PARAGRAPH.unit_at(t, 3)));
    assertEquals("Seven.  Eight", sub(t, TextScope.PARAGRAPH.next_unit(t, 25)));
    assertEquals(t, sub(t, TextScope.ALL.unit_at(t, 40)));
    assertEquals(TextScope.SENTENCE, TextScope.WORD.next());
    assertEquals(TextScope.ALL, TextScope.WORD.prev());
  }

  @Test
  public void unicode_styles()
  {
    String b = UnicodeStyles.apply("Hello 42", "bold");
    assertEquals("𝐇𝐞𝐥𝐥𝐨 𝟒𝟐", b);
    assertEquals("Hello 42", UnicodeStyles.to_plain(b));
    assertEquals("bold", UnicodeStyles.style_of(b));
    // Converting between styles goes through plain letters.
    String m = UnicodeStyles.apply(b, "mono");
    assertEquals("Hello 42", UnicodeStyles.to_plain(m));
    assertEquals("mono", UnicodeStyles.style_of(m));
    // Holes in the alphabets.
    assertEquals("ℎ", UnicodeStyles.apply("h", "italic"));
    assertEquals("ℝ", UnicodeStyles.apply("R", "double"));
    assertEquals("h", UnicodeStyles.to_plain("ℎ"));
    assertEquals("ꜱᴍᴀʟʟ", UnicodeStyles.apply("Small", "small_caps"));
    assertEquals("small", UnicodeStyles.to_plain("ꜱᴍᴀʟʟ"));
    assertEquals("Ⓐ①⓪", UnicodeStyles.apply("A10", "circled").substring(0, 3));
    assertEquals("s̶t̶ ̶".substring(0, 4), UnicodeStyles.apply("st", "strike"));
    assertEquals("st", UnicodeStyles.to_plain(UnicodeStyles.apply("st", "strike")));
    String z = UnicodeStyles.apply("zalgo", "zalgo");
    assertTrue(z.length() > 15);
    assertEquals("zalgo", UnicodeStyles.to_plain(z));
    assertEquals("café", UnicodeStyles.to_plain("café").replace("é", "é")); // Accents kept
    assertEquals("Hello", UnicodeStyles.apply(b, UnicodeStyles.PLAIN).substring(0, 5));
  }

  @Test
  public void cases()
  {
    assertEquals("HELLO WORLD", UnicodeStyles.change_case("hello world", "upper"));
    assertEquals("Hello World", UnicodeStyles.change_case("hELLO wORLD", "title"));
    assertEquals("HeLLO", UnicodeStyles.change_case("hEllo", "toggle"));
    // Keeps the style.
    String b = UnicodeStyles.apply("abc", "bold");
    assertEquals(UnicodeStyles.apply("ABC", "bold"), UnicodeStyles.change_case(b, "upper"));
  }

  @Test
  public void formats()
  {
    String[] bold = TextFormats.syntax("Markdown").formats.get("Bold");
    TextFormats.Edit e = TextFormats.toggle("say hi now", new int[]{ 4, 6 }, bold);
    assertEquals("**hi**", e.text);
    assertEquals(4, e.start);
    String after = "say **hi** now";
    // Unwrap with the markers outside the selection...
    e = TextFormats.toggle(after, new int[]{ 6, 8 }, bold);
    assertEquals(4, e.start);
    assertEquals(10, e.end);
    assertEquals("hi", e.text);
    // ...or inside it.
    e = TextFormats.toggle(after, new int[]{ 4, 10 }, bold);
    assertEquals("hi", e.text);
    // Line markers on each line, toggled.
    String[] quote = TextFormats.syntax("Markdown").formats.get("Quote");
    e = TextFormats.toggle("a\nb", new int[]{ 0, 3 }, quote);
    assertEquals("> a\n> b", e.text);
    e = TextFormats.toggle("> a\n> b", new int[]{ 0, 7 }, quote);
    assertEquals("a\nb", e.text);
    assertEquals("||x||", TextFormats.toggle("x", new int[]{ 0, 1 },
          TextFormats.syntax("Discord").formats.get("Spoiler")).text);
    assertEquals(">!x!<", TextFormats.toggle("x", new int[]{ 0, 1 },
          TextFormats.syntax("Reddit").formats.get("Spoiler")).text);
    assertEquals("*x*", TextFormats.toggle("x", new int[]{ 0, 1 },
          TextFormats.syntax("WhatsApp").formats.get("Bold")).text);
  }

  @Test
  public void syntax_by_app()
  {
    assertEquals("Discord", TextFormats.for_app("com.discord", null).name);
    assertEquals("WhatsApp", TextFormats.for_app("com.whatsapp", null).name);
    assertEquals(TextFormats.NONE, TextFormats.for_app("com.instagram.barcelona", null).name);
    assertTrue(TextFormats.for_app("com.instagram.barcelona", null).formats.isEmpty());
    assertEquals("Markdown", TextFormats.for_app("com.example.unknown", null).name);
    assertEquals("Markdown", TextFormats.for_app(null, null).name);
    assertEquals("HTML", TextFormats.for_app("com.discord", "HTML").name);
  }
}
