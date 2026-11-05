package juloo.keyboard2.studio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONException;
import org.json.JSONObject;

/** How the keys that switch something behave (owner, 2026-11-06: "make
    multiple options for each of our predefined keys per possible usecase,
    e.g. repeat: switch back to repeat after calling once").

    A switching key (key repeat, backspace direction, a layout page, edit
    mode…) can stay switched until pressed again (toggle), switch back after
    one use (what one use is depends on the key: one key edit, one delete,
    one key typed), at the end of the word, or after some seconds. Each key
    has a default (Studio → Keys); a variant name `<key>~once`, `~word`,
    `~10s`, `~toggle` places that behaviour on a key whatever the default.

    The keyboard tells this class what happens ([switched], [edited],
    [typed]); it calls [Host.revert] when a key must switch back. */
public final class Behaviours
{
  public enum Mode { TOGGLE, ONCE, WORD, TIMED }

  /** What one use of a switched key is. */
  public enum Use { EDIT, DELETE, KEY }

  public static final String SEP = "~";
  /** il-006: `layout:<name>` shows a layout made in the layout editor. */
  public static final String LAYOUT_PREFIX = "layout:";
  public static final String PREF = "studio_behaviours";

  public static final class Spec
  {
    public final Mode mode;
    /** For [Mode.TIMED]. */
    public final int seconds;

    public Spec(Mode m, int s) { mode = m; seconds = s; }

    public static final Spec TOGGLE = new Spec(Mode.TOGGLE, 0);

    /** "toggle", "once", "word", "<n>s"; null if none of these. */
    public static Spec parse(String s)
    {
      if (s == null)
        return null;
      switch (s)
      {
        case "toggle": return TOGGLE;
        case "once": return new Spec(Mode.ONCE, 0);
        case "word": return new Spec(Mode.WORD, 0);
      }
      if (s.matches("[1-9][0-9]{0,3}s"))
        return new Spec(Mode.TIMED, Integer.parseInt(s.substring(0, s.length() - 1)));
      return null;
    }

    public String code()
    {
      switch (mode)
      {
        case ONCE: return "once";
        case WORD: return "word";
        case TIMED: return seconds + "s";
        default: return "toggle";
      }
    }

    /** The small mark on a variant key's label. */
    public String mark()
    {
      switch (mode)
      {
        case ONCE: return "¹";
        case WORD: return "ʷ";
        case TIMED: return "⏱";
        default: return "";
      }
    }

    @Override public boolean equals(Object o)
    {
      return o instanceof Spec && ((Spec)o).mode == mode && ((Spec)o).seconds == seconds;
    }

    @Override public int hashCode() { return mode.hashCode() * 31 + seconds; }
  }

  /** A key that has behaviours. */
  public static final class Capable
  {
    public final String name;
    public final Use use;
    /** It shows a layout page; switching back means back to the letters. */
    public final boolean page;
    /** A quicksbar action (not a key of the layouts). */
    public final boolean quicksbar;
    public final String what;

    Capable(String n, Use u, boolean p, boolean c, String w)
    {
      name = n; use = u; page = p; quicksbar = c; what = w;
    }

    /** What "one use" is, for the screen. */
    public String use_text()
    {
      switch (use)
      {
        case EDIT: return "after one key edit";
        case DELETE: return "after one delete";
        default: return page ? "after one key typed on it" : "after one key typed";
      }
    }
  }

  static final List<Capable> CAPABLE = Collections.unmodifiableList(Arrays.asList(
      new Capable("studio_toggle_repeat", Use.EDIT, false, false,
        "Key repeat on/off. Off: holding a key opens its editor."),
      new Capable("studio_backspace_toggle", Use.DELETE, false, false,
        "Backspace deletes before or after the cursor."),
      new Capable("switch_numeric", Use.KEY, true, false, "The number page."),
      new Capable("il_symbols", Use.KEY, true, false, "The ?123 symbols page."),
      new Capable("il_symbols_more", Use.KEY, true, false, "The =\\< symbols page."),
      new Capable("switch_greekmath", Use.KEY, true, false, "The Greek and math page."),
      new Capable("toggle_repeat", Use.EDIT, false, true,
        "Quicksbar: key repeat on/off (the quickequip's ↓). Same switch as studio_toggle_repeat."),
      new Capable("split_backspace", Use.DELETE, false, true,
        "Quicksbar: backspace split into ⌫ ⌦, or whole (the quickequip's ↗)."),
      new Capable("layout_mode", Use.EDIT, false, true,
        "Quicksbar: edit mode, tapping a key opens its editor (the quickequip's tap).")));

  public static List<Capable> capable() { return CAPABLE; }

  public static Capable capable(String base)
  {
    for (Capable c : CAPABLE)
      if (c.name.equals(base))
        return c;
    if (base.startsWith(LAYOUT_PREFIX) && LayoutDoc.valid_name(base.substring(LAYOUT_PREFIX.length())))
      return new Capable(base, Use.KEY, true, false,
          "Your layout \"" + base.substring(LAYOUT_PREFIX.length()) + "\" (Studio → Layout editor).");
    return null;
  }

  /** The quicksbar action and the key share one switch, so one default. */
  static String switch_of(String base)
  {
    return base.equals("toggle_repeat") ? "studio_toggle_repeat" : base;
  }

  public static String base_of(String name)
  {
    int i = name.indexOf(SEP);
    return i < 0 ? name : name.substring(0, i);
  }

  /** The variant's spec, null if [name] has none (or a bad one). */
  public static Spec spec_of(String name)
  {
    int i = name.indexOf(SEP);
    return i < 0 ? null : Spec.parse(name.substring(i + SEP.length()));
  }

  /** "<base>~<spec>" if [base] has behaviours and [spec] parses. */
  public static boolean is_variant(String name)
  {
    int i = name.indexOf(SEP);
    return i > 0 && capable(name.substring(0, i)) != null && spec_of(name) != null;
  }

  // ---- defaults (Studio → Keys) ----

  public static Spec default_of(JSONObject defaults, String base)
  {
    Spec s = (defaults == null) ? null : Spec.parse(defaults.optString(switch_of(base), null));
    return s == null ? Spec.TOGGLE : s;
  }

  public static JSONObject with_default(JSONObject defaults, String base, Spec s) throws JSONException
  {
    JSONObject o = new JSONObject(defaults == null ? "{}" : defaults.toString());
    if (s == null || s.mode == Mode.TOGGLE)
      o.remove(switch_of(base));
    else
      o.put(switch_of(base), s.code());
    return o;
  }

  public static JSONObject parse_defaults(String json)
  {
    try { return new JSONObject(json == null ? "{}" : json); }
    catch (JSONException e) { return new JSONObject(); }
  }

  // ---- what happens on the keyboard ----

  public interface Host
  {
    /** Switch [base] back (the keyboard's own toggle, or back to the
        letters for a page). */
    void revert(String base);
    /** Run [r] in [ms], cancellable. */
    Object later(Runnable r, long ms);
    void cancel(Object handle);
  }

  static final class Armed
  {
    final String sw;
    final Capable cap;
    final Mode mode;
    Object timer;
    Armed(String s, Capable c, Mode m) { sw = s; cap = c; mode = m; }
  }

  final Host _host;
  final Map<String, Armed> _armed = new LinkedHashMap<String, Armed>();

  public Behaviours(Host host) { _host = host; }

  /** [base] was just switched by a key with [spec]. Pressing a switched
      key again while it waits to switch back is the manual way back. */
  public void switched(String base, Spec spec)
  {
    Capable cap = capable(base);
    if (cap == null)
      return;
    String sw = switch_of(base);
    Armed was = _armed.remove(sw);
    if (was != null)
    {
      cancel(was);
      if (!cap.page)
        return; // that press switched it back
    }
    if (cap.page)
      left_page(); // another page: the previous one's way back is moot
    if (spec == null || spec.mode == Mode.TOGGLE)
      return;
    final Armed a = new Armed(sw, cap, spec.mode);
    _armed.put(sw, a);
    if (spec.mode == Mode.TIMED)
      a.timer = _host.later(new Runnable() {
        public void run() { if (_armed.get(a.sw) == a) revert(a); }
      }, spec.seconds * 1000L);
  }

  /** The keyboard went back to the letters by itself (ABC key…): pages
      waiting to switch back don't need to. */
  public void left_page()
  {
    for (Iterator<Armed> it = _armed.values().iterator(); it.hasNext(); )
    {
      Armed a = it.next();
      if (a.cap.page)
      {
        cancel(a);
        it.remove();
      }
    }
  }

  /** A key editor was opened (hold, or a tap in edit mode). */
  public void edited() { used(Use.EDIT, false); }

  /** A key that types or edits text was used. */
  public void typed(boolean is_delete, boolean ends_word)
  {
    if (is_delete)
      used(Use.DELETE, ends_word);
    used(Use.KEY, ends_word);
  }

  void used(Use u, boolean ends_word)
  {
    List<Armed> due = new ArrayList<Armed>();
    for (Armed a : _armed.values())
      if ((a.mode == Mode.ONCE && a.cap.use == u) || (a.mode == Mode.WORD && ends_word && u == Use.KEY))
        due.add(a);
    for (Armed a : due)
      revert(a);
  }

  void revert(Armed a)
  {
    _armed.remove(a.sw);
    cancel(a);
    _host.revert(a.sw);
  }

  void cancel(Armed a)
  {
    if (a.timer != null)
      _host.cancel(a.timer);
    a.timer = null;
  }

  /** Switches waiting to switch back, for tests and the screen. */
  public List<String> waiting() { return new ArrayList<String>(_armed.keySet()); }

  /** Whether [c] ends a word for [Mode.WORD]. */
  public static boolean ends_word(String text)
  {
    return text != null && text.length() > 0
      && " \n\t.,;:!?".indexOf(text.charAt(text.length() - 1)) >= 0;
  }

}
