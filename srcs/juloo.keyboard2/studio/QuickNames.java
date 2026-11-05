package juloo.keyboard2.studio;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import juloo.keyboard2.KeyValue;

/** Quick names (il-008, owner 2026-11-06): every action a key, a swipe or
    the quicksbar can call is a quick, named by its function:

      quickfunction : …     the default: copy, backspace, type letter : a …
      quicktoggle : …       switching layouts: layout : numbers, : next …
      pageswitch : …        pages of the same layout: symbols, letters
      quickopen : …         opening: app : Termux, layout editor, studio …
      quickedit : …         the quickedit's work: focus, resize, move, undo
      quickequip : …        equipping keys: layout current (edit mode)
      quickbehaviour : …    key behaviours: key behaviour : long press

    Studio shows and searches these names; the layout files and saved
    settings keep the short legacy names. A quick name is also accepted as a
    key name, written with or without spaces around the colons
    (`quickfunction:copy`), and resolves to the very same key when a layout
    loads, so a layout written in quick names types exactly as fast. */
public final class QuickNames
{
  public static final String FUNCTION = "quickfunction", TOGGLE = "quicktoggle", PAGE = "pageswitch",
         OPEN = "quickopen", EDIT = "quickedit", EQUIP = "quickequip", BEHAVIOUR = "quickbehaviour";
  /** In the order Studio lists them. */
  public static final String[] CATEGORIES = { TOGGLE, PAGE, OPEN, EDIT, EQUIP, BEHAVIOUR, FUNCTION };

  public static final String SEP = " : ";

  /** Keys (names in layouts) whose quick name isn't made by the rules. */
  static final String[][] KEYS = {
    { "il_symbols", PAGE + " : symbols" },
    { "il_symbols_more", PAGE + " : symbols more" },
    { "switch_text", PAGE + " : letters" },
    { "switch_numeric", TOGGLE + " : layout : numbers" },
    { "switch_greekmath", TOGGLE + " : layout : greek" },
    { "switch_forward", TOGGLE + " : layout : next" },
    { "switch_backward", TOGGLE + " : layout : previous" },
    { "change_method_prev", TOGGLE + " : keyboard : previous" },
    { "change_method_next", TOGGLE + " : keyboard : next" },
    { "studio_toggle_repeat", BEHAVIOUR + " : key behaviour : long press" },
    { "studio_backspace_toggle", FUNCTION + " : backspace : direction" },
    { "config", OPEN + " : settings" },
    { "switch_emoji", OPEN + " : emoji" },
    { "switch_back_emoji", OPEN + " : emoji : close" },
    { "switch_clipboard", OPEN + " : clipboard" },
    { "switch_back_clipboard", OPEN + " : clipboard : close" },
    { "change_method", OPEN + " : keyboards" },
    { "voice_typing", OPEN + " : voice typing" },
    { "voice_typing_chooser", OPEN + " : voice typing : choose" },
    { "change_dictionary", OPEN + " : dictionary" },
    { "studio_open", OPEN + " : studio" },
    { "studio_open_layout_editor", OPEN + " : layout editor" },
    { "studio_open_keys", OPEN + " : keys and behaviours" },
    // Ctrl+Z works where Android's undo often does nothing: it gets the
    // plain name, Android's the qualified one.
    { "studio_undo", FUNCTION + " : undo" },
    { "studio_redo", FUNCTION + " : redo" },
    { "undo", FUNCTION + " : undo : android" },
    { "redo", FUNCTION + " : redo : android" },
    { "studio_cut", FUNCTION + " : cut" },
    { "cut", FUNCTION + " : cut : android" },
    { "hide_self", FUNCTION + " : hide keyboard" },
    { "action", FUNCTION + " : field action" },
  };

  /** Quicksbar actions whose quick name isn't the one of a key. */
  static final String[][] BAR = {
    { "layout_mode", EQUIP + " : layout current" },
    { "toggle_repeat", BEHAVIOUR + " : key behaviour : long press" },
    { "split_backspace", TOGGLE + " : layout : backspace" },
    { "dictionary", FUNCTION + " : suggestions language" },
    { "layout_menu", TOGGLE + " : layout : list" },
    { "layouts_save", EQUIP + " : arrangement : save" },
    { "layouts_pick", TOGGLE + " : arrangement : pick" },
    { "select_container", EDIT + " : focus" },
    { "height_up", EDIT + " : resize : +" },
    { "height_down", EDIT + " : resize : −" },
    { "move_up", EDIT + " : move : up" },
    { "move_down", EDIT + " : move : down" },
    { "height_undo", EDIT + " : undo" },
    { "height_revert", EDIT + " : undo all" },
    { "edit_menu", EDIT + " : menu" },
    { "studio", OPEN + " : studio" },
    { "keyboard_picker", OPEN + " : keyboards" },
  };

  private QuickNames() {}

  static Map<String, String> _keys, _bar;

  static synchronized Map<String, String> keys()
  {
    if (_keys == null)
    {
      _keys = new HashMap<String, String>();
      for (String[] p : KEYS) _keys.put(p[0], p[1]);
    }
    return _keys;
  }

  static synchronized Map<String, String> bar()
  {
    if (_bar == null)
    {
      _bar = new HashMap<String, String>();
      for (String[] p : BAR) _bar.put(p[0], p[1]);
    }
    return _bar;
  }

  /** Whether [name] is written as a quick name. Cheap: checked for every
      key name a layout has. */
  public static boolean is_quick(String name)
  {
    if (name.length() < 9)
      return false;
    char c = name.charAt(0);
    if (c != 'q' && c != 'p' && c != 'Q' && c != 'P')
      return false;
    for (String k : CATEGORIES)
      if (name.regionMatches(true, 0, k, 0, k.length()) && name.length() > k.length()
          && (name.charAt(k.length()) == ':' || name.charAt(k.length()) == ' '))
        return true;
    return false;
  }

  // ---- legacy -> quick ----

  /** The quick name of key [name] (a name in a layout), for Studio. */
  public static String of_key(String name)
  {
    if (name == null || name.isEmpty())
      return "";
    if (name.startsWith(LayoutDoc.LOC))
      return of_key(name.substring(LayoutDoc.LOC.length())) + " (for its languages only)";
    if (is_quick(name))
      return display(name);
    int t = name.indexOf(Behaviours.SEP);
    if (t > 0 && Behaviours.is_variant(name))
      return of_key(name.substring(0, t)) + " ~" + name.substring(t + Behaviours.SEP.length());
    if (name.startsWith(KeyValue.APP_PREFIX))
      return OPEN + " : app : " + name.substring(KeyValue.APP_PREFIX.length());
    if (name.startsWith(Behaviours.LAYOUT_PREFIX))
      return TOGGLE + " : layout : " + name.substring(Behaviours.LAYOUT_PREFIX.length());
    String q = keys().get(name);
    if (q != null)
      return q;
    if (name.startsWith("accent_"))
      return FUNCTION + " : accent : " + words(name.substring(7));
    if (name.startsWith("combining_"))
      return FUNCTION + " : combining : " + words(name.substring(10));
    KeyValue kv = KeyMap.key_of_name(name);
    if (kv != null && kv.getKind() == KeyValue.Kind.Placeholder)
      return name; // layout plumbing, not a quick
    // Spaces at the ends of a name would be lost between " : ": quoted.
    if (kv != null && !name.equals(name.trim()) && name.indexOf('\\') < 0 && !name.startsWith(":"))
      name = ":str:'" + name.replace("'", "\\'") + "'";
    // A letter's quick name carries its legacy name as written ("a", "nbsp",
    // "b(", "\\?"…), so it comes back to exactly the same key.
    if (kv != null && (kv.getKind() == KeyValue.Kind.Char || kv.getKind() == KeyValue.Kind.Hangul_initial))
      return FUNCTION + " : type letter : " + name;
    if (kv != null && kv.getKind() == KeyValue.Kind.String && !is_special(name))
    {
      String s = kv.getString();
      return FUNCTION + (s.codePointCount(0, s.length()) <= 2 ? " : type letter : " : " : type text : ") + name;
    }
    return FUNCTION + SEP + words(name);
  }

  /** The quick name of quicksbar action [action], for Studio. */
  public static String of_bar(String action)
  {
    if (action == null || action.isEmpty())
      return "";
    if (is_quick(action))
      return display(action);
    int t = action.indexOf(Behaviours.SEP);
    if (t > 0 && Behaviours.spec_of(action) != null)
      return of_bar(action.substring(0, t)) + " ~" + action.substring(t + Behaviours.SEP.length());
    String q = bar().get(action);
    if (q != null)
      return q;
    if (action.startsWith("switch:"))
      return TOGGLE + " : module : " + action.substring(7);
    String key = Quicksbar.key_name(action);
    return of_key(key != null ? key : action);
  }

  static boolean is_special(String name)
  {
    for (String n : KeyNames.SPECIAL)
      if (n.equals(name))
        return true;
    return false;
  }

  /** "studio_apply_look" -> "apply look", "pasteAsPlainText" -> "paste as plain text". */
  static String words(String n)
  {
    if (n.startsWith("studio_")) n = n.substring(7);
    else if (n.startsWith("il_")) n = n.substring(3);
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < n.length(); i++)
    {
      char c = n.charAt(i);
      if (c == '_') b.append(' ');
      else if (Character.isUpperCase(c) && i > 0 && Character.isLowerCase(n.charAt(i - 1)))
        b.append(' ').append(Character.toLowerCase(c));
      else b.append(c);
    }
    return b.toString();
  }

  // ---- quick -> legacy ----

  /** Segments of a quick name, trimmed: "quickfunction:copy" and
      "quickfunction : copy" are the same. */
  static List<String> segments(String q)
  {
    List<String> out = new ArrayList<String>();
    for (String s : q.split(":", -1))
      out.add(s.trim());
    return out;
  }

  /** How Studio writes a quick name: segments joined with " : ". */
  public static String display(String q)
  {
    int v = variant_at(q);
    String variant = "";
    if (v > 0) { variant = " ~" + q.substring(v + 1).trim(); q = q.substring(0, v); }
    StringBuilder b = new StringBuilder();
    for (String s : segments(q))
      b.append(b.length() == 0 ? "" : SEP).append(s);
    return b.append(variant).toString();
  }

  /** How a quick name is stored in a layout: no spaces around the colons. */
  public static String stored(String q)
  {
    return display(q).replace(SEP, ":").replace(" ~", "~");
  }

  static Map<String, String> _reverse_keys, _reverse_bar;

  static String norm(String q)
  {
    return stored(q).toLowerCase(Locale.ROOT);
  }

  /** Every special key's quick name, back to its legacy name. */
  static synchronized Map<String, String> reverse_keys()
  {
    if (_reverse_keys == null)
    {
      Map<String, String> m = new LinkedHashMap<String, String>();
      for (String n : KeyNames.SPECIAL)
        m.put(norm(of_key(n)), n);
      for (String[] p : KEYS)
        m.put(norm(p[1]), p[0]); // the explicit ones win
      _reverse_keys = m;
    }
    return _reverse_keys;
  }

  static synchronized Map<String, String> reverse_bar()
  {
    if (_reverse_bar == null)
    {
      Map<String, String> m = new HashMap<String, String>();
      for (String[] p : BAR) m.put(norm(p[1]), p[0]);
      _reverse_bar = m;
    }
    return _reverse_bar;
  }

  /** Where the behaviour variant ("~once") of [q] starts, -1 if none: a
      `~` followed by a behaviour, so typing "~" itself stays a letter. */
  static int variant_at(String q)
  {
    int v = q.lastIndexOf('~');
    if (v <= 0 || Behaviours.Spec.parse(q.substring(v + 1).trim()) == null)
      return -1;
    return v;
  }

  /** The legacy key name for quick name [q]; null if [q] names no key. */
  public static String to_key(String q)
  {
    String spec = null;
    int v = variant_at(q);
    if (v > 0) { spec = q.substring(v + 1).trim(); q = q.substring(0, v).trim(); }
    String base = to_key_base(q);
    if (base == null)
      return null;
    return spec == null ? base : base + Behaviours.SEP + spec;
  }

  static String to_key_base(String q)
  {
    List<String> s = segments(q);
    if (s.size() < 2)
      return null;
    String cat = s.get(0).toLowerCase(Locale.ROOT);
    String sub = s.get(1).toLowerCase(Locale.ROOT);
    // the parts whose case and characters matter come from [q] as written
    String rest = rest_after(q, 2);
    if (cat.equals(FUNCTION) && (sub.equals("type letter") || sub.equals("type text")) && rest != null)
      return rest; // the legacy name as written (see [of_key])
    if (cat.equals(OPEN) && sub.equals("app") && rest != null)
      return KeyValue.APP_PREFIX + rest;
    String known = reverse_keys().get(norm(q));
    if (known != null)
      return known;
    if (cat.equals(TOGGLE) && sub.equals("layout") && rest != null && LayoutDoc.valid_name(rest))
      return Behaviours.LAYOUT_PREFIX + rest;
    return null;
  }

  /** The legacy quicksbar action for quick name [q]; null if none. */
  public static String to_bar(String q)
  {
    String spec = null;
    int v = variant_at(q);
    if (v > 0) { spec = q.substring(v + 1).trim(); q = q.substring(0, v).trim(); }
    String base = reverse_bar().get(norm(q));
    if (base == null)
    {
      List<String> s = segments(q);
      if (s.size() >= 3 && s.get(0).equalsIgnoreCase(TOGGLE) && s.get(1).equalsIgnoreCase("module"))
        base = "switch:" + s.get(2);
      else
        base = to_key(q); // the quicksbar takes key names too
    }
    if (base == null)
      return null;
    return spec == null ? base : base + Behaviours.SEP + spec;
  }

  /** What follows the first [n] segments of [q], as written (trimmed). */
  static String rest_after(String q, int n)
  {
    int at = 0;
    for (int i = 0; i < n; i++)
    {
      at = q.indexOf(':', at);
      if (at < 0)
        return null;
      at++;
    }
    String r = q.substring(at).trim();
    return r.isEmpty() ? null : r;
  }

  /** The category of quick name [q] (display form), for grouping. */
  public static String category_of(String q)
  {
    int i = q.indexOf(':');
    return (i < 0 ? q : q.substring(0, i)).trim();
  }
}
