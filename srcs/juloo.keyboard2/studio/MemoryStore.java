package juloo.keyboard2.studio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import juloo.keyboard2.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Memory: items the user keeps (links, unicode combinations, snippets,
    notes), sorted into categories. Stored in a file in the app's
    credential-protected storage, so it is only readable once the phone is
    unlocked, unlike the keyboard's settings. */
public final class MemoryStore
{
  public static final String LINKS = "Links";
  public static final String SYMBOLS = "Symbols";
  public static final String SNIPPETS = "Snippets";
  public static final String NOTES = "Notes";
  public static final List<String> DEFAULT_CATEGORIES =
    Arrays.asList(LINKS, SYMBOLS, SNIPPETS, NOTES);
  public static final int MAX_ITEMS = 500;
  public static final int MAX_CATEGORY_LENGTH = 24;

  public static final class Item
  {
    public final String text;
    public final String category;
    public final long added;

    Item(String t, String c, long a) { text = t; category = c; added = a; }
  }

  final File _file;
  final List<String> _custom = new ArrayList<String>();
  /** Newest first. */
  final List<Item> _items = new ArrayList<Item>();

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

  /** Items of [category], or all of them if [null]. Newest first. */
  public List<Item> items(String category)
  {
    List<Item> l = new ArrayList<Item>();
    for (Item it : _items)
      if (category == null || category.equals(it.category))
        l.add(it);
    return l;
  }

  /** Store [text] in [category], moving it to the top if it is already
      there. Returns [false] if nothing was stored. */
  public boolean add(String text, String category)
  {
    if (text == null || text.isEmpty() || !categories().contains(category))
      return false;
    remove_matching(text, category);
    _items.add(0, new Item(text, category, System.currentTimeMillis()));
    while (_items.size() > MAX_ITEMS)
      _items.remove(_items.size() - 1);
    save();
    return true;
  }

  public void remove(Item it)
  {
    _items.remove(it);
    save();
  }

  public void move(Item it, String category)
  {
    int i = _items.indexOf(it);
    if (i < 0 || !categories().contains(category))
      return;
    _items.set(i, new Item(it.text, category, it.added));
    save();
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

  /** Removing a category moves its items to Notes. Default categories stay. */
  public void remove_category(String name)
  {
    if (!_custom.remove(name))
      return;
    for (int i = 0; i < _items.size(); i++)
    {
      Item it = _items.get(i);
      if (it.category.equals(name))
        _items.set(i, new Item(it.text, NOTES, it.added));
    }
    save();
  }

  /** The category a new item most likely belongs to. */
  public static String guess_category(String text)
  {
    String t = text.trim();
    if (t.matches("(?is)(https?://|www\\.)\\S+"))
      return LINKS;
    if (t.contains("\n"))
      return SNIPPETS;
    boolean letters = false;
    for (int i = 0; i < t.length(); i++)
      if (Character.isLetterOrDigit(t.charAt(i)))
        letters = true;
    return letters ? NOTES : SYMBOLS;
  }

  void remove_matching(String text, String category)
  {
    for (int i = _items.size() - 1; i >= 0; i--)
      if (_items.get(i).text.equals(text) && _items.get(i).category.equals(category))
        _items.remove(i);
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
      JSONArray is = j.optJSONArray("items");
      if (is != null)
        for (int i = 0; i < is.length(); i++)
        {
          JSONObject o = is.optJSONObject(i);
          if (o == null || o.optString("text", "").isEmpty())
            continue;
          String c = o.optString("category", NOTES);
          if (!categories().contains(c))
            c = NOTES;
          _items.add(new Item(o.optString("text"), c, o.optLong("added", 0)));
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
      j.put("version", 1);
      j.put("categories", new JSONArray(_custom));
      JSONArray is = new JSONArray();
      for (Item it : _items)
      {
        JSONObject o = new JSONObject();
        o.put("text", it.text);
        o.put("category", it.category);
        o.put("added", it.added);
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
