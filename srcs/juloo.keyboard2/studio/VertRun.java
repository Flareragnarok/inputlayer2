package juloo.keyboard2.studio;

import java.util.List;

/** Runs a Vert's operations ([Ops]) on a text, faithfully: on the scope
    [start, end) (the selection, or the cursor when empty), as they were
    recorded. Insertions happen even in an empty scope; transformations
    (look, case, unwrap) of an empty scope do nothing. */
public final class VertRun
{
  /** The new text and the scope in it after the run. */
  public final String text;
  public final int start, end;

  VertRun(String t, int s, int e) { text = t; start = s; end = e; }

  public static VertRun run(String text, int start, int end, List<String> ops,
      List<String[]> formats)
  {
    String t = text;
    int s = Math.max(0, Math.min(start, t.length()));
    int c = Math.max(s, Math.min(end, t.length()));
    for (String op : ops)
    {
      String p = Ops.param(op);
      switch (Ops.kind(op))
      {
        case "insert":
          t = t.substring(0, c) + p + t.substring(c);
          c += p.length();
          break;
        case "back":
        {
          int k = Math.min(Ops.count(op), c);
          t = t.substring(0, c - k) + t.substring(c);
          c -= k;
          s = Math.min(s, c);
          break;
        }
        case "del":
        {
          int k = Math.min(Ops.count(op), t.length() - c);
          t = t.substring(0, c) + t.substring(c + k);
          break;
        }
        case "clear":
          t = t.substring(0, s) + t.substring(c);
          c = s;
          break;
        case "wrap":
        {
          int bar = p.indexOf('|');
          String open = (bar < 0) ? p : p.substring(0, bar);
          String close = (bar < 0) ? "" : p.substring(bar + 1);
          t = t.substring(0, s) + open + t.substring(s, c) + close + t.substring(c);
          s += open.length();
          c += open.length();
          break;
        }
        case "unwrap":
        {
          if (s == c)
            break;
          TextFormats.Edit e = TextFormats.unwrap(t, new int[]{ s, c }, formats);
          if (e == null)
            break;
          t = t.substring(0, e.start) + e.text + t.substring(e.end);
          s = e.sel_start;
          c = e.sel_end;
          break;
        }
        case "look":
        case "case":
        {
          if (s == c)
            break;
          String body = t.substring(s, c);
          String n = Ops.kind(op).equals("case") ? UnicodeStyles.change_case(body, p)
            : UnicodeStyles.apply(body, p);
          t = t.substring(0, s) + n + t.substring(c);
          c = s + n.length();
          break;
        }
        default:
          break; // Spec operations have no place in a Vert.
      }
    }
    return new VertRun(t, s, c);
  }
}
