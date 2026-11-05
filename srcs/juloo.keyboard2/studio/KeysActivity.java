package juloo.keyboard2.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.DirectBootAwarePreferences;
import org.json.JSONObject;

/** Studio → Keys (il-005): every special key and quicksbar action, with a
    search, and the behaviours of the keys that switch something. A
    behaviour chosen here is that key's default everywhere it is placed;
    the variant names (`…~once`) put a behaviour on one place only. */
public class KeysActivity extends Activity
{
  SharedPreferences _prefs;
  List<KeyCatalog.Entry> _all;
  List<KeyCatalog.Entry> _shown = new ArrayList<KeyCatalog.Entry>();
  ArrayAdapter<String> _adapter;
  EditText _query;

  /** Quicksbar actions with behaviours, as entries of their own. */
  static final String QUICKSBAR = "Quicksbar";

  @Override public void onCreate(Bundle b)
  {
    super.onCreate(b);
    _prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    _all = entries();
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(16);
    root.setPadding(pad, pad, pad, 0);
    TextView intro = new TextView(this);
    intro.setText("Search a key, tap it for what it does. Keys marked ⇆ switch something: choose how they switch back.");
    root.addView(intro);
    final EditText q = _query = new EditText(this);
    q.setSingleLine(true);
    q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    q.setHint("Search keys (name or symbol)");
    root.addView(q);
    ListView list = new ListView(this);
    _adapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, new ArrayList<String>());
    list.setAdapter(_adapter);
    list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
      public void onItemClick(AdapterView<?> p, View v, int pos, long id) { show(_shown.get(pos)); }
    });
    root.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    q.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int c, int d) {}
      public void onTextChanged(CharSequence s, int a, int c, int d) {}
      public void afterTextChanged(Editable e) { filter(e.toString()); }
    });
    setContentView(root);
    setTitle("Keys");
    filter("");
  }

  /** The special keys (variants left out: they show in a key's details)
      and the quicksbar actions that switch something. */
  List<KeyCatalog.Entry> entries()
  {
    List<KeyCatalog.Entry> out = new ArrayList<KeyCatalog.Entry>();
    for (Behaviours.Capable c : Behaviours.capable())
      if (c.quicksbar)
        out.add(new KeyCatalog.Entry(c.name, c.name, QUICKSBAR, 0));
    for (KeyCatalog.Entry e : KeyCatalog.with_layouts(KeyCatalog.all(getResources()), new ModuleStore(_prefs)))
      if ((e.group.equals(KeyCatalog.SPECIAL) || e.group.equals(KeyCatalog.LAYOUTS)) && e.name.indexOf(Behaviours.SEP) < 0)
        out.add(e);
    return out;
  }

  void filter(String q)
  {
    _shown = KeyCatalog.filter(_all, q);
    // Switching keys first: they're what this screen is for.
    List<KeyCatalog.Entry> sw = new ArrayList<KeyCatalog.Entry>(), rest = new ArrayList<KeyCatalog.Entry>();
    for (KeyCatalog.Entry e : _shown)
      (Behaviours.capable(e.name) != null ? sw : rest).add(e);
    sw.addAll(rest);
    _shown = sw;
    List<String> labels = new ArrayList<String>();
    JSONObject d = defaults();
    for (KeyCatalog.Entry e : _shown)
    {
      Behaviours.Capable c = Behaviours.capable(e.name);
      labels.add(c == null ? e.label()
          : "⇆ " + e.label() + "   [" + describe(Behaviours.default_of(d, e.name), c) + "]");
    }
    _adapter.clear();
    _adapter.addAll(labels);
  }

  JSONObject defaults()
  {
    return Behaviours.parse_defaults(_prefs.getString(Behaviours.PREF, "{}"));
  }

  static String describe(Behaviours.Spec s, Behaviours.Capable c)
  {
    switch (s.mode)
    {
      case ONCE: return "back " + c.use_text();
      case WORD: return "back at the end of the word";
      case TIMED: return "back after " + s.seconds + " s";
      default: return "toggle";
    }
  }

  void show(final KeyCatalog.Entry e)
  {
    final Behaviours.Capable c = Behaviours.capable(e.name);
    AlertDialog.Builder d = new AlertDialog.Builder(this).setTitle(e.glyph + "    " + e.name);
    if (c == null)
    {
      d.setMessage("No behaviours for this key: it does one thing. Place it with edit mode (quick layout) or the layout editor.\n\nName: " + e.name)
        .setPositiveButton(android.R.string.ok, null)
        .setNeutralButton("Copy name", new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface di, int i) { copy(e.name); }
        })
        .show();
      return;
    }
    Behaviours.Spec cur = Behaviours.default_of(defaults(), e.name);
    final String[] items = {
      "Toggle: stays until pressed again",
      "Back " + c.use_text(),
      "Back at the end of the word",
      "Back after some seconds…" };
    int checked = cur.mode.ordinal();
    StringBuilder vars = new StringBuilder();
    for (String m : new String[]{ "toggle", "once", "word", "10s" })
      vars.append(vars.length() == 0 ? "" : ", ").append(e.name).append(Behaviours.SEP).append(m);
    final String variants = vars.toString();
    d.setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int which)
        {
          di.dismiss();
          if (which == 3)
            ask_seconds(e.name, c);
          else
            save(e.name, new Behaviours.Spec(Behaviours.Mode.values()[which], 0), c);
        }
      })
      .setNegativeButton(android.R.string.cancel, null)
      .setNeutralButton("Copy variant names", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int i) { copy(variants); }
      });
    TextView msg = new TextView(this);
    msg.setText(c.what + "\n\nDefault behaviour, wherever this key is placed. To give one place its own behaviour, place a variant: "
        + variants + (c.quicksbar ? " (as a quicksbar action)." : " (edit mode → Replace with…, or the layout editor)."));
    msg.setPadding(dp(24), dp(8), dp(24), 0);
    msg.setTextIsSelectable(true);
    LinearLayout wrap = new LinearLayout(this);
    wrap.addView(msg);
    d.setView(wrap);
    d.show();
  }

  void ask_seconds(final String name, final Behaviours.Capable c)
  {
    final EditText t = new EditText(this);
    t.setInputType(InputType.TYPE_CLASS_NUMBER);
    t.setText("10");
    new AlertDialog.Builder(this).setTitle("Back after how many seconds?").setView(t)
      .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface di, int i)
        {
          Behaviours.Spec s = Behaviours.Spec.parse(t.getText().toString().trim() + "s");
          if (s == null)
            Toast.makeText(KeysActivity.this, "1 to 9999 seconds", Toast.LENGTH_LONG).show();
          else
            save(name, s, c);
        }
      })
      .setNegativeButton(android.R.string.cancel, null).show();
  }

  void save(String name, Behaviours.Spec s, Behaviours.Capable c)
  {
    try
    {
      _prefs.edit().putString(Behaviours.PREF, Behaviours.with_default(defaults(), name, s).toString()).apply();
      Toast.makeText(this, name + ": " + describe(s, c), Toast.LENGTH_SHORT).show();
    }
    catch (Exception ex)
    {
      Toast.makeText(this, String.valueOf(ex), Toast.LENGTH_LONG).show();
    }
    filter(_query.getText().toString());
  }

  void copy(String s)
  {
    ClipboardManager cm = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
    cm.setPrimaryClip(ClipData.newPlainText("Input Layer keys", s));
    Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
  }

  int dp(int v)
  {
    return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
  }
}
