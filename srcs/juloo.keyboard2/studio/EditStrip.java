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
import juloo.keyboard2.Config;
import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.Theme;

/** The edit strip, under the space bar, eight keys:
    - ←→: selection direction, forward or backward, for Sector and Scope.
    - Scope: Word, Sentence, Line, Paragraph, All, then custom selections.
    - Spec: opens Studio to define selections.
    - Sector (the selector): select the scope's unit at the cursor, then the
      next (or previous) one; swipes grow, shrink or drop the selection.
    - View (appearance): cycles unicode styles, then saved fonts; swipe up
      cycles the case, down opens the list.
    - Vers: opens Studio to define formats and fonts.
    - Vex (format): cycles the app's markup formats, then custom formats;
      swipe down opens the list and the syntax choice.
    - ↑↓: cycling direction, up or down, for View and Vex.
    Appearance and Format act on the selection, or on the scope's unit at
    the cursor, and select the result so that the next tap cycles on. */
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

  static final ComfortKeySpec SCOPE_KEY = ComfortKeySpec.of("c", "scope");
  static final ComfortKeySpec SELECTOR_KEY =
    ComfortKeySpec.of("c", "select", "w", "select_prev", "e", "select_next",
        "n", "select_extend", "s", "select_shrink", "sw", "deselect");
  static final ComfortKeySpec CUSTOM_SELECTION_KEY = ComfortKeySpec.of("c", "custom_selection");
  static final ComfortKeySpec SELECTION_DIRECTION_KEY = ComfortKeySpec.of("c", "selection_direction");
  static final ComfortKeySpec CYCLE_DIRECTION_KEY = ComfortKeySpec.of("c", "cycle_direction");
  static final ComfortKeySpec APPEARANCE_KEY =
    ComfortKeySpec.of("c", "appearance", "n", "case", "s", "appearance_menu");
  static final ComfortKeySpec FORMAT_KEY =
    ComfortKeySpec.of("c", "format", "s", "format_menu");
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
  /** Cycling direction (↑↓): up or down, for View and Vex. */
  boolean _up = true;
  int _case = -1;
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
    addView(make_key(SCOPE_KEY, 1.8f));
    addView(make_key(CUSTOM_SELECTION_KEY, 1.3f));
    addView(make_key(SELECTOR_KEY, 1.5f));
    _appearance_key = make_key(APPEARANCE_KEY, 1.3f);
    _format_key = make_key(FORMAT_KEY, 1.3f);
    addView(_appearance_key);
    addView(make_key(CUSTOM_FORMAT_KEY, 1.3f));
    addView(_format_key);
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
        _scope = TextScope.cycle(_scopes, _scope, _forward);
        rebuild();
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
      case "case": cycle_case(); return;
      case "appearance_menu": appearance_menu(); return;
      case "format": cycle_format(); return;
      case "format_menu": format_menu(); return;
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
      case "select_next":
        r = _scope.next_unit(f.text, f.sel_end);
        break;
      case "select_prev":
        r = _scope.prev_unit(f.text, f.sel_start);
        break;
      case "select_extend":
        r = has_selection(f) ? _scope.extend(f.text, sel) : _scope.unit_at(f.text, f.sel_start);
        break;
      case "select_shrink":
        r = _scope.shrink(f.text, sel);
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
      case "scope": return new ComfortKey.Label(_scope.label, false);
      case "select": return new ComfortKey.Label("Sector", false);
      case "select_prev": return new ComfortKey.Label("‹", false);
      case "select_next": return new ComfortKey.Label("›", false);
      case "select_extend": return new ComfortKey.Label("+", false);
      case "select_shrink": return new ComfortKey.Label("−", false);
      case "deselect": return new ComfortKey.Label("×", false);
      case "custom_selection": return new ComfortKey.Label("Spec", false);
      case "selection_direction": return new ComfortKey.Label(_forward ? "→" : "←", false);
      case "cycle_direction": return new ComfortKey.Label(_up ? "↑" : "↓", false);
      case "appearance": return new ComfortKey.Label("View", false);
      case "case": return new ComfortKey.Label("aA", false);
      case "appearance_menu": return new ComfortKey.Label("☰", false);
      case "format": return new ComfortKey.Label("Vex", false);
      case "format_menu": return new ComfortKey.Label("☰", false);
      case "custom_format": return new ComfortKey.Label("Vers", false);
      default: return new ComfortKey.Label("?", false);
    }
  }

  public float swipe_dist_px()
  {
    return Math.max(dp(8), _config.swipe_dist_px / 2);
  }

  public long repeat_delay() { return _config.longPressTimeout; }

  public long repeat_interval() { return _config.longPressInterval; }

  /* Appearance */

  void cycle_appearance()
  {
    final boolean forward = _up;
    transform_target(new Transform() {
      public String apply(String s)
      {
        return UnicodeStyles.apply(s, UnicodeStyles.cycle(s, forward));
      }
    });
  }

  void cycle_case()
  {
    int n = UnicodeStyles.CASES.length;
    if (_case < 0)
      _case = _up ? 0 : n - 1;
    else
      _case = (_case + (_up ? 1 : n - 1)) % n;
    final String c = UnicodeStyles.CASES[_case];
    transform_target(new Transform() {
      public String apply(String s) { return UnicodeStyles.change_case(s, c); }
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
        (app == null) ? null : _prefs.getString(KEY_SYNTAX_PREFIX + app, null));
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
          format_menu();
        }
      });
    if (syntax.formats.isEmpty())
      add(m, "No markup in this app: Vex cycles View's styles", new Runnable() {
        public void run() { appearance_menu(); }
      });
    for (final String[] fm : TextFormats.cycle_list(syntax, _custom_formats))
      add(m, fm[0], new Runnable() {
        public void run() { format_target(new String[]{ fm[1], fm[2] }); }
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
