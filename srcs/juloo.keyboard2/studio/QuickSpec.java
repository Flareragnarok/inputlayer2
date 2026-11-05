package juloo.keyboard2.studio;

import org.json.JSONObject;

/** What a quick does: an action for a tap and for each of the 8
    swipe directions, like the keys of the keyboard. Actions are names of
    built-in actions, see [Quicksbar.perform]. */
public final class QuickSpec
{
  /** "c" is the tap, the others clockwise from the top. */
  public static final String[] DIRECTIONS =
    { "c", "n", "ne", "e", "se", "s", "sw", "w", "nw" };

  /** Indexed like [DIRECTIONS], [null] when unset. */
  public final String[] actions = new String[DIRECTIONS.length];

  public String get(int dir) { return actions[dir]; }

  public boolean is_empty()
  {
    for (String a : actions)
      if (a != null)
        return false;
    return true;
  }

  static int index_of(String dir)
  {
    for (int i = 0; i < DIRECTIONS.length; i++)
      if (DIRECTIONS[i].equals(dir))
        return i;
    return -1;
  }

  /** Pairs of direction and action. */
  public static QuickSpec of(String... dir_actions)
  {
    QuickSpec s = new QuickSpec();
    for (int i = 0; i + 1 < dir_actions.length; i += 2)
      s.actions[index_of(dir_actions[i])] = Quicksbar.resolve(dir_actions[i + 1]);
    return s;
  }

  /** [null] if no direction has an action. */
  static QuickSpec of_json(JSONObject j)
  {
    QuickSpec s = new QuickSpec();
    for (int i = 0; i < DIRECTIONS.length; i++)
    {
      String a = j.optString(DIRECTIONS[i], "");
      if (!a.isEmpty())
        s.actions[i] = Quicksbar.resolve(a);
    }
    return s.is_empty() ? null : s;
  }
}
