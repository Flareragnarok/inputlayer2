package juloo.keyboard2.studio;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.R;
import org.xmlpull.v1.XmlPullParser;

/** Every key the picker of layout mode offers: the keys of all the
    languages' layouts and all the special keys, grouped by script (Latin,
    Greek, Cyrillic, the other scripts, then digits and symbols, then special
    keys) and sorted alphabetically inside a group. */
public final class KeyCatalog
{
  public static final class Entry
  {
    /** For [KeyValue.getKeyByName], what a [keymap] module stores. */
    public final String name;
    /** What the key shows. */
    public final String glyph;
    public final String group;
    final int rank;

    Entry(String n, String gl, String gr, int r) { name = n; glyph = gl; group = gr; rank = r; }

    /** What the list shows. */
    public String label()
    {
      return glyph.equals(name) ? glyph + "    · " + group
        : glyph + "    " + name + " · " + group;
    }
  }

  static final String SYMBOLS = "Digits & symbols";
  static final String SPECIAL = "Special keys";

  private KeyCatalog() {}

  static List<Entry> _cache = null;

  /** Built once, reading every layout. */
  public static synchronized List<Entry> all(Resources res)
  {
    if (_cache == null)
    {
      Set<String> names = new LinkedHashSet<String>(layout_key_names(res));
      Collections.addAll(names, KeyNames.SPECIAL);
      _cache = build(names);
    }
    return _cache;
  }

  /** Entries for [names], one per distinct key, sorted. */
  static List<Entry> build(Collection<String> names)
  {
    List<Entry> out = new ArrayList<Entry>();
    Set<KeyValue> seen = new HashSet<KeyValue>();
    for (String n : names)
    {
      KeyValue kv = KeyMap.key_of_name(n);
      if (kv == null || kv.getKind() == KeyValue.Kind.Placeholder || !seen.add(kv))
        continue;
      String glyph = kv.getString();
      String group = group_of(kv);
      out.add(new Entry(n, glyph.isEmpty() ? n : glyph, group, rank_of(group)));
    }
    final Collator col = Collator.getInstance(Locale.ROOT);
    Collections.sort(out, new Comparator<Entry>() {
      public int compare(Entry a, Entry b)
      {
        if (a.rank != b.rank)
          return a.rank - b.rank;
        int c = a.group.compareTo(b.group);
        if (c != 0)
          return c;
        // Special keys by name, the others by what they show.
        c = a.rank == rank_of(SPECIAL) ? a.name.compareTo(b.name)
          : col.compare(a.glyph, b.glyph);
        return (c != 0) ? c : a.name.compareTo(b.name);
      }
    });
    return out;
  }

  static String group_of(KeyValue kv)
  {
    KeyValue.Kind k = kv.getKind();
    if (k != KeyValue.Kind.Char && k != KeyValue.Kind.String)
      return SPECIAL;
    String s = kv.getString();
    if (s.isEmpty())
      return SPECIAL;
    Character.UnicodeScript sc = Character.UnicodeScript.of(s.codePointAt(0));
    switch (sc)
    {
      case COMMON: case INHERITED: case UNKNOWN: return SYMBOLS;
      default:
        String n = sc.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }
  }

  static int rank_of(String group)
  {
    switch (group)
    {
      case "Latin": return 0;
      case "Greek": return 1;
      case "Cyrillic": return 2;
      case SYMBOLS: return 4;
      case SPECIAL: return 5;
      default: return 3;
    }
  }

  /** Entries whose glyph, name or group contains [q], ignoring case. */
  public static List<Entry> filter(List<Entry> all, String q)
  {
    String f = q.trim().toLowerCase(Locale.ROOT);
    if (f.isEmpty())
      return all;
    List<Entry> out = new ArrayList<Entry>();
    for (Entry e : all)
      if (e.glyph.toLowerCase(Locale.ROOT).contains(f)
          || e.name.toLowerCase(Locale.ROOT).contains(f)
          || e.group.toLowerCase(Locale.ROOT).contains(f))
        out.add(e);
    return out;
  }

  static final String[] KEY_ATTRS = {
    "c", "n", "ne", "e", "se", "s", "sw", "w", "nw",
    "key0", "key1", "key2", "key3", "key4", "key5", "key6", "key7", "key8" };

  /** The names on the keys of every layout, Input Layer's symbol pages and
      bottom row included. */
  static Set<String> layout_key_names(Resources res)
  {
    Set<String> names = new LinkedHashSet<String>();
    List<Integer> ids = new ArrayList<Integer>();
    TypedArray ta = res.obtainTypedArray(R.array.layout_ids);
    for (int i = 0; i < ta.length(); i++)
    {
      int id = ta.getResourceId(i, 0);
      if (id != 0)
        ids.add(id);
    }
    ta.recycle();
    ids.add(R.xml.il_symbols);
    ids.add(R.xml.il_symbols_more);
    ids.add(R.xml.bottom_row);
    for (int id : ids)
    {
      XmlResourceParser p = null;
      try
      {
        p = res.getXml(id);
        int ev;
        while ((ev = p.next()) != XmlPullParser.END_DOCUMENT)
        {
          if (ev != XmlPullParser.START_TAG || !"key".equals(p.getName()))
            continue;
          for (String a : KEY_ATTRS)
          {
            String v = p.getAttributeValue(null, a);
            if (v == null)
              continue;
            if (v.startsWith("loc "))
              v = v.substring(4);
            if (!v.isEmpty())
              names.add(v);
          }
        }
      }
      catch (Exception _e) {}
      finally
      {
        if (p != null)
          p.close();
      }
    }
    return names;
  }
}
