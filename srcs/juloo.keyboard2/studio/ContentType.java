package juloo.keyboard2.studio;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** What a copied text is, by its content only (never the app it came
    from), and its "look": what alike items share, to put them next to each
    other in a category.

    Rules, the first that matches wins:
    - Mails: an email address, or a mailto: link.
    - Links: a URL alone.
    - Logs: lines with a timestamp and a level (INFO, W/Tag...), several
      lines with a level, or a stack trace.
    - Output: what a terminal shows: a prompt line followed by other lines,
      `ls -l` rows, or most lines in aligned columns.
    - Commands: every line a command: after a `$`/`#` prompt, starting with
      a known program or `./`, or a lowercase word followed by flags,
      pipes or `&&`.
    - Code: at least two code signs (lines ending with `{ } ; :`, starting
      with def, class, import, function, return...).
    - Snippets: other text on several lines.
    - Text, or Symbols when there is no letter nor digit.

    Android's regex engine (ICU) is stricter than the JVM's the tests run
    on: a `{` or `}` outside a character class must be escaped unless it
    is a quantifier (ContentTypeTest checks it). */
public final class ContentType
{
  /** Changes when the rules change: the history is sorted again then. */
  public static final int RULES_VERSION = 1;

  static final Pattern MAIL =
    Pattern.compile("(?i)(mailto:)?[\\w.%+-]+@([\\w-]+(\\.[\\w-]+)+)(\\?\\S*)?");
  static final Pattern LINK = Pattern.compile("(?is)(https?://|www\\.)\\S+");
  static final Pattern HOST = Pattern.compile("(?i)^(?:https?://)?(?:www\\.)?([^/:?#\\s]+)");
  static final Pattern TIMESTAMP = Pattern.compile(
      "\\d{4}-\\d\\d-\\d\\d[ T]\\d\\d:\\d\\d|\\b\\d\\d:\\d\\d:\\d\\d(?:[.,]\\d+)?\\b|\\b\\d\\d-\\d\\d \\d\\d:\\d\\d");
  static final Pattern LEVEL = Pattern.compile(
      "\\b(TRACE|DEBUG|INFO|NOTICE|WARN|WARNING|ERROR|SEVERE|FATAL|CRITICAL)\\b|(?:^|\\s)[VDIWEF]/[\\w.$-]+|\\s[VDIWEF]\\s+[\\w.$-]+\\s*:");
  static final Pattern STACK = Pattern.compile(
      "Traceback \\(most recent call last\\)|^\\s+at [\\w$.<>]+\\(|^\\s+File \"[^\"]+\", line \\d+|^Caused by: ",
      Pattern.MULTILINE);
  /** A shell prompt: `$ `, `# `, `user@host:~$ `, `PS C:\> `, `>>> `. */
  static final Pattern PROMPT = Pattern.compile(
      "^(?:[\\w.-]+@[\\w.-]+(?::[^$#\\s]*)?\\s*[$#%]|[~/][^$#\\s]*\\s*[$#]|[$#%]|PS [^>]*>|>>>)\\s+(\\S.*)$");
  static final Pattern LS_ROW = Pattern.compile("^[dlcbps-][rwxsStT-]{9}[.+@]?\\s+\\d+");
  static final Pattern COLUMNS = Pattern.compile("\\S(?: {2,}|\\t)\\S");
  static final Pattern FLAGS = Pattern.compile("\\s--?[A-Za-z][\\w-]*(=\\S*)?(\\s|$)|\\s\\|\\s|\\s&&\\s|\\s>>?\\s");
  static final Pattern WORD = Pattern.compile("^[a-z][\\w.+-]*$");
  static final Pattern CODE_LINE = Pattern.compile(
      "[{};]\\s*$|^\\s*\\}|^\\s*(def|class|import|from \\S+ import|function|return|const|let|var|public|private|protected|static|fn|func|package|#include|#define|struct|enum|interface)\\b|=>|^\\s*(if|for|while|elif|else)\\b.*[:{]\\s*$|^\\s*@\\w+");

  static final Set<String> PROGRAMS = new HashSet<String>(Arrays.asList(
      "adb", "apt", "apt-get", "awk", "bash", "brew", "cargo", "cat", "cd",
      "chmod", "chown", "cmake", "cp", "curl", "dnf", "docker", "du", "echo",
      "export", "fastboot", "find", "gcc", "gh", "git", "go", "gradle",
      "gradlew", "grep", "head", "htop", "java", "javac", "journalctl", "kill",
      "kubectl", "less", "ln", "ls", "make", "man", "mkdir", "mv", "nano",
      "node", "npm", "npx", "pacman", "pip", "pip3", "pkg", "ps", "python",
      "python3", "rm", "rsync", "scp", "sed", "sh", "ssh", "su", "sudo",
      "systemctl", "tail", "tar", "termux-setup-storage", "top", "touch",
      "uname", "unzip", "vim", "wget", "which", "yarn", "zip", "zsh"));

  private ContentType() {}

  public static String guess(String text)
  {
    String t = text.trim();
    if (MAIL.matcher(t).matches())
      return MemoryStore.MAILS;
    if (LINK.matcher(t).matches())
      return MemoryStore.LINKS;
    List<String> lines = lines(t);
    if (is_log(t, lines))
      return MemoryStore.LOGS;
    if (lines.size() > 1 && is_output(lines))
      return MemoryStore.OUTPUT;
    if (is_commands(lines))
      return MemoryStore.COMMANDS;
    if (is_code(lines))
      return MemoryStore.CODE;
    if (lines.size() > 1)
      return MemoryStore.SNIPPETS;
    for (int i = 0; i < t.length(); i++)
      if (Character.isLetterOrDigit(t.charAt(i)))
        return MemoryStore.TEXT;
    return MemoryStore.SYMBOLS;
  }

  /** What items of [category] alike in [text] share ("git" for
      `git push`, "github.com" for a link), "" when nothing. */
  public static String look(String text, String category)
  {
    String t = text.trim();
    switch (category)
    {
      case MemoryStore.LINKS:
      {
        Matcher m = HOST.matcher(t);
        return m.find() ? m.group(1).toLowerCase(Locale.ROOT) : "";
      }
      case MemoryStore.MAILS:
      {
        Matcher m = MAIL.matcher(t);
        return m.matches() ? m.group(2).toLowerCase(Locale.ROOT) : "";
      }
      case MemoryStore.COMMANDS:
        return program(lines(t).get(0));
      case MemoryStore.OUTPUT:
      {
        Matcher m = PROMPT.matcher(lines(t).get(0));
        return m.matches() ? program(m.group(1)) : "";
      }
      case MemoryStore.LOGS:
        return log_tag(t);
      case MemoryStore.CODE:
        return language(t);
      default:
        return "";
    }
  }

  /** Non-empty lines, trailing spaces removed. */
  static List<String> lines(String t)
  {
    List<String> l = new ArrayList<String>();
    for (String s : t.split("\r?\n"))
    {
      String x = s.replaceAll("\\s+$", "");
      if (!x.isEmpty())
        l.add(x);
    }
    if (l.isEmpty())
      l.add("");
    return l;
  }

  static boolean is_log(String t, List<String> lines)
  {
    if (STACK.matcher(t).find())
      return true;
    int stamped = 0, levelled = 0;
    for (String l : lines)
    {
      boolean lv = LEVEL.matcher(l).find();
      if (lv)
        levelled++;
      if (lv && TIMESTAMP.matcher(l).find())
        stamped++;
    }
    if (stamped > 0 && stamped * 2 >= lines.size())
      return true;
    return lines.size() > 1 && levelled >= 2 && levelled * 2 >= lines.size();
  }

  static boolean is_output(List<String> lines)
  {
    if (PROMPT.matcher(lines.get(0)).matches())
      return true;
    int ls = 0, cols = 0;
    for (String l : lines)
    {
      if (LS_ROW.matcher(l).find())
        ls++;
      if (COLUMNS.matcher(l.trim()).find())
        cols++;
    }
    if (ls > 0 && ls * 2 >= lines.size())
      return true;
    return lines.size() >= 3 && cols * 3 >= lines.size() * 2 && !is_code(lines);
  }

  static boolean is_commands(List<String> lines)
  {
    for (String l : lines)
      if (!is_command(l.trim()) && !l.trim().startsWith("#"))
        return false;
    // Only comments isn't a command.
    for (String l : lines)
      if (!l.trim().startsWith("#") || PROMPT.matcher(l.trim()).matches())
        return true;
    return false;
  }

  static boolean is_command(String l)
  {
    Matcher m = PROMPT.matcher(l);
    if (m.matches())
      return true;
    if (l.isEmpty() || l.length() > 400)
      return false;
    String w = first_word(l);
    if (w.startsWith("./") || w.startsWith("~/"))
      return true;
    if (PROGRAMS.contains(w))
      // "cat", "make", "find" are words too: a single word alone is not a
      // command ("ls" excepted), nor a sentence.
      return (l.contains(" ") && !is_prose(l)) || w.equals("ls") || w.equals("pwd");
    return WORD.matcher(w).matches() && FLAGS.matcher(l).find()
      && !l.endsWith(".") && !l.endsWith("?");
  }

  static final Pattern TECHNICAL = Pattern.compile("[-/=~.:|&>$*@\\\\]");
  static final Set<String> SMALL_WORDS = new HashSet<String>(Arrays.asList(
      "a", "an", "the", "to", "at", "and", "or", "of", "in", "on", "for",
      "me", "you", "it", "is", "are", "be", "my", "your", "with", "sure"));

  /** A sentence starting with a program's name ("make sure to..."):
      punctuated like one, or small words with nothing technical. */
  static boolean is_prose(String l)
  {
    if (l.matches(".*[.?!,]$") || l.contains(", "))
      return true;
    String[] ws = l.split("\\s+");
    int small = 0;
    for (int i = 1; i < ws.length; i++)
    {
      if (TECHNICAL.matcher(ws[i]).find())
        return false;
      if (SMALL_WORDS.contains(ws[i].toLowerCase(Locale.ROOT)))
        small++;
    }
    return small > 0;
  }

  static boolean is_code(List<String> lines)
  {
    int signs = 0;
    for (String l : lines)
      if (CODE_LINE.matcher(l).find())
        signs++;
    return signs >= 2 || (lines.size() == 1 && signs == 1 && lines.get(0).trim().endsWith(";"));
  }

  static String first_word(String l)
  {
    int sp = l.indexOf(' ');
    return (sp < 0) ? l : l.substring(0, sp);
  }

  /** The program a command line runs: after a prompt, sudo and variable
      assignments. */
  static String program(String line)
  {
    String l = line.trim();
    Matcher m = PROMPT.matcher(l);
    if (m.matches())
      l = m.group(1).trim();
    for (int i = 0; i < 8; i++)
    {
      String w = first_word(l);
      boolean skip = w.equals("sudo") || w.equals("doas") || w.equals("time")
        || w.equals("env") || (w.contains("=") && !w.startsWith("="));
      if (!skip || w.length() == l.length())
        break;
      l = l.substring(w.length()).trim();
    }
    String w = first_word(l);
    int slash = w.lastIndexOf('/');
    return (slash >= 0 ? w.substring(slash + 1) : w).toLowerCase(Locale.ROOT);
  }

  static final Pattern ANDROID_TAG = Pattern.compile("(?:^|\\s)[VDIWEF]/([\\w.$-]+)|\\s[VDIWEF]\\s+([\\w.$-]+)\\s*:");
  static final Pattern BRACKET = Pattern.compile("\\[([A-Za-z][\\w.$-]*)\\]");
  static final Pattern EXCEPTION = Pattern.compile("([\\w$]+(?:Exception|Error))\\b");

  /** A log's source: an Android tag, a [name], the exception's name. */
  static String log_tag(String t)
  {
    Matcher m = ANDROID_TAG.matcher(t);
    if (m.find())
      return (m.group(1) != null) ? m.group(1) : m.group(2);
    if (t.contains("Traceback (most recent call last)"))
      return "python";
    m = EXCEPTION.matcher(t);
    if (m.find())
      return m.group(1);
    m = BRACKET.matcher(t);
    while (m.find())
      if (!LEVEL.matcher(m.group(1)).matches())
        return m.group(1);
    return "";
  }

  static String language(String t)
  {
    if (t.matches("(?sm).*^\\s*(def |from \\S+ import |import \\w+\\s*$|elif ).*")
        || t.matches("(?sm).*^\\s*(def|class) \\w+[^\\n]*:\\s*$.*"))
      return "python";
    if (t.contains("#include"))
      return "c";
    if (t.matches("(?s).*\\b(public|private|protected)\\s+(static\\s+)?(class|void|int|String)\\b.*"))
      return "java";
    if (t.matches("(?s).*\\b(fun|val)\\s+\\w+.*") && !t.contains(";"))
      return "kotlin";
    if (t.matches("(?s).*\\b(function|const|let|=>|console\\.).*"))
      return "js";
    if (t.matches("(?s).*\\bfn\\s+\\w+.*"))
      return "rust";
    if (t.matches("(?s).*\\bfunc\\s+\\w+.*"))
      return "go";
    return "";
  }
}
