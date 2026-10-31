package juloo.keyboard2.studio;

import java.text.BreakIterator;

/** Named scopes for the edit strip's selector: the containers text is made
    of, from small to big. Positions are offsets into a text, ranges are
    [start, end) as an int[2]. */
public enum TextScope
{
  WORD("Word"), SENTENCE("Sentence"), LINE("Line"), PARAGRAPH("Paragraph"), ALL("All");

  public final String label;

  TextScope(String l) { label = l; }

  public TextScope next()
  {
    return values()[(ordinal() + 1) % values().length];
  }

  public TextScope prev()
  {
    int n = values().length;
    return values()[(ordinal() + n - 1) % n];
  }

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

  /** [sel] grown to the end of the next unit. */
  public int[] extend(String text, int[] sel)
  {
    int[] n = next_unit(text, sel[1]);
    return (n == null) ? sel : new int[]{ sel[0], n[1] };
  }

  /** [sel] without its last unit, unchanged if it holds only one. */
  public int[] shrink(String text, int[] sel)
  {
    int[] last = prev_unit(text, sel[1]);
    if (last == null || last[0] <= sel[0])
      return sel;
    int[] before = prev_unit(text, last[0]);
    if (before == null || before[1] <= sel[0])
      return sel;
    return new int[]{ sel[0], before[1] };
  }

  /** The unit [pos] is inside of, [null] if [pos] is between units
      (spaces, punctuation between words, blank lines). */
  int[] unit_containing(String text, int pos)
  {
    if (pos < 0 || pos >= text.length())
      return null;
    switch (this)
    {
      case WORD: return word_at(text, pos);
      case SENTENCE: return sentence_at(text, pos);
      case LINE: return line_at(text, pos);
      case PARAGRAPH: return paragraph_at(text, pos);
      default: return trim(text, 0, text.length());
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
