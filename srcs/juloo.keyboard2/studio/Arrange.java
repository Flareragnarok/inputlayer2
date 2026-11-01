package juloo.keyboard2.studio;

import java.util.ArrayList;
import java.util.List;

/** The edit key's arrangement operations, on a layout's rows of key ids
    (the [_rows] of a [keymap] module) and on a row's widths. Pure: each
    returns whether it changed anything. */
public final class Arrange
{
  private Arrange() {}

  public static List<List<String>> copy(List<List<String>> rows)
  {
    List<List<String>> r = new ArrayList<List<String>>();
    for (List<String> row : rows)
      r.add(new ArrayList<String>(row));
    return r;
  }

  static boolean valid(List<List<String>> rows, int r)
  {
    return r >= 0 && r < rows.size();
  }

  /** Key [i] of row [r] trades places with its neighbour, left ([-1]) or
      right ([1]). */
  public static boolean move_key(List<List<String>> rows, int r, int i, int dir)
  {
    if (!valid(rows, r))
      return false;
    List<String> row = rows.get(r);
    int j = i + dir;
    if (i < 0 || i >= row.size() || j < 0 || j >= row.size())
      return false;
    String t = row.get(i);
    row.set(i, row.get(j));
    row.set(j, t);
    return true;
  }

  /** Row [r] trades places with the row above ([-1]) or below ([1]). */
  public static boolean move_row(List<List<String>> rows, int r, int dir)
  {
    int s = r + dir;
    if (!valid(rows, r) || !valid(rows, s))
      return false;
    List<String> t = rows.get(r);
    rows.set(r, rows.get(s));
    rows.set(s, t);
    return true;
  }

  /** Key [id] put at [pos] in row [r] (at the end if past it). */
  public static boolean insert(List<List<String>> rows, int r, int pos, String id)
  {
    if (!valid(rows, r) || id == null || id.isEmpty())
      return false;
    List<String> row = rows.get(r);
    row.add(Math.max(0, Math.min(pos, row.size())), id);
    return true;
  }

  /** Key [i] of row [r] removed; an emptied row goes too. */
  public static boolean remove(List<List<String>> rows, int r, int i)
  {
    if (!valid(rows, r) || i < 0 || i >= rows.get(r).size())
      return false;
    rows.get(r).remove(i);
    if (rows.get(r).isEmpty())
      rows.remove(r);
    return true;
  }

  /** Row [r] cut before its key [i]: the keys from [i] on make a new row
      below. */
  public static boolean split_row(List<List<String>> rows, int r, int i)
  {
    if (!valid(rows, r))
      return false;
    List<String> row = rows.get(r);
    if (i <= 0 || i >= row.size())
      return false;
    List<String> tail = new ArrayList<String>(row.subList(i, row.size()));
    row.subList(i, row.size()).clear();
    rows.add(r + 1, tail);
    return true;
  }

  /** Row [r] and the row below made one. */
  public static boolean join(List<List<String>> rows, int r)
  {
    if (!valid(rows, r) || !valid(rows, r + 1))
      return false;
    rows.get(r).addAll(rows.remove(r + 1));
    return true;
  }

  /** Widths of a row with key [i] [delta] wider (narrower if negative),
      the row keeping its total: the space comes from the key next to it
      (the right one, the left one for the last key), or, if [whole_row],
      from all the others: each gives in proportion to what it has above
      [min] (so keys at their smallest stay), and gets back in proportion to
      its width. [null] if there's no room or nothing changes. */
  public static float[] resize(float[] w, int i, float delta, boolean whole_row,
      float min, float max)
  {
    if (i < 0 || i >= w.length)
      return null;
    float[] r = w.clone();
    float nw = round2(Math.max(min, Math.min(max, w[i] + delta)));
    float d = nw - w[i];
    if (d == 0)
      return null;
    r[i] = nw;
    if (w.length == 1)
      return r;
    if (!whole_row)
    {
      int n = (i + 1 < w.length) ? i + 1 : i - 1;
      r[n] = round2(w[n] - d);
      return (r[n] < min) ? null : r;
    }
    float others = 0, slack = 0;
    for (int j = 0; j < w.length; j++)
      if (j != i)
      {
        others += w[j];
        slack += Math.max(0, w[j] - min);
      }
    if (d > 0 && d > slack + 1e-4f)
      return null;
    for (int j = 0; j < w.length; j++)
      if (j != i)
      {
        float give = (d > 0) ? d * Math.max(0, w[j] - min) / slack : d * w[j] / others;
        r[j] = round2(Math.max(min, w[j] - give));
      }
    return r;
  }

  static float round2(float f)
  {
    return Math.round(f * 100) / 100f;
  }
}
