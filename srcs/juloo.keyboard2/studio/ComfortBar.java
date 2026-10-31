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
import juloo.keyboard2.Logs;
import juloo.keyboard2.Theme;
import org.json.JSONObject;

/** The comfort bar, above the suggestion bar: a few tiny keys, centered and
    spaced apart, chosen by the active [comfortbar] module, and the language
    key at the right end. Each key has a tap action and swipe actions.
    Actions are built in; modules only choose and place them. */
public class ComfortBar extends FrameLayout implements ComfortKey.Host
{
  public interface Listener
  {
    /** Act as if key [kv] was typed. */
    void comfort_key(KeyValue kv);
    void open_studio();
  }

  static final int HEIGHT_STEP = 2;
  static final ComfortKeySpec LANGUAGE_KEY =
    ComfortKeySpec.of("c", "language", "sw", "keyboard_picker");

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
    _keys.removeAllViews();
    // The language key stays at the right end, the others in the middle.
    while (getChildCount() > 1)
      removeViewAt(1);
    for (ComfortKeySpec s : config.studio.keys)
      _keys.addView(make_key(s), key_params(true));
    LayoutParams lp = key_params(false);
    lp.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
    addView(make_key(LANGUAGE_KEY), lp);
  }

  View make_key(ComfortKeySpec s)
  {
    return new ComfortKey(getContext(), s, this, Theme.getKeyFont(getContext()));
  }

  LayoutParams key_params(boolean spaced)
  {
    int h = (int)(_config.studio.comfort_bar_dp * density());
    LayoutParams p = new LayoutParams((int)(h * 1.7f), h - dp(4));
    int gap = spaced ? dp(10) : dp(2);
    p.setMargins(gap, dp(2), gap, dp(2));
    return p;
  }

  /* ComfortKey.Host */

  public void perform(String action)
  {
    if (action.equals("height_up"))
      change_height(HEIGHT_STEP);
    else if (action.equals("height_down"))
      change_height(-HEIGHT_STEP);
    else if (action.startsWith("switch:"))
      cycle_module(action.substring("switch:".length()));
    else if (action.equals("studio"))
      _listener.open_studio();
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
      case "height_up": return new ComfortKey.Label("+", false);
      case "height_down": return new ComfortKey.Label("−", false);
      case "studio": return new ComfortKey.Label("⚙", false);
      case "language": return new ComfortKey.Label("🌐", false);
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
      case "copy": case "cut": case "paste": case "undo": case "redo":
        return action;
      case "select_all": return "selectAll";
      case "language": return "switch_forward";
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

  static String shorten(String s)
  {
    return (s.length() <= 5) ? s : s.substring(0, 4) + "…";
  }

  /** Change the keyboard height for the current orientation, stored in the
      active [changeheight] module or in a new one named "quick". */
  void change_height(int delta)
  {
    ModuleStore store = new ModuleStore(_prefs);
    Module m = store.active("changeheight");
    if (m == null)
      m = store.get("changeheight", "quick");
    int next = Math.max(StudioConfig.KEYBOARD_PERCENT_MIN,
        Math.min(StudioConfig.KEYBOARD_PERCENT_MAX,
          _config.keyboard_height_percent + delta));
    try
    {
      JSONObject j = (m == null) ? new JSONObject() : new JSONObject(m.json.toString());
      JSONObject kp = j.optJSONObject("keyboard_percent");
      if (kp == null)
        kp = new JSONObject();
      kp.put(_config.orientation_landscape ? "landscape" : "portrait", next);
      j.put("keyboard_percent", kp);
      Module nm = (m == null) ? Module.create("changeheight", "quick", j) : new Module(j);
      store.save(nm, true);
    }
    catch (Exception e)
    {
      Logs.exn("Comfort bar height", e);
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
}
