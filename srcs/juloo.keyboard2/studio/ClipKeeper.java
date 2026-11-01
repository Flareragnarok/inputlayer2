package juloo.keyboard2.studio;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build.VERSION;
import android.os.PersistableBundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import java.io.File;
import juloo.keyboard2.Logs;

/** Memory keeps what is copied: every new clipboard text is recorded in
    Memory's history, in the record of the current phone restart and under
    its content type (Links, Snippets, Symbols, Text), unless it comes from a
    password field or the app marked it as sensitive. Can be turned off in
    Studio. The clipboard history itself stays as upstream has it. */
public final class ClipKeeper implements ClipboardManager.OnPrimaryClipChangedListener
{
  public static final String KEY_AUTOKEEP = "studio_memory_autokeep";
  /** Bigger texts are not kept. */
  static final int MAX_TEXT_LENGTH = 10000;
  /** Copies this long or longer wait for "Keep?" in Memory instead of
      being kept. */
  public static final String KEY_BIG_COPY = "studio_memory_big_copy";
  public static final int DEFAULT_BIG_COPY = 1000;
  /** [ClipDescription.EXTRA_IS_SENSITIVE], API 33. */
  static final String EXTRA_IS_SENSITIVE = "android.content.extra.IS_SENSITIVE";

  final Context _ctx;
  final SharedPreferences _prefs;
  /** The field the keyboard is typing in is a password field. */
  boolean _in_password_field = false;

  ClipKeeper(Context ctx, SharedPreferences prefs)
  {
    _ctx = ctx;
    _prefs = prefs;
  }

  public static ClipKeeper start(Context ctx, SharedPreferences prefs)
  {
    ClipKeeper k = new ClipKeeper(ctx, prefs);
    ClipboardManager cm =
      (ClipboardManager)ctx.getSystemService(Context.CLIPBOARD_SERVICE);
    if (cm != null)
      cm.addPrimaryClipChangedListener(k);
    return k;
  }

  /** Called when the keyboard starts typing in a field. */
  public void editor_started(EditorInfo info)
  {
    _in_password_field = is_password(info.inputType);
  }

  @Override
  public void onPrimaryClipChanged()
  {
    if (!_prefs.getBoolean(KEY_AUTOKEEP, true) || _in_password_field)
      return;
    try
    {
      ClipboardManager cm =
        (ClipboardManager)_ctx.getSystemService(Context.CLIPBOARD_SERVICE);
      ClipData clip = cm.getPrimaryClip();
      if (clip == null || clip.getItemCount() == 0 || is_sensitive(clip.getDescription()))
        return;
      CharSequence cs = clip.getItemAt(0).getText();
      // Images and files: not kept yet.
      if (cs == null)
        return;
      String text = cs.toString();
      if (text.trim().isEmpty() || text.length() > MAX_TEXT_LENGTH)
        return;
      MemoryStore m = new MemoryStore(new File(_ctx.getFilesDir(), "memory.json"));
      long boot = System.currentTimeMillis() - SystemClock.elapsedRealtime();
      String cat = MemoryStore.guess_category(text);
      if (text.length() >= _prefs.getInt(KEY_BIG_COPY, DEFAULT_BIG_COPY))
        m.add_pending(text, cat, session_key(boot), boot);
      else
        m.add_history(text, cat, session_key(boot), boot);
    }
    catch (Exception e)
    {
      // Locked phone (no storage yet) or clipboard not readable.
      Logs.exn("Studio clip keeper", e);
    }
    catch (Throwable e)
    {
      // A bug in Memory doesn't stop the keyboard; the copy isn't kept.
    }
  }

  /** One history record per phone restart: the boot count when Android has
      it, the boot time to the minute otherwise. */
  String session_key(long boot)
  {
    if (VERSION.SDK_INT >= 24)
    {
      int n = Settings.Global.getInt(_ctx.getContentResolver(),
          Settings.Global.BOOT_COUNT, -1);
      if (n >= 0)
        return "boot-" + n;
    }
    return "boot-t" + (boot / 60000);
  }

  static boolean is_sensitive(ClipDescription d)
  {
    if (d == null || VERSION.SDK_INT < 24)
      return false;
    PersistableBundle e = d.getExtras();
    return e != null && e.getBoolean(EXTRA_IS_SENSITIVE, false);
  }

  public static boolean is_password_field(EditorInfo info)
  {
    return info != null && is_password(info.inputType);
  }

  static boolean is_password(int input_type)
  {
    int cls = input_type & InputType.TYPE_MASK_CLASS;
    int var = input_type & InputType.TYPE_MASK_VARIATION;
    if (cls == InputType.TYPE_CLASS_TEXT)
      return var == InputType.TYPE_TEXT_VARIATION_PASSWORD
        || var == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        || var == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD;
    if (cls == InputType.TYPE_CLASS_NUMBER)
      return var == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    return false;
  }
}
