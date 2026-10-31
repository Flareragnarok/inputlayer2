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
import juloo.keyboard2.Config;
import juloo.keyboard2.Keyboard2View;
import juloo.keyboard2.Theme;

/** The edit strip, under the space bar. Left: the scope (Word, Sentence,
    Line, Paragraph, All) and the selector, which selects the scope's unit at
    the cursor, moves to the previous or next one, grows or shrinks the
    selection by one. Right: Appearance (case, unicode styles) and Format
    (the app's markup), applied to the selection or to the scope's unit at
    the cursor. */
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
  }

  static final String KEY_SYNTAX_PREFIX = "studio_format_";
  /** Text read around the cursor when the editor doesn't give it all. */
  static final int CONTEXT_CHARS = 4000;

  static final ComfortKeySpec SCOPE_KEY =
    ComfortKeySpec.of("c", "scope_next", "s", "scope_prev");
  static final ComfortKeySpec SELECTOR_KEY =
    ComfortKeySpec.of("c", "select", "w", "select_prev", "e", "select_next",
        "n", "select_extend", "s", "select_shrink", "sw", "deselect");
  static final ComfortKeySpec APPEARANCE_KEY = ComfortKeySpec.of("c", "appearance");
  static final ComfortKeySpec FORMAT_KEY = ComfortKeySpec.of("c", "format");

  Listener _listener;
  SharedPreferences _prefs;
  Config _config;
  TextScope _scope = TextScope.WORD;
  LinearLayout _left;
  LinearLayout _right;
  View _appearance_key;
  View _format_key;
  /** Navigation bar height on Android 15+, kept clear below the keys. */
  int _bottom_inset = 0;

  public EditStrip(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
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
    setVisibility(VISIBLE);
    ViewGroup.LayoutParams p = getLayoutParams();
    p.height = (int)(dp * density()) + _bottom_inset;
    setLayoutParams(p);
    setPadding(0, 0, 0, _bottom_inset);
    removeAllViews();
    addView(make_key(SCOPE_KEY, 2.6f));
    addView(make_key(SELECTOR_KEY, 2.2f));
    addView(new View(getContext()), new LayoutParams(0, 1, 1f));
    _appearance_key = make_key(APPEARANCE_KEY, 1.7f);
    _format_key = make_key(FORMAT_KEY, 1.7f);
    addView(_appearance_key);
    addView(_format_key);
  }

  View make_key(ComfortKeySpec s, float width)
  {
    View k = new ComfortKey(getContext(), s, this, Theme.getKeyFont(getContext()));
    int h = (int)(_config.studio.edit_strip_dp * density());
    LayoutParams p = new LayoutParams((int)(h * width), h - dp(4));
    p.setMargins(dp(8), dp(2), dp(8), dp(2));
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
      case "scope_next": _scope = _scope.next(); refresh(_config); return;
      case "scope_prev": _scope = _scope.prev(); refresh(_config); return;
      case "appearance": appearance_menu(); return;
      case "format": format_menu(); return;
    }
    Field f = read();
    if (f == null)
      return;
    int[] sel = { f.sel_start, f.sel_end };
    int[] r = null;
    switch (action)
    {
      case "select":
        r = _scope.unit_at(f.text, f.sel_start);
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
      case "scope_next": return new ComfortKey.Label(_scope.label, false);
      case "scope_prev": return new ComfortKey.Label("▾", false);
      case "select": return new ComfortKey.Label("[ ]", false);
      case "select_prev": return new ComfortKey.Label("‹", false);
      case "select_next": return new ComfortKey.Label("›", false);
      case "select_extend": return new ComfortKey.Label("+", false);
      case "select_shrink": return new ComfortKey.Label("−", false);
      case "deselect": return new ComfortKey.Label("×", false);
      case "appearance": return new ComfortKey.Label("Aa", false);
      case "format": return new ComfortKey.Label("¶", false);
      default: return new ComfortKey.Label("?", false);
    }
  }

  public float swipe_dist_px()
  {
    return Math.max(dp(8), _config.swipe_dist_px / 2);
  }

  public long repeat_delay() { return _config.longPressTimeout; }

  public long repeat_interval() { return _config.longPressInterval; }

  /* Appearance and Format */

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
    for (final String st : UnicodeStyles.STYLES)
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

  void format_menu()
  {
    final String app = _listener.app();
    final TextFormats.Syntax syntax = TextFormats.for_app(app,
        (app == null) ? null : _prefs.getString(KEY_SYNTAX_PREFIX + app, null));
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
      add(m, "No formatting in this app: use Aa", new Runnable() {
        public void run() { appearance_menu(); }
      });
    for (final java.util.Map.Entry<String, String[]> e : syntax.formats.entrySet())
      add(m, e.getKey(), new Runnable() {
        public void run() { format_target(e.getValue()); }
      });
    p.show();
  }

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

  void format_target(String[] markers)
  {
    Field f = read();
    if (f == null)
      return;
    int[] r = target(f);
    if (r == null)
    {
      // Nothing to wrap: insert the markers around the cursor.
      r = new int[]{ f.sel_start, f.sel_start };
    }
    TextFormats.Edit e = TextFormats.toggle(f.text, r, markers);
    replace(f, e.start, e.end, e.text, e.sel_start, e.sel_end);
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
