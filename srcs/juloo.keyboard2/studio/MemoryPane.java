package juloo.keyboard2.studio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.ClipboardHistoryService;
import juloo.keyboard2.Config;
import juloo.keyboard2.R;
import org.json.JSONArray;

/** The pane opened from the clipboard and memory icons, with two tabs:
    - Clipboard: the history of copied text on the left third, pinned items
      on the right two thirds.
    - Memory: the user's own items, by category.
    Tap an item to type it, long-press for more (pin, save to Memory, copy to
    the clipboard, move, delete). */
public class MemoryPane extends LinearLayout
  implements ClipboardHistoryService.OnClipboardHistoryChange
{
  /** Same storage as upstream's pinned clipboards, so pins are kept. */
  static final String PINS_FILE = "pinned_clipboards";
  static final String PINS_PREF = "pinned";

  boolean _memory_tab = false;
  /** Memory category shown, [null] for all. */
  String _category = null;
  /** Memory shows the history records instead of a category. */
  boolean _history = false;
  /** Delete button waiting for its confirming second tap. */
  String _confirming = null;
  /** Content type shown in the Clipboard tab, [null] for all. */
  String _clip_type = null;
  TextView _tab_clipboard;
  TextView _tab_memory;
  FrameLayout _content;
  int _label_color;
  int _item_color;
  int _selected_color;

  public MemoryPane(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setOrientation(VERTICAL);
    _label_color = ComfortKey.attr_color(ctx, R.attr.colorLabel, 0xffffffff);
    int key = ComfortKey.attr_color(ctx, R.attr.colorKey, 0xff333333);
    _item_color = ComfortKey.brighter(key, 0.08f);
    _selected_color = ComfortKey.brighter(key, 0.3f);
    LinearLayout tabs = new LinearLayout(ctx);
    tabs.setOrientation(HORIZONTAL);
    tabs.setGravity(Gravity.CENTER);
    _tab_clipboard = chip(ctx.getString(R.string.studio_tab_clipboard));
    _tab_memory = chip(ctx.getString(R.string.studio_memory));
    _tab_clipboard.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { show(false); }
    });
    _tab_memory.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { show(true); }
    });
    tabs.addView(_tab_clipboard, chip_params());
    tabs.addView(_tab_memory, chip_params());
    addView(tabs, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)));
    _content = new FrameLayout(ctx);
    addView(_content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
  }

  /** Show the Memory tab if [memory], the Clipboard tab otherwise. */
  public void show(boolean memory)
  {
    // A pending delete confirmation doesn't carry over to the other tab.
    if (memory != _memory_tab)
      _confirming = null;
    _memory_tab = memory;
    _tab_clipboard.setBackgroundColor(memory ? _item_color : _selected_color);
    _tab_memory.setBackgroundColor(memory ? _selected_color : _item_color);
    ClipboardHistoryService s = ClipboardHistoryService.get_service(getContext());
    if (s != null)
      s.set_on_clipboard_history_change(memory ? null : this);

    _content.removeAllViews();
    _content.addView(memory ? memory_view() : clipboard_view());
  }

  public void on_clipboard_history_change()
  {
    if (!_memory_tab)
      show(false);
  }

  /* Clipboard tab */

  View clipboard_view()
  {
    ClipboardHistoryService service = ClipboardHistoryService.get_service(getContext());
    List<String> h = (service == null || !Config.globalConfig().clipboard_history_enabled) ?
      new ArrayList<String>() : service.clear_expired_and_get_history();
    List<String> ps = load_pins();
    // Content types present, as filters.
    List<String> types = new ArrayList<String>();
    for (String t : MemoryStore.DEFAULT_CATEGORIES)
      for (String s : concat(h, ps))
        if (t.equals(MemoryStore.guess_category(s)) && !types.contains(t))
          types.add(t);
    if (_clip_type != null && !types.contains(_clip_type))
      _clip_type = null;
    LinearLayout v = new LinearLayout(getContext());
    v.setOrientation(VERTICAL);
    LinearLayout chips = new LinearLayout(getContext());
    chips.setOrientation(HORIZONTAL);
    chips.addView(type_chip(null, getContext().getString(R.string.studio_all)), chip_params());
    for (String t : types)
      chips.addView(type_chip(t, t), chip_params());
    HorizontalScrollView hs = new HorizontalScrollView(getContext());
    hs.setHorizontalScrollBarEnabled(false);
    hs.addView(chips);
    v.addView(hs, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(HORIZONTAL);
    v.addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    LinearLayout history = column(R.string.studio_history);
    if (!Config.globalConfig().clipboard_history_enabled)
    {
      TextView on = item_view(getContext().getString(R.string.studio_history_off));
      on.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v)
        {
          ClipboardHistoryService.set_history_enabled(true);
          show(false);
        }
      });
      history.addView(on);
    }
    else
    {
      if (!h.isEmpty())
        history.addView(confirm_button("clipboard", R.string.studio_clear,
              new Runnable() {
                public void run()
                {
                  ClipboardHistoryService s = ClipboardHistoryService.get_service(getContext());
                  if (s != null)
                    s.clear_history();
                }
              }));
      // Newest first.
      for (int i = h.size() - 1; i >= 0; i--)
        if (shown(h.get(i)))
          history.addView(history_item(h.get(i)));
      if (h.isEmpty())
        history.addView(hint(R.string.studio_history_empty));
    }
    LinearLayout pins = column(R.string.studio_pinned);
    for (String p : ps)
      if (shown(p))
        pins.addView(pin_item(p));
    if (ps.isEmpty())
      pins.addView(hint(R.string.studio_pins_empty));
    row.addView(scroll(history), new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 34f));
    row.addView(scroll(pins), new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 66f));
    return v;
  }

  /** Whether [text] passes the content type filter. */
  boolean shown(String text)
  {
    return _clip_type == null || _clip_type.equals(MemoryStore.guess_category(text));
  }

  View type_chip(final String type, String label)
  {
    TextView t = chip(label);
    boolean selected = (type == null) ? _clip_type == null : type.equals(_clip_type);
    t.setBackgroundColor(selected ? _selected_color : _item_color);
    t.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { _clip_type = type; show(false); }
    });
    return t;
  }

  static List<String> concat(List<String> a, List<String> b)
  {
    List<String> l = new ArrayList<String>(a);
    l.addAll(b);
    return l;
  }

  View history_item(final String text)
  {
    return item(text, new MenuBuilder() {
      public void build(Menu m)
      {
        add(m, R.string.studio_pin, new Runnable() {
          public void run() { add_pin(text); show(false); }
        });
        save_to_memory_menu(m, text);
        add(m, R.string.studio_remove_from_history, new Runnable() {
          public void run()
          {
            ClipboardHistoryService s = ClipboardHistoryService.get_service(getContext());
            if (s != null)
              s.remove_history_entry(text);
          }
        });
      }
    });
  }

  View pin_item(final String text)
  {
    return item(text, new MenuBuilder() {
      public void build(Menu m)
      {
        save_to_memory_menu(m, text);
        add(m, R.string.studio_copy_to_clipboard, new Runnable() {
          public void run() { copy_to_clipboard(text); }
        });
        add(m, R.string.studio_unpin, new Runnable() {
          public void run() { remove_pin(text); show(false); }
        });
      }
    });
  }

  /** "Save to Memory" submenu, the likely category first. */
  void save_to_memory_menu(Menu m, final String text)
  {
    final MemoryStore store = memory();
    if (store == null)
      return;
    SubMenu sub = m.addSubMenu(R.string.studio_save_to_memory);
    String guess = MemoryStore.guess_category(text);
    List<String> cs = store.categories();
    cs.remove(guess);
    cs.add(0, guess);
    for (final String c : cs)
      add(sub, c, new Runnable() {
        public void run()
        {
          store.add(text, c);
          toast(getContext().getString(R.string.studio_saved_to, c));
        }
      });
  }

  /* Memory tab */

  View memory_view()
  {
    LinearLayout v = new LinearLayout(getContext());
    v.setOrientation(VERTICAL);
    final MemoryStore store = memory();
    if (store == null)
    {
      v.addView(hint(R.string.studio_memory_locked));
      return v;
    }
    if (_category != null && !store.categories().contains(_category))
      _category = null;
    // Categories scroll, the history button stays at the right end.
    LinearLayout top = new LinearLayout(getContext());
    top.setOrientation(HORIZONTAL);
    LinearLayout chips = new LinearLayout(getContext());
    chips.setOrientation(HORIZONTAL);
    chips.addView(category_chip(null, getContext().getString(R.string.studio_all)), chip_params());
    for (String c : store.categories())
      chips.addView(category_chip(c, c), chip_params());
    HorizontalScrollView hs = new HorizontalScrollView(getContext());
    hs.setHorizontalScrollBarEnabled(false);
    hs.addView(chips);
    top.addView(hs, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    TextView history = chip("⟲ " + getContext().getString(R.string.studio_history));
    history.setBackgroundColor(_history ? _selected_color : _item_color);
    history.setOnClickListener(new View.OnClickListener() {
      public void onClick(View _v) { _history = !_history; _confirming = null; show(true); }
    });
    top.addView(history, chip_params());
    v.addView(top, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(32)));
    LinearLayout items = new LinearLayout(getContext());
    items.setOrientation(VERTICAL);
    if (_history)
      fill_history(store, items);
    else
    {
      List<MemoryStore.Item> is = store.items(_category);
      for (MemoryStore.Item it : is)
        items.addView(memory_item(store, it));
      if (is.isEmpty())
        items.addView(hint(R.string.studio_memory_empty));
    }
    v.addView(scroll(items), new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    return v;
  }

  /** One record per phone restart, newest first, each deletable, and a
      button deleting all of them. Deleting asks for a second tap. */
  void fill_history(final MemoryStore store, LinearLayout items)
  {
    List<MemoryStore.Session> ss = store.sessions();
    if (ss.isEmpty())
    {
      items.addView(hint(R.string.studio_history_none));
      return;
    }
    items.addView(confirm_button("all", R.string.studio_delete_all_history,
          new Runnable() { public void run() { store.delete_history(); } }));
    java.text.DateFormat fmt = java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
    for (final MemoryStore.Session s : ss)
    {
      LinearLayout head = new LinearLayout(getContext());
      head.setOrientation(HORIZONTAL);
      head.setGravity(Gravity.CENTER_VERTICAL);
      TextView title = hint(0);
      title.setText(getContext().getString(R.string.studio_since_restart,
            fmt.format(new java.util.Date(s.started)), s.items.size()));
      title.setAlpha(0.8f);
      head.addView(title, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      head.addView(confirm_button(s.key, R.string.studio_delete_record,
            new Runnable() { public void run() { store.delete_session(s.key); } }),
          new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(30)));
      items.addView(head);
      for (MemoryStore.Item it : s.items)
        items.addView(memory_item(store, it));
    }
  }

  /** A delete button: the first tap asks to tap again, the second deletes. */
  TextView confirm_button(final String id, int label, final Runnable action)
  {
    final boolean armed = id.equals(_confirming);
    TextView t = chip(getContext().getString(armed ? R.string.studio_tap_again : label));
    t.setBackgroundColor(armed ? 0xffaa3333 : _item_color);
    t.setOnClickListener(new View.OnClickListener() {
      public void onClick(View _v)
      {
        if (armed)
        {
          action.run();
          _confirming = null;
        }
        else
          _confirming = id;
        show(_memory_tab);
      }
    });
    t.setPadding(dp(10), dp(4), dp(10), dp(4));
    return t;
  }

  View category_chip(final String category, String label)
  {
    TextView t = chip(label);
    boolean selected = !_history
      && ((category == null) ? _category == null : category.equals(_category));
    t.setBackgroundColor(selected ? _selected_color : _item_color);
    t.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { _category = category; _history = false; show(true); }
    });
    return t;
  }

  View memory_item(final MemoryStore store, final MemoryStore.Item it)
  {
    String label = (_category == null || _history) ? it.text + "   · " + it.category : it.text;
    TextView t = (TextView)item(it.text, new MenuBuilder() {
      public void build(Menu m)
      {
        add(m, R.string.studio_copy_to_clipboard, new Runnable() {
          public void run() { copy_to_clipboard(it.text); }
        });
        if (it.is_history())
        {
          // Saving keeps it when the history is deleted.
          SubMenu sub = m.addSubMenu(R.string.studio_save_to_memory);
          for (final String c : store.categories())
            add(sub, c, new Runnable() {
              public void run() { store.add(it.text, c); show(true); }
            });
        }
        else
        {
          SubMenu sub = m.addSubMenu(R.string.studio_move_to);
          for (final String c : store.categories())
            if (!c.equals(it.category))
              add(sub, c, new Runnable() {
                public void run() { store.move(it, c); show(true); }
              });
        }
        add(m, R.string.studio_delete, new Runnable() {
          public void run() { store.remove(it); show(true); }
        });
      }
    });
    t.setText(label);
    return t;
  }

  /* Storage */

  /** [null] while the phone is locked (direct-boot). */
  MemoryStore memory()
  {
    try
    {
      return new MemoryStore(new File(getContext().getFilesDir(), "memory.json"));
    }
    catch (Exception _e)
    {
      return null;
    }
  }

  SharedPreferences pins_prefs()
  {
    try
    {
      return getContext().getSharedPreferences(PINS_FILE, Context.MODE_PRIVATE);
    }
    catch (Exception _e)
    {
      return null;
    }
  }

  List<String> load_pins()
  {
    List<String> l = new ArrayList<String>();
    SharedPreferences p = pins_prefs();
    if (p == null)
      return l;
    try
    {
      JSONArray a = new JSONArray(p.getString(PINS_PREF, "[]"));
      for (int i = 0; i < a.length(); i++)
        l.add(a.getString(i));
    }
    catch (Exception _e) {}
    return l;
  }

  void save_pins(List<String> l)
  {
    SharedPreferences p = pins_prefs();
    if (p != null)
      p.edit().putString(PINS_PREF, new JSONArray(l).toString()).apply();
  }

  void add_pin(String text)
  {
    List<String> l = load_pins();
    l.remove(text);
    l.add(0, text);
    save_pins(l);
  }

  void remove_pin(String text)
  {
    List<String> l = load_pins();
    l.remove(text);
    save_pins(l);
  }

  void copy_to_clipboard(String text)
  {
    ClipboardManager cm =
      (ClipboardManager)getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    cm.setPrimaryClip(ClipData.newPlainText("Memory", text));
    toast(getContext().getString(R.string.studio_copied));
  }

  /* Views */

  interface MenuBuilder { void build(Menu m); }

  /** A row: tap to type [text], long-press for the menu. */
  View item(final String text, final MenuBuilder menu)
  {
    final TextView t = item_view(text);
    t.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { ClipboardHistoryService.paste(text); }
    });
    t.setOnLongClickListener(new View.OnLongClickListener() {
      public boolean onLongClick(View v)
      {
        PopupMenu p = new PopupMenu(getContext(), t);
        menu.build(p.getMenu());
        p.show();
        return true;
      }
    });
    return t;
  }

  static void add(Menu m, int title, final Runnable r)
  {
    m.add(title).setOnMenuItemClickListener(click(r));
  }

  static void add(Menu m, String title, final Runnable r)
  {
    m.add(title).setOnMenuItemClickListener(click(r));
  }

  static MenuItem.OnMenuItemClickListener click(final Runnable r)
  {
    return new MenuItem.OnMenuItemClickListener() {
      public boolean onMenuItemClick(MenuItem i) { r.run(); return true; }
    };
  }

  TextView item_view(String text)
  {
    TextView t = new TextView(getContext());
    t.setText(text);
    t.setTextColor(_label_color);
    t.setBackgroundColor(_item_color);
    t.setMaxLines(3);
    t.setEllipsize(TextUtils.TruncateAt.END);
    t.setPadding(dp(8), dp(6), dp(8), dp(6));
    LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT);
    p.setMargins(dp(3), dp(3), dp(3), 0);
    t.setLayoutParams(p);
    return t;
  }

  TextView hint(int res)
  {
    TextView t = new TextView(getContext());
    if (res != 0)
      t.setText(res);
    t.setTextColor(_label_color);
    t.setAlpha(0.6f);
    t.setPadding(dp(8), dp(6), dp(8), dp(6));
    return t;
  }

  LinearLayout column(int heading)
  {
    LinearLayout l = new LinearLayout(getContext());
    l.setOrientation(VERTICAL);
    TextView h = hint(heading);
    h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    l.addView(h);
    return l;
  }

  ScrollView scroll(View v)
  {
    ScrollView s = new ScrollView(getContext());
    s.addView(v);
    return s;
  }

  TextView chip(String label)
  {
    TextView t = new TextView(getContext());
    t.setText(label);
    t.setTextColor(_label_color);
    t.setGravity(Gravity.CENTER);
    t.setPadding(dp(14), 0, dp(14), 0);
    return t;
  }

  LayoutParams chip_params()
  {
    LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.MATCH_PARENT);
    p.setMargins(dp(3), dp(3), dp(3), dp(3));
    return p;
  }

  void toast(String msg)
  {
    Toast.makeText(getContext(), msg, Toast.LENGTH_SHORT).show();
  }

  int dp(int v)
  {
    return (int)(v * getResources().getDisplayMetrics().density);
  }
}
