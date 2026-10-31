package juloo.keyboard2.studio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The edit strip's Format: wraps text in the markup of the app it is typed
    into (Markdown, Discord, Reddit, WhatsApp, Telegram, HTML, BBCode).
    Apps without markup (Threads, Instagram, X) get none: Appearance's
    unicode styles work there instead. */
public final class TextFormats
{
  public static final String NONE = "None";

  /** A way to mark text up: an opening and closing marker for each format,
      "\n" in a marker meaning the text goes on its own lines. */
  public static final class Syntax
  {
    public final String name;
    /** Format -> { open, close }, in menu order. */
    public final Map<String, String[]> formats = new LinkedHashMap<String, String[]>();

    Syntax(String n, String... f)
    {
      name = n;
      for (int i = 0; i + 2 < f.length; i += 3)
        formats.put(f[i], new String[]{ f[i + 1], f[i + 2] });
    }
  }

  public static final List<Syntax> SYNTAXES = Arrays.asList(
      new Syntax("Markdown",
        "Bold", "**", "**", "Italic", "*", "*", "Strikethrough", "~~", "~~",
        "Code", "`", "`", "Code block", "```\n", "\n```", "Link", "[", "](https://)",
        "Quote", "> ", "", "Heading", "# ", ""),
      new Syntax("Discord",
        "Bold", "**", "**", "Italic", "*", "*", "Underline", "__", "__",
        "Strikethrough", "~~", "~~", "Spoiler", "||", "||", "Code", "`", "`",
        "Code block", "```\n", "\n```", "Quote", "> ", "", "Heading", "# ", ""),
      new Syntax("Reddit",
        "Bold", "**", "**", "Italic", "*", "*", "Strikethrough", "~~", "~~",
        "Spoiler", ">!", "!<", "Superscript", "^(", ")", "Code", "`", "`",
        "Link", "[", "](https://)", "Quote", "> ", "", "Heading", "# ", ""),
      new Syntax("WhatsApp",
        "Bold", "*", "*", "Italic", "_", "_", "Strikethrough", "~", "~",
        "Monospace", "```", "```", "Code", "`", "`", "Quote", "> ", ""),
      new Syntax("Telegram",
        "Bold", "**", "**", "Italic", "__", "__", "Strikethrough", "~~", "~~",
        "Spoiler", "||", "||", "Code", "`", "`", "Code block", "```\n", "\n```"),
      new Syntax("HTML",
        "Bold", "<b>", "</b>", "Italic", "<i>", "</i>", "Underline", "<u>", "</u>",
        "Strikethrough", "<s>", "</s>", "Code", "<code>", "</code>",
        "Link", "<a href=\"\">", "</a>", "Quote", "<blockquote>", "</blockquote>"),
      new Syntax("BBCode",
        "Bold", "[b]", "[/b]", "Italic", "[i]", "[/i]", "Underline", "[u]", "[/u]",
        "Strikethrough", "[s]", "[/s]", "Spoiler", "[spoiler]", "[/spoiler]",
        "Code", "[code]", "[/code]", "Link", "[url=]", "[/url]",
        "Quote", "[quote]", "[/quote]"),
      new Syntax(NONE));

  /** Known apps and the syntax they read. */
  static final String[][] APPS = {
    { "com.discord", "Discord" },
    { "com.reddit.frontpage", "Reddit" }, { "com.laurencedawson.reddit_sync", "Reddit" },
    { "ml.docilealligator.infinityforreddit", "Reddit" }, { "com.rubenmayayo.reddit", "Reddit" },
    { "com.whatsapp", "WhatsApp" }, { "com.whatsapp.w4b", "WhatsApp" },
    { "org.telegram.messenger", "Telegram" }, { "org.telegram.messenger.web", "Telegram" },
    { "org.thunderdog.challegram", "Telegram" }, { "org.telegram.plus", "Telegram" },
    { "com.github.android", "Markdown" }, { "md.obsidian", "Markdown" },
    { "net.gsantner.markor", "Markdown" }, { "com.joplin", "Markdown" },
    { "com.instagram.barcelona", NONE }, { "com.instagram.android", NONE },
    { "com.twitter.android", NONE }, { "com.zhiliaoapp.musically", NONE },
    { "com.facebook.katana", NONE }, { "com.facebook.orca", NONE },
  };

  public static Syntax syntax(String name)
  {
    for (Syntax s : SYNTAXES)
      if (s.name.equals(name))
        return s;
    return SYNTAXES.get(0);
  }

  /** The syntax for the app [pkg]: the user's choice for it, the known one,
      Markdown otherwise. */
  public static Syntax for_app(String pkg, String chosen)
  {
    if (chosen != null)
      return syntax(chosen);
    if (pkg != null)
      for (String[] a : APPS)
        if (a[0].equals(pkg))
          return syntax(a[1]);
    return syntax("Markdown");
  }

  /** Result of [toggle]: the new text and where the original text now is. */
  public static final class Edit
  {
    /** Replaces [start, end) of the original. */
    public final int start, end;
    public final String text;
    /** Selection after the edit, in the new text's coordinates. */
    public final int sel_start, sel_end;

    Edit(int s, int e, String t, int ss, int se)
    {
      start = s; end = e; text = t; sel_start = ss; sel_end = se;
    }
  }

  /** Wrap [text][sel] in [markers], or unwrap it if it already is: markers
      just outside or just inside the selection. Line markers ("> ", "# ")
      go at the start of each selected line. */
  public static Edit toggle(String text, int[] sel, String[] markers)
  {
    String open = markers[0], close = markers[1];
    int s = sel[0], e = sel[1];
    String inner = text.substring(s, e);
    if (close.isEmpty() && !open.isEmpty())
      return toggle_lines(s, e, inner, open);
    // Unwrap: markers around the selection...
    if (s >= open.length() && e + close.length() <= text.length()
        && text.startsWith(open, s - open.length()) && text.startsWith(close, e))
      return new Edit(s - open.length(), e + close.length(), inner,
          s - open.length(), e - open.length());
    // ...or inside it.
    if (inner.length() >= open.length() + close.length()
        && inner.startsWith(open) && inner.endsWith(close))
    {
      String t = inner.substring(open.length(), inner.length() - close.length());
      return new Edit(s, e, t, s, s + t.length());
    }
    String t = open + inner + close;
    return new Edit(s, e, t, s + open.length(), s + open.length() + inner.length());
  }

  static Edit toggle_lines(int s, int e, String inner, String prefix)
  {
    String[] lines = inner.split("\n", -1);
    boolean all = true;
    for (String l : lines)
      if (!l.startsWith(prefix))
        all = false;
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < lines.length; i++)
    {
      if (i > 0)
        b.append('\n');
      b.append(all ? lines[i].substring(prefix.length()) : prefix + lines[i]);
    }
    String t = b.toString();
    return new Edit(s, e, t, s, s + t.length());
  }

  public static List<String> syntax_names()
  {
    List<String> l = new ArrayList<String>();
    for (Syntax s : SYNTAXES)
      l.add(s.name);
    return l;
  }
}
