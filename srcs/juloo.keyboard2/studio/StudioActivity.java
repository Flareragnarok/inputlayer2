package juloo.keyboard2.studio;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInstaller;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import juloo.keyboard2.DirectBootAwarePreferences;
import juloo.keyboard2.Logs;
import juloo.keyboard2.R;
import juloo.keyboard2.prefs.LayoutsPreference;
import juloo.keyboard2.Utils;

/** The Studio screen: installed build and updates, and the saved modules
    (use, export, import, delete). */
public class StudioActivity extends Activity
{
  static final String KEY_UPDATE_REPO = "studio_update_repo";
  /** The updater offers beta builds too (GitHub pre-releases). */
  static final String KEY_UPDATE_BETA = "studio_update_beta";
  /** Open the Spec / Vert editor on this type ("spec", "vert") and name. */
  public static final String EXTRA_EDIT_TYPE = "studio_edit_type";
  public static final String EXTRA_EDIT_NAME = "studio_edit_name";
  /** Layout mode: the key to edit (see [KeyEditor]). */
  public static final String EXTRA_KEY_LAYOUT = "studio_key_layout";
  public static final String EXTRA_KEY_ID = "studio_key_id";
  public static final String EXTRA_KEY_NAMES = "studio_key_names";
  /** The layout's other keys: ids, and 9 names per key. */
  public static final String EXTRA_KEY_ALL_IDS = "studio_key_all_ids";
  public static final String EXTRA_KEY_ALL_NAMES = "studio_key_all_names";
  /** The edit key's "add a key": the layout, its rows (JSON, key ids), the
      row and position. */
  public static final String EXTRA_ADD_LAYOUT = "studio_add_layout";
  public static final String EXTRA_ADD_ROWS = "studio_add_rows";
  public static final String EXTRA_ADD_ROW = "studio_add_row";
  public static final String EXTRA_ADD_POS = "studio_add_pos";
  /** Widths, in key units: the key, its row, the widest row. */
  public static final String EXTRA_KEY_WIDTH = "studio_key_width";
  public static final String EXTRA_ROW_WIDTH = "studio_row_width";
  public static final String EXTRA_KEYBOARD_WIDTH = "studio_keyboard_width";
  static final String ACTION_INSTALL_STATUS = "juloo.keyboard2.studio.INSTALL_STATUS";
  static final int REQUEST_EXPORT = 1;
  static final int REQUEST_IMPORT = 2;

  SharedPreferences _prefs;
  ModuleStore _store;
  int _installed_build;

  EditText _repo;
  TextView _update_status;
  LinearLayout _releases;
  LinearLayout _modules;
  LinearLayout _categories;
  ScrollView _scroll;
  TextView _editor_top;
  Spinner _editor_pick;
  LinearLayout _editor_ops;
  LinearLayout _editor_add;
  /** "type/name" of the Spec or Vert in the editor. */
  String _editing = null;
  /** Module waiting for the export file to be picked. */
  Module _exporting;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    CrashLog.install(this);
    _prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    Renames.migrate(_prefs); // il-007
    _store = new ModuleStore(_prefs);
    // Heights out of range are repaired (keeping everything else); guarded
    // like everything on the way to the updater.
    try { Heights.check(_prefs); }
    catch (Throwable e) { android.util.Log.e("Studio", "Heights.check", e); }
    _installed_build = getResources().getInteger(R.integer.studio_build);
    setTitle(R.string.studio_title);
    setContentView(build_view());
    refresh_modules();
    refresh_categories();
    refresh_crashes();
    handle_install_status(getIntent());
    open_editor_from(getIntent());
    open_key_editor_from(getIntent());
    open_add_key_from(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent)
  {
    super.onNewIntent(intent);
    handle_install_status(intent);
    open_editor_from(intent);
    open_key_editor_from(intent);
    open_add_key_from(intent);
  }

  void open_add_key_from(Intent i)
  {
    String rows = (i == null) ? null : i.getStringExtra(EXTRA_ADD_ROWS);
    if (rows == null)
      return;
    i.removeExtra(EXTRA_ADD_ROWS);
    try
    {
      new KeyEditor(this, _prefs, i.getStringExtra(EXTRA_ADD_LAYOUT), "",
          null, null, null).add_key(rows, i.getIntExtra(EXTRA_ADD_ROW, 0),
            i.getIntExtra(EXTRA_ADD_POS, 0));
    }
    catch (Throwable e)
    {
      android.util.Log.e("Studio", "Add key", e);
    }
  }

  void open_key_editor_from(Intent i)
  {
    String id = (i == null) ? null : i.getStringExtra(EXTRA_KEY_ID);
    if (id == null)
      return;
    // Only once, not again when the activity is recreated.
    i.removeExtra(EXTRA_KEY_ID);
    try
    {
      new KeyEditor(this, _prefs, i.getStringExtra(EXTRA_KEY_LAYOUT), id,
          i.getStringArrayExtra(EXTRA_KEY_NAMES),
          i.getStringArrayExtra(EXTRA_KEY_ALL_IDS),
          i.getStringArrayExtra(EXTRA_KEY_ALL_NAMES))
        .with_widths(i.getFloatExtra(EXTRA_KEY_WIDTH, 0f),
            i.getFloatExtra(EXTRA_ROW_WIDTH, 0f),
            i.getFloatExtra(EXTRA_KEYBOARD_WIDTH, 0f))
        .show();
    }
    catch (Throwable e)
    {
      android.util.Log.e("Studio", "Key editor", e);
    }
  }

  View build_view()
  {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(16);
    root.setPadding(pad, pad, pad, pad);

    // Opened from the app's icon: the keyboard's own setup and upstream's
    // settings are one tap away.
    LinearLayout setup = new LinearLayout(this);
    setup.addView(button(R.string.studio_enable_keyboard, new View.OnClickListener() {
      public void onClick(View v)
      {
        startActivity(new Intent(android.provider.Settings.ACTION_INPUT_METHOD_SETTINGS));
      }
    }));
    setup.addView(button(R.string.studio_legacy_title, new View.OnClickListener() {
      public void onClick(View v)
      {
        startActivity(new Intent(StudioActivity.this, juloo.keyboard2.SettingsActivity.class));
      }
    }));
    root.addView(setup);
    // il-005: every key, searchable; how the switching keys switch back.
    LinearLayout tools = new LinearLayout(this);
    tools.addView(button("Keys and behaviours", new View.OnClickListener() {
      public void onClick(View v) { startActivity(new Intent(StudioActivity.this, KeysActivity.class)); }
    }));
    // il-006: whole layouts, drawn and saved; layout:<name> keys show them.
    tools.addView(button("Layout editor", new View.OnClickListener() {
      public void onClick(View v) { startActivity(new Intent(StudioActivity.this, LayoutEditorActivity.class)); }
    }));
    root.addView(tools);

    root.addView(heading(R.string.studio_updates));
    root.addView(text(getString(R.string.studio_installed,
            getVersionName(), _installed_build)));
    _repo = new EditText(this);
    _repo.setSingleLine(true);
    _repo.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
    _repo.setHint(R.string.studio_repo_hint);
    _repo.setText(_prefs.getString(KEY_UPDATE_REPO,
          getString(R.string.studio_update_repo)));
    root.addView(_repo);
    final CheckBox beta = new CheckBox(this);
    beta.setText(R.string.studio_update_beta);
    beta.setChecked(_prefs.getBoolean(KEY_UPDATE_BETA, false));
    beta.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      public void onCheckedChanged(CompoundButton b, boolean checked)
      {
        _prefs.edit().putBoolean(KEY_UPDATE_BETA, checked).apply();
        // The list already read follows the switch at once.
        if (_all_releases != null)
          show_releases(_all_releases);
      }
    });
    root.addView(beta);
    root.addView(text(getString(R.string.studio_update_beta_desc)));
    root.addView(button(R.string.studio_check_updates, new View.OnClickListener() {
      public void onClick(View v) { check_updates(); }
    }));
    _update_status = text("");
    root.addView(_update_status);
    _releases = vertical();
    root.addView(_releases);

    root.addView(heading(R.string.studio_crashes));
    root.addView(text(getString(R.string.studio_crashes_desc)));
    _crashes = text("");
    root.addView(_crashes);
    LinearLayout crash_buttons = new LinearLayout(this);
    crash_buttons.addView(button(R.string.studio_crashes_copy, new View.OnClickListener() {
      public void onClick(View v)
      {
        List<String> rs = CrashLog.reports(StudioActivity.this);
        if (rs.isEmpty())
          return;
        android.content.ClipboardManager cm = (android.content.ClipboardManager)
          getSystemService(CLIPBOARD_SERVICE);
        cm.setPrimaryClip(android.content.ClipData.newPlainText("Input Layer crash reports",
              android.text.TextUtils.join("\n\n", rs)));
        toast(getString(R.string.studio_crashes_copied, rs.size()));
      }
    }));
    crash_buttons.addView(button(R.string.studio_crashes_clear, new View.OnClickListener() {
      public void onClick(View v) { CrashLog.clear(StudioActivity.this); refresh_crashes(); }
    }));
    root.addView(crash_buttons);

    build_editor(root);

    root.addView(heading(R.string.studio_tools));
    root.addView(text(getString(R.string.studio_tools_desc)));
    tool_form(root, "selection", R.string.studio_tool_selection,
        new String[]{ "open", "close", "pattern" },
        new int[]{ R.string.studio_tool_open, R.string.studio_tool_close, R.string.studio_tool_pattern });
    tool_form(root, "format", R.string.studio_tool_format,
        new String[]{ "open", "close" },
        new int[]{ R.string.studio_tool_before, R.string.studio_tool_after });
    tool_form(root, "font", R.string.studio_tool_font,
        new String[]{ "lower", "upper", "digits" },
        new int[]{ R.string.studio_tool_lower, R.string.studio_tool_upper, R.string.studio_tool_digits });

    root.addView(heading(R.string.studio_modules));
    root.addView(text(getString(R.string.studio_modules_desc)));
    _modules = vertical();
    root.addView(_modules);
    root.addView(button(R.string.studio_import, new View.OnClickListener() {
      public void onClick(View v) { start_import(); }
    }));

    CheckBox autokeep = new CheckBox(this);
    autokeep.setText(R.string.studio_autokeep);
    autokeep.setChecked(_prefs.getBoolean(ClipKeeper.KEY_AUTOKEEP, true));
    autokeep.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      public void onCheckedChanged(CompoundButton b, boolean checked)
      {
        _prefs.edit().putBoolean(ClipKeeper.KEY_AUTOKEEP, checked).apply();
      }
    });
    languages_section(root);

    root.addView(heading(R.string.studio_memory));
    root.addView(autokeep);
    root.addView(text(getString(R.string.studio_autokeep_desc)));
    root.addView(text(getString(R.string.studio_big_copy)));
    final EditText big = new EditText(this);
    big.setSingleLine(true);
    big.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    big.setText(String.valueOf(_prefs.getInt(ClipKeeper.KEY_BIG_COPY, ClipKeeper.DEFAULT_BIG_COPY)));
    big.addTextChangedListener(new android.text.TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(android.text.Editable s)
      {
        try
        {
          int n = Integer.parseInt(s.toString());
          if (n > 0)
            _prefs.edit().putInt(ClipKeeper.KEY_BIG_COPY, n).apply();
        }
        catch (NumberFormatException _e) {}
      }
    });
    root.addView(big);
    root.addView(text(getString(R.string.studio_big_copy_desc)));
    root.addView(heading(R.string.studio_suggestions));
    CheckBox learn = new CheckBox(this);
    learn.setText(R.string.studio_learn_words);
    learn.setChecked(_prefs.getBoolean(UserWords.KEY_LEARN, true));
    learn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
      public void onCheckedChanged(CompoundButton b, boolean checked)
      {
        _prefs.edit().putBoolean(UserWords.KEY_LEARN, checked).apply();
      }
    });
    root.addView(learn);
    root.addView(text(getString(R.string.studio_learn_words_desc)));
    final Button forget = button(R.string.studio_forget_words, null);
    forget.setOnClickListener(new View.OnClickListener() {
      boolean armed = false;
      public void onClick(View v)
      {
        if (!armed)
        {
          armed = true;
          forget.setText(R.string.studio_tap_again);
          return;
        }
        new UserWords(new File(getFilesDir(), "user_words.json")).forget_all();
        armed = false;
        forget.setText(R.string.studio_forget_words);
        toast(getString(R.string.studio_words_forgotten));
      }
    });
    root.addView(forget);

    root.addView(heading(R.string.studio_memory_categories));
    root.addView(text(getString(R.string.studio_memory_categories_desc)));
    _categories = vertical();
    root.addView(_categories);
    final EditText new_category = new EditText(this);
    new_category.setSingleLine(true);
    new_category.setHint(R.string.studio_category_hint);
    root.addView(new_category);
    root.addView(button(R.string.studio_category_add, new View.OnClickListener() {
      public void onClick(View v)
      {
        if (memory().add_category(new_category.getText().toString()))
        {
          new_category.setText("");
          refresh_categories();
        }
        else
          toast(getString(R.string.studio_category_invalid));
      }
    }));

    ScrollView sv = new ScrollView(this);
    _scroll = sv;
    sv.setFitsSystemWindows(true);
    sv.addView(root);
    return sv;
  }

  /* Modules */

  void refresh_modules()
  {
    _modules.removeAllViews();
    for (String type : Module.TYPES)
    {
      TextView h = text(type);
      h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
      h.setPadding(0, dp(12), 0, dp(4));
      _modules.addView(h);
      List<Module> ms = _store.list(type);
      if (ms.isEmpty())
        _modules.addView(text(getString(R.string.studio_no_modules)));
      for (Module m : ms)
        _modules.addView(module_row(m));
    }
  }

  View module_row(final Module m)
  {
    final boolean active = _store.is_active(m);
    LinearLayout row = new LinearLayout(this);
    row.setOrientation(LinearLayout.HORIZONTAL);
    TextView name = text((active ? "● " : "") + m.name);
    row.addView(name, new LinearLayout.LayoutParams(0,
          ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
    row.addView(button(active ? R.string.studio_stop_using : R.string.studio_use,
          new View.OnClickListener() {
            public void onClick(View v)
            {
              _store.set_active(m.type, active ? null : m.name);
              refresh_modules();
            }
          }));
    row.addView(button(R.string.studio_export, new View.OnClickListener() {
      public void onClick(View v) { start_export(m); }
    }));
    row.addView(button(R.string.studio_delete, new View.OnClickListener() {
      public void onClick(View v)
      {
        _store.delete(m.type, m.name);
        refresh_modules();
      }
    }));
    return row;
  }

  void start_export(Module m)
  {
    _exporting = m;
    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    i.setType("application/json");
    i.putExtra(Intent.EXTRA_TITLE, m.file_name());
    startActivityForResult(i, REQUEST_EXPORT);
  }

  void start_import()
  {
    Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    i.addCategory(Intent.CATEGORY_OPENABLE);
    // Files from chat apps and file managers rarely say they are JSON.
    i.setType("*/*");
    startActivityForResult(i, REQUEST_IMPORT);
  }

  @Override
  protected void onActivityResult(int request, int result, Intent data)
  {
    if (result != RESULT_OK || data == null || data.getData() == null)
      return;
    Uri uri = data.getData();
    try
    {
      if (request == REQUEST_EXPORT && _exporting != null)
      {
        OutputStream out = getContentResolver().openOutputStream(uri);
        try { out.write(_exporting.to_json().getBytes("UTF-8")); }
        finally { out.close(); }
        toast(getString(R.string.studio_exported, _exporting.name));
        _exporting = null;
      }
      else if (request == REQUEST_IMPORT)
      {
        InputStream inp = getContentResolver().openInputStream(uri);
        String s;
        try { s = Utils.read_all_utf8(inp); }
        finally { inp.close(); }
        Module m = Module.parse(s);
        _store.save(m);
        refresh_modules();
        toast(getString(R.string.studio_imported, m.type, m.name));
      }
    }
    catch (Module.InvalidModule e)
    {
      toast(getString(R.string.studio_import_failed, e.getMessage()));
    }
    catch (Exception e)
    {
      Logs.exn("Studio file", e);
      toast(e.toString());
    }
  }

  /* Edit strip tools */

  /** A small form creating a [type] module from a name and [fields]. */
  void tool_form(LinearLayout root, final String type, int title,
      final String[] fields, int[] hints)
  {
    TextView t = text(getString(title));
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
    t.setPadding(0, dp(12), 0, 0);
    root.addView(t);
    final EditText name = new EditText(this);
    name.setSingleLine(true);
    name.setHint(R.string.studio_tool_name);
    root.addView(name);
    final EditText[] inputs = new EditText[fields.length];
    for (int i = 0; i < fields.length; i++)
    {
      inputs[i] = new EditText(this);
      inputs[i].setSingleLine(true);
      inputs[i].setHint(hints[i]);
      root.addView(inputs[i]);
    }
    root.addView(button(R.string.studio_category_add, new View.OnClickListener() {
      public void onClick(View v)
      {
        try
        {
          JSONObject j = new JSONObject();
          for (int i = 0; i < fields.length; i++)
          {
            String s = inputs[i].getText().toString();
            if (!s.isEmpty())
              j.put(fields[i], s);
          }
          Module m = Module.create(type, name.getText().toString().trim(), j);
          boolean ok = type.equals("selection") ? CustomTools.selection(m) != null
            : type.equals("format") ? CustomTools.format(m) != null
            : CustomTools.font(m) != null;
          if (!ok)
          {
            toast(getString(R.string.studio_tool_invalid));
            return;
          }
          _store.save(m);
          name.setText("");
          for (EditText e : inputs)
            e.setText("");
          refresh_modules();
          toast(getString(R.string.studio_imported, m.type, m.name));
        }
        catch (Exception e)
        {
          toast(getString(R.string.studio_tool_invalid) + " " + e.getMessage());
        }
      }
    }));
  }

  /* Spec and Vert editor */

  /** Operation kinds offered when adding, with whether they take a value. */
  static final String[][] SPEC_KINDS = {
    { "step", "" }, { "unit", "" }, { "move:next", "" }, { "move:prev", "" },
    { "dir:fwd", "" }, { "dir:back", "" }, { "mode:excl", "" }, { "mode:incl", "" },
    { "mode:desel", "" }, { "scope", "Word, Sentence, Line, Paragraph, All or a custom one" } };
  static final String[][] VERT_KINDS = {
    { "insert", "text to type" }, { "back", "how many, before the cursor" },
    { "del", "how many, after the cursor" }, { "clear", "" },
    { "wrap", "before|after, e.g. **|**" }, { "unwrap", "" },
    { "look", "bold, italic, mono, script, fraktur, double, small_caps, circled, ..." },
    { "case", "upper, lower, title, toggle, snake, camel, alternate" } };

  void build_editor(LinearLayout root)
  {
    _editor_top = heading(R.string.studio_editor);
    root.addView(_editor_top);
    root.addView(text(getString(R.string.studio_editor_desc)));
    _editor_pick = new Spinner(this);
    root.addView(_editor_pick);
    _editor_ops = vertical();
    root.addView(_editor_ops);
    _editor_add = vertical();
    root.addView(_editor_add);
    LinearLayout row = new LinearLayout(this);
    row.addView(button(R.string.studio_new_spec, new View.OnClickListener() {
      public void onClick(View v) { new_macro("spec"); }
    }));
    row.addView(button(R.string.studio_new_vert, new View.OnClickListener() {
      public void onClick(View v) { new_macro("vert"); }
    }));
    row.addView(button(R.string.studio_delete, new View.OnClickListener() {
      public void onClick(View v)
      {
        Module m = editing();
        if (m == null)
          return;
        _store.delete(m.type, m.name);
        _editing = null;
        refresh_editor();
        refresh_modules();
      }
    }));
    root.addView(row);
  }

  List<Module> editable()
  {
    List<Module> l = new ArrayList<Module>(_store.list("spec"));
    l.addAll(_store.list("vert"));
    return l;
  }

  Module editing()
  {
    for (Module m : editable())
      if (_editing != null && (m.type + "/" + m.name).equals(_editing))
        return m;
    return null;
  }

  void refresh_editor()
  {
    final List<Module> ms = editable();
    if (editing() == null && !ms.isEmpty())
      _editing = ms.get(0).type + "/" + ms.get(0).name;
    List<String> labels = new ArrayList<String>();
    int sel = 0;
    for (int i = 0; i < ms.size(); i++)
    {
      Module m = ms.get(i);
      labels.add((m.type.equals("spec") ? "Spec · " : "Vert · ") + m.name);
      if ((m.type + "/" + m.name).equals(_editing))
        sel = i;
    }
    ArrayAdapter<String> ad = new ArrayAdapter<String>(this,
        android.R.layout.simple_spinner_item, labels);
    ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    _editor_pick.setOnItemSelectedListener(null);
    _editor_pick.setAdapter(ad);
    _editor_pick.setSelection(sel, false);
    _editor_pick.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> parent, View v, int pos, long id)
      {
        String key = ms.get(pos).type + "/" + ms.get(pos).name;
        if (!key.equals(_editing))
        {
          _editing = key;
          refresh_editor();
        }
      }
      public void onNothingSelected(AdapterView<?> parent) {}
    });
    _editor_ops.removeAllViews();
    _editor_add.removeAllViews();
    Module m = editing();
    if (m == null)
    {
      _editor_ops.addView(text(getString(R.string.studio_editor_empty)));
      return;
    }
    final List<String> ops = ops_of(m);
    for (int i = 0; i < ops.size(); i++)
    {
      final int idx = i;
      LinearLayout r = new LinearLayout(this);
      r.addView(text((i + 1) + ". " + Ops.describe(ops.get(i))),
          new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      r.addView(small_button("↑", new View.OnClickListener() {
        public void onClick(View v) { if (idx > 0) { swap(ops, idx, idx - 1); save_ops(ops); } }
      }));
      r.addView(small_button("↓", new View.OnClickListener() {
        public void onClick(View v) { if (idx + 1 < ops.size()) { swap(ops, idx, idx + 1); save_ops(ops); } }
      }));
      r.addView(small_button("✕", new View.OnClickListener() {
        public void onClick(View v) { ops.remove(idx); save_ops(ops); }
      }));
      _editor_ops.addView(r);
    }
    if (ops.isEmpty())
      _editor_ops.addView(text(getString(R.string.studio_editor_no_ops)));
    // Adding an operation: its kind and, for some, a value.
    final String[][] kinds = m.type.equals("spec") ? SPEC_KINDS : VERT_KINDS;
    List<String> kl = new ArrayList<String>();
    for (String[] k : kinds)
      kl.add(Ops.describe(k[0].contains(":") ? k[0] : k[0] + ":…"));
    final Spinner kind = new Spinner(this);
    ArrayAdapter<String> ka = new ArrayAdapter<String>(this,
        android.R.layout.simple_spinner_item, kl);
    ka.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    kind.setAdapter(ka);
    final EditText value = new EditText(this);
    value.setSingleLine(true);
    value.setHint(kinds[0][1]);
    kind.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> parent, View v, int pos, long id)
      {
        value.setHint(kinds[pos][1]);
        value.setEnabled(!kinds[pos][1].isEmpty());
      }
      public void onNothingSelected(AdapterView<?> parent) {}
    });
    _editor_add.addView(kind);
    _editor_add.addView(value);
    _editor_add.addView(button(R.string.studio_add_op, new View.OnClickListener() {
      public void onClick(View v)
      {
        String[] k = kinds[kind.getSelectedItemPosition()];
        String op = k[0];
        if (!k[1].isEmpty())
        {
          String val = value.getText().toString();
          if (val.isEmpty())
          {
            toast(getString(R.string.studio_add_op_value));
            return;
          }
          op = k[0] + ":" + val;
        }
        ops.add(op);
        value.setText("");
        save_ops(ops);
      }
    }));
  }

  static void swap(List<String> l, int a, int b)
  {
    String t = l.get(a);
    l.set(a, l.get(b));
    l.set(b, t);
  }

  static List<String> ops_of(Module m)
  {
    List<String> l = new ArrayList<String>();
    JSONArray a = m.json.optJSONArray("ops");
    for (int i = 0; a != null && i < a.length(); i++)
      l.add(a.optString(i, ""));
    return l;
  }

  void save_ops(List<String> ops)
  {
    Module m = editing();
    if (m == null)
      return;
    try
    {
      JSONObject j = new JSONObject(m.json.toString());
      j.put("ops", new JSONArray(ops));
      j.put("summary", Ops.summary(ops));
      _store.save(new Module(j));
    }
    catch (Exception e)
    {
      toast(e.getMessage());
    }
    refresh_editor();
    refresh_modules();
  }

  void new_macro(String type)
  {
    try
    {
      String name = (type.equals("spec") ? "Spec " : "Vert ") + (_store.list(type).size() + 1);
      JSONObject j = new JSONObject();
      j.put("ops", new JSONArray());
      j.put("summary", "");
      _store.save(Module.create(type, name, j));
      _editing = type + "/" + name;
      refresh_editor();
      refresh_modules();
    }
    catch (Exception e)
    {
      toast(e.getMessage());
    }
  }

  Button small_button(String label, View.OnClickListener l)
  {
    Button b = button(label, l);
    b.setMinWidth(0);
    b.setMinimumWidth(0);
    b.setPadding(dp(8), 0, dp(8), 0);
    return b;
  }

  /** Spec ↑ / Vert ↑ on the keyboard: the editor on that one. */
  void open_editor_from(Intent i)
  {
    String type = (i == null) ? null : i.getStringExtra(EXTRA_EDIT_TYPE);
    if (type == null)
    {
      refresh_editor();
      return;
    }
    String name = i.getStringExtra(EXTRA_EDIT_NAME);
    if (name == null)
      new_macro(type);
    else
    {
      _editing = type + "/" + name;
      refresh_editor();
    }
    _scroll.post(new Runnable() {
      public void run() { _scroll.scrollTo(0, _editor_top.getTop()); }
    });
  }

  /* Languages */

  /** Layouts offered in Studio, in the order the quicksbar's layout key
      cycles through them: { layout name ("system" for the phone's
      language), label }. */
  static final String[][] LANGUAGE_LAYOUTS = {
    { "system", "Phone's language (automatic)" },
    { "il_azerty", "Français · Input Layer AZERTY" },
    { "il_qwertz", "Deutsch · Input Layer QWERTZ" },
    { "il_qwerty", "English · Input Layer QWERTY" },
    { "cyrl_jcuken_ru", "Русский · ЙЦУКЕН" },
    { "grek_qwerty", "Ελληνικά · Greek" },
  };

  void languages_section(LinearLayout root)
  {
    root.addView(heading(R.string.studio_languages));
    root.addView(text(getString(R.string.studio_languages_desc)));
    final List<CheckBox> boxes = new ArrayList<CheckBox>();
    List<LayoutsPreference.Layout> saved = LayoutsPreference.load_layouts(_prefs);
    for (String[] l : LANGUAGE_LAYOUTS)
    {
      CheckBox b = new CheckBox(this);
      b.setText(l[1]);
      b.setChecked(index_of_layout(saved, l[0]) >= 0);
      boxes.add(b);
      root.addView(b);
    }
    CompoundButton.OnCheckedChangeListener save = new CompoundButton.OnCheckedChangeListener() {
      public void onCheckedChanged(CompoundButton b, boolean checked)
      {
        save_languages(boxes);
      }
    };
    for (CheckBox b : boxes)
      b.setOnCheckedChangeListener(save);
  }

  /** The ticked layouts in the order of [LANGUAGE_LAYOUTS], then the
      other layouts already in the list (added in Legacy settings). */
  void save_languages(List<CheckBox> boxes)
  {
    List<LayoutsPreference.Layout> saved = LayoutsPreference.load_layouts(_prefs);
    List<LayoutsPreference.Layout> l = new ArrayList<LayoutsPreference.Layout>();
    for (int i = 0; i < LANGUAGE_LAYOUTS.length; i++)
      if (boxes.get(i).isChecked())
        l.add(LANGUAGE_LAYOUTS[i][0].equals("system") ? new LayoutsPreference.SystemLayout()
            : new LayoutsPreference.NamedLayout(LANGUAGE_LAYOUTS[i][0]));
    for (LayoutsPreference.Layout s : saved)
      if (!is_offered(s))
        l.add(s);
    if (l.isEmpty())
      l.add(new LayoutsPreference.SystemLayout());
    SharedPreferences.Editor e = _prefs.edit();
    LayoutsPreference.save_to_preferences(e, l);
    e.apply();
  }

  static boolean is_offered(LayoutsPreference.Layout s)
  {
    for (String[] o : LANGUAGE_LAYOUTS)
      if (same_layout(s, o[0]))
        return true;
    return false;
  }

  static int index_of_layout(List<LayoutsPreference.Layout> l, String name)
  {
    for (int i = 0; i < l.size(); i++)
      if (same_layout(l.get(i), name))
        return i;
    return -1;
  }

  static boolean same_layout(LayoutsPreference.Layout l, String name)
  {
    if (name.equals("system"))
      return l instanceof LayoutsPreference.SystemLayout;
    return (l instanceof LayoutsPreference.NamedLayout)
      && ((LayoutsPreference.NamedLayout)l).name.equals(name);
  }

  /* Crash reports */

  TextView _crashes;

  /** How many reports, and the newest one's first lines. */
  void refresh_crashes()
  {
    List<String> rs = CrashLog.reports(this);
    if (rs.isEmpty())
    {
      _crashes.setText(R.string.studio_crashes_none);
      return;
    }
    String[] lines = rs.get(0).split("\n");
    StringBuilder b = new StringBuilder(getString(R.string.studio_crashes_count, rs.size()));
    for (int i = 0; i < Math.min(6, lines.length); i++)
      b.append('\n').append(lines[i]);
    _crashes.setText(b.toString());
  }

  /* Memory categories */

  MemoryStore memory()
  {
    return new MemoryStore(new File(getFilesDir(), "memory.json"));
  }

  void refresh_categories()
  {
    _categories.removeAllViews();
    final MemoryStore store;
    // Whatever goes wrong with Memory, the rest of Studio (the updates
    // above all) must stay usable.
    try { store = memory(); }
    catch (Throwable e)
    {
      CrashLog.handled(this, "Studio: Memory categories", e);
      _categories.addView(text(getString(R.string.studio_memory_error, String.valueOf(e))));
      return;
    }
    for (final String c : store.custom_categories())
    {
      LinearLayout row = new LinearLayout(this);
      row.setOrientation(LinearLayout.HORIZONTAL);
      row.addView(text(c), new LinearLayout.LayoutParams(0,
            ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
      row.addView(button(R.string.studio_delete, new View.OnClickListener() {
        public void onClick(View v)
        {
          store.remove_category(c);
          refresh_categories();
        }
      }));
      _categories.addView(row);
    }
  }

  /* Updates */

  void check_updates()
  {
    final String repo = _repo.getText().toString().trim();
    if (!Updater.is_valid_repo(repo))
    {
      _update_status.setText(R.string.studio_repo_invalid);
      return;
    }
    _prefs.edit().putString(KEY_UPDATE_REPO, repo).apply();
    _update_status.setText(R.string.studio_checking);
    _releases.removeAllViews();
    new Thread() {
      public void run()
      {
        try
        {
          final List<Updater.Release> rs = Updater.fetch_releases(repo);
          runOnUiThread(new Runnable() {
            public void run() { show_releases(rs); }
          });
        }
        catch (final Exception e)
        {
          Logs.exn("Studio update check", e);
          runOnUiThread(new Runnable() {
            public void run() { _update_status.setText(e.getMessage()); }
          });
        }
      }
    }.start();
  }

  /** Releases from the last check, [null] before one. */
  List<Updater.Release> _all_releases = null;

  void show_releases(List<Updater.Release> all)
  {
    _all_releases = all;
    _releases.removeAllViews();
    // The installed build is a beta: once it has proven itself, mark it
    // stable on GitHub.
    for (final Updater.Release r : all)
      if (r.build == _installed_build && r.beta)
        _releases.addView(button(getString(R.string.studio_mark_stable, r.build),
              new View.OnClickListener() {
                public void onClick(View v)
                {
                  toast(getString(R.string.studio_mark_stable_howto));
                  startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                          "https://github.com/" + _prefs.getString(KEY_UPDATE_REPO, "")
                          + "/releases/edit/" + r.tag)));
                }
              }));
    List<Updater.Release> rs = Updater.channel(all, _prefs.getBoolean(KEY_UPDATE_BETA, false));
    if (all.isEmpty())
    {
      _update_status.setText(R.string.studio_no_releases);
      return;
    }
    final List<Updater.Release> newer = new ArrayList<Updater.Release>();
    List<Updater.Release> older = new ArrayList<Updater.Release>();
    for (Updater.Release r : rs)
      if (r.build > _installed_build)
        newer.add(r);
    // Restoring is a deliberate pick: every past build, betas included
    // (labelled), whatever the switch says.
    for (Updater.Release r : all)
      if (r.build < _installed_build)
        older.add(r);
    // Never say "newest" while a newer beta is hidden.
    Updater.Release hidden = _prefs.getBoolean(KEY_UPDATE_BETA, false) ? null
      : Updater.newer_beta(all, _installed_build);
    if (!newer.isEmpty())
      _update_status.setText(getString(R.string.studio_update_available, newer.get(0).build));
    else if (hidden != null)
      _update_status.setText(getString(R.string.studio_beta_hidden, hidden.build));
    else
      _update_status.setText(R.string.studio_up_to_date);
    if (!newer.isEmpty())
      show_newer(newer);
    if (!older.isEmpty())
    {
      _releases.addView(heading(R.string.studio_past_builds));
      _releases.addView(text(getString(R.string.studio_past_builds_desc)));
      show_past(older);
    }
  }

  /** A dropdown of the builds not installed yet, newest first and selected. */
  void show_newer(final List<Updater.Release> newer)
  {
    final Spinner spinner = spinner(newer);
    final TextView notes = text(newer.get(0).notes);
    final Button install = button(install_label(newer.get(0)), null);
    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> parent, View v, int pos, long id)
      {
        notes.setText(newer.get(pos).notes);
        install.setText(install_label(newer.get(pos)));
      }
      public void onNothingSelected(AdapterView<?> parent) {}
    });
    install.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v)
      {
        start_install(newer.get(spinner.getSelectedItemPosition()));
      }
    });
    _releases.addView(spinner);
    _releases.addView(notes);
    _releases.addView(install);
  }

  /** A dropdown of older builds, newest first. Android doesn't install older
      versions over newer ones, so restoring republishes the selected one as
      a new build on GitHub. */
  void show_past(final List<Updater.Release> older)
  {
    final Spinner spinner = spinner(older);
    final TextView notes = text(older.get(0).notes);
    final Button restore = button(getString(R.string.studio_restore, older.get(0).build), null);
    spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
      public void onItemSelected(AdapterView<?> parent, View v, int pos, long id)
      {
        notes.setText(older.get(pos).notes);
        restore.setText(getString(R.string.studio_restore, older.get(pos).build));
      }
      public void onNothingSelected(AdapterView<?> parent) {}
    });
    restore.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v)
      {
        Updater.Release r = older.get(spinner.getSelectedItemPosition());
        toast(getString(R.string.studio_restore_howto, r.build));
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://github.com/" + _prefs.getString(KEY_UPDATE_REPO, "")
                + "/actions/workflows/studio-restore.yml")));
      }
    });
    _releases.addView(spinner);
    _releases.addView(notes);
    _releases.addView(restore);
  }

  Spinner spinner(List<Updater.Release> rs)
  {
    List<String> labels = new ArrayList<String>();
    for (Updater.Release r : rs)
      labels.add(r.beta ? r.title + " · beta" : r.title);
    Spinner sp = new Spinner(this);
    ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
        android.R.layout.simple_spinner_item, labels);
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
    sp.setAdapter(adapter);
    return sp;
  }

  String install_label(Updater.Release r)
  {
    return getString(R.string.studio_install, r.apk_size / (1024 * 1024));
  }

  void start_install(final Updater.Release r)
  {
    if (VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls())
    {
      toast(getString(R.string.studio_allow_installs));
      startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:" + getPackageName())));
      return;
    }
    _update_status.setText(getString(R.string.studio_downloading, r.build));
    new Thread() {
      public void run()
      {
        try
        {
          File apk = Updater.download(StudioActivity.this, r);
          Updater.install(StudioActivity.this, apk, install_status_intent());
        }
        catch (final Exception e)
        {
          Logs.exn("Studio update install", e);
          runOnUiThread(new Runnable() {
            public void run() { _update_status.setText(e.getMessage()); }
          });
        }
      }
    }.start();
  }

  PendingIntent install_status_intent()
  {
    Intent i = new Intent(this, StudioActivity.class);
    i.setAction(ACTION_INSTALL_STATUS);
    i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    // The installer adds its status to the intent.
    if (VERSION.SDK_INT >= 31)
      flags |= PendingIntent.FLAG_MUTABLE;
    return PendingIntent.getActivity(this, 0, i, flags);
  }

  void handle_install_status(Intent intent)
  {
    if (intent == null || !ACTION_INSTALL_STATUS.equals(intent.getAction()))
      return;
    int status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
        PackageInstaller.STATUS_FAILURE);
    if (status == PackageInstaller.STATUS_PENDING_USER_ACTION)
    {
      Intent confirm = (Intent)intent.getParcelableExtra(Intent.EXTRA_INTENT);
      if (confirm != null)
        startActivity(confirm);
    }
    else if (status != PackageInstaller.STATUS_SUCCESS)
    {
      String msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
      toast(getString(R.string.studio_install_failed, msg == null ? "" : msg));
    }
  }

  @Override
  protected void onStop()
  {
    DirectBootAwarePreferences.copy_preferences_to_protected_storage(this, _prefs);
    super.onStop();
  }

  /* Views */

  String getVersionName()
  {
    try
    {
      return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
    }
    catch (Exception _e)
    {
      return "?";
    }
  }

  LinearLayout vertical()
  {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  TextView heading(int res)
  {
    TextView t = text(getString(res));
    t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
    t.setPadding(0, dp(20), 0, dp(8));
    return t;
  }

  TextView text(String s)
  {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextIsSelectable(true);
    return t;
  }

  Button button(int res, View.OnClickListener l)
  {
    return button(getString(res), l);
  }

  Button button(String label, View.OnClickListener l)
  {
    Button b = new Button(this);
    b.setText(label);
    b.setOnClickListener(l);
    return b;
  }

  void toast(String msg)
  {
    Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
  }

  int dp(int v)
  {
    return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
        getResources().getDisplayMetrics());
  }
}
