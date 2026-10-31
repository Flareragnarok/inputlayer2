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
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import juloo.keyboard2.DirectBootAwarePreferences;
import juloo.keyboard2.Logs;
import juloo.keyboard2.R;
import juloo.keyboard2.Utils;

/** The Studio screen: installed build and updates, and the saved modules
    (use, export, import, delete). */
public class StudioActivity extends Activity
{
  static final String KEY_UPDATE_REPO = "studio_update_repo";
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
  /** Module waiting for the export file to be picked. */
  Module _exporting;

  @Override
  public void onCreate(Bundle savedInstanceState)
  {
    super.onCreate(savedInstanceState);
    _prefs = DirectBootAwarePreferences.get_shared_preferences(this);
    _store = new ModuleStore(_prefs);
    _installed_build = getResources().getInteger(R.integer.studio_build);
    setTitle(R.string.studio_title);
    setContentView(build_view());
    refresh_modules();
    handle_install_status(getIntent());
  }

  @Override
  protected void onNewIntent(Intent intent)
  {
    super.onNewIntent(intent);
    handle_install_status(intent);
  }

  View build_view()
  {
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    int pad = dp(16);
    root.setPadding(pad, pad, pad, pad);

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
    root.addView(button(R.string.studio_check_updates, new View.OnClickListener() {
      public void onClick(View v) { check_updates(); }
    }));
    _update_status = text("");
    root.addView(_update_status);
    _releases = vertical();
    root.addView(_releases);

    root.addView(heading(R.string.studio_modules));
    root.addView(text(getString(R.string.studio_modules_desc)));
    _modules = vertical();
    root.addView(_modules);
    root.addView(button(R.string.studio_import, new View.OnClickListener() {
      public void onClick(View v) { start_import(); }
    }));

    ScrollView sv = new ScrollView(this);
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

  void show_releases(List<Updater.Release> rs)
  {
    _releases.removeAllViews();
    if (rs.isEmpty())
    {
      _update_status.setText(R.string.studio_no_releases);
      return;
    }
    Updater.Release newest = rs.get(0);
    _update_status.setText(newest.build > _installed_build ?
        getString(R.string.studio_update_available, newest.build) :
        getString(R.string.studio_up_to_date));
    for (final Updater.Release r : rs)
    {
      TextView title = text(r.title
          + (r.build == _installed_build ? "  " + getString(R.string.studio_installed_mark) : ""));
      title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
      title.setPadding(0, dp(12), 0, 0);
      _releases.addView(title);
      if (!r.notes.isEmpty())
        _releases.addView(text(r.notes));
      if (r.build > _installed_build)
        _releases.addView(button(getString(R.string.studio_install,
                r.apk_size / (1024 * 1024)), new View.OnClickListener() {
          public void onClick(View v) { start_install(r); }
        }));
    }
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
