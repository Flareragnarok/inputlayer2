package juloo.keyboard2.studio;

import android.content.Context;
import android.os.Build.VERSION;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import juloo.keyboard2.R;
import juloo.keyboard2.Utils;

/** Crash reports kept on the phone, so they can be copied from Studio and
    sent: what crashed, where, in which build. Crashes stopping the app, and
    errors the app survived (a bug in Memory shown as a message). Nothing is
    sent anywhere; the report stays on the phone until copied or cleared.
    Kept in device-protected storage, so a crash before the phone is unlocked
    (the keyboard runs then) is kept too. Only the error and where it
    happened are written, never text from the field. */
public final class CrashLog
{
  static final String DIR = "crashes";
  /** Reports kept, the oldest deleted beyond. */
  static final int MAX_REPORTS = 10;

  private CrashLog() {}

  static boolean _installed = false;

  /** Keep a report of crashes in this process; Android then stops the app
      as before. Called by the keyboard and Studio, once per process. */
  public static synchronized void install(Context ctx)
  {
    if (_installed)
      return;
    _installed = true;
    final Context app = ctx.getApplicationContext();
    final Thread.UncaughtExceptionHandler previous =
      Thread.getDefaultUncaughtExceptionHandler();
    Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
      public void uncaughtException(Thread t, Throwable e)
      {
        try { write(app, "crash", "thread " + t.getName(), e); }
        catch (Throwable _e) {}
        if (previous != null)
          previous.uncaughtException(t, e);
      }
    });
  }

  /** An error the app survived, at [where]. */
  public static void handled(Context ctx, String where, Throwable e)
  {
    try { write(ctx, "handled", where, e); }
    catch (Throwable _e) {}
  }

  static File dir(Context ctx)
  {
    Context c = (VERSION.SDK_INT >= 24) ? ctx.createDeviceProtectedStorageContext() : ctx;
    File d = new File(c.getFilesDir(), DIR);
    d.mkdirs();
    return d;
  }

  static void write(Context ctx, String kind, String where, Throwable e) throws Exception
  {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    long now = System.currentTimeMillis();
    pw.println(kind + " · " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(new Date(now)));
    pw.println("build " + build(ctx) + " · Android " + VERSION.RELEASE + " (API " + VERSION.SDK_INT + ")");
    pw.println(where);
    e.printStackTrace(pw);
    pw.flush();
    File d = dir(ctx);
    OutputStream out = new FileOutputStream(new File(d, now + "-" + kind + ".txt"));
    try { out.write(sw.toString().getBytes("UTF-8")); }
    finally { out.close(); }
    trim(d);
  }

  static String build(Context ctx)
  {
    try { return String.valueOf(ctx.getResources().getInteger(R.integer.studio_build)); }
    catch (Throwable _e) { return "?"; }
  }

  /** Newest first. */
  static List<File> files(File d)
  {
    File[] fs = d.listFiles();
    List<File> l = new ArrayList<File>();
    if (fs == null)
      return l;
    Arrays.sort(fs);
    for (int i = fs.length - 1; i >= 0; i--)
      if (fs[i].getName().endsWith(".txt"))
        l.add(fs[i]);
    return l;
  }

  static void trim(File d)
  {
    List<File> l = files(d);
    for (int i = MAX_REPORTS; i < l.size(); i++)
      l.get(i).delete();
  }

  /** The reports, newest first. */
  public static List<String> reports(Context ctx)
  {
    List<String> l = new ArrayList<String>();
    for (File f : files(dir(ctx)))
    {
      try
      {
        InputStream inp = new FileInputStream(f);
        try { l.add(Utils.read_all_utf8(inp)); }
        finally { inp.close(); }
      }
      catch (Exception _e) {}
    }
    return l;
  }

  public static void clear(Context ctx)
  {
    for (File f : files(dir(ctx)))
      f.delete();
  }
}
