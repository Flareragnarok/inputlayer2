package juloo.keyboard2.studio;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.Config;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.KeyboardData;
import juloo.keyboard2.Logs;
import juloo.keyboard2.Theme;
import org.json.JSONObject;

/** The comfort bar, above the suggestion bar: a few tiny keys, centered and
    spaced apart, chosen by the active [comfortbar] module. Each key has a
    tap action and swipe actions.
    Actions are built in; modules only choose and place them. */
public class ComfortBar extends FrameLayout implements ComfortKey.Host
{
  public interface Listener
  {
    /** Act as if key [kv] was typed. */
    void comfort_key(KeyValue kv);
    void open_studio();
    void open_dictionaries();
    /** il-005: a switching action ([spec] null: its default). */
    void behave(String base, Behaviours.Spec spec);
    /** Layout mode: tapping a key of the keyboard opens it in Studio. */
    void toggle_layout_mode();
    boolean layout_mode();
    /** Highlight container [target] (one of [TARGETS]), or none if [-1].
        Targets from [TARGET_ROWS] on are the keyboard's rows. */
    void highlight(int target);
    /** Rows of the keyboard shown. */
    int keyboard_rows();
    /** The layouts switched between (Studio → Languages), and the one
        shown. */
    List<KeyboardData> layouts();
    int current_layout();
    void select_layout(int i);
    /** The edit key: the keyboard shown, and its focus changed (redraw the
        outlines from [layer], [target], [sel_row], [sel_pos]). */
    KeyboardData shown_keyboard();
    void focus_changed();
    /** Studio's picker for a key added at [pos] in row [row] of [layout],
        whose rows are [rows] (key ids). */
    void open_add_key(String layout, List<List<String>> rows, int row, int pos);
    /** Studio's key editor for the key at [pos] in row [row]. */
    void open_key_editor_at(int row, int pos);
  }

  /** Containers the height key resizes, in the order it selects them. Only
      whole containers, never the elements inside them. */
  public static final int TARGET_COMFORT_BAR = 0;
  public static final int TARGET_SUGGESTIONS = 1;
  public static final int TARGET_KEYBOARD = 2;
  public static final int TARGET_EDIT_STRIP = 3;
  public static final int TARGET_SELECTION_BAR = 4;
  static final String[] TARGET_LABELS = { "bar", "sugg", "keys", "edit", "sel" };
  /** Targets from here on are keyboard rows, top to bottom. */
  public static final int TARGET_ROWS = 5;
  /** Names of the targets in [StudioConfig.order]. */
  static final String[] TARGET_NAMES = { "comfort", "suggestions", "keyboard", "edit", "selection" };

  /** Container selected by the height key, [-1] for none (height up/down
      then resize the keyboard). */
  int _target = -1;
  Listener _listener;
  SharedPreferences _prefs;
  Config _config;
  LinearLayout _keys;

  public ComfortBar(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
  }

  public void setup(Listener l, SharedPreferences prefs)
  {
    _listener = l;
    _prefs = prefs;
    removeAllViews();
    _keys = new LinearLayout(getContext());
    _keys.setOrientation(LinearLayout.HORIZONTAL);
    _keys.setGravity(Gravity.CENTER);
    addView(_keys, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
          ViewGroup.LayoutParams.MATCH_PARENT));
  }

  /** Rebuild the keys, called whenever the config changes. */
  public void refresh(Config config)
  {
    _config = config;
    float dp = config.studio.comfort_bar_dp;
    if (_listener == null || dp <= 0)
    {
      setVisibility(GONE);
      return;
    }
    setVisibility(VISIBLE);
    ViewGroup.LayoutParams p = getLayoutParams();
    p.height = (int)(dp * density());
    setLayoutParams(p);
    // Rebuilding would remove the key under the finger in the middle of a
    // gesture (height keys save on every repeat): only relabel when the keys
    // are the same.
    if (dp == _built_dp && same_keys(config.studio.keys, _built_keys)
        && _keys.getChildCount() == _built_keys.size())
    {
      for (int i = 0; i < _keys.getChildCount(); i++)
        ((ComfortKey)_keys.getChildAt(i)).relabel();
      return;
    }
    _built_dp = dp;
    _built_keys = config.studio.keys;
    _keys.removeAllViews();
    for (ComfortKeySpec s : config.studio.keys)
      _keys.addView(make_key(s), key_params());
  }

  List<ComfortKeySpec> _built_keys = null;
  float _built_dp = -1;

  static boolean same_keys(List<ComfortKeySpec> a, List<ComfortKeySpec> b)
  {
    if (a == null || b == null || a.size() != b.size())
      return false;
    for (int i = 0; i < a.size(); i++)
      if (!java.util.Arrays.equals(a.get(i).actions, b.get(i).actions))
        return false;
    return true;
  }

  /** The edit key (its tap selects the layer of focus), [null] if not on
      the bar. */
  public View edit_key_view()
  {
    if (_keys == null)
      return null;
    for (int i = 0; i < _keys.getChildCount(); i++)
    {
      View v = _keys.getChildAt(i);
      if (v instanceof ComfortKey && "select_container".equals(((ComfortKey)v)._spec.get(0)))
        return v;
    }
    return null;
  }

  View make_key(ComfortKeySpec s)
  {
    return new ComfortKey(getContext(), s, this, Theme.getKeyFont(getContext()));
  }

  LayoutParams key_params()
  {
    int h = (int)(_config.studio.comfort_bar_dp * density());
    LayoutParams p = new LayoutParams((int)(h * 1.7f), h - dp(4));
    p.setMargins(dp(10), dp(2), dp(10), dp(2));
    return p;
  }

  /* ComfortKey.Host */

  public void perform(String action)
  {
    // il-005: repeat, backspace split and edit mode switch through the
    // keyboard's behaviours ("toggle_repeat~once" places a variant).
    String base = Behaviours.base_of(action);
    Behaviours.Capable cap = Behaviours.capable(base);
    if (cap != null && cap.comfort)
    {
      _listener.behave(base, Behaviours.spec_of(action));
      if (base.equals("layout_mode"))
        refresh(_config); // Update the key's label.
      return;
    }
    if (action.equals("select_container"))
      next_layer();
    else if (action.equals("edit_menu"))
      edit_menu();
    else if (action.equals("dictionary"))
    {
      // The switcher also offers downloading. Without any language to
      // switch to, go to the download screen directly.
      if (_config.should_show_dictionary_switch)
        _listener.comfort_key(KeyValue.getKeyByName("change_dictionary"));
      else
        _listener.open_dictionaries();
    }
    else if (action.equals("move_up") || action.equals("move_down"))
      op_move(action.equals("move_up") ? -1 : 1);
    else if (action.equals("height_up"))
      op_size(true);
    else if (action.equals("height_down"))
      op_size(false);
    else if (action.equals("layouts_save"))
    {
      try { toast("Saved as " + SavedLayouts.save_current(new ModuleStore(_prefs))); }
      catch (Exception e) { Logs.exn("Save layout", e); }
    }
    else if (action.equals("layouts_pick"))
      layouts_pick();
    else if (action.equals("height_undo"))
      back();
    else if (action.equals("height_revert"))
      revert_all();
    else if (action.startsWith("switch:"))
      cycle_module(action.substring("switch:".length()));
    else if (action.equals("studio"))
      _listener.open_studio();
    else if (action.equals("layout_menu"))
      layout_menu();
    else
    {
      String name = key_name(action);
      KeyValue kv = (name == null) ? null : KeyValue.getKeyByName(name);
      if (kv != null)
        _listener.comfort_key(kv);
    }
  }

  public ComfortKey.Label label_of(String action)
  {
    if (Behaviours.spec_of(action) != null && Behaviours.capable(Behaviours.base_of(action)) != null)
    {
      ComfortKey.Label l = label_of(Behaviours.base_of(action));
      return new ComfortKey.Label(l.text + Behaviours.spec_of(action).mark(), l.key_font);
    }
    switch (action)
    {
      case "select_container": return new ComfortKey.Label(focus_label(), false);
      case "edit_menu": return new ComfortKey.Label("⋯", false);
      case "move_up": return new ComfortKey.Label("⇡", false);
      case "move_down": return new ComfortKey.Label("⇣", false);
      case "height_up": return new ComfortKey.Label("+", false);
      case "height_down": return new ComfortKey.Label("−", false);
      case "height_undo": return new ComfortKey.Label("↶", false);
      case "split_backspace":
        return new ComfortKey.Label(_config.studio.split_backspace ? "⌫⌦" : "⌫", false);
      case "layouts_save": return new ComfortKey.Label("⤓", false);
      case "layouts_pick": return new ComfortKey.Label("▤", false);
      case "toggle_repeat":
        return new ComfortKey.Label(_config.keyrepeat_enabled ? "↻" : "✎", false);
      case "layout_mode":
        return new ComfortKey.Label(_listener.layout_mode() ? "⇄ ✓" : "⇄", false);
      case "height_revert": return new ComfortKey.Label("⟲", false);
      case "studio": return new ComfortKey.Label("⚙", false);
      // Tap: the suggestions' language, like upstream's button on the
      // suggestion bar.
      case "dictionary": return new ComfortKey.Label(language_label(), false);
      // The layout shown, as a language code: tap switches to the next.
      case "layout": return new ComfortKey.Label(current_layout_code(), false);
      case "layout_prev": return new ComfortKey.Label("‹", false);
      case "layout_menu": return new ComfortKey.Label("☰", false);
      case "keyboard_picker": return new ComfortKey.Label("⌨", false);
    }
    if (action.startsWith("switch:"))
    {
      String type = action.substring("switch:".length());
      Module active = Module.is_known_type(type) ?
        new ModuleStore(_prefs).active(type) : null;
      String name = (active == null) ? "" : " " + shorten(active.name);
      return new ComfortKey.Label(type_symbol(type) + name, false);
    }
    String name = key_name(action);
    KeyValue kv = (name == null) ? null : KeyValue.getKeyByName(name);
    if (kv == null)
      return new ComfortKey.Label("?", false);
    return new ComfortKey.Label(kv.getString(),
        kv.hasFlagsAny(KeyValue.FLAG_KEY_FONT));
  }

  public float swipe_dist_px()
  {
    return Math.max(dp(8), _config.swipe_dist_px / 2);
  }

  public long repeat_delay() { return _config.longPressTimeout; }

  public long repeat_interval() { return _config.longPressInterval; }

  /** Name of the keyboard key that does [action], or [null]. */
  static String key_name(String action)
  {
    switch (action)
    {
      case "left": case "right": case "up": case "down":
      case "copy": case "cut": case "paste":
        return action;
      // Ctrl+Z / Ctrl+Shift+Z: the editing actions often do nothing.
      case "undo": return "studio_undo";
      case "redo": return "studio_redo";
      case "select_all": return "selectAll";
      case "layout": return "switch_forward";
      case "layout_prev": return "switch_backward";
      case "keyboard_picker": return "change_method";
      case "clipboard": return "switch_clipboard";
      default:
        // il-004: "app:<package>[?k=v&…]" opens an app, as on a key.
        return action.startsWith(KeyValue.APP_PREFIX) ? action : null;
    }
  }

  /** Actions that repeat while held. */
  static boolean repeats(String action)
  {
    switch (action)
    {
      case "left": case "right": case "up": case "down":
      case "undo": case "redo": case "height_up": case "height_down":
        return true;
      default: return false;
    }
  }

  static String type_symbol(String type)
  {
    switch (type)
    {
      case "changeheight": return "↕";
      case "comfortbar": return "☰";
      case "snippets": return "✎";
      case "theme": return "◐";
      default: return type;
    }
  }

  /** Short name of the suggestions' language, e.g. "EN". */
  String language_label()
  {
    String n = _config.current_dictionary_name;
    String code = (n == null || n.isEmpty()) ? "🌐" :
      n.substring(0, Math.min(2, n.length())).toUpperCase();
    // No dictionary installed for it yet: tap downloads.
    return (_config.current_dictionary == null) ? "⬇ " + code : code;
  }

  static String shorten(String s)
  {
    return (s.length() <= 5) ? s : s.substring(0, 4) + "…";
  }

  /** Clear the container selection, e.g. when the keyboard is shown again. */
  public void reset_target()
  {
    if (_target < 0 && _layer == LAYER_OFF)
      return;
    _target = -1;
    _layer = LAYER_OFF;
    _sel_row = -1;
    _sel_pos = -1;
    _listener.highlight(-1);
    _listener.focus_changed();
    if (_config != null)
      refresh(_config);
  }

  /* The edit key. Its tap cycles the layer of focus: whole keyboard, bars,
     components (a keyboard row, or a bar), items (a key). In a layer, tap
     the element itself to select it (the keyboard catches the taps, see
     [tapped]); the edit key's swipes then act on it: ↑ ↓ size, ← → order,
     ↗ more (the menu), ↖ undo, ↙ undo all. */

  public static final int LAYER_OFF = 0, LAYER_WHOLE = 1, LAYER_BARS = 2,
         LAYER_COMPONENTS = 3, LAYER_ITEMS = 4;
  static final String[] LAYER_LABELS = { "", "all", "bars", "rows", "keys" };
  static final String[] LAYER_HINTS = { "",
    "Whole keyboard: ↑ ↓ resize everything",
    "Bars: tap one, then ↑ ↓ size, ← → order",
    "Rows: tap a row, then ↑ ↓ height, ← → order, ↗ more",
    "Keys: tap one, then ↑ ↓ width, ← → order, ↗ more" };
  /** Item sizes: the space comes from the whole row (else from the key next
      to it). */
  public static final String KEY_SIZE_FROM_ROW = "studio_edit_size_from_row";
  static final float WIDTH_STEP = 0.25f;

  int _layer = LAYER_OFF;
  /** Selected keyboard row and key (its position in the row), [-1]. */
  int _sel_row = -1;
  int _sel_pos = -1;

  public int layer() { return _layer; }
  public int target() { return _target; }
  public int sel_row() { return _sel_row; }
  public int sel_pos() { return _sel_pos; }
  /** Whether taps on the keyboard and bars select instead of typing. */
  public boolean focusing() { return _layer != LAYER_OFF; }

  void next_layer()
  {
    _layer = (_layer + 1) % LAYER_LABELS.length;
    _target = -1;
    _sel_row = -1;
    _sel_pos = -1;
    if (_layer != LAYER_OFF)
      toast(LAYER_HINTS[_layer]);
    _listener.highlight(-1);
    _listener.focus_changed();
    refresh(_config);
  }

  String focus_label()
  {
    if (_layer == LAYER_OFF)
      return (_target < 0) ? "↕" : "↕ " + target_label();
    if (_layer == LAYER_ITEMS && _sel_pos >= 0)
      return "↕ key";
    if (_layer == LAYER_COMPONENTS && _sel_row >= 0)
      return "↕ row " + (_sel_row + 1);
    if (_layer == LAYER_BARS && _target >= 0)
      return "↕ " + target_label();
    return "↕ " + LAYER_LABELS[_layer];
  }

  /** A tap while focusing: on part [target], and if it's the keyboard, on
      its row [row] and that row's key [pos] ([-1] when none). */
  public void tapped(int target, int row, int pos)
  {
    switch (_layer)
    {
      case LAYER_BARS:
        _target = target;
        _listener.highlight(_target);
        break;
      case LAYER_COMPONENTS:
        if (target == TARGET_KEYBOARD && row >= 0)
        {
          _sel_row = row;
          _target = TARGET_ROWS + row;
          _listener.highlight(-1);
        }
        else
        {
          _sel_row = -1;
          _target = target;
          _listener.highlight(_target);
        }
        break;
      case LAYER_ITEMS:
        if (target == TARGET_KEYBOARD && row >= 0 && pos >= 0)
        {
          _sel_row = row;
          _sel_pos = pos;
          _target = TARGET_ROWS + row;
        }
        else
          toast("Keys: only the keyboard's for now");
        break;
      default:
        return;
    }
    _listener.focus_changed();
    refresh(_config);
  }

  void op_size(boolean bigger)
  {
    switch (_layer)
    {
      case LAYER_WHOLE: scale_all(bigger); return;
      case LAYER_ITEMS: resize_key(bigger); return;
      case LAYER_BARS: case LAYER_COMPONENTS:
        if (_target < 0)
        {
          toast(LAYER_HINTS[_layer]);
          return;
        }
        change_height(bigger);
        return;
      default: change_height(bigger); // No layer: as before, keyboard or target.
    }
  }

  void op_move(int dir)
  {
    try
    {
      if (_layer == LAYER_ITEMS && _sel_pos >= 0)
      {
        List<List<String>> rows = rows_now();
        if (rows != null && Arrange.move_key(rows, _sel_row, _sel_pos, dir))
        {
          save_rows(rows, null);
          _sel_pos += dir;
          _listener.focus_changed();
        }
      }
      else if (_layer == LAYER_COMPONENTS && _sel_row >= 0)
      {
        List<List<String>> rows = rows_now();
        if (rows != null && Arrange.move_row(rows, _sel_row, dir))
        {
          // The row's height goes with it.
          float[] fs = row_factors(rows.size());
          float t = fs[_sel_row]; fs[_sel_row] = fs[_sel_row + dir]; fs[_sel_row + dir] = t;
          save_rows(rows, fs);
          _sel_row += dir;
          _target = TARGET_ROWS + _sel_row;
          _listener.focus_changed();
          refresh(_config);
        }
      }
      else
        move(dir);
    }
    catch (Exception e)
    {
      Logs.exn("Edit key move", e);
    }
  }

  /** The whole keyboard bigger or smaller, the bars in proportion. */
  void scale_all(boolean bigger)
  {
    ModuleStore store = new ModuleStore(_prefs);
    Module m = heights_module(store);
    try
    {
      JSONObject r = Heights.scale_all(m == null ? new JSONObject() : m.json, bigger,
          _config.orientation_landscape, _config.keyboard_height_percent);
      if (r == null)
        return;
      remember_undo(store);
      store.save((m == null) ? Module.create("changeheight", "quick", r) : new Module(r), true);
    }
    catch (Exception e)
    {
      Logs.exn("Edit key scale", e);
    }
  }

  /** The selected key a quarter wider (narrower), the row keeping its
      width: from the key next to it, or the whole row (menu). */
  void resize_key(boolean bigger)
  {
    if (_sel_pos < 0)
    {
      toast(LAYER_HINTS[LAYER_ITEMS]);
      return;
    }
    KeyboardData kw = _listener.shown_keyboard();
    if (kw == null || _sel_row >= kw.rows.size()
        || _sel_pos >= kw.rows.get(_sel_row).keys.size())
      return;
    List<KeyboardData.Key> ks = kw.rows.get(_sel_row).keys;
    String layout = KeyMap.layout_key(kw);
    KeyMap km = keymap();
    float[] w = new float[ks.size()];
    for (int i = 0; i < w.length; i++)
      w[i] = ks.get(i).width;
    boolean from_row = _prefs.getBoolean(KEY_SIZE_FROM_ROW, false);
    float[] nw = Arrange.resize(w, _sel_pos, bigger ? WIDTH_STEP : -WIDTH_STEP,
        from_row, KeyMap.WIDTH_MIN, KeyMap.WIDTH_MAX);
    if (nw == null)
    {
      toast(from_row ? "No room left in the row" : "No room: the key next to it is at its smallest (↗: size from the whole row)");
      return;
    }
    try
    {
      ModuleStore store = new ModuleStore(_prefs);
      Module m = keymap_module(store);
      JSONObject j = (m == null) ? new JSONObject() : m.json;
      for (int i = 0; i < w.length; i++)
        if (nw[i] != w[i])
        {
          if (ks.get(i).keys[0] == null)
            return; // An empty key: can't be named.
          j = KeyMap.set_width(j, layout, km.id_of(layout, ks.get(i)), nw[i]);
        }
      remember_undo(store);
      store.save((m == null) ? Module.create("keymap", KeyEditor.NEW_MODULE, j) : new Module(j), true);
    }
    catch (Exception e)
    {
      Logs.exn("Edit key width", e);
    }
  }

  /** The edit key's ↗: what else can be done with the selection. */
  void edit_menu()
  {
    final List<String> labels = new ArrayList<String>();
    final List<Runnable> acts = new ArrayList<Runnable>();
    final KeyboardData kw = _listener.shown_keyboard();
    if (_layer == LAYER_ITEMS && _sel_pos >= 0 && kw != null)
    {
      final boolean from_row = _prefs.getBoolean(KEY_SIZE_FROM_ROW, false);
      labels.add(from_row ? "Size from: the whole row ✓ (tap: the key next to it)"
          : "Size from: the key next to it ✓ (tap: the whole row)");
      acts.add(new Runnable() { public void run() {
        _prefs.edit().putBoolean(KEY_SIZE_FROM_ROW, !from_row).apply(); } });
      labels.add("Add a key after this one…");
      acts.add(new Runnable() { public void run() { add_key(_sel_row, _sel_pos + 1); } });
      labels.add("Edit this key: tap, swipes, split…");
      acts.add(new Runnable() { public void run() {
        _listener.open_key_editor_at(_sel_row, _sel_pos); } });
      labels.add("Remove this key");
      acts.add(new Runnable() { public void run() { remove_key(); } });
      labels.add("Split the row before this key");
      acts.add(new Runnable() { public void run() { split_row(_sel_row, _sel_pos); } });
    }
    else if (_layer == LAYER_COMPONENTS && _sel_row >= 0 && kw != null)
    {
      final int n = kw.rows.get(_sel_row).keys.size();
      labels.add("Add a key at the end of the row…");
      acts.add(new Runnable() { public void run() { add_key(_sel_row, n); } });
      labels.add("Split the row in two");
      acts.add(new Runnable() { public void run() { split_row(_sel_row, n / 2); } });
      labels.add("Join with the row below");
      acts.add(new Runnable() { public void run() { join_row(_sel_row); } });
    }
    else
    {
      toast((_layer == LAYER_OFF) ? "Tap the edit key to pick a layer first"
          : LAYER_HINTS[_layer]);
      return;
    }
    labels.add("Arrangement back to the layout's own");
    acts.add(new Runnable() { public void run() { reset_rows(); } });
    android.widget.PopupMenu p = new android.widget.PopupMenu(getContext(), this);
    for (int i = 0; i < labels.size(); i++)
    {
      final int idx = i;
      p.getMenu().add(labels.get(i)).setOnMenuItemClickListener(
          new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it)
            {
              _gesture_saved = false; // Its own undo step.
              acts.get(idx).run();
              return true;
            }
          });
    }
    p.show();
  }

  KeyMap keymap()
  {
    Module m = new ModuleStore(_prefs).active("keymap");
    return KeyMap.of_json(m == null ? null : m.json);
  }

  /** The active [keymap] module, or "mine" (made active when saved). */
  static Module keymap_module(ModuleStore store)
  {
    Module m = store.active("keymap");
    return (m != null) ? m : store.get("keymap", KeyEditor.NEW_MODULE);
  }

  static Module heights_module(ModuleStore store)
  {
    Module m = store.active("changeheight");
    return (m != null) ? m : store.get("changeheight", "quick");
  }

  /** The rows shown, as key ids: what the arrangement operations change. */
  List<List<String>> rows_now()
  {
    KeyboardData kw = _listener.shown_keyboard();
    return (kw == null) ? null : keymap().rows_now(kw);
  }

  /** The row height factors, [n] of them. */
  float[] row_factors(int n)
  {
    float[] fs = new float[n];
    for (int i = 0; i < n; i++)
      fs[i] = _config.studio.row_factor(i);
    return fs;
  }

  /** Save [rows] as the arrangement of the layout shown and, if not
      [null], the row heights [factors]: one undo step. */
  void save_rows(List<List<String>> rows, float[] factors) throws Exception
  {
    KeyboardData kw = _listener.shown_keyboard();
    if (kw == null)
      return;
    ModuleStore store = new ModuleStore(_prefs);
    remember_undo(store);
    Module m = keymap_module(store);
    JSONObject j = KeyMap.set_rows(m == null ? new JSONObject() : m.json,
        KeyMap.layout_key(kw), rows);
    store.save((m == null) ? Module.create("keymap", KeyEditor.NEW_MODULE, j) : new Module(j), true);
    if (factors != null)
    {
      Module h = heights_module(store);
      JSONObject hj = (h == null) ? new JSONObject() : new JSONObject(h.json.toString());
      org.json.JSONArray a = new org.json.JSONArray();
      for (float f : factors)
        a.put(Math.round(f * 100) / 100.0);
      hj.put("rows", a);
      store.save((h == null) ? Module.create("changeheight", "quick", hj) : new Module(hj), true);
    }
  }

  void add_key(int row, int pos)
  {
    KeyboardData kw = _listener.shown_keyboard();
    List<List<String>> rows = rows_now();
    if (kw != null && rows != null)
      _listener.open_add_key(KeyMap.layout_key(kw), rows, row, pos);
  }

  void remove_key()
  {
    try
    {
      List<List<String>> rows = rows_now();
      int n = (rows == null) ? 0 : rows.size();
      if (rows == null || !Arrange.remove(rows, _sel_row, _sel_pos))
        return;
      float[] fs = null;
      if (rows.size() < n) // The row went too.
        fs = without(row_factors(n), _sel_row);
      save_rows(rows, fs);
      _sel_pos = -1;
      _listener.focus_changed();
      refresh(_config);
    }
    catch (Exception e)
    {
      Logs.exn("Edit key remove", e);
    }
  }

  void split_row(int row, int pos)
  {
    try
    {
      List<List<String>> rows = rows_now();
      if (rows == null)
        return;
      int n = rows.size();
      if (!Arrange.split_row(rows, row, pos))
      {
        toast("Nothing to split here");
        return;
      }
      // The new row is as high as the one it came from.
      float[] fs = row_factors(n), nf = new float[n + 1];
      for (int i = 0; i <= n; i++)
        nf[i] = fs[(i <= row) ? i : i - 1];
      save_rows(rows, nf);
    }
    catch (Exception e)
    {
      Logs.exn("Edit key split row", e);
    }
  }

  void join_row(int row)
  {
    try
    {
      List<List<String>> rows = rows_now();
      if (rows == null)
        return;
      int n = rows.size();
      if (!Arrange.join(rows, row))
      {
        toast("No row below");
        return;
      }
      save_rows(rows, without(row_factors(n), row + 1));
    }
    catch (Exception e)
    {
      Logs.exn("Edit key join", e);
    }
  }

  void reset_rows()
  {
    try
    {
      save_rows(null, null);
      _sel_pos = -1;
      _listener.focus_changed();
    }
    catch (Exception e)
    {
      Logs.exn("Edit key reset", e);
    }
  }

  static float[] without(float[] a, int i)
  {
    if (i < 0 || i >= a.length)
      return a;
    float[] r = new float[a.length - 1];
    for (int j = 0, k = 0; j < a.length; j++)
      if (j != i)
        r[k++] = a[j];
    return r;
  }

  /** Resize the selected container (the keyboard if none), stored in the
      active [changeheight] module or in a new one named "quick". The new
      value comes from the module only ([Heights.step]), and nothing is
      saved at the edge of a range. */
  void change_height(boolean bigger)
  {
    ModuleStore store = new ModuleStore(_prefs);
    Module m = store.active("changeheight");
    if (m == null)
      m = store.get("changeheight", "quick");
    try
    {
      JSONObject j = (m == null) ? new JSONObject() : m.json;
      JSONObject r = Heights.step(j, _target, bigger, _listener.keyboard_rows(),
          _config.orientation_landscape, _config.keyboard_height_percent);
      if (r == null)
        return;
      remember_undo(store);
      Module nm = (m == null) ? Module.create("changeheight", "quick", r) : new Module(r);
      store.save(nm, true);
    }
    catch (Exception e)
    {
      Logs.exn("Comfort bar height", e);
    }
  }

  /* Undo. Every height change and move since the keyboard was shown can be
     undone with Back, one gesture at a time (a held key is one gesture). */

  static final int MAX_UNDO = 100;
  /** Each step: the heights and the key arrangement as they were. */
  final java.util.ArrayDeque<ModuleStore.Snapshot[]> _undo =
    new java.util.ArrayDeque<ModuleStore.Snapshot[]>();
  /** Whether the gesture in progress already has its undo step. */
  boolean _gesture_saved = false;

  public void gesture_started()
  {
    _gesture_saved = false;
  }

  void remember_undo(ModuleStore store)
  {
    if (_gesture_saved)
      return;
    _gesture_saved = true;
    _undo.push(new ModuleStore.Snapshot[]{
      store.snapshot("changeheight"), store.snapshot("keymap") });
    if (_undo.size() > MAX_UNDO)
      _undo.removeLast();
  }

  /** Whether Back is ours: there is something to undo, or a container is
      selected. */
  public boolean wants_back()
  {
    return !_undo.isEmpty() || _target >= 0 || _layer != LAYER_OFF;
  }

  void restore(ModuleStore.Snapshot[] s)
  {
    ModuleStore store = new ModuleStore(_prefs);
    for (ModuleStore.Snapshot x : s)
      store.restore(x);
  }

  /** Back: undo the last height change, or else leave the container
      selection. */
  public void back()
  {
    if (!_undo.isEmpty())
    {
      _gesture_saved = false;
      restore(_undo.pop());
    }
    else
      reset_target();
  }

  /** Long Back: undo every change since the keyboard was shown. */
  public void revert_all()
  {
    if (!_undo.isEmpty())
      restore(_undo.getLast());
    _undo.clear();
    _gesture_saved = false;
  }

  /** The keyboard is hidden: the changes are kept, Back is free again. */
  public void commit()
  {
    _undo.clear();
    _gesture_saved = false;
  }

  String target_label()
  {
    return (_target >= TARGET_ROWS) ? "row " + (_target - TARGET_ROWS + 1)
      : TARGET_LABELS[_target];
  }

  /** Move the selected container up ([-1]) or down one place in the stack,
      saved in the active [changeheight] module (or "quick"). */
  void move(int dir)
  {
    // Rows keep their place in the layout.
    if (_target < 0 || _target >= TARGET_ROWS)
      return;
    List<String> order = new java.util.ArrayList<String>(_config.studio.order);
    int i = order.indexOf(TARGET_NAMES[_target]);
    int j = i + dir;
    if (i < 0 || j < 0 || j >= order.size())
      return;
    order.set(i, order.get(j));
    order.set(j, TARGET_NAMES[_target]);
    ModuleStore store = new ModuleStore(_prefs);
    Module m = store.active("changeheight");
    if (m == null)
      m = store.get("changeheight", "quick");
    try
    {
      JSONObject o = (m == null) ? new JSONObject() : new JSONObject(m.json.toString());
      o.put("order", new org.json.JSONArray(order));
      remember_undo(store);
      Module nm = (m == null) ? Module.create("changeheight", "quick", o) : new Module(o);
      store.save(nm, true);
    }
    catch (Exception e)
    {
      Logs.exn("Comfort bar move", e);
    }
  }

  /** Next saved module of [type], then none, then the first again. */
  void cycle_module(String type)
  {
    ModuleStore store = new ModuleStore(_prefs);
    List<Module> ms = store.list(type);
    if (ms.isEmpty())
      return;
    Module active = store.active(type);
    int i = -1;
    if (active != null)
      for (int j = 0; j < ms.size(); j++)
        if (ms.get(j).name.equals(active.name))
          i = j;
    store.set_active(type, (i + 1 < ms.size()) ? ms.get(i + 1).name : null);
  }

  float density()
  {
    return getResources().getDisplayMetrics().density;
  }

  int dp(int v)
  {
    return (int)(v * density());
  }

  /* Layouts */

  String current_layout_code()
  {
    List<KeyboardData> ls = _listener.layouts();
    int i = _listener.current_layout();
    return (i >= 0 && i < ls.size()) ? layout_code(ls.get(i)) : "🌐";
  }

  /** A short name for a layout: the language it is for. */
  static String layout_code(KeyboardData l)
  {
    if (l == null)
      return "🌐";
    String n = (l.name == null) ? "" : l.name;
    if ("cyrillic".equals(l.script))
      return "RU";
    if ("greek".equals(l.script))
      return "EL";
    if (n.contains("AZERTY"))
      return "FR";
    if (n.contains("QWERTZ"))
      return "DE";
    if (n.contains("QWERTY"))
      return "EN";
    return n.isEmpty() ? "⌨" : n.substring(0, Math.min(2, n.length())).toUpperCase();
  }

  /** The layouts switched between, to pick one. */
  void toast(String msg)
  {
    android.widget.Toast.makeText(getContext(), msg, android.widget.Toast.LENGTH_SHORT).show();
  }

  /** The saved layouts (key arrangements), the one in use ticked. */
  void layouts_pick()
  {
    final ModuleStore store = new ModuleStore(_prefs);
    Module active = store.active("keymap");
    String cur = (active == null) ? null : active.name;
    android.widget.PopupMenu p = new android.widget.PopupMenu(getContext(), this);
    p.getMenu().add(((cur == null) ? "✓ " : "") + "No changes (the layouts' own)")
      .setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
        public boolean onMenuItemClick(android.view.MenuItem it)
        {
          SavedLayouts.pick(store, null);
          return true;
        }
      });
    for (final Module m : SavedLayouts.list(store))
      p.getMenu().add((m.name.equals(cur) ? "✓ " : "") + m.name)
        .setOnMenuItemClickListener(new android.view.MenuItem.OnMenuItemClickListener() {
          public boolean onMenuItemClick(android.view.MenuItem it)
          {
            SavedLayouts.pick(store, m.name);
            return true;
          }
        });
    if (SavedLayouts.list(store).isEmpty())
      toast("No saved layout yet: swipe ↓ on this key to save one");
    p.show();
  }

  void layout_menu()
  {
    android.widget.PopupMenu p = new android.widget.PopupMenu(getContext(), this);
    List<KeyboardData> ls = _listener.layouts();
    for (int i = 0; i < ls.size(); i++)
    {
      final int idx = i;
      KeyboardData l = ls.get(i);
      String label = layout_code(l) + "  " + ((l == null || l.name == null) ? "" : l.name);
      p.getMenu().add(label).setOnMenuItemClickListener(
          new android.view.MenuItem.OnMenuItemClickListener() {
            public boolean onMenuItemClick(android.view.MenuItem it)
            {
              _listener.select_layout(idx);
              return true;
            }
          });
    }
    p.show();
  }
}
