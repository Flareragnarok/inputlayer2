package juloo.keyboard2.studio;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import juloo.keyboard2.Utils;
import org.json.JSONObject;

/** Words learned from the user's typing, for suggestions:
    - every finished word is counted, so frequent words rank higher;
    - a word becomes a suggestion once typed [MIN_COUNT] times;
    - words written in typing in format are kept as written (their look and
      case) and suggested at once, from their plain letters;
    - which word followed which, to suggest the next word before anything
      is typed.
    Stored in the app's credential-protected storage (readable only once
    the phone is unlocked); never fed from password fields. */
public final class UserWords
{
  public static final String KEY_LEARN = "studio_learn_words";
  public static final int MIN_COUNT = 2;
  public static final int MAX_WORDS = 5000;
  /** Followers kept per word. */
  public static final int MAX_NEXT = 12;

  static final class Entry
  {
    /** As shown and inserted: as last typed, or as written in format. */
    String display;
    int count;
    long last;
    /** Written in typing in format: suggested at once, display kept. */
    boolean written;
  }

  final File _file;
  final Map<String, Entry> _words = new HashMap<String, Entry>();
  /** Word key -> following word key -> count. */
  final Map<String, Map<String, Integer>> _next = new HashMap<String, Map<String, Integer>>();
  long _clock = 0;

  public UserWords(File file)
  {
    _file = file;
    load();
  }

  /** Lookup key of a word: plain letters (no unicode look), lowercase. */
  public static String key(String word)
  {
    return UnicodeStyles.to_plain(word).toLowerCase();
  }

  /** [word] was typed, after [prev] ([null] at the start of a text). */
  public void learn(String word, String prev)
  {
    if (word == null || word.length() < 2 || word.length() > 48)
      return;
    String k = key(word);
    Entry e = _words.get(k);
    if (e == null)
    {
      e = new Entry();
      e.display = word;
      _words.put(k, e);
    }
    else if (!e.written)
      e.display = word;
    e.count++;
    e.last = ++_clock;
    if (prev != null && prev.length() > 0)
      follow(key(prev), k);
    trim();
    _dirty = true;
  }

  /** A word written in typing in format: kept as written. */
  public void learn_written(String word)
  {
    if (word == null || word.isEmpty() || word.length() > 96)
      return;
    String k = key(word);
    if (k.trim().isEmpty())
      return;
    Entry e = _words.get(k);
    if (e == null)
    {
      e = new Entry();
      _words.put(k, e);
    }
    e.display = word;
    e.written = true;
    e.count = Math.max(e.count, MIN_COUNT) + 1;
    e.last = ++_clock;
    trim();
    _dirty = true;
  }

  /** Written to storage by [flush], not on every word. */
  boolean _dirty = false;

  public boolean dirty() { return _dirty; }

  public void flush()
  {
    if (_dirty)
      save();
    _dirty = false;
  }

  /** Learned words starting with [prefix] (plain letters, any case), most
      used first, at most [n]. */
  public List<String> suggest(String prefix, int n)
  {
    String p = key(prefix);
    List<Entry> l = new ArrayList<Entry>();
    for (Map.Entry<String, Entry> me : _words.entrySet())
      if (me.getKey().startsWith(p) && usable(me.getValue()))
        l.add(me.getValue());
    return displays(l, n);
  }

  /** What usually comes after [prev]; without it (or with no history),
      the most used words. At most [n]. */
  public List<String> predict(String prev, int n)
  {
    List<Entry> l = new ArrayList<Entry>();
    Map<String, Integer> f = (prev == null) ? null : _next.get(key(prev));
    if (f != null)
    {
      List<Map.Entry<String, Integer>> fs = new ArrayList<Map.Entry<String, Integer>>(f.entrySet());
      Collections.sort(fs, new Comparator<Map.Entry<String, Integer>>() {
        public int compare(Map.Entry<String, Integer> a, Map.Entry<String, Integer> b)
        {
          return b.getValue() - a.getValue();
        }
      });
      for (Map.Entry<String, Integer> fe : fs)
      {
        Entry e = _words.get(fe.getKey());
        if (e != null && l.size() < n)
          l.add(e);
      }
    }
    List<String> r = new ArrayList<String>();
    for (Entry e : l)
      r.add(e.display);
    if (r.size() < n)
      for (String d : suggest("", n))
        if (r.size() < n && !r.contains(d))
          r.add(d);
    return r;
  }

  public int count(String word)
  {
    Entry e = _words.get(key(word));
    return (e == null) ? 0 : e.count;
  }

  public int size() { return _words.size(); }

  public void forget_all()
  {
    _words.clear();
    _next.clear();
    save();
  }

  static boolean usable(Entry e)
  {
    return e.written || e.count >= MIN_COUNT;
  }

  static List<String> displays(List<Entry> l, int n)
  {
    Collections.sort(l, new Comparator<Entry>() {
      public int compare(Entry a, Entry b)
      {
        if (a.count != b.count)
          return b.count - a.count;
        return Long.compare(b.last, a.last);
      }
    });
    List<String> r = new ArrayList<String>();
    for (int i = 0; i < l.size() && r.size() < n; i++)
      r.add(l.get(i).display);
    return r;
  }

  void follow(String prev, String next)
  {
    Map<String, Integer> f = _next.get(prev);
    if (f == null)
    {
      f = new HashMap<String, Integer>();
      _next.put(prev, f);
    }
    Integer c = f.get(next);
    f.put(next, (c == null) ? 1 : c + 1);
    if (f.size() > MAX_NEXT)
    {
      // Drop the least followed.
      String least = null;
      for (Map.Entry<String, Integer> e : f.entrySet())
        if (least == null || e.getValue() < f.get(least))
          least = e.getKey();
      f.remove(least);
    }
  }

  /** Too many words: the least used (oldest first) go. */
  void trim()
  {
    while (_words.size() > MAX_WORDS)
    {
      String worst = null;
      Entry we = null;
      for (Map.Entry<String, Entry> e : _words.entrySet())
      {
        Entry x = e.getValue();
        if (we == null || x.count < we.count || (x.count == we.count && x.last < we.last))
        {
          worst = e.getKey();
          we = x;
        }
      }
      _words.remove(worst);
      _next.remove(worst);
    }
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
      _clock = j.optLong("clock", 0);
      JSONObject ws = j.optJSONObject("words");
      if (ws != null)
        for (Iterator<String> it = ws.keys(); it.hasNext(); )
        {
          String k = it.next();
          JSONObject o = ws.optJSONObject(k);
          if (o == null)
            continue;
          Entry e = new Entry();
          e.display = o.optString("d", k);
          e.count = o.optInt("c", 0);
          e.last = o.optLong("l", 0);
          e.written = o.optBoolean("w", false);
          _words.put(k, e);
        }
      JSONObject ns = j.optJSONObject("next");
      if (ns != null)
        for (Iterator<String> it = ns.keys(); it.hasNext(); )
        {
          String k = it.next();
          JSONObject o = ns.optJSONObject(k);
          if (o == null)
            continue;
          Map<String, Integer> f = new HashMap<String, Integer>();
          for (Iterator<String> it2 = o.keys(); it2.hasNext(); )
          {
            String n = it2.next();
            f.put(n, o.optInt(n, 0));
          }
          _next.put(k, f);
        }
    }
    catch (Exception _e)
    {
      _file.renameTo(new File(_file.getPath() + ".unreadable"));
    }
  }

  void save()
  {
    try
    {
      JSONObject ws = new JSONObject();
      for (Map.Entry<String, Entry> me : _words.entrySet())
      {
        Entry e = me.getValue();
        JSONObject o = new JSONObject();
        o.put("d", e.display);
        o.put("c", e.count);
        o.put("l", e.last);
        if (e.written)
          o.put("w", true);
        ws.put(me.getKey(), o);
      }
      JSONObject ns = new JSONObject();
      for (Map.Entry<String, Map<String, Integer>> me : _next.entrySet())
        ns.put(me.getKey(), new JSONObject(me.getValue()));
      JSONObject j = new JSONObject();
      j.put("version", 1);
      j.put("clock", _clock);
      j.put("words", ws);
      j.put("next", ns);
      File tmp = new File(_file.getPath() + ".tmp");
      OutputStream out = new FileOutputStream(tmp);
      try { out.write(j.toString().getBytes("UTF-8")); }
      finally { out.close(); }
      tmp.renameTo(_file);
    }
    catch (Exception _e) {}
  }
}
