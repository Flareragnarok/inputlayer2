package juloo.keyboard2.studio;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import juloo.keyboard2.Config;
import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.Theme;

/** The edit strip, under the space bar, eight keys:
    - ←→: selection direction, forward or backward, for Sector and Scope.
    - Scope, View, Verse are nested: the nest shows on top, its current item
      at the bottom; tap cycles the items, swipe right the nests, swipe left
      lists everything, swipe down goes back to the default nest (Scope's
      down types in View's format until the scope's separator, e.g. a space
      for Word). View and Verse: swipe up restores the text.
    - The space bar's swipes keep nest configs: down resets every nest, left
      saves them as a config, up switches to the next saved config.
    - Scope: nests Text (Word, Sentence, Line, Paragraph, All) and Custom
      (custom selections).
    - Spec: opens Studio to define selections.
    - Sector (the selector): tap selects the scope's unit at the cursor,
      then jumps to the next (or previous) one; swipe up adds a unit, down
      removes one, on the side the ←→ arrow points to; right deselects.
    - View (format): nests are the syntaxes (the app's first, remembered
      per app), items their formats and custom formats.
    - Vert: opens Studio to define formats and fonts.
    - Verse (appearance): nests Case, Serif, Sans, Type, Shapes, Marks,
      Fonts.
    - ↑↓: cycling direction, up or down, for View and Verse.
    View and Verse act on the selection, or on the scope's unit at the
    cursor, and select the result so that the next tap cycles on. */
public class EditStrip extends LinearLayout
  implements ComfortKey.Host, Keyboard2View.BottomInsetTaker
{
  public interface Listener
  {
    /** [null] when not typing in a field. */
    InputConnection input();
    /** Package of the app being typed into, [null] if unknown. */
    String app();
    /** Absolute selection, as last reported by the editor. */
    int sel_start();
    int sel_end();
    void open_studio();
  }

  static final String KEY_SYNTAX_PREFIX = "studio_format_";
  /** Text read around the cursor when the editor doesn't give it all. */
  static final int CONTEXT_CHARS = 4000;

  /** Nested keys (Scope, View, Verse): the nest shows on top and its
      current item at the bottom. Tap cycles the items, swipe right the
      nests, swipe left lists everything, swipe down goes back to the
      default nest. Scope's down instead starts typing in View's format. */
  static final ComfortKeySpec SCOPE_KEY =
    ComfortKeySpec.of("c", "scope", "e", "scope_nest", "w", "scope_menu", "s", "scope_type");
  /** Designed around taps: tap jumps to the next unit, up adds one, down
      removes one, all in the ←→ direction. */
  static final ComfortKeySpec SELECTOR_KEY =
    ComfortKeySpec.of("c", "select", "n", "select_extend", "s", "select_shrink",
        "e", "deselect");
  static final ComfortKeySpec CUSTOM_SELECTION_KEY = ComfortKeySpec.of("c", "custom_selection");
  static final ComfortKeySpec SELECTION_DIRECTION_KEY = ComfortKeySpec.of("c", "selection_direction");
  static final ComfortKeySpec CYCLE_DIRECTION_KEY = ComfortKeySpec.of("c", "cycle_direction");
  static final ComfortKeySpec APPEARANCE_KEY =
    ComfortKeySpec.of("c", "appearance", "e", "appearance_nest",
        "w", "appearance_menu", "s", "appearance_default", "n", "appearance_restore");
  static final ComfortKeySpec FORMAT_KEY =
    ComfortKeySpec.of("c", "format", "e", "format_nest", "w", "format_menu",
        "s", "format_default", "n", "format_restore");

  static final String KEY_NEST_CONFIGS = "studio_nest_configs";
  static final String KEY_NEST_CONFIG_INDEX = "studio_nest_config_index";
  static final int DEFAULT_VERSE_NEST = 1;
  static final ComfortKeySpec CUSTOM_FORMAT_KEY = ComfortKeySpec.of("c", "custom_format");

  Listener _listener;
  SharedPreferences _prefs;
  Config _config;
  TextScope _scope = TextScope.WORD;
  /** Built-in scopes then custom selections. */
  List<TextScope> _scopes = TextScope.BUILTIN;
  /** Custom formats, { name, open, close }. */
  List<String[]> _custom_formats = new ArrayList<String[]>();
  /** Selection direction (←→): forward or backward, for Sector and Scope. */
  boolean _forward = true;
  /** Cycling direction (↑↓): up or down, for View and Verse. */
  boolean _up = true;
  /** Scope's nest: 0 for the built-in scopes, 1 for custom selections. */
  int _scope_nest = 0;
  /** Verse's nest, index into [UnicodeStyles.nests()], and the item last
      applied in it ([-1]: none yet). */
  int _verse_nest = DEFAULT_VERSE_NEST;
  int _verse_item = -1;
  /** Format View last applied, [null] for none or plain. */
  String _view_item = null;
  /** View's syntax when the app is unknown and the user picked one. */
  String _syntax_override = null;
  /** Typing in View's format (Scope swiped down): the closing marker the
      cursor sits before, [null] when not typing in a format. */
  String _typing_close = null;
  View _scope_key;
  View _appearance_key;
  View _format_key;
  /** Navigation bar height on Android 15+, kept clear below the keys. */
  int _bottom_inset = 0;

  public EditStrip(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER);
  }

  public void setup(Listener l, SharedPreferences prefs)
  {
    _listener = l;
    _prefs = prefs;
  }

  public void refresh(Config config)
  {
    _config = config;
    float dp = config.studio.edit_strip_dp;
    if (_listener == null || dp <= 0)
    {
      setVisibility(GONE);
      return;
    }
    load_custom();
    setVisibility(VISIBLE);
    ViewGroup.LayoutParams p = getLayoutParams();
    p.height = (int)(dp * density()) + _bottom_inset;
    setLayoutParams(p);
    setPadding(0, 0, 0, _bottom_inset);
    rebuild();
  }

  void rebuild()
  {
    removeAllViews();
    addView(make_key(SELECTION_DIRECTION_KEY, 1.0f));
    _scope_key = make_key(SCOPE_KEY, 1.6f);
    ((ComfortKey)_scope_key).set_lines(scope_nest_name(), _scope.label);
    addView(_scope_key);
    addView(make_key(CUSTOM_SELECTION_KEY, 1.3f));
    addView(make_key(SELECTOR_KEY, 1.5f));
    _format_key = make_key(FORMAT_KEY, 1.6f);
    ((ComfortKey)_format_key).set_lines(syntax().name,
        (_typing_close != null ? "✎ " : "") + ((_view_item == null) ? "plain" : _view_item));
    _appearance_key = make_key(APPEARANCE_KEY, 1.6f);
    String[] vn = verse_nest();
    ((ComfortKey)_appearance_key).set_lines(vn[0],
        verse_item_label(vn, Math.max(0, _verse_item)));
    // View formats, Vert defines formats and fonts, Verse changes the look.
    addView(_format_key);
    addView(make_key(CUSTOM_FORMAT_KEY, 1.3f));
    addView(_appearance_key);
    addView(make_key(CYCLE_DIRECTION_KEY, 1.0f));
  }

  /** Custom selections, formats and fonts, from their modules. */
  void load_custom()
  {
    ModuleStore store = new ModuleStore(_prefs);
    List<TextScope> scopes = new ArrayList<TextScope>(TextScope.BUILTIN);
    for (Module m : store.list("selection"))
    {
      TextScope s = CustomTools.selection(m);
      if (s != null)
        scopes.add(s);
    }
    _scopes = scopes;
    TextScope current = null;
    for (TextScope s : scopes)
      if (s.label.equals(_scope.label))
        current = s;
    _scope = (current == null) ? TextScope.WORD : current;
    List<String[]> formats = new ArrayList<String[]>();
    for (Module m : store.list("format"))
    {
      String[] f = CustomTools.format(m);
      if (f != null)
        formats.add(f);
    }
    _custom_formats = formats;
    List<UnicodeStyles.Font> fonts = new ArrayList<UnicodeStyles.Font>();
    for (Module m : store.list("font"))
    {
      UnicodeStyles.Font f = CustomTools.font(m);
      if (f != null)
        fonts.add(f);
    }
    UnicodeStyles.set_fonts(fonts);
  }

  View make_key(ComfortKeySpec s, float width)
  {
    View k = new ComfortKey(getContext(), s, this, Theme.getKeyFont(getContext()));
    int h = (int)(_config.studio.edit_strip_dp * density());
    LayoutParams p = new LayoutParams((int)(h * width), h - dp(4));
    p.setMargins(dp(3), dp(2), dp(3), dp(2));
    k.setLayoutParams(p);
    return k;
  }

  /* Keyboard2View.BottomInsetTaker */

  public boolean takes_bottom_inset() { return getVisibility() == VISIBLE; }

  public void set_bottom_inset(int px)
  {
    if (px == _bottom_inset)
      return;
    _bottom_inset = px;
    if (_config != null)
      refresh(_config);
  }

  /* ComfortKey.Host */

  public void perform(String action)
  {
    switch (action)
    {
      case "scope":
        _scope = TextScope.cycle(scope_nest_items(), _scope, _forward);
        rebuild();
        return;
      case "scope_nest":
        _scope_nest = (_scope_nest + 1) % scope_nest_count();
        _scope = scope_nest_items().get(0);
        rebuild();
        return;
      case "scope_menu":
        scope_menu();
        return;
      case "scope_type":
        start_typing();
        return;
      case "appearance_default":
        _verse_nest = DEFAULT_VERSE_NEST;
        _verse_item = -1;
        rebuild();
        return;
      case "format_default":
        default_syntax();
        rebuild();
        return;
      case "appearance_nest":
        _verse_nest = (_verse_nest + 1) % UnicodeStyles.nests().size();
        _verse_item = -1;
        rebuild();
        return;
      case "format_nest":
        next_syntax();
        return;
      case "selection_direction":
        _forward = !_forward;
        rebuild();
        return;
      case "cycle_direction":
        _up = !_up;
        rebuild();
        return;
      case "custom_selection":
      case "custom_format":
        _listener.open_studio();
        return;
      case "appearance": cycle_appearance(); return;
      case "appearance_menu": appearance_menu(); return;
      case "appearance_restore": restore_appearance(); return;
      case "format": cycle_format(); return;
      case "format_menu": format_menu(); return;
      case "format_restore": restore_format(); return;
    }
    Field f = read();
    if (f == null)
      return;
    int[] sel = { f.sel_start, f.sel_end };
    int[] r = null;
    switch (action)
    {
      case "select":
        // First the unit at the cursor, then the next ones.
        if (!has_selection(f))
          r = _scope.unit_at(f.text, f.sel_start);
        else
          r = _forward ? _scope.next_unit(f.text, f.sel_end)
            : _scope.prev_unit(f.text, f.sel_start);
        break;
      case "select_extend":
        r = has_selection(f) ? _scope.extend(f.text, sel, _forward)
          : _scope.unit_at(f.text, f.sel_start);
        break;
      case "select_shrink":
        r = _scope.shrink(f.text, sel, _forward);
        break;
      case "deselect":
        r = new int[]{ f.sel_end, f.sel_end };
        break;
    }
    if (r != null)
      f.ic.setSelection(f.base + r[0], f.base + r[1]);
  }

  public ComfortKey.Label label_of(String action)
  {
    switch (action)
    {
      case "scope": return new ComfortKey.Label("Scope", false);
      case "scope_menu": return new ComfortKey.Label("☰", false);
      case "deselect": return new ComfortKey.Label("×", false);
      case "select": return new ComfortKey.Label("Sector", false);
      case "select_extend": return new ComfortKey.Label("+", false);
      case "select_shrink": return new ComfortKey.Label("−", false);
      case "custom_selection": return new ComfortKey.Label("Spec", false);
      case "selection_direction": return new ComfortKey.Label(_forward ? "→" : "←", false);
      case "cycle_direction": return new ComfortKey.Label(_up ? "↑" : "↓", false);
      case "appearance": return new ComfortKey.Label("Verse", false);
      case "appearance_menu": return new ComfortKey.Label("☰", false);
      case "appearance_nest": case "format_nest": case "scope_nest":
        return new ComfortKey.Label("›", false);
      case "appearance_default": case "format_default":
        return new ComfortKey.Label("⌂", false);
      case "scope_type": return new ComfortKey.Label("✎", false);
      case "appearance_restore": return new ComfortKey.Label("⟲", false);
      case "format_restore": return new ComfortKey.Label("⟲", false);
      case "format": return new ComfortKey.Label("View", false);
      case "format_menu": return new ComfortKey.Label("☰", false);
      case "custom_format": return new ComfortKey.Label("Vert", false);
      default: return new ComfortKey.Label("?", false);
    }
  }

  public float swipe_dist_px()
  {
    return Math.max(dp(8), _config.swipe_dist_px / 2);
  }

  public long repeat_delay() { return _config.longPressTimeout; }

  public long repeat_interval() { return _config.longPressInterval; }

  /* Defaults and nest configs (space bar swipes) */

  /** View's syntax back to the app's own. */
  void default_syntax()
  {
    String app = _listener.app();
    if (app != null)
      _prefs.edit().remove(KEY_SYNTAX_PREFIX + app).apply();
    _syntax_override = null;
    _view_item = null;
  }

  /** Space bar down: every nest back to its default. */
  public void reset_nests()
  {
    _scope_nest = 0;
    _scope = TextScope.WORD;
    default_syntax();
    _verse_nest = DEFAULT_VERSE_NEST;
    _verse_item = -1;
    rebuild();
    toast("Nests reset");
  }

  JSONObject nest_config()
  {
    JSONObject j = new JSONObject();
    try
    {
      j.put("scope_nest", _scope_nest);
      j.put("scope", _scope.label);
      j.put("view_nest", syntax().name);
      j.put("view_item", (_view_item == null) ? "" : _view_item);
      j.put("verse_nest", _verse_nest);
      j.put("verse_item", _verse_item);
    }
    catch (Exception _e) {}
    return j;
  }

  void apply_nest_config(JSONObject j)
  {
    _scope_nest = j.optInt("scope_nest", 0);
    _scope = TextScope.WORD;
    for (TextScope s : _scopes)
      if (s.label.equals(j.optString("scope")))
        _scope = s;
    String syntax = j.optString("view_nest", "");
    String app = _listener.app();
    if (!syntax.isEmpty())
    {
      if (app != null)
        _prefs.edit().putString(KEY_SYNTAX_PREFIX + app, syntax).apply();
      else
        _syntax_override = syntax;
    }
    String vi = j.optString("view_item", "");
    _view_item = vi.isEmpty() ? null : vi;
    _verse_nest = j.optInt("verse_nest", DEFAULT_VERSE_NEST);
    _verse_item = j.optInt("verse_item", -1);
    rebuild();
  }

  JSONArray nest_configs()
  {
    try
    {
      return new JSONArray(_prefs.getString(KEY_NEST_CONFIGS, "[]"));
    }
    catch (Exception _e)
    {
      return new JSONArray();
    }
  }

  /** Space bar left: save the nests (scope, view and verse nests and
      items) as a config, unless the same one is saved already. */
  public void save_nest_config()
  {
    JSONArray cs = nest_configs();
    String cur = nest_config().toString();
    int index = -1;
    for (int i = 0; i < cs.length(); i++)
    {
      JSONObject o = cs.optJSONObject(i);
      if (o != null && cur.equals(o.toString()))
        index = i;
    }
    if (index < 0)
    {
      cs.put(nest_config());
      index = cs.length() - 1;
    }
    _prefs.edit().putString(KEY_NEST_CONFIGS, cs.toString())
      .putInt(KEY_NEST_CONFIG_INDEX, index).apply();
    toast("Nest config " + (index + 1) + " of " + cs.length() + " saved");
  }

  /** Space bar up: the next saved config. */
  public void next_nest_config()
  {
    JSONArray cs = nest_configs();
    if (cs.length() == 0)
    {
      toast("No nest config saved: swipe left on space to save one");
      return;
    }
    int i = (_prefs.getInt(KEY_NEST_CONFIG_INDEX, -1) + 1) % cs.length();
    _prefs.edit().putInt(KEY_NEST_CONFIG_INDEX, i).apply();
    JSONObject j = cs.optJSONObject(i);
    if (j == null)
      return;
    apply_nest_config(j);
    toast("Nest config " + (i + 1) + " of " + cs.length() + ": " + _scope.label + ", "
        + syntax().name + " " + ((_view_item == null) ? "plain" : _view_item) + ", "
        + verse_nest()[0]);
  }

  /* Typing in View's format (Scope swiped down) */

  /** The format View last applied, or the first one of the syntax. */
  String[] view_format()
  {
    List<String[]> fs = formats();
    for (String[] f : fs)
      if (f[0].equals(_view_item))
        return f;
    return fs.isEmpty() ? null : fs.get(0);
  }

  /** Insert the format's markers and type between them, until the scope's
      separator (a space for Word) closes them. */
  void start_typing()
  {
    String[] f = view_format();
    InputConnection ic = _listener.input();
    if (f == null || ic == null)
    {
      toast("No format in this app: pick a syntax on View");
      return;
    }
    _typing_close = null;
    ic.beginBatchEdit();
    ic.commitText(f[1], 1);
    if (!f[2].isEmpty())
      ic.commitText(f[2], 0); // Cursor before the closing marker.
    ic.endBatchEdit();
    _view_item = f[0];
    _typing_close = f[2].isEmpty() ? null : f[2];
    rebuild();
  }

  /** The editor moved the cursor: close the format when the scope's
      separator was just typed, stop when the cursor left the markers. */
  public void selection_updated(int start, int end)
  {
    String close = _typing_close;
    InputConnection ic = (close == null) ? null : _listener.input();
    if (ic == null)
      return;
    CharSequence after = ic.getTextAfterCursor(close.length(), 0);
    if (start != end || after == null || !after.toString().equals(close))
    {
      _typing_close = null;
      rebuild();
      return;
    }
    CharSequence before = ic.getTextBeforeCursor(1, 0);
    if (before == null || before.length() == 0 || !is_separator(before.charAt(0)))
      return;
    _typing_close = null;
    ic.beginBatchEdit();
    ic.deleteSurroundingText(1, close.length());
    ic.commitText(close + before, 1);
    ic.endBatchEdit();
    rebuild();
  }

  boolean is_separator(char c)
  {
    if (_scope == TextScope.ALL)
      return false;
    if (_scope == TextScope.LINE || _scope == TextScope.PARAGRAPH)
      return c == '\n';
    if (_scope == TextScope.SENTENCE)
      return c == '.' || c == '!' || c == '?' || c == '\n';
    return Character.isWhitespace(c);
  }

  void toast(String msg)
  {
    android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
  }

  /* Scope */

  /** Built-in scopes, then custom selections when there are some. */
  int scope_nest_count()
  {
    return (_scopes.size() > TextScope.BUILTIN.size()) ? 2 : 1;
  }

  String scope_nest_name()
  {
    return (_scope_nest == 0) ? "Text" : "Custom";
  }

  List<TextScope> scope_nest_items()
  {
    if (_scope_nest >= scope_nest_count())
      _scope_nest = 0;
    if (_scope_nest == 0)
      return TextScope.BUILTIN;
    return _scopes.subList(TextScope.BUILTIN.size(), _scopes.size());
  }

  void scope_menu()
  {
    PopupMenu p = new PopupMenu(getContext(), _scope_key);
    for (final TextScope s : _scopes)
      add(p.getMenu(), (s == _scope ? "● " : "") + s.label, new Runnable() {
        public void run()
        {
          _scope = s;
          _scope_nest = TextScope.BUILTIN.contains(s) ? 0 : 1;
          rebuild();
        }
      });
    p.show();
  }

  /* Appearance (Verse) */

  String[] verse_nest()
  {
    List<String[]> ns = UnicodeStyles.nests();
    if (_verse_nest >= ns.size())
      _verse_nest = 1;
    return ns.get(_verse_nest);
  }

  static String verse_item_label(String[] nest, int i)
  {
    String item = nest[1 + Math.min(i, nest.length - 2)];
    return nest[0].equals("Case") ? case_label(item) : UnicodeStyles.label(item);
  }

  /** Next item of the nest: after the target's current style in this nest,
      the first one (last one going down) otherwise. Case items cycle on
      their own, a text's case not being a style. */
  void cycle_appearance()
  {
    final String[] nest = verse_nest();
    final int n = nest.length - 1;
    final boolean is_case = nest[0].equals("Case");
    transform_target(new Transform() {
      public String apply(String s)
      {
        int k = -1;
        if (is_case)
          k = _verse_item;
        else
        {
          String st = UnicodeStyles.style_of(s);
          for (int i = 1; i < nest.length; i++)
            if (nest[i].equals(st))
              k = i - 1;
        }
        int next = (k < 0) ? (_up ? 0 : n - 1) : (k + (_up ? 1 : n - 1)) % n;
        _verse_item = next;
        String item = nest[1 + next];
        return is_case ? UnicodeStyles.change_case(s, item) : UnicodeStyles.apply(s, item);
      }
    });
    rebuild();
  }

  /** Back to plain letters; the case stays as it is. */
  void restore_appearance()
  {
    transform_target(new Transform() {
      public String apply(String s) { return UnicodeStyles.apply(s, UnicodeStyles.PLAIN); }
    });
  }

  void appearance_menu()
  {
    PopupMenu p = new PopupMenu(getContext(), _appearance_key);
    Menu m = p.getMenu();
    SubMenu cases = m.addSubMenu("Aa  Case");
    for (final String c : UnicodeStyles.CASES)
      add(cases, case_label(c), new Runnable() {
        public void run() { transform_target(new Transform() {
          public String apply(String s) { return UnicodeStyles.change_case(s, c); }
        }); }
      });
    for (final String st : UnicodeStyles.styles())
      add(m, UnicodeStyles.label(st), new Runnable() {
        public void run() { transform_target(new Transform() {
          public String apply(String s) { return UnicodeStyles.apply(s, st); }
        }); }
      });
    p.show();
  }

  static String case_label(String c)
  {
    switch (c)
    {
      case "upper": return "UPPER";
      case "lower": return "lower";
      case "title": return "Title Case";
      default: return "tOGGLE cASE";
    }
  }

  /* Format */

  TextFormats.Syntax syntax()
  {
    String app = _listener.app();
    return TextFormats.for_app(app,
        (app == null) ? _syntax_override : _prefs.getString(KEY_SYNTAX_PREFIX + app, null));
  }

  /** View's nests are the syntaxes; the one picked is remembered for the
      app. */
  void next_syntax()
  {
    List<String> names = TextFormats.syntax_names();
    int i = names.indexOf(syntax().name);
    String next = names.get((i + 1) % names.size());
    String app = _listener.app();
    if (app != null)
      _prefs.edit().putString(KEY_SYNTAX_PREFIX + app, next).apply();
    else
      _syntax_override = next;
    _view_item = null;
    rebuild();
  }

  List<String[]> formats()
  {
    return TextFormats.cycle_list(syntax(), _custom_formats);
  }

  void cycle_format()
  {
    List<String[]> fs = formats();
    if (fs.isEmpty())
    {
      // No markup in this app: the unicode styles look formatted anywhere.
      cycle_appearance();
      return;
    }
    Field f = read();
    if (f == null)
      return;
    int[] r = target(f);
    if (r == null)
      r = new int[]{ f.sel_start, f.sel_start };
    TextFormats.Edit e = TextFormats.cycle(f.text, r, fs, _up);
    replace(f, e.start, e.end, e.text, e.sel_start, e.sel_end);
    _view_item = e.name;
    rebuild();
  }

  void restore_format()
  {
    Field f = read();
    if (f == null)
      return;
    int[] r = target(f);
    if (r == null)
      return;
    TextFormats.Edit e = TextFormats.unwrap(f.text, r, formats());
    if (e != null)
      replace(f, e.start, e.end, e.text, e.sel_start, e.sel_end);
    _view_item = null;
    rebuild();
  }

  void format_menu()
  {
    final String app = _listener.app();
    final TextFormats.Syntax syntax = syntax();
    PopupMenu p = new PopupMenu(getContext(), _format_key);
    Menu m = p.getMenu();
    SubMenu choose = m.addSubMenu("Syntax: " + syntax.name + " ▸");
    for (final String name : TextFormats.syntax_names())
      add(choose, name, new Runnable() {
        public void run()
        {
          if (app != null)
            _prefs.edit().putString(KEY_SYNTAX_PREFIX + app, name).apply();
          else
            _syntax_override = name;
          _view_item = null;
          rebuild();
          format_menu();
        }
      });
    if (syntax.formats.isEmpty())
      add(m, "No markup in this app: View cycles Verse's styles", new Runnable() {
        public void run() { appearance_menu(); }
      });
    for (final String[] fm : TextFormats.cycle_list(syntax, _custom_formats))
      add(m, fm[0], new Runnable() {
        public void run()
        {
          format_target(new String[]{ fm[1], fm[2] });
          _view_item = fm[0];
          rebuild();
        }
      });
    p.show();
  }

  void format_target(String[] markers)
  {
    Field f = read();
    if (f == null)
      return;
    int[] r = target(f);
    if (r == null)
      // Nothing to wrap: insert the markers around the cursor.
      r = new int[]{ f.sel_start, f.sel_start };
    TextFormats.Edit e = TextFormats.toggle(f.text, r, markers);
    replace(f, e.start, e.end, e.text, e.sel_start, e.sel_end);
  }

  /* Editing */

  interface Transform { String apply(String s); }

  /** Apply [t] to the selection, or to the scope's unit at the cursor, and
      select the result. */
  void transform_target(Transform t)
  {
    Field f = read();
    if (f == null)
      return;
    int[] r = target(f);
    if (r == null)
      return;
    String n = t.apply(f.text.substring(r[0], r[1]));
    replace(f, r[0], r[1], n, r[0], r[0] + n.length());
  }

  int[] target(Field f)
  {
    if (has_selection(f))
      return new int[]{ f.sel_start, f.sel_end };
    return _scope.unit_at(f.text, f.sel_start);
  }

  void replace(Field f, int start, int end, String text, int sel_start, int sel_end)
  {
    InputConnection ic = f.ic;
    ic.beginBatchEdit();
    ic.setSelection(f.base + start, f.base + end);
    ic.commitText(text, 1);
    ic.setSelection(f.base + sel_start, f.base + sel_end);
    ic.endBatchEdit();
  }

  /* Reading the field */

  /** The field's text around the cursor. [base] is the absolute offset of
      [text]; selections are relative to [text]. */
  static final class Field
  {
    InputConnection ic;
    String text;
    int base;
    int sel_start, sel_end;
  }

  static boolean has_selection(Field f) { return f.sel_end > f.sel_start; }

  Field read()
  {
    InputConnection ic = _listener.input();
    if (ic == null)
      return null;
    Field f = new Field();
    f.ic = ic;
    ExtractedTextRequest req = new ExtractedTextRequest();
    req.hintMaxChars = 100000;
    ExtractedText et = ic.getExtractedText(req, 0);
    if (et != null && et.text != null && et.partialStartOffset < 0)
    {
      f.text = et.text.toString();
      f.base = et.startOffset;
      f.sel_start = Math.min(et.selectionStart, et.selectionEnd);
      f.sel_end = Math.max(et.selectionStart, et.selectionEnd);
    }
    else
    {
      CharSequence before = ic.getTextBeforeCursor(CONTEXT_CHARS, 0);
      CharSequence sel = ic.getSelectedText(0);
      CharSequence after = ic.getTextAfterCursor(CONTEXT_CHARS, 0);
      String b = (before == null) ? "" : before.toString();
      String s = (sel == null) ? "" : sel.toString();
      f.text = b + s + (after == null ? "" : after.toString());
      f.sel_start = b.length();
      f.sel_end = b.length() + s.length();
      f.base = Math.min(_listener.sel_start(), _listener.sel_end()) - b.length();
      if (f.base < 0)
        return null;
    }
    if (f.sel_start < 0 || f.sel_end > f.text.length())
      return null;
    return f;
  }

  static void add(Menu m, String title, final Runnable r)
  {
    m.add(title).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem i) { r.run(); return true; }
    });
  }

  float density()
  {
    return getResources().getDisplayMetrics().density;
  }

  int dp(int v)
  {
    return (int)(v * density());
  }
}
