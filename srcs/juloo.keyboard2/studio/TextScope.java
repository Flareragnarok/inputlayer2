package juloo.keyboard2.studio;

import java.text.BreakIterator;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Scopes for the edit strip's selector: the containers text is made of.
    Built in: Word, Sentence, Line, Paragraph, All. Custom ones (from
    [selection] modules): text between two delimiters, or matches of a
    pattern. Positions are offsets into a text, ranges are [start, end) as an
    int[2]. */
public abstract class TextScope
{
  public final String label;

  protected TextScope(String l) { label = l; }

  /** The unit [pos] is inside of, [null] if [pos] is between units. */
  abstract int[] unit_containing(String text, int pos);

  public static final TextScope WORD = new Builtin("Word", 0);
  public static final TextScope SENTENCE = new Builtin("Sentence", 1);
  public static final TextScope LINE = new Builtin("Line", 2);
  public static final TextScope PARAGRAPH = new Builtin("Paragraph", 3);
  public static final TextScope ALL = new Builtin("All", 4);
  public static final List<TextScope> BUILTIN =
    Arrays.asList(WORD, SENTENCE, LINE, PARAGRAPH, ALL);

  /** The unit containing [pos], or the closest one after it, or before it.
      [null] if the text has none. */
  public int[] unit_at(String text, int pos)
  {
    pos = Math.max(0, Math.min(text.length(), pos));
    int[] u = unit_containing(text, pos);
    if (u == null && pos > 0)
      u = unit_containing(text, pos - 1);
    if (u == null)
      u = next_unit(text, pos);
    if (u == null)
      u = prev_unit(text, pos);
    return u;
  }

  /** First unit starting at or after [pos]. */
  public int[] next_unit(String text, int pos)
  {
    for (int p = Math.max(0, pos); p < text.length(); p++)
    {
      int[] u = unit_containing(text, p);
      if (u != null && u[0] >= pos)
        return u;
      if (u != null)
        p = u[1] - 1;
    }
    return null;
  }

  /** Last unit ending at or before [pos]. */
  public int[] prev_unit(String text, int pos)
  {
    for (int p = Math.min(text.length(), pos) - 1; p >= 0; p--)
    {
      int[] u = unit_containing(text, p);
      if (u != null && u[1] <= pos)
        return u;
      if (u != null)
        p = u[0];
    }
    return null;
  }

  /** [sel] grown to the end of the next unit, or to the start of the
      previous one if not [forward]. */
  public int[] extend(String text, int[] sel, boolean forward)
  {
    if (!forward)
    {
      int[] p = prev_unit(text, sel[0]);
      return (p == null) ? sel : new int[]{ p[0], sel[1] };
    }
    int[] n = next_unit(text, sel[1]);
    return (n == null) ? sel : new int[]{ sel[0], n[1] };
  }

  /** [sel] without its last unit, or its first one if not [forward].
      Unchanged if it holds only one. */
  public int[] shrink(String text, int[] sel, boolean forward)
  {
    if (!forward)
    {
      int[] first = next_unit(text, sel[0]);
      if (first == null || first[1] >= sel[1])
        return sel;
      int[] second = next_unit(text, first[1]);
      if (second == null || second[0] >= sel[1])
        return sel;
      return new int[]{ second[0], sel[1] };
    }
    int[] last = prev_unit(text, sel[1]);
    if (last == null || last[0] <= sel[0])
      return sel;
    int[] before = prev_unit(text, last[0]);
    if (before == null || before[1] <= sel[0])
      return sel;
    return new int[]{ sel[0], before[1] };
  }

  /** The scope after [s] in [l] (or before, if not [forward]), wrapping
      around. The first one if [s] isn't in [l]. */
  public static TextScope cycle(List<TextScope> l, TextScope s, boolean forward)
  {
    int i = l.indexOf(s);
    if (i < 0)
      return l.get(0);
    int n = l.size();
    return l.get((i + (forward ? 1 : n - 1)) % n);
  }

  /** The text between [open] and [close], without them. Same delimiters
      (quotes) pair up from the start of the text. Nesting is not followed. */
  public static TextScope delimited(String label, String open, String close)
  {
    return new Delimited(label, open, close);
  }

  /** Matches of [regex]; [null] if it doesn't compile. */
  public static TextScope pattern(String label, String regex)
  {
    try
    {
      return new PatternScope(label, Pattern.compile(regex));
    }
    catch (PatternSyntaxException _e)
    {
      return null;
    }
  }

  static final class Builtin extends TextScope
  {
    final int _kind;

    Builtin(String l, int kind) { super(l); _kind = kind; }

    int[] unit_containing(String text, int pos)
    {
      if (pos < 0 || pos >= text.length())
        return null;
      switch (_kind)
      {
        case 0: return word_at(text, pos);
        case 1: return sentence_at(text, pos);
        case 2: return line_at(text, pos);
        case 3: return paragraph_at(text, pos);
        default: return trim(text, 0, text.length());
      }
    }
  }

  static final class Delimited extends TextScope
  {
    final String _open, _close;

    Delimited(String l, String open, String close)
    {
      super(l);
      _open = open;
      _close = close;
    }

    int[] unit_containing(String text, int pos)
    {
      if (pos < 0 || pos >= text.length() || _open.isEmpty() || _close.isEmpty())
        return null;
      if (_open.equals(_close))
      {
        // Delimiters pair up: 1st with 2nd, 3rd with 4th...
        int i = text.indexOf(_open);
        while (i >= 0)
        {
          int j = text.indexOf(_close, i + _open.length());
          if (j < 0)
            return null;
          if (pos >= i + _open.length() && pos < j)
            return (j > i + _open.length()) ? new int[]{ i + _open.length(), j } : null;
          if (pos < j)
            return null;
          i = text.indexOf(_open, j + _close.length());
        }
        return null;
      }
      int o = text.lastIndexOf(_open, pos - _open.length());
      if (o < 0)
        return null;
      int s = o + _open.length();
      int closed = text.indexOf(_close, s);
      if (closed >= 0 && closed < pos)
        return null;
      int c = text.indexOf(_close, pos);
      if (c < 0 || c == s)
        return null;
      return new int[]{ s, c };
    }
  }

  static final class PatternScope extends TextScope
  {
    final Pattern _p;

    PatternScope(String l, Pattern p) { super(l); _p = p; }

    int[] unit_containing(String text, int pos)
    {
      Matcher m = _p.matcher(text);
      while (m.find())
      {
        if (m.start() > pos)
          return null;
        if (m.end() > pos && m.end() > m.start())
          return new int[]{ m.start(), m.end() };
      }
      return null;
    }

    @Override
    public int[] next_unit(String text, int pos)
    {
      Matcher m = _p.matcher(text);
      while (m.find())
        if (m.start() >= pos && m.end() > m.start())
          return new int[]{ m.start(), m.end() };
      return null;
    }

    @Override
    public int[] prev_unit(String text, int pos)
    {
      Matcher m = _p.matcher(text);
      int[] last = null;
      while (m.find() && m.end() <= pos)
        if (m.end() > m.start())
          last = new int[]{ m.start(), m.end() };
      return last;
    }
  }

  static int[] word_at(String text, int pos)
  {
    if (!is_word_char(text.charAt(pos)))
      return null;
    int s = pos, e = pos + 1;
    while (s > 0 && is_word_char(text.charAt(s - 1)))
      s--;
    while (e < text.length() && is_word_char(text.charAt(e)))
      e++;
    return new int[]{ s, e };
  }

  /** Letters, digits, combining marks (zalgo, accents), apostrophes inside
      words, and the surrogates of styled unicode letters. */
  static boolean is_word_char(char c)
  {
    if (Character.isLetterOrDigit(c) || Character.isSurrogate(c) || c == '_')
      return true;
    int t = Character.getType(c);
    return t == Character.NON_SPACING_MARK || t == Character.ENCLOSING_MARK
      || t == Character.COMBINING_SPACING_MARK || c == '\'' || c == '’';
  }

  static int[] sentence_at(String text, int pos)
  {
    BreakIterator it = BreakIterator.getSentenceInstance();
    it.setText(text);
    int e = it.following(pos);
    if (e == BreakIterator.DONE)
      e = text.length();
    int s = it.previous();
    if (s == BreakIterator.DONE)
      s = 0;
    // A sentence also ends at a line break.
    int nl = text.lastIndexOf('\n', pos - 1);
    if (nl + 1 > s)
      s = nl + 1;
    int nl2 = text.indexOf('\n', pos);
    if (nl2 >= 0 && nl2 < e)
      e = nl2;
    return trim(text, s, e, pos);
  }

  static int[] line_at(String text, int pos)
  {
    int s = text.lastIndexOf('\n', pos - 1) + 1;
    int e = text.indexOf('\n', pos);
    if (e < 0)
      e = text.length();
    return trim(text, s, e, pos);
  }

  static int[] paragraph_at(String text, int pos)
  {
    int s = pos, e = pos;
    while (s > 0 && !blank_line_before(text, s))
      s--;
    while (e < text.length() && !blank_line_before(text, e))
      e++;
    return trim(text, s, e, pos);
  }

  /** Whether [i] starts a line that follows an empty (or blank) line. */
  static boolean blank_line_before(String text, int i)
  {
    if (i == 0 || text.charAt(i - 1) != '\n')
      return false;
    int j = i - 2;
    while (j >= 0 && text.charAt(j) != '\n')
    {
      if (!Character.isWhitespace(text.charAt(j)))
        return false;
      j--;
    }
    return j >= 0;
  }

  static int[] trim(String text, int s, int e)
  {
    while (s < e && Character.isWhitespace(text.charAt(s)))
      s++;
    while (e > s && Character.isWhitespace(text.charAt(e - 1)))
      e--;
    return (s < e) ? new int[]{ s, e } : null;
  }

  /** Like [trim], [null] if [pos] falls on the trimmed spaces. */
  static int[] trim(String text, int s, int e, int pos)
  {
    int[] r = trim(text, s, e);
    return (r == null || pos < r[0] || pos >= r[1]) ? null : r;
  }
}
