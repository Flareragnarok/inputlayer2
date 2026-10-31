package juloo.keyboard2.studio;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import juloo.keyboard2.Config;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.Logs;
import juloo.keyboard2.R;
import org.json.JSONObject;

/** The comfort bar, above the suggestion bar: a scrollable row of applets
    chosen by the active [comfortbar] module, and the language switch fixed at
    the right end. Applets are built in; modules only choose and order them. */
public class ComfortBar extends LinearLayout
{
  public interface Listener
  {
    /** Act as if key [kv] was typed. */
    void comfort_key(KeyValue kv);
    void open_studio();
  }

  static final int HEIGHT_STEP = 2;

  Listener _listener;
  SharedPreferences _prefs;
  Config _config;
  LinearLayout _applets;
  TextView _language;
  Handler _handler = new Handler();

  public ComfortBar(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
  }

  public void setup(Listener l, SharedPreferences prefs)
  {
    _listener = l;
    _prefs = prefs;
    removeAllViews();
    HorizontalScrollView scroll = new HorizontalScrollView(getContext());
    scroll.setHorizontalScrollBarEnabled(false);
    _applets = new LinearLayout(getContext());
    _applets.setOrientation(HORIZONTAL);
    scroll.addView(_applets, new ViewGroup.LayoutParams(
          ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
    addView(scroll, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    _language = cell("🌐");
    _language.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { key("switch_forward"); }
    });
    _language.setOnLongClickListener(new View.OnLongClickListener() {
      public boolean onLongClick(View v) { key("change_method"); return true; }
    });
    addView(_language, cell_params());
  }

  /** Rebuild the applets, called whenever the config changes. */
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
    p.height = (int)(dp * getResources().getDisplayMetrics().density);
    setLayoutParams(p);
    _applets.removeAllViews();
    for (String a : config.studio.applets)
      add_applet(a);
  }

  void add_applet(String a)
  {
    if (a.equals("height"))
    {
      add_height_cell("−", -HEIGHT_STEP);
      add_height_cell("+", HEIGHT_STEP);
    }
    else if (a.startsWith("switch:"))
      add_switch_cell(a.substring("switch:".length()));
    else if (a.equals("memory"))
      add_key_cell(getContext().getString(R.string.studio_memory), "switch_clipboard", false);
    else
    {
      String[] k = editing_applet(a);
      if (k != null)
        add_key_cell(k[0], k[1], k[2] != null);
    }
  }

  /** Label, key name and whether it repeats when held, or [null]. */
  static String[] editing_applet(String a)
  {
    switch (a)
    {
      case "left": return new String[]{ "←", "left", "repeat" };
      case "right": return new String[]{ "→", "right", "repeat" };
      case "up": return new String[]{ "↑", "up", "repeat" };
      case "down": return new String[]{ "↓", "down", "repeat" };
      case "select_all": return new String[]{ "All", "selectAll", null };
      case "copy": return new String[]{ "Copy", "copy", null };
      case "cut": return new String[]{ "Cut", "cut", null };
      case "paste": return new String[]{ "Paste", "paste", null };
      case "undo": return new String[]{ "↶", "undo", null };
      case "redo": return new String[]{ "↷", "redo", null };
      default: return null;
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

  void add_key_cell(String label, final String key_name, boolean repeat)
  {
    TextView c = cell(label);
    if (repeat)
      c.setOnTouchListener(new Repeater(key_name));
    else
      c.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { key(key_name); }
      });
    _applets.addView(c, cell_params());
  }

  void add_height_cell(String label, final int delta)
  {
    TextView c = cell(label);
    c.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { change_height(delta); }
    });
    c.setOnLongClickListener(new View.OnLongClickListener() {
      public boolean onLongClick(View v) { _listener.open_studio(); return true; }
    });
    _applets.addView(c, cell_params());
  }

  void add_switch_cell(final String type)
  {
    if (!Module.is_known_type(type))
      return;
    Module active = new ModuleStore(_prefs).active(type);
    TextView c = cell(type_symbol(type) + " " + (active == null ? "off" : active.name));
    c.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { cycle_module(type); }
    });
    _applets.addView(c, cell_params());
  }

  void key(String name)
  {
    KeyValue kv = KeyValue.getKeyByName(name);
    if (kv != null)
      _listener.comfort_key(kv);
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

  TextView cell(String label)
  {
    TextView t = new TextView(getContext());
    t.setText(label);
    t.setGravity(Gravity.CENTER);
    t.setMaxLines(1);
    t.setBackgroundResource(R.drawable.suggestions_item_background);
    TypedValue tv = new TypedValue();
    if (getContext().getTheme().resolveAttribute(R.attr.colorLabel, tv, true))
      t.setTextColor(tv.data);
    int pad = dp(10);
    t.setPadding(pad, 0, pad, 0);
    t.setMinWidth(dp(40));
    return t;
  }

  LayoutParams cell_params()
  {
    LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.MATCH_PARENT);
    p.setMargins(dp(4), dp(4), 0, dp(4));
    return p;
  }

  int dp(int v)
  {
    return (int)(v * getResources().getDisplayMetrics().density);
  }

  /** Arrow keys repeat while held, like on the keyboard. */
  final class Repeater implements View.OnTouchListener, Runnable
  {
    final String _key_name;

    Repeater(String key_name) { _key_name = key_name; }

    public boolean onTouch(View v, MotionEvent ev)
    {
      switch (ev.getActionMasked())
      {
        case MotionEvent.ACTION_DOWN:
          v.setPressed(true);
          key(_key_name);
          if (_config.keyrepeat_enabled)
            _handler.postDelayed(this, _config.longPressTimeout);
          return true;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_CANCEL:
          v.setPressed(false);
          _handler.removeCallbacks(this);
          return true;
      }
      return false;
    }

    public void run()
    {
      key(_key_name);
      _handler.postDelayed(this, _config.longPressInterval);
    }
  }
}
