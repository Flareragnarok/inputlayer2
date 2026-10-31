package juloo.keyboard2.studio;

import org.json.JSONObject;

/** The user's own edit strip tools, defined as modules:
    - [selection]: { "open": "(", "close": ")" } or { "pattern": "#\\w+" },
      added to the Scope cycle;
    - [format]: { "open": "<<", "close": ">>" }, added to the Format cycle;
    - [font]: { "lower": 26 letters, "upper": 26 letters (optional),
      "digits": 10 digits (optional) }, added to the Appearance cycle.
    The module's name is the tool's name. */
public final class CustomTools
{
  /** [null] if the module doesn't define a usable selection. */
  public static TextScope selection(Module m)
  {
    JSONObject j = m.json;
    String pattern = j.optString("pattern", "");
    if (!pattern.isEmpty())
      return TextScope.pattern(m.name, pattern);
    String open = j.optString("open", ""), close = j.optString("close", "");
    if (open.isEmpty() || close.isEmpty())
      return null;
    return TextScope.delimited(m.name, open, close);
  }

  /** { name, open, close }, [null] if both markers are empty. */
  public static String[] format(Module m)
  {
    String open = m.json.optString("open", ""), close = m.json.optString("close", "");
    if (open.isEmpty() && close.isEmpty())
      return null;
    return new String[]{ m.name, open, close };
  }

  public static UnicodeStyles.Font font(Module m)
  {
    return UnicodeStyles.font(m.name, m.json.optString("lower", ""),
        m.json.optString("upper", ""), m.json.optString("digits", ""));
  }
}
