package juloo.keyboard2.studio;

import java.util.ArrayList;
import java.util.List;

/** Atomic operations of Specs and Verts, as "kind:param" strings.

    A Spec is a lens: selection steps, run from the cursor or selection,
    giving the scope a Vert works on.
    - scope:<label>    use that scope (Word, Sentence..., custom ones)
    - mode:excl|incl|desel
    - dir:fwd|back     Sector's ←→ direction
    - step             Sector's step (select, then jump/add/remove a unit)
    - unit             select the unit at the cursor, or deselect
    - move:next|prev   move the cursor (or the selection) by a unit

    A Vert operates on that scope, once, as recorded:
    - insert:<text>    typed at the cursor
    - back:<n>         n characters removed before the cursor
    - del:<n>          n characters removed after the cursor
    - clear            the scope deleted
    - wrap:<open>|<close>  inserted before and after the scope
    - unwrap           the scope's format (markers around it) removed
    - look:<style>     a unicode look on the scope
    - case:<case>      a case on the scope

    Saving simplifies: steps undoing each other cancel out. */
public final class Ops
{
  public static String kind(String op)
  {
    int i = op.indexOf(':');
    return (i < 0) ? op : op.substring(0, i);
  }

  public static String param(String op)
  {
    int i = op.indexOf(':');
    return (i < 0) ? "" : op.substring(i + 1);
  }

  public static boolean is_spec_op(String op)
  {
    switch (kind(op))
    {
      case "scope": case "mode": case "dir": case "step": case "unit": case "move":
        return true;
      default:
        return false;
    }
  }

  static boolean is_state(String op)
  {
    String k = kind(op);
    return k.equals("scope") || k.equals("mode") || k.equals("dir");
  }

  /** The Spec's steps without redundant ones:
      - of consecutive changes of the same setting (scope, mode, direction)
        only the last counts, and one that sets what is already set goes;
      - an inclusive step in one direction followed by a deselecting step
        with the arrow the other way (only settings in between) remove the
        same unit: both go;
      - "move:next" then "move:prev" (and back) cancel, so do two "unit";
      - settings at the end change nothing: they go.
      Repeated until nothing changes. */
  public static List<String> simplify_spec(List<String> ops)
  {
    List<String> l = new ArrayList<String>(ops);
    boolean changed = true;
    while (changed)
    {
      changed = false;
      // Settings: last of a run, and only when it changes something.
      String scope = null, mode = "excl", dir = "fwd";
      List<String> out = new ArrayList<String>();
      for (int i = 0; i < l.size(); i++)
      {
        String op = l.get(i);
        if (is_state(op))
        {
          // A later setting of the same kind before any step wins.
          boolean overridden = false;
          for (int j = i + 1; j < l.size() && is_state(l.get(j)); j++)
            if (kind(l.get(j)).equals(kind(op)))
              overridden = true;
          String p = param(op);
          String cur = kind(op).equals("scope") ? scope
            : kind(op).equals("mode") ? mode : dir;
          if (overridden || p.equals(cur))
          {
            changed = true;
            continue;
          }
          if (kind(op).equals("scope")) scope = p;
          else if (kind(op).equals("mode")) mode = p;
          else dir = p;
        }
        out.add(op);
      }
      l = out;
      // Pairs undoing each other.
      for (int i = 0; i < l.size() && !changed; i++)
      {
        String a = l.get(i);
        if (a.equals("unit") || a.startsWith("move:"))
        {
          if (i + 1 < l.size())
          {
            String b = l.get(i + 1);
            boolean cancel = (a.equals("unit") && b.equals("unit"))
              || (a.equals("move:next") && b.equals("move:prev"))
              || (a.equals("move:prev") && b.equals("move:next"));
            if (cancel)
            {
              l.remove(i + 1);
              l.remove(i);
              changed = true;
            }
          }
          continue;
        }
        if (!a.equals("step") || !state_at(l, i, "mode").equals("incl"))
          continue;
        String d = state_at(l, i, "dir");
        int j = i + 1;
        while (j < l.size() && is_state(l.get(j)))
          j++;
        if (j < l.size() && l.get(j).equals("step")
            && state_at(l, j, "mode").equals("desel")
            && !state_at(l, j, "dir").equals(d))
        {
          l.remove(j);
          l.remove(i);
          changed = true;
        }
      }
      // Settings at the end.
      while (!l.isEmpty() && is_state(l.get(l.size() - 1)))
      {
        l.remove(l.size() - 1);
        changed = true;
      }
    }
    return l;
  }

  /** The value of setting [kind] in effect at [i]. */
  static String state_at(List<String> l, int i, String kind)
  {
    String v = kind.equals("mode") ? "excl" : kind.equals("dir") ? "fwd" : "";
    for (int j = 0; j < i; j++)
      if (kind(l.get(j)).equals(kind))
        v = param(l.get(j));
    return v;
  }

  /** The Vert's operations merged: consecutive insertions join, removals
      add up, an insertion followed by removing before the cursor is cut
      short, a look or case followed by another of the same kind is
      replaced by it. */
  public static List<String> simplify_vert(List<String> ops)
  {
    List<String> l = new ArrayList<String>();
    for (String op : ops)
    {
      String last = l.isEmpty() ? null : l.get(l.size() - 1);
      String k = kind(op);
      if (last != null && k.equals("insert") && kind(last).equals("insert"))
      {
        l.set(l.size() - 1, "insert:" + param(last) + param(op));
        continue;
      }
      if (last != null && (k.equals("back") || k.equals("del")) && kind(last).equals(k))
      {
        l.set(l.size() - 1, k + ":" + (count(last) + count(op)));
        continue;
      }
      if (last != null && k.equals("back") && kind(last).equals("insert"))
      {
        String t = param(last);
        int n = count(op);
        int cut = Math.min(n, t.length());
        String rest = t.substring(0, t.length() - cut);
        l.remove(l.size() - 1);
        if (!rest.isEmpty())
          l.add("insert:" + rest);
        if (n > cut)
          l.add("back:" + (n - cut));
        continue;
      }
      if (last != null && (k.equals("look") || k.equals("case")) && kind(last).equals(k))
      {
        l.set(l.size() - 1, op);
        continue;
      }
      l.add(op);
    }
    return l;
  }

  static int count(String op)
  {
    try { return Integer.parseInt(param(op)); }
    catch (NumberFormatException _e) { return 0; }
  }

  /** Readable form, for the editors and summaries. */
  public static String describe(String op)
  {
    String p = param(op);
    switch (kind(op))
    {
      case "scope": return "scope " + p;
      case "mode": return "mode " + p;
      case "dir": return p.equals("back") ? "direction ←" : "direction →";
      case "step": return "step";
      case "unit": return "select unit";
      case "move": return p.equals("prev") ? "move ←" : "move →";
      case "insert": return "type \"" + p + "\"";
      case "back": return "⌫ " + p;
      case "del": return "⌦ " + p;
      case "clear": return "clear";
      case "wrap": return "wrap " + p.replace("|", " … ");
      case "unwrap": return "unwrap";
      case "look": return "look " + p;
      case "case": return "case " + p;
      default: return op;
    }
  }

  public static String summary(List<String> ops)
  {
    StringBuilder b = new StringBuilder();
    for (String op : ops)
    {
      if (b.length() > 0)
        b.append(" · ");
      b.append(describe(op));
    }
    return (b.length() > 60) ? b.substring(0, 59) + "…" : b.toString();
  }
}
