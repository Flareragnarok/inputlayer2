package juloo.keyboard2.studio;

import java.util.HashMap;
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
  public static final String[] CASES = { "upper", "lower", "title", "toggle" };

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
    Map<Character, Integer> t = TO.get(style);
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
      case "title": r = title(plain); break;
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
      if (!FROM.containsKey(cp))
        continue;
      for (String st : STYLES)
      {
        Map<Character, Integer> t = TO.get(st);
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

  static String toggle(String s)
  {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      b.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
    }
    return b.toString();
  }

  /** Menu label: the style's name written in the style. */
  public static String label(String style)
  {
    return apply(style.replace('_', ' '), style);
  }
}
