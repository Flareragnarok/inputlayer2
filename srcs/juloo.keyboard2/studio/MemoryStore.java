package juloo.keyboard2.studio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import juloo.keyboard2.Utils;
import org.json.JSONArray;
import org.json.JSONObject;

/** Memory: what the user keeps (links, unicode combinations, snippets, text),
    sorted into categories. Two kinds of items:
    - saved: saved or moved by the user, only removed one by one;
    - history: kept automatically when copied (see [ClipKeeper]), grouped
      into one record per phone restart ("session"), which can be deleted
      by item, by record or all at once.
    Stored in a file in the app's credential-protected storage, so it is only
    readable once the phone is unlocked, unlike the keyboard's settings. */
public final class MemoryStore
{
  /** Content types, in which copied things are kept automatically. */
  public static final String TEXT = "Text";
  public static final String LINKS = "Links";
  public static final String MAILS = "Mails";
  public static final String SNIPPETS = "Snippets";
  public static final String SYMBOLS = "Symbols";
  public static final String IMAGES = "Images";
  public static final String FILES = "Files";
  public static final List<String> DEFAULT_CATEGORIES =
    Arrays.asList(TEXT, LINKS, MAILS, SNIPPETS, SYMBOLS, IMAGES, FILES);
  public static final int MAX_ITEMS = 1000;
  public static final int MAX_CATEGORY_LENGTH = 24;

  public static final class Item
  {
    public final String text;
    public final String category;
    public final long added;
    /** Session of a history item, [null] for a saved item. */
    public final String session;

    Item(String t, String c, long a, String s)
    {
      text = t; category = c; added = a; session = s;
    }

    public boolean is_history() { return session != null; }

    Item with_category(String c) { return new Item(text, c, added, session); }
  }

  /** A phone restart and what was copied since. */
  public static final class Session
  {
    public final String key;
    /** When the phone started, in milliseconds. */
    public final long started;
    /** Newest first. */
    public final List<Item> items = new ArrayList<Item>();

    Session(String k, long s) { key = k; started = s; }
  }

  final File _file;
  final List<String> _custom = new ArrayList<String>();
  /** Newest first. */
  final List<Item> _items = new ArrayList<Item>();
  /** Session key -> start time, in the order they appeared. */
  final List<String> _session_keys = new ArrayList<String>();
  final List<Long> _session_starts = new ArrayList<Long>();

  public MemoryStore(File file)
  {
    _file = file;
    load();
  }

  /** Default categories, then the user's, in the order they were added. */
  public List<String> categories()
  {
    List<String> l = new ArrayList<String>(DEFAULT_CATEGORIES);
    l.addAll(_custom);
    return l;
  }

  public List<String> custom_categories()
  {
    return new ArrayList<String>(_custom);
  }

  /** Items of [category], or of all categories if [null], newest first. A
      text that is both saved and in the history is listed once. */
  public List<Item> items(String category)
  {
    List<Item> l = new ArrayList<Item>();
    Set<String> seen = new HashSet<String>();
    // Saved items first claim their text.
    for (Item it : _items)
      if (!it.is_history())
        seen.add(it.text);
    for (Item it : _items)
    {
      if (category != null && !category.equals(it.category))
        continue;
      if (it.is_history() && !seen.add(it.text))
        continue;
      l.add(it);
    }
    return l;
  }

  /** History records, newest restart first. Empty records are left out. */
  public List<Session> sessions()
  {
    List<Session> l = new ArrayList<Session>();
    for (int i = _session_keys.size() - 1; i >= 0; i--)
    {
      Session s = new Session(_session_keys.get(i), _session_starts.get(i));
      for (Item it : _items)
        if (s.key.equals(it.session))
          s.items.add(it);
      if (!s.items.isEmpty())
        l.add(s);
    }
    return l;
  }

  /** Whether [text] is saved (not only in the history). */
  public boolean is_saved(String text)
  {
    for (Item it : _items)
      if (!it.is_history() && it.text.equals(text))
        return true;
    return false;
  }

  /** Save [text] in [category], moving it to the top if it is already
      there. Returns [false] if nothing was saved. */
  public boolean add(String text, String category)
  {
    if (text == null || text.isEmpty() || !categories().contains(category))
      return false;
    for (int i = _items.size() - 1; i >= 0; i--)
    {
      Item it = _items.get(i);
      if (!it.is_history() && it.text.equals(text) && it.category.equals(category))
        _items.remove(i);
    }
    _items.add(0, new Item(text, category, System.currentTimeMillis(), null));
    trim();
    save();
    return true;
  }

  /** Record a copied [text] in the history of the session [session_key]
      (started at [session_start]), moving it up if it was already copied
      since that restart. */
  public boolean add_history(String text, String category, String session_key,
      long session_start)
  {
    if (text == null || text.isEmpty() || !categories().contains(category))
      return false;
    if (!_session_keys.contains(session_key))
    {
      _session_keys.add(session_key);
      _session_starts.add(session_start);
    }
    for (int i = _items.size() - 1; i >= 0; i--)
    {
      Item it = _items.get(i);
      if (session_key.equals(it.session) && it.text.equals(text))
        _items.remove(i);
    }
    _items.add(0, new Item(text, category, System.currentTimeMillis(), session_key));
    trim();
    save();
    return true;
  }

  public void remove(Item it)
  {
    _items.remove(it);
    save();
  }

  /** Delete the history of one restart. Saved items stay. */
  public void delete_session(String key)
  {
    for (int i = _items.size() - 1; i >= 0; i--)
      if (key.equals(_items.get(i).session))
        _items.remove(i);
    int i = _session_keys.indexOf(key);
    if (i >= 0)
    {
      _session_keys.remove(i);
      _session_starts.remove(i);
    }
    save();
  }

  /** Delete all the history. Saved items stay. */
  public void delete_history()
  {
    for (int i = _items.size() - 1; i >= 0; i--)
      if (_items.get(i).is_history())
        _items.remove(i);
    _session_keys.clear();
    _session_starts.clear();
    save();
  }

  /** Moving a history item saves it. */
  public void move(Item it, String category)
  {
    int i = _items.indexOf(it);
    if (i < 0 || !categories().contains(category))
      return;
    _items.remove(i);
    add(it.text, category);
  }

  /** Returns [false] for empty, too long or existing names. */
  public boolean add_category(String name)
  {
    name = (name == null) ? "" : name.trim();
    if (name.isEmpty() || name.length() > MAX_CATEGORY_LENGTH)
      return false;
    for (String c : categories())
      if (c.equalsIgnoreCase(name))
        return false;
    _custom.add(name);
    save();
    return true;
  }

  /** Removing a category moves its items to Text. Default categories stay. */
  public void remove_category(String name)
  {
    if (!_custom.remove(name))
      return;
    for (int i = 0; i < _items.size(); i++)
    {
      Item it = _items.get(i);
      if (it.category.equals(name))
        _items.set(i, it.with_category(TEXT));
    }
    save();
  }

  /** The category a new item most likely belongs to. */
  public static String guess_category(String text)
  {
    String t = text.trim();
    // An email address, or a mailto: link.
    if (t.matches("(?i)(mailto:)?[\\w.%+-]+@[\\w-]+(\\.[\\w-]+)+(\\?\\S*)?"))
      return MAILS;
    if (t.matches("(?is)(https?://|www\\.)\\S+"))
      return LINKS;
    if (t.contains("\n"))
      return SNIPPETS;
    boolean letters = false;
    for (int i = 0; i < t.length(); i++)
      if (Character.isLetterOrDigit(t.charAt(i)))
        letters = true;
    return letters ? TEXT : SYMBOLS;
  }

  /** Too many items: the oldest history goes first, then the oldest saved. */
  void trim()
  {
    for (int i = _items.size() - 1; i >= 0 && _items.size() > MAX_ITEMS; i--)
      if (_items.get(i).is_history())
        _items.remove(i);
    while (_items.size() > MAX_ITEMS)
      _items.remove(_items.size() - 1);
  }

  void load()
  {
    if (!_file.exists())
      return;
    try
    {
      InputStream inp = new FileInputStream(_file);
      String s;
      try { s = Utils.read_all_utf8(inp); }
      finally { inp.close(); }
      JSONObject j = new JSONObject(s);
      JSONArray cs = j.optJSONArray("categories");
      if (cs != null)
        for (int i = 0; i < cs.length(); i++)
        {
          String c = cs.optString(i, "");
          if (!c.isEmpty() && !DEFAULT_CATEGORIES.contains(c) && !_custom.contains(c))
            _custom.add(c);
        }
      JSONObject ss = j.optJSONObject("sessions");
      JSONArray order = j.optJSONArray("session_order");
      if (ss != null && order != null)
        for (int i = 0; i < order.length(); i++)
        {
          String k = order.optString(i, "");
          if (ss.has(k) && !_session_keys.contains(k))
          {
            _session_keys.add(k);
            _session_starts.add(ss.optLong(k, 0));
          }
        }
      JSONArray is = j.optJSONArray("items");
      if (is != null)
        for (int i = 0; i < is.length(); i++)
        {
          JSONObject o = is.optJSONObject(i);
          if (o == null || o.optString("text", "").isEmpty())
            continue;
          String c = o.optString("category", TEXT);
          if (!categories().contains(c))
            c = TEXT;
          String session = o.optString("session", "");
          if (session.isEmpty() || !_session_keys.contains(session))
            session = null;
          _items.add(new Item(o.optString("text"), c, o.optLong("added", 0), session));
        }
    }
    catch (Exception _e)
    {
      // An unreadable file is kept aside rather than overwritten.
      _file.renameTo(new File(_file.getPath() + ".unreadable"));
    }
  }

  void save()
  {
    try
    {
      JSONObject j = new JSONObject();
      j.put("version", 2);
      j.put("categories", new JSONArray(_custom));
      JSONObject ss = new JSONObject();
      for (int i = 0; i < _session_keys.size(); i++)
        ss.put(_session_keys.get(i), (long)_session_starts.get(i));
      j.put("sessions", ss);
      j.put("session_order", new JSONArray(_session_keys));
      JSONArray is = new JSONArray();
      for (Item it : _items)
      {
        JSONObject o = new JSONObject();
        o.put("text", it.text);
        o.put("category", it.category);
        o.put("added", it.added);
        if (it.session != null)
          o.put("session", it.session);
        is.put(o);
      }
      j.put("items", is);
      File tmp = new File(_file.getPath() + ".tmp");
      OutputStream out = new FileOutputStream(tmp);
      try { out.write(j.toString().getBytes("UTF-8")); }
      finally { out.close(); }
      tmp.renameTo(_file);
    }
    catch (Exception _e) {}
  }
}
