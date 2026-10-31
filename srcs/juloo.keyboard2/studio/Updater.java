package juloo.keyboard2.studio;

import android.app.PendingIntent;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import juloo.keyboard2.Utils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Self-update from GitHub releases, the way Obtainium does it: read the
    releases API, keep the releases that carry an APK, then hand the APK to the
    system installer. The system asks the user to confirm every install and
    refuses an APK signed with a different key. Only runs when the user asks,
    from the Studio screen; nothing here runs in the keyboard. */
public final class Updater
{
  /** Studio release tags are "build-N", N being the CI run number, which is
      also the installed build number (see build.gradle.kts). */
  static final String TAG_PREFIX = "build-";

  public static final class Release
  {
    public final int build;
    public final String tag;
    public final String title;
    public final String notes;
    public final String apk_url;
    public final long apk_size;

    Release(int b, String t, String ti, String n, String url, long size)
    {
      build = b; tag = t; title = ti; notes = n; apk_url = url; apk_size = size;
    }
  }

  /** [-1] if [tag] is not a Studio build tag. */
  static int build_of_tag(String tag)
  {
    if (tag == null || !tag.startsWith(TAG_PREFIX))
      return -1;
    try
    {
      return Integer.parseInt(tag.substring(TAG_PREFIX.length()));
    }
    catch (NumberFormatException _e)
    {
      return -1;
    }
  }

  /** "owner/repo" on GitHub. */
  public static boolean is_valid_repo(String repo)
  {
    return repo != null && repo.matches("[A-Za-z0-9-]+/[A-Za-z0-9._-]+");
  }

  /** Parse a GitHub "list releases" response. Drafts, prereleases, releases
      without a Studio tag or without an APK are skipped. Newest build first. */
  static List<Release> parse_releases(String json) throws JSONException
  {
    JSONArray rs = new JSONArray(json);
    List<Release> out = new ArrayList<Release>();
    for (int i = 0; i < rs.length(); i++)
    {
      JSONObject r = rs.getJSONObject(i);
      if (r.optBoolean("draft") || r.optBoolean("prerelease"))
        continue;
      String tag = r.optString("tag_name");
      int build = build_of_tag(tag);
      if (build < 0)
        continue;
      JSONArray assets = r.optJSONArray("assets");
      if (assets == null)
        continue;
      for (int j = 0; j < assets.length(); j++)
      {
        JSONObject a = assets.getJSONObject(j);
        if (a.optString("name").endsWith(".apk"))
        {
          String title = r.optString("name");
          out.add(new Release(build, tag, title.isEmpty() ? tag : title,
                r.optString("body"), a.optString("browser_download_url"),
                a.optLong("size")));
          break;
        }
      }
    }
    Collections.sort(out, new Comparator<Release>() {
      public int compare(Release a, Release b) { return b.build - a.build; }
    });
    return out;
  }

  /** Network access, don't call on the main thread. */
  public static List<Release> fetch_releases(String repo) throws Exception
  {
    HttpURLConnection con = open(
        "https://api.github.com/repos/" + repo + "/releases?per_page=20");
    con.setRequestProperty("Accept", "application/vnd.github+json");
    try
    {
      int code = con.getResponseCode();
      if (code == 404)
        throw new IOException("Repository " + repo + " not found or not public");
      if (code != 200)
        throw new IOException("GitHub answered " + code);
      return parse_releases(Utils.read_all_utf8(con.getInputStream()));
    }
    finally
    {
      con.disconnect();
    }
  }

  /** Download the APK of [r] into the cache and check that it is an update of
      this app, not some other package. Network access. */
  public static File download(Context ctx, Release r) throws Exception
  {
    File apk = new File(ctx.getCacheDir(), "studio-update.apk");
    HttpURLConnection con = open(r.apk_url);
    try
    {
      if (con.getResponseCode() != 200)
        throw new IOException("Download failed: " + con.getResponseCode());
      InputStream inp = con.getInputStream();
      OutputStream out = new FileOutputStream(apk);
      try { copy(inp, out); }
      finally { out.close(); inp.close(); }
    }
    finally
    {
      con.disconnect();
    }
    PackageInfo info = ctx.getPackageManager()
      .getPackageArchiveInfo(apk.getPath(), 0);
    if (info == null)
      throw new IOException("Downloaded file is not an APK");
    if (!ctx.getPackageName().equals(info.packageName))
      throw new IOException("APK is for " + info.packageName + ", not "
          + ctx.getPackageName());
    return apk;
  }

  /** Start installing [apk]. The installer reports to [status]; on
      [PackageInstaller.STATUS_PENDING_USER_ACTION] the caller must start the
      confirmation activity it carries. */
  public static void install(Context ctx, File apk, PendingIntent status)
    throws IOException
  {
    PackageInstaller pi = ctx.getPackageManager().getPackageInstaller();
    PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
        PackageInstaller.SessionParams.MODE_FULL_INSTALL);
    params.setAppPackageName(ctx.getPackageName());
    int id = pi.createSession(params);
    PackageInstaller.Session session = pi.openSession(id);
    try
    {
      OutputStream out = session.openWrite("base.apk", 0, apk.length());
      InputStream inp = new FileInputStream(apk);
      try
      {
        copy(inp, out);
        session.fsync(out);
      }
      finally { inp.close(); out.close(); }
      session.commit(status.getIntentSender());
    }
    catch (IOException e)
    {
      session.abandon();
      throw e;
    }
    finally
    {
      session.close();
    }
  }

  static HttpURLConnection open(String url) throws IOException
  {
    HttpURLConnection con = (HttpURLConnection)new URL(url).openConnection();
    con.setRequestProperty("User-Agent", "Unexpected-Keyboard-Studio");
    con.setConnectTimeout(15000);
    con.setReadTimeout(30000);
    return con;
  }

  static void copy(InputStream inp, OutputStream out) throws IOException
  {
    byte[] buf = new byte[64 * 1024];
    int n;
    while ((n = inp.read(buf)) > 0)
      out.write(buf, 0, n);
  }
}
