package juloo.keyboard2.studio;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import juloo.keyboard2.KeyValue;

/** quickopen : app — picking the app with a search (owner, 2026-11-06:
    "quickequip : quickopen / quickapp also needs a search"), then its
    optional extras. Used by the key editor (quickequip) and the layout
    editor. */
public final class AppPick
{
  public interface Done { void picked(String key_name); }

  private AppPick() {}

  /** The launchable apps, {label, package}, by label. */
  static List<String[]> apps(Activity act)
  {
    PackageManager pm = act.getPackageManager();
    Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
    List<String[]> apps = new ArrayList<String[]>();
    for (ResolveInfo ri : pm.queryIntentActivities(main, 0))
    {
      String pkg = ri.activityInfo.packageName;
      boolean seen = false;
      for (String[] a : apps) seen |= a[1].equals(pkg);
      if (!seen && !pkg.equals(act.getPackageName()))
        apps.add(new String[]{ String.valueOf(ri.loadLabel(pm)), pkg });
    }
    Collections.sort(apps, (a, b) -> a[0].compareToIgnoreCase(b[0]));
    return apps;
  }

  /** Apps whose label or package contains [q], ignoring case. */
  static List<String[]> filter(List<String[]> all, String q)
  {
    String f = q.trim().toLowerCase(Locale.ROOT);
    if (f.isEmpty())
      return new ArrayList<String[]>(all);
    List<String[]> out = new ArrayList<String[]>();
    for (String[] a : all)
      if (a[0].toLowerCase(Locale.ROOT).contains(f) || a[1].toLowerCase(Locale.ROOT).contains(f))
        out.add(a);
    return out;
  }

  /** Pick an app (search box on top), then its extras; [done] gets the key
      name (`app:<package>[?extras]`), [cancel] runs on cancel. */
  public static void show(final Activity act, String title, final Done done, final Runnable cancel)
  {
    final List<String[]> all = apps(act);
    final List<String[]> shown = new ArrayList<String[]>(all);
    final ArrayAdapter<String> adapter = new ArrayAdapter<String>(act,
        android.R.layout.simple_list_item_1, labels(shown));
    LinearLayout box = new LinearLayout(act);
    box.setOrientation(LinearLayout.VERTICAL);
    final EditText q = new EditText(act);
    q.setSingleLine(true);
    q.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
    q.setHint("Search apps (name or package)");
    final ListView list = new ListView(act);
    list.setAdapter(adapter);
    box.addView(q);
    box.addView(list, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
    final AlertDialog dlg = new AlertDialog.Builder(act).setTitle(title).setView(box)
      .setNegativeButton(android.R.string.cancel, (di, i) -> cancel.run())
      .create();
    q.addTextChangedListener(new TextWatcher() {
      public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
      public void onTextChanged(CharSequence s, int a, int b, int c) {}
      public void afterTextChanged(Editable s)
      {
        shown.clear();
        shown.addAll(filter(all, s.toString()));
        adapter.clear();
        adapter.addAll(labels(shown));
      }
    });
    list.setOnItemClickListener((p, v, i, id) -> {
      dlg.dismiss();
      extras(act, shown.get(i)[1], done, cancel);
    });
    dlg.show();
  }

  static List<String> labels(List<String[]> apps)
  {
    List<String> l = new ArrayList<String>(apps.size());
    for (String[] a : apps) l.add(a[0] + "   " + a[1]);
    return l;
  }

  /** Optional string extras for apps that take them (the Remote Client:
      tab=roadmap). */
  static void extras(final Activity act, final String pkg, final Done done, final Runnable cancel)
  {
    final EditText t = new EditText(act);
    t.setHint("optional, e.g. tab=roadmap");
    t.setSingleLine(true);
    new AlertDialog.Builder(act).setTitle("quickopen : app : " + pkg).setView(t)
      .setPositiveButton(android.R.string.ok, (di, i) -> {
        String x = t.getText().toString().trim();
        String name = KeyValue.APP_PREFIX + pkg + (x.isEmpty() ? "" : "?" + x);
        if (KeyValue.getKeyByName(name).getKind() != KeyValue.Kind.App)
        {
          Toast.makeText(act, "Extras are name=value, joined with &", Toast.LENGTH_LONG).show();
          extras(act, pkg, done, cancel);
        }
        else
          done.picked(name);
      })
      .setNegativeButton(android.R.string.cancel, (di, i) -> cancel.run())
      .show();
  }
}
