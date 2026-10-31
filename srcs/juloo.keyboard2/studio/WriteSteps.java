package juloo.keyboard2.studio;

import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** The steps of a typing in format, as Spec saves and replays them: changes
    made to single units (sectors) of the typed text, each recorded with
    the scope it was made in and the unit's index within the text.
    - { "kind": "look", "scope", "unit", "style", "case" }: Verse's look or
      case applied to the unit;
    - { "kind": "format", "scope", "unit", "open", "close" }: View's markers
      put around the unit; both empty removes the unit's format. */
public final class WriteSteps
{
  /** Index of the unit of [scope] that [pos] is in (or the next one), 0 for
      the first unit of [text]. [-1] if there is none. */
  public static int unit_index(TextScope scope, String text, int pos)
  {
    int i = 0;
    int[] u = scope.next_unit(text, 0);
    while (u != null)
    {
      if (pos < u[1])
        return i;
      i++;
      u = scope.next_unit(text, u[1]);
    }
    return -1;
  }

  /** Range of the [index]th unit of [scope] in [text], [null] if there are
      fewer units. */
  public static int[] unit(TextScope scope, String text, int index)
  {
    int[] u = scope.next_unit(text, 0);
    for (int i = 0; u != null && i < index; i++)
      u = scope.next_unit(text, u[1]);
    return u;
  }

  /** [text] with [steps] replayed in order. Steps whose scope isn't in
      [scopes] or whose unit doesn't exist in [text] are skipped. */
  public static String replay(String text, JSONArray steps, List<TextScope> scopes,
      List<String[]> formats)
  {
    for (int i = 0; i < steps.length(); i++)
    {
      JSONObject s = steps.optJSONObject(i);
      if (s == null)
        continue;
      TextScope scope = null;
      for (TextScope sc : scopes)
        if (sc.label.equals(s.optString("scope")))
          scope = sc;
      int[] u = (scope == null) ? null : unit(scope, text, s.optInt("unit", -1));
      if (u == null)
        continue;
      String body = text.substring(u[0], u[1]);
      String n;
      if (s.optString("kind").equals("look"))
      {
        String style = s.optString("style", UnicodeStyles.PLAIN);
        n = s.optBoolean("case", false) ? UnicodeStyles.change_case(body, style)
          : UnicodeStyles.apply(body, style);
      }
      else
      {
        String open = s.optString("open", ""), close = s.optString("close", "");
        if (open.isEmpty() && close.isEmpty())
        {
          TextFormats.Edit e = TextFormats.unwrap(text, u, formats);
          if (e == null)
            continue;
          text = text.substring(0, e.start) + e.text + text.substring(e.end);
          continue;
        }
        n = open + body + close;
      }
      text = text.substring(0, u[0]) + n + text.substring(u[1]);
    }
    return text;
  }
}
