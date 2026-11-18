package juloo.keyboard2.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Paint;
import android.os.Build;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** il-009: the code table. Every Unicode block as a grid of characters, and
    a search by character, code (20AA, U+20AA) or Unicode name (sheqel);
    the character picked goes on the key (owner, 2026-11-18: "a browsable
    code table … ₪ è ♪ whatever"). Used by the key editor and, through the
    key picker's button, everywhere a key is picked. */
public final class CodeTable
{
  public interface Done { void picked(String key_name); }

  public static final class Block
  {
    public final String name;
    public final int first;
    public final int last;
    /** Its usable characters, in code order. */
    final int[] cps;
    /** Whether this phone draws at least one of them (set in the
        background, [true] until known). */
    volatile boolean drawable = true;

    Block(String n, int f, int l, int[] c) { name = n; first = f; last = l; cps = c; }
  }

  /** Planes 0 to 3: everything with characters but tags and private use. */
  static final int LAST_CP = 0x3FFFF;
  static final int MAX_HITS = 400;

  private CodeTable() {}

  /** A character that can be put on a key and shown. */
  static boolean usable(int cp)
  {
    switch (Character.getType(cp))
    {
      case Character.UNASSIGNED: case Character.CONTROL: case Character.FORMAT:
      case Character.SURROGATE: case Character.PRIVATE_USE:
      case Character.LINE_SEPARATOR: case Character.PARAGRAPH_SEPARATOR:
        return false;
      default:
        return true;
    }
  }

  static List<Block> _blocks = null;

  /** The blocks that have usable characters, in code order. */
  public static synchronized List<Block> blocks()
  {
    if (_blocks != null)
      return _blocks;
    List<Block> out = new ArrayList<Block>();
    Character.UnicodeBlock cur = null;
    int[] buf = new int[1024];
    int n = 0;
    for (int cp = 0; cp <= LAST_CP + 1; cp++)
    {
      Character.UnicodeBlock b = (cp > LAST_CP) ? null : Character.UnicodeBlock.of(cp);
      if (b != cur)
      {
        if (cur != null && n > 0)
          out.add(new Block(pretty(cur.toString()), buf[0], buf[n - 1],
                Arrays.copyOf(buf, n)));
        cur = b;
        n = 0;
      }
      if (b != null && usable(cp))
      {
        if (n == buf.length)
          buf = Arrays.copyOf(buf, n * 2);
        buf[n++] = cp;
      }
    }
    _blocks = out;
    return out;
  }

  /** "CURRENCY_SYMBOLS" → "Currency symbols". */
  static String pretty(String id)
  {
    String s = id.toLowerCase(Locale.ROOT).replace('_', ' ');
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  static String text(int cp) { return new String(Character.toChars(cp)); }

  /** What the table shows: combining marks on ◌, spaces as ␣. */
  static String shown(int cp)
  {
    switch (Character.getType(cp))
    {
      case Character.NON_SPACING_MARK: case Character.ENCLOSING_MARK:
      case Character.COMBINING_SPACING_MARK:
        return "◌" + text(cp);
      case Character.SPACE_SEPARATOR:
        return "␣";
      default:
        return text(cp);
    }
  }

  static String hex(int cp) { return String.format(Locale.ROOT, "U+%04X", cp); }

  static String name(int cp)
  {
    String n = null;
    try { n = Character.getName(cp); } catch (Throwable _e) {}
    return n == null ? hex(cp) : n;
  }

  /** Names to search, without the blocks whose names are only numbers
      (ideographs, syllables). Built once, in the background. */
  static final class Names
  {
    final int[] cps;
    final String[] names;
    Names(int[] c, String[] n) { cps = c; names = n; }
  }

  static volatile Names _names = null;

  static boolean numbered(Block b)
  {
    String n = b.name.toLowerCase(Locale.ROOT);
    return b.first >= 0x20000 || n.startsWith("cjk unified")
      || n.startsWith("cjk compatibility ideographs") || n.startsWith("hangul syllables")
      || n.startsWith("tangut") || n.startsWith("khitan") || n.startsWith("nushu");
  }

  static Names names()
  {
    Names ns = _names;
    if (ns != null)
      return ns;
    List<Integer> cps = new ArrayList<Integer>();
    List<String> names = new ArrayList<String>();
    for (Block b : blocks())
      if (!numbered(b))
        for (int cp : b.cps)
        {
          cps.add(cp);
          names.add(name(cp).toLowerCase(Locale.ROOT));
        }
    int[] c = new int[cps.size()];
    for (int i = 0; i < c.length; i++)
      c[i] = cps.get(i);
    _names = ns = new Names(c, names.toArray(new String[0]));
    return ns;
  }

  /** The blocks whose name contains [q]. */
  static List<Block> search_blocks(String q)
  {
    String f = q.trim().toLowerCase(Locale.ROOT);
    List<Block> out = new ArrayList<Block>();
    if (f.length() < 2)
      return out;
    for (Block b : blocks())
      if (b.name.toLowerCase(Locale.ROOT).contains(f))
        out.add(b);
    return out;
  }

  /** The characters [q] asks for, best first: the code it spells (20AA,
      U+20AA, 0x20AA), the characters pasted in it, then those whose name
      has all its words, in code order ([names] null: not searched yet). */
  static List<Integer> search(String q, Names names)
  {
    Set<Integer> out = new LinkedHashSet<Integer>();
    String t = q.trim();
    String h = t.replaceFirst("^(?i)(u\\+|0x)", "");
    if (h.matches("[0-9a-fA-F]{2,6}"))
    {
      int cp = Integer.parseInt(h, 16);
      if (cp <= Character.MAX_CODE_POINT && usable(cp))
        out.add(cp);
    }
    List<String> words = new ArrayList<String>();
    for (int i = 0; i < t.length(); )
    {
      int cp = t.codePointAt(i);
      i += Character.charCount(cp);
      if (cp >= 0x80 && usable(cp) && !Character.isWhitespace(cp))
        out.add(cp);
    }
    for (String w : t.toLowerCase(Locale.ROOT).split("[^a-z0-9-]+"))
      if (!w.isEmpty())
        words.add(w);
    if (names != null && !words.isEmpty() && t.length() >= 2)
      for (int i = 0; i < names.cps.length && out.size() < MAX_HITS; i++)
      {
        boolean all = true;
        for (String w : words)
          if (!names.names[i].contains(w)) { all = false; break; }
        if (all)
          out.add(names.cps[i]);
      }
    return new ArrayList<Integer>(out);
  }

  /** The key name for [cp], [null] if it can't be a key. */
  static String key_name(int cp) { return KeyMap.name_for_text(text(cp)); }

  /** Whether this phone's fonts draw [s] (always, before Android 6). */
  static boolean draws(Paint p, String s)
  {
    return Build.VERSION.SDK_INT < 23 || p.hasGlyph(s);
  }

  /** The table: the blocks, or what the search finds; a block opens as a
      grid. [done] gets the key name of the character tapped. */
  public static void show(final Activity act, String title, final Done done,
      final Runnable cancel)
  {
    final List<Block> blocks;
    try { blocks = blocks(); }
    catch (Throwable e)
    {
      Toast.makeText(act, "Can't read the code table: " + e, Toast.LENGTH_LONG).show();
      cancel.run();
      return;
    }
    final Paint paint = new Paint();
    final List<Object> rows = new ArrayList<Object>();
    final ArrayAdapter<String> adapter = new ArrayAdapter<String>(act,
        android.R.layout.simple_list_item_1, new ArrayList<String>());
    LinearLayout box = new LinearLayout(act);
    box.setOrientation(LinearLayout.VERTICAL);
    final EditText q = new EditText(act);
    q.setSingleLine(true);
    q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    q.setHint("Search: ₪, 20AA, sheqel, grave, arrows");
    final ListView list = new ListView(act);
    list.setAdapter(adapter);
    box.addView(q);
    box.addView(list, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    final AlertDialog dlg = new AlertDialog.Builder(act).setTitle(title).setView(box)
      .setNegativeButton(android.R.string.cancel, (di, i) -> cancel.run())
      .create();
    final Runnable refill = new Runnable() {
      public void run()
      {
        String s = q.getText().toString();
        rows.clear();
        List<String> labels = new ArrayList<String>();
        if (s.trim().isEmpty())
        {
          for (Block b : blocks)
            if (b.drawable)
            {
              rows.add(b);
              labels.add(block_label(b, paint));
            }
        }
        else
        {
          for (Block b : search_blocks(s))
            if (b.drawable)
            {
              rows.add(b);
              labels.add("▸ " + block_label(b, paint));
            }
          Names ns = _names;
          for (int cp : search(s, ns))
            if (draws(paint, shown(cp)))
            {
              rows.add(cp);
              labels.add(shown(cp) + "    " + hex(cp) + "  " + name(cp));
            }
          if (ns == null)
          {
            rows.add(null);
            labels.add("Loading the names…");
          }
        }
        adapter.clear();
        adapter.addAll(labels);
      }
    };
    q.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(Editable s) { refill.run(); }
    });
    list.setOnItemClickListener((p, v, i, id) -> {
      Object r = rows.get(i);
      if (r instanceof Block)
        open_block(act, (Block)r, paint, dlg, done);
      else if (r instanceof Integer)
        picked(act, (Integer)r, dlg, null, done);
    });
    list.setOnItemLongClickListener((p, v, i, id) -> {
      Object r = rows.get(i);
      if (r instanceof Integer)
        Toast.makeText(act, hex((Integer)r) + " " + name((Integer)r), Toast.LENGTH_LONG).show();
      return r instanceof Integer;
    });
    refill.run();
    dlg.show();
    // The names and which blocks this phone draws, off the screen's thread.
    new Thread(new Runnable() {
      public void run()
      {
        try
        {
          Paint bg = new Paint();
          for (Block b : blocks)
          {
            boolean any = false;
            for (int cp : b.cps)
              if (draws(bg, shown(cp))) { any = true; break; }
            b.drawable = any;
          }
          names();
        }
        catch (Throwable _e) {}
        act.runOnUiThread(new Runnable() {
          public void run() { if (dlg.isShowing()) refill.run(); }
        });
      }
    }).start();
  }

  static String block_label(Block b, Paint p)
  {
    StringBuilder prev = new StringBuilder();
    int k = 0;
    for (int i = 0; i < b.cps.length && k < 6 && i < 64; i++)
      if (draws(p, shown(b.cps[i])))
      {
        prev.append(shown(b.cps[i])).append(' ');
        k++;
      }
    return b.name + "    " + hex(b.first) + "–" + hex(b.last) + "\n" + prev;
  }

  /** The block as a grid of big characters; tap picks, long press names. */
  static void open_block(final Activity act, Block b, Paint p,
      final AlertDialog table, final Done done)
  {
    final List<Integer> cps = new ArrayList<Integer>();
    for (int cp : b.cps)
      if (draws(p, shown(cp)))
        cps.add(cp);
    final float dp = act.getResources().getDisplayMetrics().density;
    GridView grid = new GridView(act);
    grid.setNumColumns(GridView.AUTO_FIT);
    grid.setColumnWidth((int)(52 * dp));
    grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
    grid.setAdapter(new ArrayAdapter<Integer>(act, 0, cps) {
      @Override
      public View getView(int i, View v, ViewGroup parent)
      {
        TextView t = (v instanceof TextView) ? (TextView)v : new TextView(act);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26);
        t.setGravity(Gravity.CENTER);
        t.setMinHeight((int)(52 * dp));
        t.setText(shown(getItem(i)));
        return t;
      }
    });
    final AlertDialog dlg = new AlertDialog.Builder(act)
      .setTitle(b.name + "  " + hex(b.first) + "–" + hex(b.last))
      .setView(grid)
      .setNegativeButton("Blocks", null)
      .create();
    grid.setOnItemClickListener((pa, v, i, id) -> picked(act, cps.get(i), table, dlg, done));
    grid.setOnItemLongClickListener((pa, v, i, id) -> {
      Toast.makeText(act, hex(cps.get(i)) + " " + name(cps.get(i)), Toast.LENGTH_LONG).show();
      return true;
    });
    dlg.show();
  }

  static void picked(Activity act, int cp, AlertDialog table, AlertDialog grid, Done done)
  {
    String n = key_name(cp);
    if (n == null)
    {
      Toast.makeText(act, "Can't put " + hex(cp) + " on a key", Toast.LENGTH_LONG).show();
      return;
    }
    if (grid != null)
      grid.dismiss();
    table.dismiss();
    done.picked(n);
  }
}
