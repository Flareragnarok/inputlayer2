package juloo.keyboard2.studio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
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

/** The pane opened from the clipboard and memory icons, in the suggestion
    bar's place. On top, a bar like the suggestion bar: the last three
    copies, or the whole text of the item last tapped. Then one of two tabs:
    - Clipboard: the history of copied text on the left 30%, pinned items on
      the right; one line per item.
    - Memory: the list of categories on the right (big copies waiting for
      "Keep?" first, the history records last); the one tapped shows its
      items in the middle, one per line. The left 30% is kept free.
    Tap an item to type it, ✎ to edit it in the whole pane, long-press for
    more (delete first). */
public class MemoryPane extends LinearLayout
  implements ClipboardHistoryService.OnClipboardHistoryChange
{
  /** The keyboard: closing the pane, typing into an item being edited. */
  public interface Listener
  {
    void close();
    /** Editing started: the keyboard types into [e] until it ends. */
    void edit_started(ItemEditor e);
    void edit_ended();
    /** The item being edited changed (what the app's field would report). */
    void edit_changed(int sel_start, int sel_end);
  }

  public interface Saver { void save(String text); }

  Listener _listener = null;

  public void set_listener(Listener l) { _listener = l; }

  /** Showing the Memory tab (else Clipboard). */
  public boolean memory_tab() { return _memory_tab; }

  /** Same storage as upstream's pinned clipboards, so pins are kept. */
  static final String PINS_FILE = "pinned_clipboards";
  static final String PINS_PREF = "pinned";
  /** Characters of an item shown in its one line. */
  static final int PREVIEW_CHARS = 120;

  boolean _memory_tab = false;
  /** Delete button waiting for its confirming second tap. */
  String _confirming = null;
  /** Content type shown in the Clipboard tab, [null] for all. */
  String _clip_type = null;
  /** The item last tapped, shown whole in the bar; [null] for none. */
  String _selected = null;
  /** The item being edited in the pane, [null] when not editing. */
  ItemEditor _editor = null;
  Saver _editor_saver = null;
  String _editor_original = null;
  TextView _edit_title;
  TextView _edit_text;
  ScrollView _edit_scroll;
  FrameLayout _bar;
  FrameLayout _content;
  int _label_color;
  int _item_color;
  int _selected_color;
  int _highlight = 0x553399ff;

  public MemoryPane(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setOrientation(VERTICAL);
    _label_color = ComfortKey.attr_color(ctx, R.attr.colorLabel, 0xffffffff);
    int key = ComfortKey.attr_color(ctx, R.attr.colorKey, 0xff333333);
    _item_color = ComfortKey.brighter(key, 0.08f);
    _selected_color = ComfortKey.brighter(key, 0.3f);
    // No tab row: the clipboard and memory icons over the pane switch
    // between them and close it.
    _bar = new FrameLayout(ctx);
    addView(_bar, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
    _content = new FrameLayout(ctx);
    addView(_content, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
  }

  /** Show the Memory tab if [memory], the Clipboard tab otherwise. While
      an item is edited, the editor stays. */
  public void show(boolean memory)
  {
    // A pending delete confirmation doesn't carry over to the other tab.
    if (memory != _memory_tab)
      _confirming = null;
    _memory_tab = memory;
    ClipboardHistoryService s = ClipboardHistoryService.get_service(getContext());
    if (s != null)
      s.set_on_clipboard_history_change(memory ? null : this);
    _content.removeAllViews();
    if (_editor != null)
    {
      _bar.setVisibility(GONE);
      _content.addView(editor_view());
      return;
    }
    _bar.setVisibility(VISIBLE);
    refresh_bar();
    _content.addView(memory ? memory_view() : clipboard_view());
  }

  /** The pane closed: the item shown in the bar is forgotten, an edit is
      cancelled. */
  public void closed()
  {
    end_edit(false);
    _selected = null;
  }

  public void on_clipboard_history_change()
  {
    if (!_memory_tab && _editor == null)
      show(false);
  }

  /** Type [text] and show it whole in the bar. Only the bar is redrawn, so
      the lists keep their scroll. */
  void type(String text)
  {
    ClipboardHistoryService.paste(text);
    _selected = text;
    refresh_bar();
  }

  /* The bar */

  void refresh_bar()
  {
    _bar.removeAllViews();
    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(HORIZONTAL);
    _bar.addView(row);
    if (_selected != null)
    {
      // The whole text on one line, scrolling sideways; tap types it again.
      final String text = _selected;
      TextView t = bar_text(text.replace("\n", " ⏎ "));
      t.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { ClipboardHistoryService.paste(text); }
      });
      HorizontalScrollView hs = new HorizontalScrollView(getContext());
      hs.setHorizontalScrollBarEnabled(false);
      hs.addView(t, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT));
      row.addView(hs, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
      TextView close = bar_text("✕");
      close.setGravity(Gravity.CENTER);
      close.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { _selected = null; refresh_bar(); }
      });
      row.addView(close, new LayoutParams(dp(36), ViewGroup.LayoutParams.MATCH_PARENT));
      return;
    }
    List<String> recent = recent(3);
    if (recent.isEmpty())
    {
      TextView t = bar_text(getContext().getString(R.string.studio_bar_empty));
      t.setAlpha(0.6f);
      row.addView(t, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
      return;
    }
    for (final String r : recent)
    {
      TextView t = bar_text(one_line(r));
      t.setGravity(Gravity.CENTER);
      t.setSingleLine(true);
      t.setEllipsize(TextUtils.TruncateAt.END);
      t.setBackgroundColor(_item_color);
      t.setOnClickListener(new View.OnClickListener() {
        public void onClick(View v) { type(r); }
      });
      LayoutParams lp = new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
      lp.setMargins(dp(2), dp(2), dp(2), dp(2));
      row.addView(t, lp);
    }
  }

  /** The last [n] copies: the clipboard history, then Memory's. */
  List<String> recent(int n)
  {
    List<String> l = new ArrayList<String>();
    ClipboardHistoryService service = ClipboardHistoryService.get_service(getContext());
    if (service != null && Config.globalConfig().clipboard_history_enabled)
    {
      List<String> h = service.clear_expired_and_get_history();
      for (int i = h.size() - 1; i >= 0 && l.size() < n; i--)
        if (!l.contains(h.get(i)))
          l.add(h.get(i));
    }
    MemoryStore store = (l.size() < n) ? memory() : null;
    if (store != null)
      for (MemoryStore.Item it : store.items(null))
        if (l.size() < n && !l.contains(it.text))
          l.add(it.text);
    return l;
  }

  TextView bar_text(String s)
  {
    TextView t = new TextView(getContext());
    t.setText(s);
    t.setTextColor(_label_color);
    t.setSingleLine(true);
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setPadding(dp(8), 0, dp(8), 0);
    return t;
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
    v.addView(hs, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30)));
    LinearLayout row = new LinearLayout(getContext());
    row.setOrientation(HORIZONTAL);
    v.addView(row, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    LinearLayout history = column(R.string.studio_history);
    if (!Config.globalConfig().clipboard_history_enabled)
    {
      TextView on = hint(R.string.studio_history_off);
      on.setAlpha(1f);
      on.setBackgroundColor(_item_color);
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
    row.addView(scroll(history), new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 30f));
    row.addView(scroll(pins), new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 70f));
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
    // The edited text becomes the newest clipboard entry, instead of this
    // one.
    Saver saver = new Saver() {
      public void save(String t)
      {
        ClipboardHistoryService s = ClipboardHistoryService.get_service(getContext());
        if (s != null)
          s.remove_history_entry(text);
        copy_to_clipboard(t);
      }
    };
    return entry(text, saver, new MenuBuilder() {
      public void build(Menu m)
      {
        add(m, R.string.studio_delete, new Runnable() {
          public void run()
          {
            ClipboardHistoryService s = ClipboardHistoryService.get_service(getContext());
            if (s != null)
              s.remove_history_entry(text);
          }
        });
        add(m, R.string.studio_pin, new Runnable() {
          public void run() { add_pin(text); show(false); }
        });
        save_to_memory_menu(m, text);
      }
    });
  }

  View pin_item(final String text)
  {
    Saver saver = new Saver() {
      public void save(String t) { replace_pin(text, t); }
    };
    return entry(text, saver, new MenuBuilder() {
      public void build(Menu m)
      {
        add(m, R.string.studio_unpin, new Runnable() {
          public void run() { remove_pin(text); show(false); }
        });
        save_to_memory_menu(m, text);
        add(m, R.string.studio_copy_to_clipboard, new Runnable() {
          public void run() { copy_to_clipboard(text); }
        });
      }
    });
  }

  void replace_pin(String old, String text)
  {
    List<String> l = load_pins();
    int i = l.indexOf(old);
    if (i < 0)
      l.add(0, text);
    else
      l.set(i, text);
    save_pins(l);
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

  /** Category shown in the Memory tab: one of the store's, [KEEP] or
      [HISTORY]; [null] for the first one with items. */
  String _category = null;
  static final String KEEP = "\u0000keep";
  static final String HISTORY = "\u0000history";

  View memory_view()
  {
    LinearLayout v = new LinearLayout(getContext());
    v.setOrientation(HORIZONTAL);
    final MemoryStore store = memory();
    if (store == null)
    {
      v.addView(hint(R.string.studio_memory_locked));
      return v;
    }
    // What the list on the right offers: big copies waiting, the categories
    // with items, the history records.
    List<MemoryStore.Item> pending = store.pending();
    List<String> keys = new ArrayList<String>();
    List<String> labels = new ArrayList<String>();
    if (!pending.isEmpty())
    {
      keys.add(KEEP);
      labels.add(getContext().getString(R.string.studio_keep_big, pending.size()));
    }
    for (String c : store.categories())
    {
      int n = store.items(c).size();
      if (n > 0)
      {
        keys.add(c);
        labels.add(c + " · " + n);
      }
    }
    List<MemoryStore.Session> sessions = store.sessions();
    if (!sessions.isEmpty())
    {
      keys.add(HISTORY);
      labels.add("⟲ " + getContext().getString(R.string.studio_history));
    }
    if (_category == null || !keys.contains(_category))
      _category = keys.isEmpty() ? null : keys.get(0);
    // The left side is kept free for now.
    v.addView(new View(getContext()), new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 30f));
    LinearLayout items = new LinearLayout(getContext());
    items.setOrientation(VERTICAL);
    if (_category == null)
      items.addView(hint(R.string.studio_memory_empty));
    else if (_category.equals(KEEP))
    {
      items.addView(column_head(getContext().getString(R.string.studio_keep_hint), null));
      for (MemoryStore.Item it : pending)
        items.addView(pending_entry(store, it));
    }
    else if (_category.equals(HISTORY))
      fill_history(store, sessions, items);
    else
      fill_category(store, _category, items);
    v.addView(scroll(items), new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 45f));
    LinearLayout cats = new LinearLayout(getContext());
    cats.setOrientation(VERTICAL);
    for (int i = 0; i < keys.size(); i++)
      cats.addView(category_entry(keys.get(i), labels.get(i)));
    v.addView(scroll(cats), new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 25f));
    return v;
  }

  /** A category's items, one per line; alike items are next to each other
      and a group of several starts with what they share ("git"). */
  void fill_category(MemoryStore store, String c, LinearLayout items)
  {
    List<MemoryStore.Item> is = store.items(c);
    for (int i = 0; i < is.size(); i++)
    {
      String k = ContentType.look(is.get(i).text, c);
      boolean starts = !k.isEmpty()
        && (i == 0 || !k.equals(ContentType.look(is.get(i - 1).text, c)));
      if (starts && i + 1 < is.size() && k.equals(ContentType.look(is.get(i + 1).text, c)))
        items.addView(group_label(k));
      items.addView(memory_item(store, is.get(i)));
    }
  }

  /** A category in the list on the right: tap shows its items. */
  View category_entry(final String key, String label)
  {
    TextView t = bar_text(label);
    t.setEllipsize(TextUtils.TruncateAt.END);
    t.setBackgroundColor(key.equals(_category) ? _selected_color : _item_color);
    t.setOnClickListener(new View.OnClickListener() {
      public void onClick(View _v) { _category = key; _confirming = null; show(true); }
    });
    LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
    p.setMargins(dp(3), dp(3), dp(3), 0);
    t.setLayoutParams(p);
    return t;
  }

  /** One record per phone restart, newest first, each deletable, and a
      button deleting all of them. Deleting asks for a second tap. */
  void fill_history(final MemoryStore store, List<MemoryStore.Session> ss,
      LinearLayout items)
  {
    items.addView(column_head("", confirm_button("all", R.string.studio_delete_all_history,
            new Runnable() { public void run() { store.delete_history(); } })));
    java.text.DateFormat fmt = java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
    for (final MemoryStore.Session s : ss)
    {
      items.addView(column_head(getContext().getString(R.string.studio_since_restart,
              fmt.format(new java.util.Date(s.started)), s.items.size()),
            confirm_button(s.key, R.string.studio_delete_record,
              new Runnable() { public void run() { store.delete_session(s.key); } })));
      for (MemoryStore.Item it : s.items)
        items.addView(memory_item(store, it));
    }
  }

  /** A small title over items, with a button at its end ([null] for none). */
  View column_head(String title, View button)
  {
    LinearLayout head = new LinearLayout(getContext());
    head.setOrientation(HORIZONTAL);
    head.setGravity(Gravity.CENTER_VERTICAL);
    TextView t = hint(0);
    t.setText(title);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
    t.setPadding(dp(6), dp(4), dp(6), 0);
    head.addView(t, new LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    if (button != null)
      head.addView(button, new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)));
    return head;
  }

  /** What a group of alike items shares, over them. */
  TextView group_label(String k)
  {
    TextView t = hint(0);
    t.setText(k);
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
    t.setPadding(dp(8), dp(4), dp(4), 0);
    return t;
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
    t.setPadding(dp(10), dp(2), dp(10), dp(2));
    return t;
  }

  View memory_item(final MemoryStore store, final MemoryStore.Item it)
  {
    Saver saver = new Saver() {
      public void save(String t) { memory().replace_text(it.text, it.category, t); }
    };
    return entry(it.text, saver, new MenuBuilder() {
      public void build(Menu m)
      {
        add(m, R.string.studio_delete, new Runnable() {
          public void run() { store.remove(it); show(true); }
        });
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
      }
    });
  }

  /** A big copy waiting: tap types it, ✓ keeps it in the history, ✕ drops
      it. */
  View pending_entry(final MemoryStore store, final MemoryStore.Item it)
  {
    LinearLayout r = entry_row(it.text);
    TextView keep = small_button("✓");
    keep.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { store.keep_pending(it); show(true); }
    });
    TextView drop = small_button("✕");
    drop.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { store.drop_pending(it); show(true); }
    });
    r.addView(keep, new LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT));
    r.addView(drop, new LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT));
    return r;
  }

  /* Editing, in the whole pane */

  /** The keyboard types into [text] in the pane until ✓ or ✕; [saver]
      gets the result if it changed. */
  void start_edit(String text, Saver saver)
  {
    end_edit(false);
    _editor_original = text;
    _editor_saver = saver;
    _editor = new ItemEditor(this, text, new ItemEditor.Listener() {
      public void changed(ItemEditor e)
      {
        refresh_editor();
        if (_listener != null)
          _listener.edit_changed(e.sel_start(), e.sel_end());
      }
    });
    if (_listener != null)
      _listener.edit_started(_editor);
    show(_memory_tab);
  }

  public boolean editing() { return _editor != null; }

  public void end_edit(boolean save)
  {
    if (_editor == null)
      return;
    String t = _editor.text();
    Saver s = _editor_saver;
    boolean changed = !t.equals(_editor_original);
    _editor = null;
    _editor_saver = null;
    _editor_original = null;
    if (_listener != null)
      _listener.edit_ended();
    if (save && changed && s != null && !t.isEmpty())
    {
      s.save(t);
      if (_selected != null)
        _selected = t;
    }
    show(_memory_tab);
  }

  View editor_view()
  {
    LinearLayout v = new LinearLayout(getContext());
    v.setOrientation(VERTICAL);
    LinearLayout head = new LinearLayout(getContext());
    head.setOrientation(HORIZONTAL);
    _edit_title = bar_text("");
    _edit_title.setAlpha(0.7f);
    head.addView(_edit_title, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    TextView save = small_button("✓");
    save.setOnClickListener(new View.OnClickListener() {
      public void onClick(View _v) { end_edit(true); }
    });
    head.addView(save, new LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
    TextView cancel = small_button("✕");
    cancel.setOnClickListener(new View.OnClickListener() {
      public void onClick(View _v) { end_edit(false); }
    });
    head.addView(cancel, new LayoutParams(dp(44), ViewGroup.LayoutParams.MATCH_PARENT));
    v.addView(head, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(28)));
    _edit_text = new TextView(getContext());
    _edit_text.setTextColor(_label_color);
    _edit_text.setBackgroundColor(_item_color);
    _edit_text.setPadding(dp(8), dp(6), dp(8), dp(6));
    // A tap places the cursor.
    _edit_text.setOnTouchListener(new View.OnTouchListener() {
      public boolean onTouch(View view, MotionEvent ev)
      {
        if (ev.getAction() != MotionEvent.ACTION_UP || _editor == null)
          return true;
        int o = _edit_text.getOffsetForPosition(ev.getX(), ev.getY());
        int s = _editor.sel_start();
        // The cursor is drawn as one more character.
        if (s == _editor.sel_end() && o > s)
          o -= ItemEditor.CURSOR.length();
        o = Math.max(0, Math.min(o, _editor.text().length()));
        _editor.setSelection(o, o);
        return true;
      }
    });
    _edit_scroll = scroll(_edit_text);
    v.addView(_edit_scroll, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
    refresh_editor();
    return v;
  }

  /** The text with its cursor, scrolled to show the cursor. */
  void refresh_editor()
  {
    if (_editor == null || _edit_text == null)
      return;
    _edit_title.setText(getContext().getString(R.string.studio_editing,
          _editor.text().length()));
    _edit_text.setText(_editor.display(_highlight, true));
    // The selection's moving end stays in view, whichever way it goes.
    final int cursor = _editor.tip();
    _edit_text.post(new Runnable() {
      public void run()
      {
        Layout l = _edit_text.getLayout();
        if (l == null || _edit_scroll == null)
          return;
        int line = l.getLineForOffset(Math.min(cursor, l.getText().length()));
        int top = l.getLineTop(line) + _edit_text.getPaddingTop();
        int bottom = l.getLineBottom(line) + _edit_text.getPaddingTop();
        int y = _edit_scroll.getScrollY(), h = _edit_scroll.getHeight();
        if (top < y)
          _edit_scroll.scrollTo(0, top);
        else if (bottom > y + h)
          _edit_scroll.scrollTo(0, bottom - h);
      }
    });
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

  /** An item on one line, its start then "…": tap types it, ✎ edits it
      in the pane, long-press opens [menu] (delete first). */
  View entry(final String text, final Saver saver, final MenuBuilder menu)
  {
    final LinearLayout r = entry_row(text);
    TextView pen = small_button("✎");
    pen.setAlpha(0.7f);
    pen.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { start_edit(text, saver); }
    });
    r.addView(pen, new LayoutParams(dp(28), ViewGroup.LayoutParams.MATCH_PARENT));
    r.setOnLongClickListener(new View.OnLongClickListener() {
      public boolean onLongClick(View v)
      {
        PopupMenu p = new PopupMenu(getContext(), r);
        menu.build(p.getMenu());
        p.show();
        return true;
      }
    });
    return r;
  }

  /** The row of an entry, with its text; tapping it types [text]. */
  LinearLayout entry_row(final String text)
  {
    LinearLayout r = new LinearLayout(getContext());
    r.setOrientation(HORIZONTAL);
    r.setBackgroundColor(_item_color);
    TextView t = new TextView(getContext());
    t.setText(one_line(text));
    t.setTextColor(_label_color);
    t.setSingleLine(true);
    t.setEllipsize(TextUtils.TruncateAt.END);
    t.setGravity(Gravity.CENTER_VERTICAL);
    t.setPadding(dp(8), 0, dp(4), 0);
    r.addView(t, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    r.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { type(text); }
    });
    LayoutParams p = new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30));
    p.setMargins(dp(3), dp(3), dp(3), 0);
    r.setLayoutParams(p);
    return r;
  }

  /** The start of [s] on one line: spaces and line breaks as single spaces. */
  static String one_line(String s)
  {
    String t = s.length() > PREVIEW_CHARS * 2 ? s.substring(0, PREVIEW_CHARS * 2) : s;
    t = t.trim().replaceAll("\\s+", " ");
    return t.length() > PREVIEW_CHARS ? t.substring(0, PREVIEW_CHARS) + "…" : t;
  }

  TextView small_button(String label)
  {
    TextView t = new TextView(getContext());
    t.setText(label);
    t.setTextColor(_label_color);
    t.setGravity(Gravity.CENTER);
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
