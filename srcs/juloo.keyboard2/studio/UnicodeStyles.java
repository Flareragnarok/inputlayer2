package juloo.keyboard2.studio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** The edit strip's Appearance: converts text between plain letters and
    unicode lookalikes (mathematical alphabets, small caps, circled,
    fullwidth, combining strike/underline, zalgo) and changes its case.
    Every style converts from plain letters, so converting first goes back to
    plain: styles can be switched freely. */
public final class UnicodeStyles
{
  public static final String PLAIN = "plain";
  /** Styles in menu order, with their name shown in their own style. */
  public static final String[] STYLES = {
    PLAIN, "bold", "italic", "bold_italic", "sans", "sans_bold",
    "sans_italic", "sans_bold_italic", "mono", "script", "bold_script",
    "fraktur", "bold_fraktur", "double", "small_caps", "circled",
    "fullwidth", "strike", "underline", "zalgo" };
  public static final String[] CASES =
    { "upper", "lower", "title", "toggle", "snake", "camel", "alternate" };

  static final char STRIKE = '̶';
  static final char UNDERLINE = '̲';

  /** Code points of 'A', 'a' and '0' in the mathematical alphabets, [0] for
      no digits. */
  static final Object[][] MATH = {
    { "bold", 0x1D400, 0x1D41A, 0x1D7CE },
    { "italic", 0x1D434, 0x1D44E, 0 },
    { "bold_italic", 0x1D468, 0x1D482, 0 },
    { "script", 0x1D49C, 0x1D4B6, 0 },
    { "bold_script", 0x1D4D0, 0x1D4EA, 0 },
    { "fraktur", 0x1D504, 0x1D51E, 0 },
    { "double", 0x1D538, 0x1D552, 0x1D7D8 },
    { "bold_fraktur", 0x1D56C, 0x1D586, 0 },
    { "sans", 0x1D5A0, 0x1D5BA, 0x1D7E2 },
    { "sans_bold", 0x1D5D4, 0x1D5EE, 0x1D7EC },
    { "sans_italic", 0x1D608, 0x1D622, 0 },
    { "sans_bold_italic", 0x1D63C, 0x1D656, 0 },
    { "mono", 0x1D670, 0x1D68A, 0x1D7F6 },
  };

  /** Letters that live outside the mathematical blocks (holes in them). */
  static final Object[][] EXCEPTIONS = {
    { "italic", 'h', 0x210E },
    { "script", 'B', 0x212C }, { "script", 'E', 0x2130 }, { "script", 'F', 0x2131 },
    { "script", 'H', 0x210B }, { "script", 'I', 0x2110 }, { "script", 'L', 0x2112 },
    { "script", 'M', 0x2133 }, { "script", 'R', 0x211B }, { "script", 'e', 0x212F },
    { "script", 'g', 0x210A }, { "script", 'o', 0x2134 },
    { "fraktur", 'C', 0x212D }, { "fraktur", 'H', 0x210C }, { "fraktur", 'I', 0x2111 },
    { "fraktur", 'R', 0x211C }, { "fraktur", 'Z', 0x2128 },
    { "double", 'C', 0x2102 }, { "double", 'H', 0x210D }, { "double", 'N', 0x2115 },
    { "double", 'P', 0x2119 }, { "double", 'Q', 0x211A }, { "double", 'R', 0x211D },
    { "double", 'Z', 0x2124 },
  };

  static final String SMALL_CAPS = "ᴀʙᴄᴅᴇꜰɢʜɪᴊᴋʟᴍɴᴏᴘǫʀꜱᴛᴜᴠᴡxʏᴢ";

  /** style -> plain char -> styled code point. */
  static final Map<String, Map<Character, Integer>> TO = new HashMap<String, Map<Character, Integer>>();
  /** styled code point -> plain char, for all styles. */
  static final Map<Integer, Character> FROM = new HashMap<Integer, Character>();

  static
  {
    for (Object[] m : MATH)
    {
      Map<Character, Integer> t = new HashMap<Character, Integer>();
      for (int i = 0; i < 26; i++)
      {
        t.put((char)('A' + i), (Integer)m[1] + i);
        t.put((char)('a' + i), (Integer)m[2] + i);
      }
      if ((Integer)m[3] != 0)
        for (int i = 0; i < 10; i++)
          t.put((char)('0' + i), (Integer)m[3] + i);
      TO.put((String)m[0], t);
    }
    for (Object[] e : EXCEPTIONS)
      TO.get((String)e[0]).put((Character)e[1], (Integer)e[2]);
    Map<Character, Integer> sc = new HashMap<Character, Integer>();
    Map<Character, Integer> ci = new HashMap<Character, Integer>();
    Map<Character, Integer> fw = new HashMap<Character, Integer>();
    for (int i = 0; i < 26; i++)
    {
      sc.put((char)('a' + i), (int)SMALL_CAPS.charAt(i));
      ci.put((char)('A' + i), 0x24B6 + i);
      ci.put((char)('a' + i), 0x24D0 + i);
      fw.put((char)('A' + i), 0xFF21 + i);
      fw.put((char)('a' + i), 0xFF41 + i);
    }
    for (int i = 0; i < 10; i++)
    {
      ci.put((char)('0' + i), (i == 0) ? 0x24EA : 0x2460 + i - 1);
      fw.put((char)('0' + i), 0xFF10 + i);
    }
    TO.put("small_caps", sc);
    TO.put("circled", ci);
    TO.put("fullwidth", fw);
    for (Map<Character, Integer> t : TO.values())
      for (Map.Entry<Character, Integer> e : t.entrySet())
        // 'x' is its own small cap: don't map plain 'x' back to anything.
        if (e.getValue() != (int)e.getKey().charValue())
          FROM.put(e.getValue(), e.getKey());
  }

  /** A saved font (from a [font] module): the user's own letters for a-z,
      optionally A-Z and 0-9. */
  public static final class Font
  {
    public final String name;
    final Map<Character, Integer> map = new HashMap<Character, Integer>();

    Font(String n) { name = n; }
  }

  /** Saved fonts, after the built-in styles in the cycle. */
  static List<Font> _fonts = new ArrayList<Font>();
  static Map<Integer, Character> _fonts_from = new HashMap<Integer, Character>();

  /** [null] unless [lower] has 26 characters, [upper] 26 or none and
      [digits] 10 or none. Characters can be outside the BMP. */
  public static Font font(String name, String lower, String upper, String digits)
  {
    int[] lo = code_points(lower);
    int[] up = code_points(upper);
    int[] di = code_points(digits);
    if (name == null || name.trim().isEmpty() || lo.length != 26
        || (up.length != 0 && up.length != 26) || (di.length != 0 && di.length != 10))
      return null;
    Font f = new Font(name.trim());
    for (int i = 0; i < 26; i++)
    {
      f.map.put((char)('a' + i), lo[i]);
      // Without capitals, capitals use the lowercase forms.
      f.map.put((char)('A' + i), (up.length == 26) ? up[i] : lo[i]);
    }
    for (int i = 0; i < di.length; i++)
      f.map.put((char)('0' + i), di[i]);
    return f;
  }

  /** [String.codePoints] needs Android 7. */
  static int[] code_points(String s)
  {
    if (s == null)
      return new int[0];
    int[] r = new int[s.codePointCount(0, s.length())];
    for (int i = 0, k = 0; i < s.length(); k++)
    {
      r[k] = s.codePointAt(i);
      i += Character.charCount(r[k]);
    }
    return r;
  }

  public static synchronized void set_fonts(List<Font> fonts)
  {
    Map<Integer, Character> from = new HashMap<Integer, Character>();
    for (Font f : fonts)
      for (Map.Entry<Character, Integer> e : f.map.entrySet())
        if (e.getValue() != (int)e.getKey().charValue() && !from.containsKey(e.getValue()))
          // Lowercase first: 'a' and 'A' may share a form.
          from.put(e.getValue(), Character.toLowerCase(e.getKey()));
    for (Font f : fonts)
      for (Map.Entry<Character, Integer> e : f.map.entrySet())
        if (Character.isLowerCase(e.getKey()))
          from.put(e.getValue(), e.getKey());
    _fonts = new ArrayList<Font>(fonts);
    _fonts_from = from;
  }

  /** Verse's nests: { name, items... }. Every nest but "Case" (which holds
      [CASES]) starts with [PLAIN]; "Fonts" holds the saved fonts, only when
      there are some. */
  public static List<String[]> nests()
  {
    List<String[]> l = new ArrayList<String[]>();
    l.add(new String[]{ "Case", "upper", "lower", "title", "toggle", "snake", "camel", "alternate" });
    l.add(new String[]{ "Serif", PLAIN, "bold", "italic", "bold_italic" });
    l.add(new String[]{ "Sans", PLAIN, "sans", "sans_bold", "sans_italic", "sans_bold_italic" });
    l.add(new String[]{ "Type", PLAIN, "mono", "script", "bold_script", "fraktur", "bold_fraktur", "double" });
    l.add(new String[]{ "Shapes", PLAIN, "small_caps", "circled", "fullwidth" });
    l.add(new String[]{ "Marks", PLAIN, "strike", "underline", "zalgo" });
    if (!_fonts.isEmpty())
    {
      String[] f = new String[_fonts.size() + 2];
      f[0] = "Fonts";
      f[1] = PLAIN;
      for (int i = 0; i < _fonts.size(); i++)
        f[i + 2] = _fonts.get(i).name;
      l.add(f);
    }
    return l;
  }

  /** Built-in styles then saved fonts, in cycle order. */
  public static List<String> styles()
  {
    List<String> l = new ArrayList<String>(Arrays.asList(STYLES));
    for (Font f : _fonts)
      l.add(f.name);
    return l;
  }

  static Map<Character, Integer> table(String style)
  {
    Map<Character, Integer> t = TO.get(style);
    if (t != null)
      return t;
    for (Font f : _fonts)
      if (f.name.equals(style))
        return f.map;
    return null;
  }

  /** The style after (or before) the style of [s], wrapping around. */
  public static String cycle(String s, boolean forward)
  {
    List<String> l = styles();
    int i = l.indexOf(style_of(s));
    int n = l.size();
    return l.get(((i < 0 ? 0 : i) + (forward ? 1 : n - 1)) % n);
  }

  /** Back to plain letters: styled letters mapped back, strike and
      underline marks and zalgo's piles of marks removed. Accents (a single
      combining mark) are kept. */
  public static String to_plain(String s)
  {
    s = s.replace(String.valueOf(STRIKE), "").replace(String.valueOf(UNDERLINE), "");
    StringBuilder b = new StringBuilder();
    int i = 0;
    while (i < s.length())
    {
      int cp = s.codePointAt(i);
      i += Character.charCount(cp);
      if (is_mark(cp))
      {
        // Zalgo piles marks on a letter: drop runs of two or more.
        int start = i - Character.charCount(cp);
        int j = start, n = 0;
        while (j < s.length() && is_mark(s.codePointAt(j)))
        {
          j += Character.charCount(s.codePointAt(j));
          n++;
        }
        if (n == 1)
          b.appendCodePoint(cp);
        i = j;
        continue;
      }
      Character p = FROM.get(cp);
      if (p == null)
        p = _fonts_from.get(cp);
      if (p != null)
        b.append(p.charValue());
      else
        b.appendCodePoint(cp);
    }
    return b.toString();
  }

  /** [s] in [style], from whatever style it was in. */
  public static String apply(String s, String style)
  {
    String plain = to_plain(s);
    switch (style)
    {
      case PLAIN: return plain;
      case "strike": return combine(plain, STRIKE);
      case "underline": return combine(plain, UNDERLINE);
      case "zalgo": return zalgo(plain, new Random(plain.hashCode()));
    }
    Map<Character, Integer> t = table(style);
    if (t == null)
      return s;
    // Small caps only have lowercase forms.
    if (style.equals("small_caps"))
      plain = plain.toLowerCase();
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < plain.length(); i++)
    {
      char c = plain.charAt(i);
      Integer cp = t.get(c);
      if (cp == null)
        b.append(c);
      else
        b.appendCodePoint(cp);
    }
    return b.toString();
  }

  /** Change the case of [s], keeping its style. */
  public static String change_case(String s, String c)
  {
    String style = style_of(s);
    String plain = to_plain(s);
    String r;
    switch (c)
    {
      case "upper": r = plain.toUpperCase(); break;
      case "lower": r = plain.toLowerCase(); break;
      case "title": r = identifier(plain) ? join(words(plain), " ", true, true) : title(plain); break;
      case "snake": r = join(words(plain), "_", false, false); break;
      case "camel": r = join(words(plain), "", false, true); break;
      case "alternate": r = alternate(plain); break;
      default: r = toggle(plain); break;
    }
    return style.equals(PLAIN) ? r : apply(r, style);
  }

  /** The style of the first styled character, [PLAIN] if none. */
  public static String style_of(String s)
  {
    if (s.indexOf(STRIKE) >= 0)
      return "strike";
    if (s.indexOf(UNDERLINE) >= 0)
      return "underline";
    for (int i = 0; i < s.length(); )
    {
      int cp = s.codePointAt(i);
      i += Character.charCount(cp);
      if (!FROM.containsKey(cp) && !_fonts_from.containsKey(cp))
        continue;
      for (String st : styles())
      {
        Map<Character, Integer> t = table(st);
        if (t != null && t.containsValue(cp))
          return st;
      }
    }
    return PLAIN;
  }

  static String combine(String s, char mark)
  {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); )
    {
      int cp = s.codePointAt(i);
      i += Character.charCount(cp);
      b.appendCodePoint(cp);
      if (!Character.isWhitespace(cp))
        b.append(mark);
    }
    return b.toString();
  }

  static String zalgo(String s, Random r)
  {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); )
    {
      int cp = s.codePointAt(i);
      i += Character.charCount(cp);
      b.appendCodePoint(cp);
      if (Character.isWhitespace(cp))
        continue;
      int n = 3 + r.nextInt(4);
      for (int k = 0; k < n; k++)
      {
        char m = (char)(0x0300 + r.nextInt(0x70));
        // Strike and underline marks are styles of their own.
        if (m != STRIKE && m != UNDERLINE)
          b.append(m);
      }
    }
    return b.toString();
  }

  static boolean is_mark(int cp)
  {
    int t = Character.getType(cp);
    return t == Character.NON_SPACING_MARK || t == Character.ENCLOSING_MARK;
  }

  static String title(String s)
  {
    StringBuilder b = new StringBuilder();
    boolean start = true;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      b.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
      start = Character.isWhitespace(c);
    }
    return b.toString();
  }

  /** Words of [s]: split at spaces, '_' and '-', and inside camelCase
      ("helloBigWorld", "HTTPServer"). */
  static java.util.List<String> words(String s)
  {
    java.util.List<String> l = new java.util.ArrayList<String>();
    StringBuilder w = new StringBuilder();
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (Character.isWhitespace(c) || c == '_' || c == '-')
      {
        if (w.length() > 0) { l.add(w.toString()); w.setLength(0); }
        continue;
      }
      if (w.length() > 0 && Character.isUpperCase(c))
      {
        char p = w.charAt(w.length() - 1);
        boolean next_lower = i + 1 < s.length() && Character.isLowerCase(s.charAt(i + 1));
        // "bigWorld" -> big|World, "HTTPServer" -> HTTP|Server.
        if (Character.isLowerCase(p) || Character.isDigit(p)
            || (Character.isUpperCase(p) && next_lower))
        {
          l.add(w.toString());
          w.setLength(0);
        }
      }
      w.append(c);
    }
    if (w.length() > 0)
      l.add(w.toString());
    return l;
  }

  /** One word written as an identifier: snake_case or camelCase. */
  static boolean identifier(String s)
  {
    String t = s.trim();
    return !t.isEmpty() && t.indexOf(' ') < 0 && words(t).size() > 1;
  }

  /** [ws] joined by [sep]; words after the first (or all, if [first_cap])
      capitalized when [cap], all lowercase otherwise. */
  static String join(java.util.List<String> ws, String sep, boolean first_cap, boolean cap)
  {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < ws.size(); i++)
    {
      String w = ws.get(i).toLowerCase();
      if (cap && (i > 0 || first_cap) && !w.isEmpty())
        w = Character.toUpperCase(w.charAt(0)) + w.substring(1);
      if (i > 0)
        b.append(sep);
      b.append(w);
    }
    return b.toString();
  }

  /** "aLtErNaTiNg": letters alternate small and capital, spaces and
      symbols not counted. */
  static String alternate(String s)
  {
    StringBuilder b = new StringBuilder();
    boolean upper = false;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      if (Character.isLetter(c))
      {
        b.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
        upper = !upper;
      }
      else
        b.append(c);
    }
    return b.toString();
  }

  /** "tOGGLE cASE": each word's first letter small, the others capital
      (flipping every letter gave UPPER for lowercase text). */
  static String toggle(String s)
  {
    StringBuilder b = new StringBuilder();
    boolean start = true;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      b.append(start ? Character.toLowerCase(c) : Character.toUpperCase(c));
      start = Character.isWhitespace(c);
    }
    return b.toString();
  }

  /** Menu label: the style's name written in the style. */
  public static String label(String style)
  {
    return apply(style.replace('_', ' '), style);
  }
}
