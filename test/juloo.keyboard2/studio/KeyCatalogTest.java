package juloo.keyboard2.studio;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.Test;
import static org.junit.Assert.*;

public class KeyCatalogTest
{
  public KeyCatalogTest() {}

  static List<String> names(List<KeyCatalog.Entry> es)
  {
    List<String> l = new ArrayList<String>();
    for (KeyCatalog.Entry e : es)
      l.add(e.name);
    return l;
  }

  @Test
  public void grouped_and_sorted()
  {
    List<KeyCatalog.Entry> es = KeyCatalog.build(Arrays.asList(
          "enter", "b", "ж", "1", "a", "β", "config", "α", "а", ",", "a"));
    assertEquals(Arrays.asList("a", "b", "α", "β", "а", "ж", ",", "1", "config", "enter"),
        names(es));
    assertEquals("Latin", es.get(0).group);
    assertEquals("Greek", es.get(2).group);
    assertEquals("Cyrillic", es.get(4).group);
    assertEquals(KeyCatalog.SPECIAL, es.get(9).group);
  }

  @Test
  public void music_and_emoji_keep_their_order()
  {
    List<KeyCatalog.Entry> es = KeyCatalog.build(Arrays.asList("a", "♪", "enter"),
        Arrays.asList("♫", "♪"), Arrays.asList("😀", "🎶", "😀"));
    List<String> glyphs = new ArrayList<String>();
    for (KeyCatalog.Entry e : es)
      glyphs.add(e.glyph);
    // ♪ from a layout stays in Music; the second 😀 is the same key.
    assertEquals(Arrays.asList("a", "♫", "♪", "😀", "🎶", "enter"), glyphs);
    assertEquals(KeyCatalog.MUSIC, es.get(1).group);
    assertEquals(KeyCatalog.EMOJI, es.get(3).group);
    assertEquals(2, KeyCatalog.filter(es, "emoji").size());
  }

  @Test
  public void same_key_once()
  {
    // "\\?" and "?" are the same key.
    assertEquals(1, KeyCatalog.build(Arrays.asList("?", "\\?")).size());
  }

  @Test
  public void filter()
  {
    List<KeyCatalog.Entry> es = KeyCatalog.build(Arrays.asList("a", "ж", "config", "enter"));
    assertEquals(Arrays.asList("ж"), names(KeyCatalog.filter(es, "cyril")));
    assertEquals(Arrays.asList("config"), names(KeyCatalog.filter(es, "CONF")));
    assertEquals(4, KeyCatalog.filter(es, "  ").size());
  }

  /** A Java string literal's text to its value (\\uXXXX and \\\\ only). */
  static String unescape(String lit)
  {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < lit.length(); i++)
    {
      char c = lit.charAt(i);
      if (c == '\\' && i + 5 < lit.length() && lit.charAt(i + 1) == 'u')
      {
        b.append((char)Integer.parseInt(lit.substring(i + 2, i + 6), 16));
        i += 5;
      }
      else if (c == '\\' && i + 1 < lit.length())
        b.append(lit.charAt(++i));
      else
        b.append(c);
    }
    return b.toString();
  }

  /** KeyNames is generated (scripts/gen_key_names.py): it must list every
      special key of KeyValue.getSpecialKeyByName. */
  @Test
  public void key_names_up_to_date() throws Exception
  {
    String src = new String(Files.readAllBytes(
          Paths.get("srcs/juloo.keyboard2/KeyValue.java")), StandardCharsets.UTF_8);
    int start = src.indexOf("public static KeyValue getSpecialKeyByName");
    String body = src.substring(start, src.indexOf("\n  }\n", start));
    Matcher m = Pattern.compile("case \"((?:[^\"\\\\]|\\\\.)*)\"").matcher(body);
    List<String> special = Arrays.asList(KeyNames.SPECIAL);
    while (m.find())
    {
      String n = unescape(m.group(1));
      if (n.endsWith("_placeholder") || n.equals("removed"))
        continue;
      assertTrue("Run scripts/gen_key_names.py: missing " + n, special.contains(n));
    }
  }
}
