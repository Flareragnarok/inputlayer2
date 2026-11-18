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
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** il-009: the code table. Every Unicode block in one grid table, and
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

  /** The cells this phone draws, [null] until known (then every usable
      cell shows). */
  static volatile BitSet _draws = null;

  static boolean shows(int cp, BitSet draws)
  {
    return usable(cp) && (draws == null || draws.get(cp));
  }

  /** A line of the table: a block's title ([base] -1), or [cols] cells
      from code [base]. */
  static final class Row
  {
    final Block block;
    final int base;
    Row(Block b, int ba) { block = b; base = ba; }
  }

  /** The grid table (owner, 2026-11-18: "need like a grid table"): each
      block's title, then its rows of [cols] codes, like the Unicode
      charts. Rows and blocks with nothing to show are left out. */
  static List<Row> rows(List<Block> blocks, int cols, BitSet draws)
  {
    List<Row> out = new ArrayList<Row>();
    for (Block b : blocks)
    {
      boolean titled = false;
      int i = 0;
      while (i < b.cps.length)
      {
        int base = b.cps[i] - b.cps[i] % cols;
        boolean any = false;
        for (; i < b.cps.length && b.cps[i] < base + cols; i++)
          any |= draws == null || draws.get(b.cps[i]);
        if (!any)
          continue;
        if (!titled)
        {
          out.add(new Row(b, -1));
          titled = true;
        }
        out.add(new Row(b, base));
      }
    }
    return out;
  }

  /** What the cell of [cp] in a row of [b] shows, "" for none. */
  static String cell(Block b, int cp, BitSet draws)
  {
    return (cp >= b.first && cp <= b.last && shows(cp, draws)) ? shown(cp) : "";
  }

  /** The table: the grid of every block, or what the search finds. Tap a
      character to pick it, long-press it for its code and name. [done]
      gets the key name of the character tapped. */
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
    final float dp = act.getResources().getDisplayMetrics().density;
    final float width_dp = act.getResources().getDisplayMetrics().widthPixels / dp;
    final int cols = width_dp >= 600 ? 16 : 8;
    final List<Row> table = new ArrayList<Row>(rows(blocks, cols, _draws));
    final AlertDialog[] dlg = new AlertDialog[1];
    final Picker pick = new Picker() {
      public void picked(int cp)
      {
        String n = key_name(cp);
        if (n == null)
        {
          Toast.makeText(act, "Can't put " + hex(cp) + " on a key", Toast.LENGTH_LONG).show();
          return;
        }
        dlg[0].dismiss();
        done.picked(n);
      }
    };
    final TableAdapter grid = new TableAdapter(act, table, cols, dp, pick);
    final List<Object> hits = new ArrayList<Object>();
    final ArrayAdapter<String> found = new ArrayAdapter<String>(act,
        android.R.layout.simple_list_item_1, new ArrayList<String>());

    LinearLayout box = new LinearLayout(act);
    box.setOrientation(LinearLayout.VERTICAL);
    final EditText q = new EditText(act);
    q.setSingleLine(true);
    q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    q.setHint("Search: ₪, 20AA, sheqel, grave, arrows");
    // The column digits, over the cells.
    final LinearLayout heads = TableAdapter.line(act, dp);
    ((TextView)heads.getChildAt(0)).setText("");
    for (int c = 0; c < cols; c++)
    {
      TextView t = TableAdapter.cell_view(act, dp, 12);
      t.setText(Integer.toHexString(c).toUpperCase(Locale.ROOT));
      t.setMinHeight(0);
      heads.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    }
    final ListView list = new ListView(act);
    list.setAdapter(grid);
    list.setFastScrollEnabled(true);
    box.addView(q);
    box.addView(heads);
    box.addView(list, new LinearLayout.LayoutParams(
          LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    dlg[0] = new AlertDialog.Builder(act).setTitle(title).setView(box)
      .setNegativeButton(android.R.string.cancel, (di, i) -> cancel.run())
      .setNeutralButton("Blocks", null)
      .create();

    final Runnable refill = new Runnable() {
      public void run()
      {
        String s = q.getText().toString();
        if (s.trim().isEmpty())
        {
          heads.setVisibility(View.VISIBLE);
          if (list.getAdapter() != grid)
            list.setAdapter(grid);
          return;
        }
        heads.setVisibility(View.GONE);
        BitSet draws = _draws;
        hits.clear();
        List<String> labels = new ArrayList<String>();
        for (Block b : search_blocks(s))
        {
          hits.add(b);
          labels.add("▸ " + b.name + "    " + hex(b.first) + "–" + hex(b.last));
        }
        Names ns = _names;
        for (int cp : search(s, ns))
          if (shows(cp, draws))
          {
            hits.add(cp);
            labels.add(shown(cp) + "    " + hex(cp) + "  " + name(cp));
          }
        if (ns == null)
        {
          hits.add(null);
          labels.add("Loading the names…");
        }
        found.clear();
        found.addAll(labels);
        if (list.getAdapter() != found)
          list.setAdapter(found);
      }
    };
    final Jump jump = new Jump() {
      public void to(Block b)
      {
        q.setText("");
        refill.run();
        for (int i = 0; i < table.size(); i++)
          if (table.get(i).block == b)
          {
            list.setSelection(i);
            return;
          }
      }
    };
    q.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(Editable s) { refill.run(); }
    });
    list.setOnItemClickListener((p, v, i, id) -> {
      if (list.getAdapter() != found)
        return;
      Object r = hits.get(i);
      if (r instanceof Block)
        jump.to((Block)r);
      else if (r instanceof Integer)
        pick.picked((Integer)r);
    });
    list.setOnItemLongClickListener((p, v, i, id) -> {
      if (list.getAdapter() != found || !(hits.get(i) instanceof Integer))
        return false;
      int cp = (Integer)hits.get(i);
      Toast.makeText(act, hex(cp) + " " + name(cp), Toast.LENGTH_LONG).show();
      return true;
    });
    dlg[0].show();
    // "Blocks" jumps to a block, and the table stays open.
    dlg[0].getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
      final List<Block> shown_blocks = new ArrayList<Block>();
      for (Row r : table)
        if (r.base < 0)
          shown_blocks.add(r.block);
      String[] names = new String[shown_blocks.size()];
      for (int i = 0; i < names.length; i++)
        names[i] = shown_blocks.get(i).name + "    " + hex(shown_blocks.get(i).first);
      new AlertDialog.Builder(act).setTitle("Go to block")
        .setItems(names, (di, i) -> jump.to(shown_blocks.get(i)))
        .show();
    });
    // Which cells this phone draws, and the names, off the screen's thread.
    new Thread(new Runnable() {
      public void run()
      {
        try
        {
          if (_draws == null)
          {
            Paint bg = new Paint();
            BitSet d = new BitSet(LAST_CP + 1);
            for (Block b : blocks)
              for (int cp : b.cps)
                if (draws(bg, shown(cp)))
                  d.set(cp);
            _draws = d;
          }
          names();
        }
        catch (Throwable _e) {}
        final List<Row> drawn = rows(blocks, cols, _draws);
        act.runOnUiThread(new Runnable() {
          public void run()
          {
            if (!dlg[0].isShowing())
              return;
            grid.draws = _draws;
            table.clear();
            table.addAll(drawn);
            grid.notifyDataSetChanged();
            refill.run();
          }
        });
      }
    }).start();
  }

  interface Picker { void picked(int cp); }
  interface Jump { void to(Block b); }

  /** The grid: a block's title, or a row label (the code of its first
      cell; the column digit adds to it) then [cols] cells. */
  static final class TableAdapter extends android.widget.BaseAdapter
  {
    final Activity _act;
    final List<Row> _rows;
    final int _cols;
    final float _dp;
    final Picker _pick;
    BitSet draws = _draws;

    TableAdapter(Activity act, List<Row> rows, int cols, float dp, Picker pick)
    {
      _act = act; _rows = rows; _cols = cols; _dp = dp; _pick = pick;
    }

    public int getCount() { return _rows.size(); }
    public Object getItem(int i) { return _rows.get(i); }
    public long getItemId(int i) { return i; }
    @Override public int getViewTypeCount() { return 2; }
    @Override public int getItemViewType(int i) { return _rows.get(i).base < 0 ? 0 : 1; }
    @Override public boolean isEnabled(int i) { return false; }

    static LinearLayout line(Activity act, float dp)
    {
      LinearLayout l = new LinearLayout(act);
      l.setOrientation(LinearLayout.HORIZONTAL);
      TextView label = new TextView(act);
      label.setTypeface(android.graphics.Typeface.MONOSPACE);
      label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
      label.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
      label.setPadding(0, 0, (int)(4 * dp), 0);
      l.addView(label, new LinearLayout.LayoutParams((int)(44 * dp),
            ViewGroup.LayoutParams.MATCH_PARENT));
      return l;
    }

    static TextView cell_view(Activity act, float dp, int sp)
    {
      TextView t = new TextView(act);
      t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
      t.setGravity(Gravity.CENTER);
      t.setMinHeight((int)(44 * dp));
      t.setSingleLine(true);
      return t;
    }

    public View getView(int i, View v, ViewGroup parent)
    {
      final Row r = _rows.get(i);
      if (r.base < 0)
      {
        TextView t = (v instanceof TextView) ? (TextView)v : new TextView(_act);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        t.setPadding((int)(8 * _dp), (int)(14 * _dp), 0, (int)(4 * _dp));
        t.setText(r.block.name + "    " + hex(r.block.first) + "–" + hex(r.block.last));
        return t;
      }
      LinearLayout l = (LinearLayout)v;
      if (l == null)
      {
        l = line(_act, _dp);
        for (int c = 0; c < _cols; c++)
        {
          TextView t = cell_view(_act, _dp, 22);
          t.setBackgroundResource(android.R.drawable.list_selector_background);
          l.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        }
      }
      ((TextView)l.getChildAt(0)).setText(String.format(Locale.ROOT, "%04X", r.base));
      for (int c = 0; c < _cols; c++)
      {
        final int cp = r.base + c;
        TextView t = (TextView)l.getChildAt(c + 1);
        String s = cell(r.block, cp, draws);
        t.setText(s);
        if (s.isEmpty())
        {
          t.setOnClickListener(null);
          t.setOnLongClickListener(null);
          t.setClickable(false);
          continue;
        }
        t.setOnClickListener(x -> _pick.picked(cp));
        t.setOnLongClickListener(x -> {
          Toast.makeText(_act, hex(cp) + " " + name(cp), Toast.LENGTH_LONG).show();
          return true;
        });
      }
      return l;
    }
  }
}
