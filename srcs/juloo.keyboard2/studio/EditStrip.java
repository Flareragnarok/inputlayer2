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
      down starts typing in View's format and Verse's look, previewed in the
      selection bar, and its up pushes it). View and Verse: swipe up
      restores the text. Scope and Sector show their name over their state.
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
    /** Studio's Spec / Vert editor on [name] ([null]: a new one). */
    void open_editor(String type, String name);
    /** The pinned selection changed, [null] when unpinned. */
    void pinned(String text);
    /** What typing in format produces, shown in the selection bar; [null]
        when not typing in format. */
    void preview(String text);
    /** The layout must be built again (a key's label changed). */
    void relayout();
    /** Text written in typing in format, when it ends: its words become
        suggestions as written. */
    void written(String text);
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
        "s", "scope_type", "n", "scope_push");
  /** Tap cycles the selection mode (excl, incl, desel); the step in the
      ←→ direction is on the arrow key's tap, next to the space bar; left
      and right move the cursor (or the selection) by a unit; up selects
      the unit at the cursor or deselects. */
  static final ComfortKeySpec SELECTOR_KEY =
    ComfortKeySpec.of("c", "inclusive_toggle", "w", "cursor_prev", "e", "cursor_next",
        "n", "select_toggle");
  /** Spec and Vert: tap applies the current macro, right picks the next,
      left lists them, down saves the last typing in format as one (Spec:
      its steps, Vert: its key replacements), up opens the definitions. */
  static final ComfortKeySpec CUSTOM_SELECTION_KEY =
    ComfortKeySpec.of("c", "spec_apply", "e", "spec_next", "w", "spec_menu",
        "s", "spec_save", "n", "custom_selection");
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
  static final ComfortKeySpec CUSTOM_FORMAT_KEY =
    ComfortKeySpec.of("c", "vert_apply", "e", "vert_next", "w", "vert_menu",
        "s", "vert_save", "n", "custom_format");

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
  /** The same, for the layout: the arrow key's tap label. */
  public static boolean selection_forward = true;
  /** Backspace removes after the cursor (switched with its up swipe). */
  public static boolean forward_delete = false;
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
  /** Third selection mode (after exclusive and inclusive): steps remove
      the unit at the end the arrow points to, until nothing is selected. */
  boolean _deselect = false;
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
  /** Typing in View's format and Verse's look (Scope swiped down). */
  boolean _typing = false;
  /** Closing marker the cursor sits before, "" for none. */
  String _typing_close = "";
  /** Absolute range of the typed text, between the markers. */
  int _typing_start = 0;
  int _typing_end = 0;
  /** The field's text length, to follow what is typed. */
  int _typing_len = 0;
  /** Text length expected after the keyboard's own edit, [-1] for none. */
  int _own_edit_len = -1;
  /** Recorded during typing in format: selection steps (Spec) and
      operations (Vert), see [Ops]. */
  List<String> _spec_ops = new ArrayList<String>();
  List<String> _vert_ops = new ArrayList<String>();
  /** A Spec is being run: its steps aren't recorded again. */
  boolean _replaying = false;
  /** Cursor at the last update, to tell ⌫ from ⌦. */
  int _prev_cursor = 0;
  /** Where Scope ↑ left the cursor and the text's length then: until
      either changes, the pushed text isn't the unit at the cursor. */
  int _pushed_at = -1;
  int _pushed_len = -1;
  /** Opening marker, for the selection bar's preview. */
  String _typing_open = "";
  /** Verse's item applied to the typed text, [null] for none. */
  String _typing_style = null;
  boolean _typing_case = false;
  View _scope_key;
  View _spec_key;
  View _vert_key;
  /** Current macros, by name ([null]: the first saved). */
  String _spec_name = null;
  String _vert_name = null;

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
    // Like Sector: its name on top, the scope at the bottom; lit while
    // typing in format, which it starts and pushes.
    ((ComfortKey)_scope_key).set_lines("Scope", _scope.label);
    ((ComfortKey)_scope_key)._highlight = _typing;
    addView(_scope_key);
    _spec_key = make_key(CUSTOM_SELECTION_KEY, 1.4f);
    Module spec = current_macro("spec");
    ((ComfortKey)_spec_key).set_lines("Spec", (spec == null) ? "—" : spec.name);
    addView(_spec_key);
    View sector = make_key(SELECTOR_KEY, 1.5f);
    // The selection mode at the bottom; tapping Sector switches it.
    ((ComfortKey)sector).set_lines("Sector", _deselect ? "desel" : (_inclusive ? "incl" : "excl"));
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
    _vert_key = make_key(CUSTOM_FORMAT_KEY, 1.4f);
    Module vert = current_macro("vert");
    ((ComfortKey)_vert_key).set_lines("Vert", (vert == null) ? "—" : vert.name);
    addView(_vert_key);
    addView(_appearance_key);
    addView(make_key(CYCLE_DIRECTION_KEY, 1.0f));
  }

  /** Custom selections, formats and fonts, from their modules. */
  void load_custom()
  {
    migrate_macros();
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
        rec_spec("scope:" + _scope.label);
        rebuild();
        return;
      case "scope_nest":
        _scope_nest = (_scope_nest + 1) % scope_nest_count();
        _scope = scope_nest_items().get(0);
        rec_spec("scope:" + _scope.label);
        rebuild();
        return;
      case "scope_menu":
        scope_menu();
        return;
      case "scope_type":
        start_typing();
        return;
      case "scope_push":
        push_typing();
        return;
      case "appearance_default":
        // Plain letters, in the default nest.
        _verse_nest = DEFAULT_VERSE_NEST;
        _verse_item = -1;
        follow_verse();
        rebuild();
        return;
      case "format_default":
        // Plain, in the app's own syntax.
        default_syntax();
        rebuild();
        return;
      case "appearance_nest":
        _verse_nest = (_verse_nest + 1) % UnicodeStyles.nests().size();
        _verse_item = -1;
        follow_verse();
        rebuild();
        return;
      case "format_nest":
        next_syntax();
        return;
      case "selection_direction":
        _forward = !_forward;
        rec_spec("dir:" + (_forward ? "fwd" : "back"));
        selection_forward = _forward;
        rebuild();
        // The arrow key's tap shows the direction.
        _listener.relayout();
        return;
      case "cycle_direction":
        _up = !_up;
        rebuild();
        return;
      case "spec_apply": apply_macro("spec"); return;
      case "spec_next": next_macro("spec"); return;
      case "spec_menu": macro_menu("spec", _spec_key); return;
      case "spec_save": save_macro("spec"); return;
      case "vert_apply": apply_macro("vert"); return;
      case "vert_next": next_macro("vert"); return;
      case "vert_menu": macro_menu("vert", _vert_key); return;
      case "vert_save": save_macro("vert"); return;
      // Spec ↑ / Vert ↑: the editor, on the current one.
      case "custom_selection":
      {
        Module m = current_macro("spec");
        _listener.open_editor("spec", (m == null) ? null : m.name);
        return;
      }
      case "custom_format":
      {
        Module m = current_macro("vert");
        _listener.open_editor("vert", (m == null) ? null : m.name);
        return;
      }
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
    // Typing in format: Sector moves within the typed text only.
    if (_typing)
      f = within(f, _typing_start - f.base, _typing_end - f.base);
    if (f == null)
      return;
    int[] sel = { f.sel_start, f.sel_end };
    int[] r = null;
    // Jumps start from the unit itself, without what inclusive added.
    int[] unit = _inclusive ? _scope.exclusive(f.text, sel) : sel;
    switch (action)
    {
      case "select":
        rec_spec("step");
        // First the unit at the cursor, then the next ones: inclusive adds
        // them to the selection, exclusive moves to them.
        if (!has_selection(f))
          r = adjust(f.text, _scope.unit_at(f.text, f.sel_start));
        else
          r = step(f.text, sel, unit, _forward);
        break;
      // With a selection, the selection moves by a unit, staying selected
      // (and inclusive or exclusive). Without, the cursor moves.
      case "cursor_next":
      {
        rec_spec("move:next");
        if (has_selection(f))
        {
          r = step(f.text, sel, unit, true);
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
        rec_spec("move:prev");
        if (has_selection(f))
        {
          r = step(f.text, sel, unit, false);
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
        rec_spec("unit");
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
        // excl -> incl -> desel -> excl.
        if (_deselect)
        {
          _deselect = false;
          if (has_selection(f))
            r = _scope.exclusive(f.text, sel);
        }
        else if (_inclusive)
        {
          _inclusive = false;
          _deselect = true;
        }
        else
        {
          _inclusive = true;
          if (has_selection(f))
            r = _scope.inclusive(f.text, sel);
        }
        rec_spec("mode:" + mode_name());
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
      case "scope_push": return new ComfortKey.Label("⤒", false);
      case "custom_selection": case "custom_format": return new ComfortKey.Label("⚙", false);
      case "spec_apply": case "vert_apply": return new ComfortKey.Label("", false);
      case "spec_next": case "vert_next": return new ComfortKey.Label("›", false);
      case "spec_menu": case "vert_menu": return new ComfortKey.Label("☰", false);
      case "spec_save": case "vert_save": return new ComfortKey.Label("⤓", false);
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

  /* Typing in View's format and Verse's look (Scope down, pushed with Scope up) */

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
      (applied to the letters typed) until Scope ↑ pushes it. With a
      selection, the selection is the text: it gets the format and look at
      once, and typing goes on inside it. */
  void start_typing()
  {
    // What View and Verse show: their last item, or the one a tap applies.
    String[] f = view_format();
    String[] n = verse_nest();
    String style = n[1 + Math.min(Math.max(0, _verse_item), n.length - 2)];
    _typing_case = n[0].equals("Case");
    if (style.equals(UnicodeStyles.PLAIN))
      style = null;
    Field fl = read();
    if (fl == null)
      return;
    _typing_style = style;
    String open = (f == null) ? "" : f[1], close = (f == null) ? "" : f[2];
    String body = convert(fl.text.substring(fl.sel_start, fl.sel_end));
    int s = fl.base + fl.sel_start;
    InputConnection ic = fl.ic;
    ic.beginBatchEdit();
    ic.setSelection(s, fl.base + fl.sel_end);
    ic.commitText(open + body + close, 1);
    // Cursor at the end of the text, before the closing marker.
    int end = s + open.length() + body.length();
    ic.setSelection(end, end);
    ic.endBatchEdit();
    _typing = true;
    _spec_ops = new ArrayList<String>();
    _vert_ops = new ArrayList<String>();
    // Its start is recorded too: the look, then the markers.
    if (style != null)
      rec_vert((_typing_case ? "case:" : "look:") + style);
    if (!open.isEmpty() || !close.isEmpty())
      rec_vert("wrap:" + open + "|" + close);
    _typing_open = open;
    _typing_close = close;
    _typing_start = s + open.length();
    _typing_end = end;
    _prev_cursor = end;
    _typing_len = fl.text.length() - (fl.sel_end - fl.sel_start)
      + open.length() + body.length() + close.length();
    _listener.preview(open + body + close);
    rebuild();
    toast("Typing in " + typing_summary() + ": Scope ↑ pushes it");
  }

  /** E.g. "Bold · 𝐛𝐨𝐥𝐝", "plain" when neither applies anything. */
  String typing_summary()
  {
    String f = (_typing_close.isEmpty() && view_format() == null) ? null : _view_item;
    String v = (_typing_style == null) ? null
      : (_typing_case ? case_label(_typing_style) : UnicodeStyles.label(_typing_style));
    if (f != null && v != null)
      return f + " · " + v;
    return (f != null) ? f : (v != null ? v : "plain");
  }

  /** Scope ↑: the typed text is done; the cursor goes after the closing
      marker, nothing selected, and the text is left as it is (see
      [_pushed_at]). */
  void push_typing()
  {
    if (!_typing)
      return;
    InputConnection ic = _listener.input();
    int after = _typing_end + _typing_close.length();
    _pushed_at = after;
    _pushed_len = _typing_len;
    stop_typing();
    if (ic != null)
      ic.setSelection(after, after);
  }

  void stop_typing()
  {
    // Its words, as written, become suggestions.
    Field f = read();
    if (f != null)
    {
      int ts = _typing_start - f.base, te = _typing_end - f.base;
      if (ts >= 0 && te <= f.text.length() && ts < te)
        _listener.written(f.text.substring(ts, te));
    }
    _typing = false;
    _listener.preview(null);
    rebuild();
  }

  /** The editor changed the text or moved the cursor. The typed text is
      [_typing_start, _typing_end): it grows by what was typed inside it,
      whose letters get Verse's look; it ends when the cursor leaves it. */
  public void selection_updated(int start, int end)
  {
    if (!_typing)
      return;
    Field f = read();
    if (f == null)
    {
      stop_typing();
      return;
    }
    int len = f.text.length();
    int delta = len - _typing_len;
    _typing_len = len;
    // The keyboard's own edit (View, Verse, a macro), not typed letters.
    boolean own = (_own_edit_len >= 0 && len == _own_edit_len);
    _own_edit_len = -1;
    int cursor = f.base + f.sel_end;
    // Text typed (or deleted) inside the typed text moves its end.
    if (delta != 0 && cursor - Math.max(delta, 0) >= _typing_start
        && cursor - Math.max(delta, 0) <= _typing_end)
      _typing_end += delta;
    if (f.base + f.sel_start < _typing_start || cursor > _typing_end
        || _typing_end < _typing_start)
    {
      stop_typing();
      return;
    }
    int ts = _typing_start - f.base, te = _typing_end - f.base;
    if (ts < 0 || te > f.text.length())
      return;
    String body = f.text.substring(ts, te);
    String close = _typing_close;
    _listener.preview(_typing_open + body + close);
    // Only the letters just typed get Verse's look: changes made to units
    // of the text stay.
    int ins_end = cursor - f.base, ins_start = ins_end - delta;
    // Removed text, for Vert: before the cursor (it moved back) or after.
    if (!own && delta < 0)
      rec_vert(((cursor == _prev_cursor + delta) ? "back:" : "del:") + (-delta));
    _prev_cursor = cursor;
    if (own || delta <= 0 || has_selection(f) || ins_start < ts || ins_end > te)
      return;
    String ins = f.text.substring(ins_start, ins_end);
    String conv = (_typing_style == null) ? ins
      : convert_typed(ins, f.text.substring(ts, ins_start));
    rec_vert("insert:" + conv);
    if (conv.equals(ins))
      return;
    _prev_cursor = f.base + ins_start + conv.length();
    f.ic.beginBatchEdit();
    f.ic.setSelection(f.base + ins_start, f.base + ins_end);
    f.ic.commitText(conv, 1);
    f.ic.endBatchEdit();
    _typing_end += conv.length() - ins.length();
    _typing_len += conv.length() - ins.length();
    _listener.preview(_typing_open + body.substring(0, ins_start - ts) + conv
        + body.substring(ins_end - ts) + close);
  }

  /** [s] just typed after [prefix] (the typed text before it), in Verse's
      look. Cases look at [prefix]: title for where words start, alternate
      for the letters so far, camelCase for a space just dropped. */
  String convert_typed(String s, String prefix)
  {
    if (_typing_style == null)
      return s;
    char before = prefix.isEmpty() ? ' ' : prefix.charAt(prefix.length() - 1);
    if (_typing_case && (_typing_style.equals("snake") || _typing_style.equals("camel")
          || _typing_style.equals("alternate")))
      return convert_typed_identifier(s, prefix);
    if (_typing_case && _typing_style.equals("title"))
    {
      StringBuilder b = new StringBuilder();
      boolean start = Character.isWhitespace(before);
      for (int i = 0; i < s.length(); i++)
      {
        char c = s.charAt(i);
        b.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
        start = Character.isWhitespace(c);
      }
      return b.toString();
    }
    return convert(s);
  }

  /** snake_case: spaces become '_', letters small. camelCase: spaces
      dropped, the next letter capital. aLtErNaTiNg: continues the
      alternation of [prefix]. */
  String convert_typed_identifier(String s, String prefix)
  {
    StringBuilder b = new StringBuilder();
    int letters = 0;
    for (int i = 0; i < prefix.length(); i++)
      if (Character.isLetter(prefix.charAt(i)))
        letters++;
    for (int i = 0; i < s.length(); i++)
    {
      char c = s.charAt(i);
      switch (_typing_style)
      {
        case "snake":
          b.append(Character.isWhitespace(c) ? '_' : Character.toLowerCase(c));
          break;
        case "camel":
          if (Character.isWhitespace(c))
            _camel_capital = !prefix.isEmpty() || b.length() > 0;
          else
          {
            b.append(_camel_capital ? Character.toUpperCase(c) : Character.toLowerCase(c));
            _camel_capital = false;
          }
          break;
        default:
          if (Character.isLetter(c))
          {
            b.append((letters % 2 == 1) ? Character.toUpperCase(c) : Character.toLowerCase(c));
            letters++;
          }
          else
            b.append(c);
          break;
      }
    }
    return b.toString();
  }

  /** camelCase while typing: a space was dropped, the next letter is
      capital. */
  boolean _camel_capital = false;

  String convert(String s)
  {
    if (_typing_style == null)
      return s;
    return _typing_case ? UnicodeStyles.change_case(s, _typing_style)
      : UnicodeStyles.apply(s, _typing_style);
  }



  void toast(String msg)
  {
    android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
  }

  /* Specs (lenses: selection steps) and Verts (operations on the scope a
     Spec gives), recorded as atomic operations ([Ops]) during typing in
     format, simplified when saved, edited in Studio. */

  List<Module> macros(String type)
  {
    return new ModuleStore(_prefs).list(type);
  }

  /** The current one of [type]: picked last, or the first. */
  Module current_macro(String type)
  {
    List<Module> ms = macros(type);
    String name = type.equals("spec") ? _spec_name : _vert_name;
    for (Module m : ms)
      if (m.name.equals(name))
        return m;
    return ms.isEmpty() ? null : ms.get(0);
  }

  void pick_macro(String type, String name)
  {
    if (type.equals("spec"))
      _spec_name = name;
    else
      _vert_name = name;
    rebuild();
  }

  void next_macro(String type)
  {
    List<Module> ms = macros(type);
    if (ms.isEmpty())
    {
      toast(type.equals("spec") ? "No Spec yet: record one in typing in format, swipe down on Spec"
          : "No Vert yet: record one in typing in format, swipe down on Vert");
      return;
    }
    Module cur = current_macro(type);
    int i = 0;
    for (int j = 0; j < ms.size(); j++)
      if (ms.get(j).name.equals(cur.name))
        i = j;
    pick_macro(type, ms.get((i + 1) % ms.size()).name);
  }

  void macro_menu(final String type, View anchor)
  {
    PopupMenu p = new PopupMenu(getContext(), anchor);
    Module cur = current_macro(type);
    for (final Module m : macros(type))
      add(p.getMenu(), ((cur != null && cur.name.equals(m.name)) ? "● " : "") + m.name
          + "  " + m.json.optString("summary", ""), new Runnable() {
        public void run() { pick_macro(type, m.name); }
      });
    if (p.getMenu().size() == 0)
      add(p.getMenu(), "Nothing saved yet", new Runnable() { public void run() {} });
    p.show();
  }

  static List<String> ops_of(Module m)
  {
    List<String> l = new ArrayList<String>();
    JSONArray a = (m == null) ? null : m.json.optJSONArray("ops");
    for (int i = 0; a != null && i < a.length(); i++)
      l.add(a.optString(i, ""));
    return l;
  }

  /** Spec ↓ / Vert ↓: what the last typing in format recorded, simplified. */
  void save_macro(String type)
  {
    boolean spec = type.equals("spec");
    List<String> ops = spec ? Ops.simplify_spec(_spec_ops) : Ops.simplify_vert(_vert_ops);
    if (ops.isEmpty())
    {
      toast(spec ? "No selection steps recorded: in typing in format, use Sector, Scope, ←→"
          : "Nothing recorded: in typing in format, type, delete, use View or Verse");
      return;
    }
    try
    {
      JSONObject j = new JSONObject();
      j.put("ops", new JSONArray(ops));
      j.put("summary", Ops.summary(ops));
      String name = (spec ? "Spec " : "Vert ") + (macros(type).size() + 1);
      new ModuleStore(_prefs).save(Module.create(type, name, j));
      pick_macro(type, name);
      toast("Saved " + name + ": " + Ops.summary(ops));
    }
    catch (Exception e)
    {
      toast("Couldn't save: " + e.getMessage());
    }
  }

  void apply_macro(String type)
  {
    Module m = current_macro(type);
    if (m == null)
    {
      next_macro(type); // Says how to make one.
      return;
    }
    if (type.equals("spec"))
      apply_spec(ops_of(m));
    else
      apply_vert(ops_of(m));
  }

  /** Run a Spec's selection steps from the cursor or selection, the way
      Sector does them; the settings (scope, mode, direction) are only used
      for the run. */
  void apply_spec(List<String> ops)
  {
    TextScope scope = _scope;
    int nest = _scope_nest;
    boolean forward = _forward, inclusive = _inclusive, deselect = _deselect;
    _replaying = true;
    try
    {
      for (String op : ops)
      {
        String p = Ops.param(op);
        switch (Ops.kind(op))
        {
          case "scope":
            for (TextScope s : _scopes)
              if (s.label.equals(p))
                _scope = s;
            break;
          case "mode":
            _inclusive = p.equals("incl");
            _deselect = p.equals("desel");
            break;
          case "dir": _forward = !p.equals("back"); break;
          case "step": perform("select"); break;
          case "unit": perform("select_toggle"); break;
          case "move": perform(p.equals("prev") ? "cursor_prev" : "cursor_next"); break;
          default: break;
        }
      }
    }
    finally
    {
      _replaying = false;
      _scope = scope;
      _scope_nest = nest;
      _forward = forward;
      _inclusive = inclusive;
      _deselect = deselect;
    }
  }

  /** Run a Vert's operations on the selection (or at the cursor), as
      recorded: the field gets the result as one edit. */
  void apply_vert(List<String> ops)
  {
    Field f = read();
    if (f == null)
      return;
    VertRun r = VertRun.run(f.text, f.sel_start, f.sel_end, ops, formats());
    if (r.text.equals(f.text) && r.start == f.sel_start && r.end == f.sel_end)
      return;
    // The smallest part that changed.
    int a = 0, n = f.text.length(), m = r.text.length();
    while (a < n && a < m && f.text.charAt(a) == r.text.charAt(a))
      a++;
    int b = 0;
    while (b < n - a && b < m - a
        && f.text.charAt(n - 1 - b) == r.text.charAt(m - 1 - b))
      b++;
    if (_typing)
    {
      _own_edit_len = r.text.length();
      _typing_end += m - n;
      _typing_len = m;
    }
    f.ic.beginBatchEdit();
    f.ic.setSelection(f.base + a, f.base + n - b);
    f.ic.commitText(r.text.substring(a, m - b), 1);
    f.ic.setSelection(f.base + r.start, f.base + r.end);
    f.ic.endBatchEdit();
  }

  /** Old Specs (format and look) become Verts, old Verts (a replacement
      text) get their operations: clear, then type it. */
  void migrate_macros()
  {
    ModuleStore store = new ModuleStore(_prefs);
    try
    {
      for (Module m : store.list("spec"))
      {
        if (m.json.has("ops"))
          continue;
        List<String> ops = new ArrayList<String>();
        String style = m.json.optString("style", "");
        if (!style.isEmpty())
          ops.add((m.json.optBoolean("case", false) ? "case:" : "look:") + style);
        String open = m.json.optString("open", ""), close = m.json.optString("close", "");
        if (!open.isEmpty() || !close.isEmpty())
          ops.add("wrap:" + open + "|" + close);
        if (!ops.isEmpty())
        {
          JSONObject j = new JSONObject();
          j.put("ops", new JSONArray(ops));
          j.put("summary", Ops.summary(ops));
          // Saved before the old Spec goes.
          store.save(Module.create("vert", "From " + m.name, j));
        }
        store.delete("spec", m.name);
      }
      for (Module m : store.list("vert"))
      {
        if (m.json.has("ops"))
          continue;
        List<String> ops = new ArrayList<String>();
        ops.add("clear");
        String t = m.json.optString("replace", "");
        if (!t.isEmpty())
          ops.add("insert:" + t);
        JSONObject j = new JSONObject(m.json.toString());
        j.put("ops", new JSONArray(ops));
        j.put("summary", Ops.summary(ops));
        store.save(new Module(j));
      }
    }
    catch (Exception e)
    {
      juloo.keyboard2.Logs.exn("Studio macro migration", e);
    }
  }

  void rec_spec(String op)
  {
    if (_typing && !_replaying)
      _spec_ops.add(op);
  }

  void rec_vert(String op)
  {
    if (_typing && !_replaying)
      _vert_ops.add(op);
  }

  String mode_name()
  {
    return _deselect ? "desel" : (_inclusive ? "incl" : "excl");
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
          rec_spec("scope:" + s.label);
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

  /** Tap: the next item of the nest is picked (the previous one going
      down). Picks change no text: Backspace ← applies them. */
  void cycle_appearance()
  {
    String[] nest = verse_nest();
    int n = nest.length - 1;
    int k = Math.max(0, _verse_item);
    _verse_item = (k + (_up ? 1 : n - 1)) % n;
    follow_verse();
    rebuild();
  }

  /** Verse ↑: plain letters picked, in this nest if it has them (the case
      stays as it is). */
  void restore_appearance()
  {
    if (!lock_in_verse(UnicodeStyles.PLAIN))
    {
      _verse_nest = DEFAULT_VERSE_NEST;
      _verse_item = -1;
      follow_verse();
      rebuild();
    }
  }

  /** Typing in format follows Verse's pick: the letters typed from now on
      get the new look. */
  void follow_verse()
  {
    if (!_typing)
      return;
    String[] n = verse_nest();
    String style = n[1 + Math.min(Math.max(0, _verse_item), n.length - 2)];
    _typing_case = n[0].equals("Case");
    _typing_style = style.equals(UnicodeStyles.PLAIN) ? null : style;
  }

  void appearance_menu()
  {
    PopupMenu p = new PopupMenu(getContext(), _appearance_key);
    Menu m = p.getMenu();
    SubMenu cases = m.addSubMenu("Aa  Case");
    for (final String c : UnicodeStyles.CASES)
      add(cases, case_label(c), new Runnable() {
        public void run() { lock_in_verse(c); }
      });
    for (final String st : UnicodeStyles.styles())
      add(m, UnicodeStyles.label(st), new Runnable() {
        public void run() { lock_in_verse(st); }
      });
    p.show();
  }

  /** A look picked from the list becomes Verse's nest and item, so that
      the next tap, Backspace ← and typing in format go on from it.
      [false] if no nest has it. */
  boolean lock_in_verse(String item)
  {
    List<String[]> ns = UnicodeStyles.nests();
    for (int i = 0; i < ns.size(); i++)
      for (int j = 1; j < ns.get(i).length; j++)
        // Plain is in every nest: stay in the current one for it.
        if (ns.get(i)[j].equals(item)
            && !(item.equals(UnicodeStyles.PLAIN) && i != _verse_nest))
        {
          _verse_nest = i;
          _verse_item = j - 1;
          follow_verse();
          rebuild();
          return true;
        }
    return false;
  }

  static String case_label(String c)
  {
    switch (c)
    {
      case "upper": return "UPPER";
      case "lower": return "lower";
      case "title": return "Title Case";
      case "snake": return "snake_case";
      case "camel": return "camelCase";
      case "alternate": return "aLtErNaTiNg";
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

  /** Tap: the next format of the syntax is picked, plain between the
      last and the first. Picks change no text: Backspace ← applies them. */
  void cycle_format()
  {
    List<String[]> fs = formats();
    if (fs.isEmpty())
    {
      // No markup in this app: the unicode styles look formatted anywhere.
      cycle_appearance();
      return;
    }
    int i = -1;
    for (int k = 0; k < fs.size(); k++)
      if (fs.get(k)[0].equals(_view_item))
        i = k;
    int j = (i < 0) ? (_up ? 0 : fs.size() - 1) : (_up ? i + 1 : i - 1);
    _view_item = (j < 0 || j >= fs.size()) ? null : fs.get(j)[0];
    rebuild();
  }

  /** View ↑: plain picked. */
  void restore_format()
  {
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
          _view_item = fm[0];
          rebuild();
        }
      });
    p.show();
  }

  /* Editing */

  /** Backspace ←: Verse's look, as picked, applied to the selection or
      the scope's unit at the cursor. Markers around it stay as they are;
      the letters inside them get the look and stay selected. */
  public void apply_look()
  {
    Field f = read();
    int[] r = (f == null) ? null : apply_target(f);
    if (r == null)
      return;
    int[] outer = { r[0], r[1] }, inner = { r[0], r[1] };
    TextFormats.current(f.text, r, formats(), outer, inner);
    String[] n = verse_nest();
    String style = n[1 + Math.min(Math.max(0, _verse_item), n.length - 2)];
    boolean is_case = n[0].equals("Case");
    String body = f.text.substring(inner[0], inner[1]);
    body = is_case ? UnicodeStyles.change_case(body, style) : UnicodeStyles.apply(body, style);
    rec_vert((is_case ? "case:" : "look:") + style);
    replace(f, inner[0], inner[1], body, inner[0], inner[0] + body.length());
  }

  /** Shift ←: View's format, as picked, applied to the selection or the
      scope's unit at the cursor, replacing a format already around it
      (plain removes it). The text inside the markers stays selected, so
      another pick and Shift ← replace it in turn. In apps without markup,
      View picks Verse's looks: Shift ← applies the look. */
  public void apply_format()
  {
    List<String[]> fs = formats();
    if (fs.isEmpty())
    {
      apply_look();
      return;
    }
    Field f = read();
    int[] r = (f == null) ? null : apply_target(f);
    if (r == null)
      return;
    int[] outer = { r[0], r[1] }, inner = { r[0], r[1] };
    boolean had = TextFormats.current(f.text, r, fs, outer, inner) >= 0;
    String body = f.text.substring(inner[0], inner[1]);
    String[] vf = view_format();
    String open = (vf == null) ? "" : vf[1], close = (vf == null) ? "" : vf[2];
    String t;
    int ss, se;
    if (close.isEmpty() && !open.isEmpty())
    {
      // Line markers ("> ", "# ") start each line.
      t = TextFormats.toggle_lines(0, 0, body, open).text;
      ss = 0;
      se = t.length();
    }
    else
    {
      t = open + body + close;
      ss = open.length();
      se = ss + body.length();
    }
    // Recorded as: remove the format, then wrap in the new one.
    if (had)
      rec_vert("unwrap");
    if (vf != null)
      rec_vert("wrap:" + open + "|" + close);
    replace(f, outer[0], outer[1], t, outer[0] + ss, outer[0] + se);
  }

  /** What Backspace ← and Shift ← apply to, [null] (with a toast) for
      nothing. */
  int[] apply_target(Field f)
  {
    int[] r = target(f);
    if (r == null)
      toast("Nothing to apply to: select some text");
    return r;
  }

  /** The selection [sel] (of unit [unit]) after a step forward or back:
      inclusive keeps it and adds the next or previous unit, exclusive moves
      to that unit. [null] when there is none. */
  int[] step(String text, int[] sel, int[] unit, boolean forward)
  {
    if (_deselect)
      return deselect_step(text, sel, forward);
    int[] n = forward ? _scope.next_unit(text, unit[1]) : _scope.prev_unit(text, unit[0]);
    if (n == null)
      return null;
    if (!_inclusive)
      return n;
    n = _scope.inclusive(text, n);
    return new int[]{ Math.min(sel[0], n[0]), Math.max(sel[1], n[1]) };
  }

  /** Deselect mode: [sel] without the unit at the end opposite to the
      arrow's tip, so that the unit at the tip goes last; then nothing
      selected, the cursor at the tip. */
  int[] deselect_step(String text, int[] sel, boolean forward)
  {
    int[] r = _scope.shrink(text, sel, !forward);
    if (r[0] != sel[0] || r[1] != sel[1])
      return r;
    int c = forward ? sel[1] : sel[0];
    return new int[]{ c, c };
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
    // Text Scope ↑ just pushed is done: it isn't the unit at the cursor.
    if (f.base + f.sel_start == _pushed_at && f.text.length() == _pushed_len)
      return null;
    return _scope.unit_at(f.text, f.sel_start);
  }

  void replace(Field f, int start, int end, String text, int sel_start, int sel_end)
  {
    InputConnection ic = f.ic;
    if (_typing)
      _own_edit_len = f.text.length() + text.length() - (end - start);
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

  /** The arrow key's tap: Sector's step in the ←→ direction (select the
      unit at the cursor, then jump to, add or remove the next unit). */
  public void sector_step()
  {
    perform("select");
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

  /** [f] narrowed to [s, e) of its text, selection clamped into it. */
  static Field within(Field f, int s, int e)
  {
    if (s < 0 || e > f.text.length() || s > e)
      return null;
    Field n = new Field();
    n.ic = f.ic;
    n.text = f.text.substring(s, e);
    n.base = f.base + s;
    n.sel_start = Math.max(0, Math.min(e - s, f.sel_start - s));
    n.sel_end = Math.max(n.sel_start, Math.min(e - s, f.sel_end - s));
    return n;
  }

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
