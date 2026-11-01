package juloo.keyboard2.studio;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
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
    if (action.equals("select_container"))
    {
      // Containers, then each row of the keyboard, then none.
      int n = TARGET_ROWS + _listener.keyboard_rows();
      _target = (_target + 2) % (n + 1) - 1;
      _listener.highlight(_target);
      refresh(_config); // Update the key's label.
    }
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
      move(action.equals("move_up") ? -1 : 1);
    else if (action.equals("height_up"))
      change_height(true);
    else if (action.equals("height_down"))
      change_height(false);
    else if (action.equals("toggle_repeat"))
    {
      boolean on = !_config.keyrepeat_enabled;
      _prefs.edit().putBoolean("keyrepeat_enabled", on).apply();
      android.widget.Toast.makeText(getContext(), on ? "Key repeat on"
          : "Key repeat off: hold a key to edit it", android.widget.Toast.LENGTH_SHORT).show();
    }
    else if (action.equals("layout_mode"))
    {
      _listener.toggle_layout_mode();
      refresh(_config); // Update the key's label.
    }
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
    switch (action)
    {
      case "select_container":
        return new ComfortKey.Label((_target < 0) ? "↕" : "↕ " + target_label(), false);
      case "move_up": return new ComfortKey.Label("⇡", false);
      case "move_down": return new ComfortKey.Label("⇣", false);
      case "height_up": return new ComfortKey.Label("+", false);
      case "height_down": return new ComfortKey.Label("−", false);
      case "height_undo": return new ComfortKey.Label("↶", false);
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
      default: return null;
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
    if (_target < 0)
      return;
    _target = -1;
    _listener.highlight(-1);
    if (_config != null)
      refresh(_config);
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
  final java.util.ArrayDeque<ModuleStore.Snapshot> _undo =
    new java.util.ArrayDeque<ModuleStore.Snapshot>();
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
    _undo.push(store.snapshot("changeheight"));
    if (_undo.size() > MAX_UNDO)
      _undo.removeLast();
  }

  /** Whether Back is ours: there is something to undo, or a container is
      selected. */
  public boolean wants_back()
  {
    return !_undo.isEmpty() || _target >= 0;
  }

  /** Back: undo the last height change, or else leave the container
      selection. */
  public void back()
  {
    if (!_undo.isEmpty())
    {
      _gesture_saved = false;
      new ModuleStore(_prefs).restore(_undo.pop());
    }
    else
      reset_target();
  }

  /** Long Back: undo every change since the keyboard was shown. */
  public void revert_all()
  {
    if (!_undo.isEmpty())
      new ModuleStore(_prefs).restore(_undo.getLast());
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
