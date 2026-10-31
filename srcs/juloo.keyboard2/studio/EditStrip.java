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
      lists everything, swipe down goes back to the default nest. Scope's up
      types in View's format and Verse's look until the scope's separator
      (e.g. a space for Word), previewed in the selection bar. View and
      Verse: swipe up restores the text.
    - The space bar's swipes keep nest configs: down resets every nest, left
      saves them as a config, up switches to the next saved config.
    - Scope: nests Text (Word, Sentence, Line, Paragraph, All) and Custom
      (custom selections).
    - Spec: opens Studio to define selections.
    - Sector (the selector): tap selects the scope's unit at the cursor,
      then jumps to the next (or previous) one in the ←→ direction; left
      and right move the cursor (or the selection) by a unit; up selects
      or deselects; down switches exclusive/inclusive, shown at the bottom.
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
    /** The pinned selection changed, [null] when unpinned. */
    void pinned(String text);
    /** What typing in format produces, shown in the selection bar; [null]
        when not typing in format. */
    void preview(String text);
  }

  static final String KEY_SYNTAX_PREFIX = "studio_format_";
  /** Text read around the cursor when the editor doesn't give it all. */
  static final int CONTEXT_CHARS = 4000;

  /** Nested keys (Scope, View, Verse): the nest shows on top and its
      current item at the bottom. Tap cycles the items, swipe right the
      nests, swipe left lists everything, swipe down goes back to the
      default nest. Scope's down instead starts typing in View's format. */
  static final ComfortKeySpec SCOPE_KEY =
    ComfortKeySpec.of("c", "scope", "e", "scope_nest", "w", "scope_menu",
        "s", "scope_default", "n", "scope_type");
  /** Designed around taps: tap jumps to the next unit in the ←→
      direction; left and right move the cursor by a unit without
      selecting; up selects the unit at the cursor or deselects; down
      switches between exclusive and inclusive selection. */
  static final ComfortKeySpec SELECTOR_KEY =
    ComfortKeySpec.of("c", "select", "w", "cursor_prev", "e", "cursor_next",
        "n", "select_toggle", "s", "inclusive_toggle");
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
  /** Pinned selection (Sector up-left): its text and absolute start, kept
      while the cursor types elsewhere. View, Verse and cut act on it.
      [null] when nothing is pinned. */
  String _pin_text = null;
  int _pin_start = 0;
  /** The current View/Verse edit acts on the pinned text. */
  boolean _target_is_pin = false;

  /** Selections include what surrounds a unit (trailing space,
      delimiters). Switched by swiping down on Sector. */
  boolean _inclusive = false;
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
  /** Typing in View's format and Verse's look (Scope swiped up). */
  boolean _typing = false;
  /** Closing marker the cursor sits before, "" for none. */
  String _typing_close = "";
  /** Absolute start of the typed text, after the opening marker. */
  int _typing_start = 0;
  /** Opening marker, for the selection bar's preview. */
  String _typing_open = "";
  /** Verse's item applied to the typed text, [null] for none. */
  String _typing_style = null;
  boolean _typing_case = false;
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
    if (_typing)
    {
      // Typing in format: say so where it was started.
      ((ComfortKey)_scope_key).set_lines("✎ typing", typing_summary());
      ((ComfortKey)_scope_key)._highlight = true;
    }
    else
      ((ComfortKey)_scope_key).set_lines(scope_nest_name(), _scope.label);
    addView(_scope_key);
    addView(make_key(CUSTOM_SELECTION_KEY, 1.3f));
    View sector = make_key(SELECTOR_KEY, 1.5f);
    // The selection type sits at the bottom, by the down swipe switching it.
    ((ComfortKey)sector).set_lines("Sector", _inclusive ? "incl" : "excl");
    addView(sector);
    _format_key = make_key(FORMAT_KEY, 1.6f);
    ((ComfortKey)_format_key).set_lines(syntax().name,
        (_typing ? "✎ " : "") + ((_view_item == null) ? "plain" : _view_item));
    _appearance_key = make_key(APPEARANCE_KEY, 1.6f);
    String[] vn = verse_nest();
    ((ComfortKey)_appearance_key).set_lines(vn[0],
        ((_typing && _typing_style != null) ? "✎ " : "")
        + verse_item_label(vn, Math.max(0, _verse_item)));
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

  /** Decided by the keyboard, which knows the containers' order. */
  public boolean takes_bottom_inset() { return false; }

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
      case "scope_default":
        _scope_nest = 0;
        _scope = TextScope.WORD;
        rebuild();
        return;
      case "appearance_default":
        // Plain letters, and the default nest.
        restore_appearance();
        _verse_nest = DEFAULT_VERSE_NEST;
        _verse_item = -1;
        rebuild();
        return;
      case "format_default":
        // Format removed, and the app's own syntax.
        restore_format();
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
    // Jumps start from the unit itself, without what inclusive added.
    int[] unit = _inclusive ? _scope.exclusive(f.text, sel) : sel;
    switch (action)
    {
      case "select":
        // First the unit at the cursor, then the next ones.
        if (!has_selection(f))
          r = _scope.unit_at(f.text, f.sel_start);
        else
          r = _forward ? _scope.next_unit(f.text, unit[1])
            : _scope.prev_unit(f.text, unit[0]);
        r = adjust(f.text, r);
        break;
      // With a selection, the selection moves by a unit, staying selected
      // (and inclusive or exclusive). Without, the cursor moves.
      case "cursor_next":
      {
        if (has_selection(f))
        {
          r = adjust(f.text, _scope.next_unit(f.text, unit[1]));
          break;
        }
        int p = unit[1];
        int[] u = _scope.unit_containing(f.text, p);
        if (u == null || u[1] <= p)
          u = _scope.next_unit(f.text, p);
        int to = (u == null) ? f.text.length() : adjust(f.text, u)[1];
        r = new int[]{ to, to };
        break;
      }
      case "cursor_prev":
      {
        if (has_selection(f))
        {
          r = adjust(f.text, _scope.prev_unit(f.text, unit[0]));
          break;
        }
        int p = unit[0];
        int[] u = (p > 0) ? _scope.unit_containing(f.text, p - 1) : null;
        if (u == null || u[0] >= p)
          u = _scope.prev_unit(f.text, p);
        int to = (u == null) ? 0 : adjust(f.text, u)[0];
        r = new int[]{ to, to };
        break;
      }
      case "select_toggle":
        r = has_selection(f) ? new int[]{ f.sel_end, f.sel_end }
          : adjust(f.text, _scope.unit_at(f.text, f.sel_start));
        break;
      case "pin":
        if (has_selection(f))
          set_pin(f.text.substring(f.sel_start, f.sel_end), f.base + f.sel_start);
        else
          set_pin(null, 0);
        return;
      case "inclusive_toggle":
        _inclusive = !_inclusive;
        if (has_selection(f))
          r = _inclusive ? _scope.inclusive(f.text, sel) : _scope.exclusive(f.text, sel);
        rebuild();
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
      case "select": return new ComfortKey.Label("Sector", false);
      case "cursor_prev": return new ComfortKey.Label("◂", false);
      case "cursor_next": return new ComfortKey.Label("▸", false);
      case "select_toggle": return new ComfortKey.Label("[ ]", false);
      case "inclusive_toggle": return new ComfortKey.Label("⊂", false);
      case "scope_default": return new ComfortKey.Label("⌂", false);
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

  /* Typing in View's format and Verse's look (Scope swiped up) */

  /** The format View last applied, [null] when it shows plain. */
  String[] view_format()
  {
    if (_view_item == null)
      return null;
    for (String[] f : formats())
      if (f[0].equals(_view_item))
        return f;
    return null;
  }

  /** Type in View's format (its markers around the text) and Verse's look
      (applied to every letter typed) until the scope's separator, e.g. a
      space for Word. */
  void start_typing()
  {
    String[] f = view_format();
    String style = null;
    if (_verse_item >= 0)
    {
      String[] n = verse_nest();
      style = n[1 + Math.min(_verse_item, n.length - 2)];
      _typing_case = n[0].equals("Case");
    }
    InputConnection ic = _listener.input();
    if (ic == null)
      return;
    if (f == null && style == null)
    {
      toast("Pick a format on View or a look on Verse first");
      return;
    }
    String open = (f == null) ? "" : f[1], close = (f == null) ? "" : f[2];
    int pos = Math.min(_listener.sel_start(), _listener.sel_end());
    ic.beginBatchEdit();
    if (!open.isEmpty())
      ic.commitText(open, 1);
    if (!close.isEmpty())
      ic.commitText(close, 0); // Cursor before the closing marker.
    ic.endBatchEdit();
    _typing = true;
    _typing_close = close;
    _typing_start = pos + open.length();
    _typing_style = style;
    _typing_open = open;
    _listener.preview(open + close);
    rebuild();
    toast("Typing in " + typing_summary() + " until " + separator_name());
  }

  /** E.g. "Bold · 𝐛𝐨𝐥𝐝". */
  String typing_summary()
  {
    String f = (_typing_close.isEmpty() && view_format() == null) ? null : _view_item;
    String v = (_typing_style == null) ? null
      : (_typing_case ? case_label(_typing_style) : UnicodeStyles.label(_typing_style));
    if (f != null && v != null)
      return f + " · " + v;
    return (f != null) ? f : (v != null ? v : "");
  }

  /** What ends typing in format, for the scope. */
  String separator_name()
  {
    if (_scope == TextScope.ALL)
      return "moving the cursor away";
    if (_scope == TextScope.LINE || _scope == TextScope.PARAGRAPH)
      return "a new line";
    if (_scope == TextScope.SENTENCE)
      return ". ! ? or a new line";
    return "a space";
  }

  void stop_typing()
  {
    _typing = false;
    _listener.preview(null);
    rebuild();
  }

  /** The editor moved the cursor: apply Verse's look to what was typed,
      close the format when the scope's separator was just typed, stop when
      the cursor left the typed text. */
  public void selection_updated(int start, int end)
  {
    if (!_typing)
      return;
    InputConnection ic = _listener.input();
    if (ic == null || start != end || start < _typing_start)
    {
      stop_typing();
      return;
    }
    String close = _typing_close;
    if (!close.isEmpty())
    {
      CharSequence after = ic.getTextAfterCursor(close.length(), 0);
      if (after == null || !after.toString().equals(close))
      {
        stop_typing();
        return;
      }
    }
    if (start == _typing_start)
      return;
    CharSequence cs = ic.getTextBeforeCursor(start - _typing_start, 0);
    if (cs == null || cs.length() != start - _typing_start)
      return;
    String typed = cs.toString();
    char last = typed.charAt(typed.length() - 1);
    boolean sep = is_separator(last);
    String body = sep ? typed.substring(0, typed.length() - 1) : typed;
    String conv = convert(body);
    if (sep)
    {
      _typing = false;
      ic.beginBatchEdit();
      ic.setSelection(_typing_start, start + close.length());
      ic.commitText(conv + close + last, 1);
      ic.endBatchEdit();
      _listener.preview(null);
      rebuild();
      return;
    }
    _listener.preview(_typing_open + conv + close);
    if (!conv.equals(body))
    {
      ic.beginBatchEdit();
      ic.setSelection(_typing_start, start);
      ic.commitText(conv, 1);
      ic.endBatchEdit();
    }
  }

  String convert(String s)
  {
    if (_typing_style == null)
      return s;
    return _typing_case ? UnicodeStyles.change_case(s, _typing_style)
      : UnicodeStyles.apply(s, _typing_style);
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
    add(m, "plain", new Runnable() {
      public void run() { restore_format(); }
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

  /** A unit as Sector selects it: exclusive, or inclusive. */
  int[] adjust(String text, int[] r)
  {
    return (r == null || !_inclusive) ? r : _scope.inclusive(text, r);
  }

  /** The pinned text, the selection, or the scope's unit at the cursor. */
  int[] target(Field f)
  {
    _target_is_pin = false;
    if (_pin_text != null)
    {
      int[] p = locate_pin(f);
      if (p != null)
      {
        _target_is_pin = true;
        return p;
      }
      set_pin(null, 0);
      toast("The pinned text is gone");
    }
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
    if (_target_is_pin)
    {
      // The pin follows the result; the cursor stays where it was typing.
      set_pin(text.substring(sel_start - start, sel_end - start), f.base + sel_start);
      int c = f.sel_start, delta = text.length() - (end - start);
      if (c >= end)
        c += delta;
      else if (c > start)
        c = start + text.length();
      ic.setSelection(f.base + c, f.base + c);
    }
    else
      ic.setSelection(f.base + sel_start, f.base + sel_end);
    ic.endBatchEdit();
  }

  /* Pinned selection */

  void set_pin(String text, int abs_start)
  {
    _pin_text = (text == null || text.isEmpty()) ? null : text;
    _pin_start = abs_start;
    _listener.pinned(_pin_text);
    rebuild();
  }

  /** The pinned text's range in [f], found again if the text before it
      changed: the occurrence closest to where it was. [null] if gone. */
  int[] locate_pin(Field f)
  {
    int[] r = find_nearest(f.text, _pin_text, _pin_start - f.base);
    if (r != null)
      _pin_start = f.base + r[0];
    return r;
  }

  /** Range of [p] in [text], at [rel] if it is there, else the occurrence
      closest to [rel]. [null] if [p] isn't in [text]. */
  static int[] find_nearest(String text, String p, int rel)
  {
    if (rel >= 0 && text.startsWith(p, rel))
      return new int[]{ rel, rel + p.length() };
    int best = -1;
    for (int i = text.indexOf(p); i >= 0; i = text.indexOf(p, i + 1))
      if (best < 0 || Math.abs(i - rel) < Math.abs(best - rel))
        best = i;
    return (best < 0) ? null : new int[]{ best, best + p.length() };
  }

  /** Select the pinned text in the field (the selection bar's tap). */
  public void select_pinned()
  {
    Field f = read();
    if (f == null || _pin_text == null)
      return;
    int[] r = locate_pin(f);
    if (r != null)
      f.ic.setSelection(f.base + r[0], f.base + r[1]);
  }

  /** The selection bar's ✕: stops typing in format, drops a pin. */
  public void unpin()
  {
    if (_typing)
      stop_typing();
    if (_pin_text != null)
      set_pin(null, 0);
  }

  /** Cut the pinned text to the clipboard, keeping the cursor. Returns
      [false] when nothing is pinned. */
  public boolean cut_pinned()
  {
    if (_pin_text == null)
      return false;
    Field f = read();
    int[] r = (f == null) ? null : locate_pin(f);
    if (r == null)
    {
      set_pin(null, 0);
      return false;
    }
    android.content.ClipboardManager cm = (android.content.ClipboardManager)
      getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    cm.setPrimaryClip(android.content.ClipData.newPlainText("Input Layer", _pin_text));
    int c = f.sel_start, len = r[1] - r[0];
    if (c >= r[1])
      c -= len;
    else if (c > r[0])
      c = r[0];
    f.ic.beginBatchEdit();
    f.ic.setSelection(f.base + r[0], f.base + r[1]);
    f.ic.commitText("", 1);
    f.ic.setSelection(f.base + c, f.base + c);
    f.ic.endBatchEdit();
    set_pin(null, 0);
    return true;
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
