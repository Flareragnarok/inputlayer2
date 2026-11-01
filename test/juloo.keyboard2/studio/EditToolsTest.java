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
    sel = w.extend(t, sel, true);
    assertEquals("wonderful world", sub(t, sel));
    sel = w.extend(t, sel, true);
    assertEquals("wonderful world! It's", sub(t, sel));
    sel = w.shrink(t, sel, true);
    assertEquals("wonderful world", sub(t, sel));
    sel = w.shrink(t, w.shrink(t, sel, true), true);
    assertEquals("wonderful", sub(t, sel));               // Never below one unit
    // Backward: grows and shrinks at the start.
    sel = w.extend(t, sel, false);
    assertEquals("Hello, wonderful", sub(t, sel));
    sel = w.extend(t, w.extend(t, sel, true), false);      // Nothing before Hello
    assertEquals("Hello, wonderful world", sub(t, sel));
    sel = w.shrink(t, sel, false);
    assertEquals("wonderful world", sub(t, sel));
    sel = w.shrink(t, w.shrink(t, sel, false), false);
    assertEquals("world", sub(t, sel));
    // Until one unit is left: shrink then keeps it (deselect mode clears it).
    assertEquals("world", sub(t, w.shrink(t, sel, true)));
  }

  @Test
  public void inclusive_exclusive()
  {
    String t = "say \"hi there\" now\nnext";
    TextScope w = TextScope.WORD;
    int[] say = w.unit_at(t, 1);
    assertEquals("say ", sub(t, w.inclusive(t, say)));
    assertEquals("say", sub(t, w.exclusive(t, w.inclusive(t, say))));
    int[] now = w.unit_at(t, 16);
    assertEquals(" now", sub(t, w.inclusive(t, now)));      // End of line: space before
    TextScope q = TextScope.delimited("Quotes", "\"", "\"");
    int[] hi = q.unit_at(t, 6);
    assertEquals("hi there", sub(t, hi));
    assertEquals("\"hi there\"", sub(t, q.inclusive(t, hi)));
    assertEquals("hi there", sub(t, q.exclusive(t, q.inclusive(t, hi))));
    // A partial selection grows out to whole units, keeping what it held.
    assertEquals("say ", sub(t, w.inclusive(t, new int[]{ 1, 2 })));
    assertEquals("hi there", sub(t, w.inclusive(t, new int[]{ 6, 10 })));  // "i th" -> both words
    assertEquals("\"hi there\"", sub(t, q.inclusive(t, new int[]{ 7, 9 })));
    int[] line = TextScope.LINE.unit_at(t, 2);
    assertEquals("say \"hi there\" now\n", sub(t, TextScope.LINE.inclusive(t, line)));
  }

  @Test
  public void pinned_text_found_again()
  {
    String t = "pin me, then pin me again";
    assertArrayEquals(new int[]{ 0, 6 }, EditStrip.find_nearest(t, "pin me", 0));
    // Text typed before it moved it: the closest occurrence wins.
    assertArrayEquals(new int[]{ 13, 19 }, EditStrip.find_nearest(t, "pin me", 11));
    assertArrayEquals(new int[]{ 0, 6 }, EditStrip.find_nearest(t, "pin me", 3));
    assertNull(EditStrip.find_nearest(t, "gone", 5));
  }

  @Test
  public void write_steps()
  throws Exception
  {
    String t = "one two three";
    TextScope w = TextScope.WORD;
    assertEquals(0, WriteSteps.unit_index(w, t, 1));
    assertEquals(1, WriteSteps.unit_index(w, t, 4));
    assertEquals(1, WriteSteps.unit_index(w, t, 3));        // Space: the next unit
    assertEquals(2, WriteSteps.unit_index(w, t, 12));
    assertEquals(-1, WriteSteps.unit_index(w, t, 13));
    org.json.JSONArray steps = new org.json.JSONArray(
        "[{\"kind\":\"look\",\"scope\":\"Word\",\"unit\":1,\"style\":\"italic\"},"
        + "{\"kind\":\"format\",\"scope\":\"Word\",\"unit\":2,\"open\":\"**\",\"close\":\"**\"},"
        + "{\"kind\":\"look\",\"scope\":\"Word\",\"unit\":0,\"style\":\"upper\",\"case\":true},"
        + "{\"kind\":\"look\",\"scope\":\"Word\",\"unit\":9,\"style\":\"bold\"}]");
    java.util.List<String[]> fs = TextFormats.cycle_list(TextFormats.syntax("Markdown"),
        new java.util.ArrayList<String[]>());
    // Replayed on another text with as many words: the same units change.
    String r = WriteSteps.replay("red big apple", steps, TextScope.BUILTIN, fs);
    assertEquals("RED " + UnicodeStyles.apply("big", "italic") + " **apple**", r);
    // Removing a format.
    org.json.JSONArray un = new org.json.JSONArray(
        "[{\"kind\":\"format\",\"scope\":\"Word\",\"unit\":1,\"open\":\"\",\"close\":\"\"}]");
    assertEquals("a b c", WriteSteps.replay("a **b** c", un, TextScope.BUILTIN, fs));
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
    assertEquals(TextScope.SENTENCE, TextScope.cycle(TextScope.BUILTIN, TextScope.WORD, true));
    assertEquals(TextScope.ALL, TextScope.cycle(TextScope.BUILTIN, TextScope.WORD, false));
  }

  @Test
  public void custom_selections()
  {
    String t = "call(a, b) then (c) and \"quoted text\" or \"two\"";
    TextScope paren = TextScope.delimited("Parens", "(", ")");
    assertEquals("a, b", sub(t, paren.unit_at(t, 6)));
    assertEquals("c", sub(t, paren.next_unit(t, 10)));
    assertNull(paren.unit_containing(t, 12));             // Between the pairs
    assertEquals("a, b", sub(t, paren.prev_unit(t, 15)));
    TextScope quotes = TextScope.delimited("Quotes", "\"", "\"");
    assertEquals("quoted text", sub(t, quotes.unit_at(t, 27)));
    assertNull(quotes.unit_containing(t, 38));            // " or " is between pairs
    assertEquals("two", sub(t, quotes.next_unit(t, 37)));
    TextScope tags = TextScope.pattern("Tags", "#\\w+");
    String h = "hi #one and #two";
    assertEquals("#one", sub(h, tags.unit_at(h, 4)));
    assertEquals("#two", sub(h, tags.next_unit(h, 7)));
    assertEquals("#one", sub(h, tags.prev_unit(h, 12)));
    assertNull(TextScope.pattern("Bad", "(unclosed"));
  }

  @Test
  public void format_cycle()
  {
    java.util.List<String[]> fs = TextFormats.cycle_list(TextFormats.syntax("Markdown"),
        java.util.Arrays.asList(new String[][]{ { "Angles", "<<", ">>" } }));
    assertEquals("Bold", fs.get(0)[0]);
    assertEquals("Angles", fs.get(fs.size() - 1)[0]);
    TextFormats.Edit e = TextFormats.cycle("a hi b", new int[]{ 2, 4 }, fs, true);
    assertEquals("**hi**", e.text);
    String t = "a **hi** b";
    // Selection kept on the inner text: next format replaces bold by italic.
    e = TextFormats.cycle(t, new int[]{ 4, 6 }, fs, true);
    assertEquals(2, e.start);
    assertEquals(8, e.end);
    assertEquals("*hi*", e.text);
    assertEquals("Bold", TextFormats.current_name(t, new int[]{ 4, 6 }, fs));
    // Backward from bold: plain.
    e = TextFormats.cycle(t, new int[]{ 4, 6 }, fs, false);
    assertEquals("hi", e.text);
    // After the last one: plain.
    e = TextFormats.cycle("<<hi>>", new int[]{ 2, 4 }, fs, true);
    assertEquals("hi", e.text);
    // Restore removes the format, or does nothing.
    assertEquals("hi", TextFormats.unwrap(t, new int[]{ 4, 6 }, fs).text);
    assertEquals(2, TextFormats.unwrap(t, new int[]{ 2, 8 }, fs).start);
    assertNull(TextFormats.unwrap("a hi b", new int[]{ 2, 4 }, fs));
    // Backward from plain: the last one.
    assertEquals("<<hi>>", TextFormats.cycle("hi", new int[]{ 0, 2 }, fs, false).text);
  }

  @Test
  public void fonts_and_style_cycle()
  {
    UnicodeStyles.Font f = UnicodeStyles.font("Greekish",
        "αβςδεφγηιξκλμνοπθρστυvωχψζ", "", "");
    assertNotNull(f);
    assertNull(UnicodeStyles.font("Short", "abc", "", ""));
    assertNull(UnicodeStyles.font("Digits", "αβςδεφγηιξκλμνοπθρστυvωχψζ", "", "12"));
    UnicodeStyles.set_fonts(java.util.Arrays.asList(f));
    try
    {
      assertEquals("Greekish", UnicodeStyles.styles().get(UnicodeStyles.styles().size() - 1));
      String g = UnicodeStyles.apply("Bad", "Greekish");
      assertEquals("βαδ", g);
      assertEquals("bad", UnicodeStyles.to_plain(g));
      assertEquals("Greekish", UnicodeStyles.style_of(g));
      // Cycling: plain -> bold -> italic ... -> zalgo -> Greekish -> plain.
      assertEquals("bold", UnicodeStyles.cycle("abc", true));
      assertEquals("Greekish", UnicodeStyles.cycle("abc", false));
      assertEquals(UnicodeStyles.PLAIN, UnicodeStyles.cycle(g, true));
      assertEquals("italic", UnicodeStyles.cycle(UnicodeStyles.apply("x", "bold"), true));
    }
    finally
    {
      UnicodeStyles.set_fonts(new java.util.ArrayList<UnicodeStyles.Font>());
    }
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
  public void verse_nests()
  {
    java.util.List<String> styles = java.util.Arrays.asList(UnicodeStyles.STYLES);
    java.util.Set<String> seen = new java.util.HashSet<String>();
    for (String[] n : UnicodeStyles.nests())
      for (int i = 1; i < n.length; i++)
      {
        if (n[0].equals("Case"))
          assertTrue(n[i], java.util.Arrays.asList(UnicodeStyles.CASES).contains(n[i]));
        else
          assertTrue(n[i], styles.contains(n[i]));
        seen.add(n[i]);
      }
    // Every nest but Case starts with plain.
    for (String[] n : UnicodeStyles.nests())
      if (!n[0].equals("Case"))
        assertEquals(n[0], UnicodeStyles.PLAIN, n[1]);
    // Every style but plain is in a nest.
    for (String st : UnicodeStyles.STYLES)
      if (!st.equals(UnicodeStyles.PLAIN))
        assertTrue(st, seen.contains(st));
    assertEquals("Bold", TextFormats.cycle("hi", new int[]{ 0, 2 },
          TextFormats.cycle_list(TextFormats.syntax("Markdown"),
            new java.util.ArrayList<String[]>()), true).name);
  }

  @Test
  public void cases()
  {
    assertEquals("HELLO WORLD", UnicodeStyles.change_case("hello world", "upper"));
    assertEquals("Hello World", UnicodeStyles.change_case("hELLO wORLD", "title"));
    assertEquals("tOGGLE cASE", UnicodeStyles.change_case("toggle case", "toggle"));
    assertEquals("tOGGLE cASE", UnicodeStyles.change_case("Toggle Case", "toggle"));
    assertEquals("hello_big_world", UnicodeStyles.change_case("Hello big World", "snake"));
    assertEquals("helloBigWorld", UnicodeStyles.change_case("Hello big World", "camel"));
    assertEquals("hello_big_world", UnicodeStyles.change_case("helloBigWorld", "snake"));
    assertEquals("helloBigWorld", UnicodeStyles.change_case("hello_big_world", "camel"));
    assertEquals("http_server_v2", UnicodeStyles.change_case("HTTPServer v2", "snake"));
    assertEquals("Hello Big World", UnicodeStyles.change_case("hello_big_world", "title"));
    assertEquals("Hello World", UnicodeStyles.change_case("hello world", "title"));
    assertEquals("hElLo BiG wOrLd", UnicodeStyles.change_case("Hello big World", "alternate"));
    // A look is kept.
    assertEquals(UnicodeStyles.apply("helloWorld", "bold"),
        UnicodeStyles.change_case(UnicodeStyles.apply("hello world", "bold"), "camel"));
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
