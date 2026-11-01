package juloo.keyboard2.studio;

import java.io.File;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContentTypeTest
{
  public ContentTypeTest() {}

  static void is(String category, String text)
  {
    assertEquals(text, category, ContentType.guess(text));
  }

  @Test
  public void commands()
  {
    is(MemoryStore.COMMANDS, "git push -u origin studio");
    is(MemoryStore.COMMANDS, "$ ls -la ~/work");
    is(MemoryStore.COMMANDS, "sudo apt install ripgrep");
    is(MemoryStore.COMMANDS, "./gradlew --no-watch-fs clean test assembleDebug");
    is(MemoryStore.COMMANDS, "rg --files | head");
    is(MemoryStore.COMMANDS, "user@host:~/work$ make install");
    is(MemoryStore.COMMANDS, "cd ~/work\ngit status\n# then push\ngit push");
    is(MemoryStore.COMMANDS, "ls");
    // Words that are programs too, in sentences.
    is(MemoryStore.TEXT, "cat");
    is(MemoryStore.TEXT, "make sure to call back at 5");
    is(MemoryStore.TEXT, "find me at the station.");
    is(MemoryStore.TEXT, "Hello there, how are you?");
  }

  @Test
  public void output()
  {
    is(MemoryStore.OUTPUT, "$ ls\nREADME.md\nsrcs\ntest");
    is(MemoryStore.OUTPUT, "drwxr-xr-x  4 me me 4096 Oct 31 17:58 .\n-rw-rw-r--  1 me me 1191 Oct 30 19:41 ffpng.py");
    is(MemoryStore.OUTPUT, "NAME      READY   STATUS\nweb-1     1/1     Running\ndb-0      1/1     Running");
  }

  @Test
  public void logs()
  {
    is(MemoryStore.LOGS, "2026-11-01 10:22:01 ERROR Connection refused");
    is(MemoryStore.LOGS, "11-01 10:22:01.123  1234  1250 E ActivityManager: ANR in com.x\n11-01 10:22:02.000  1234  1250 I ActivityManager: Killing 999");
    is(MemoryStore.LOGS, "Traceback (most recent call last):\n  File \"a.py\", line 3, in <module>\nZeroDivisionError: division by zero");
    is(MemoryStore.LOGS, "java.lang.NullPointerException: x\n    at juloo.keyboard2.Keyboard2.onCreate(Keyboard2.java:120)");
    is(MemoryStore.LOGS, "[WARN] disk almost full\n[ERROR] write failed");
  }

  @Test
  public void code()
  {
    is(MemoryStore.CODE, "def f(x):\n    return x + 1");
    is(MemoryStore.CODE, "int main() {\n  return 0;\n}");
    is(MemoryStore.CODE, "const f = (x) => x + 1;\nconsole.log(f(1));");
  }

  @Test
  public void others()
  {
    is(MemoryStore.LINKS, "https://github.com/x");
    is(MemoryStore.MAILS, "kaze.s@example.co.uk");
    is(MemoryStore.SNIPPETS, "line one\nline two");
    is(MemoryStore.SNIPPETS, "Dear Sam,\nthanks for the photos.\nSee you soon");
    is(MemoryStore.SYMBOLS, "→ ★ ✓");
    is(MemoryStore.TEXT, "see https://x.org later");
  }

  @Test
  public void looks()
  {
    assertEquals("git", ContentType.look("$ sudo git push", MemoryStore.COMMANDS));
    assertEquals("gradlew", ContentType.look("./gradlew test", MemoryStore.COMMANDS));
    assertEquals("apt", ContentType.look("DEBIAN_FRONTEND=noninteractive sudo apt install x", MemoryStore.COMMANDS));
    assertEquals("github.com", ContentType.look("https://www.github.com/x/y", MemoryStore.LINKS));
    assertEquals("example.org", ContentType.look("Me@Example.org", MemoryStore.MAILS));
    assertEquals("ActivityManager", ContentType.look("11-01 10:22:01.123  1234  1250 E ActivityManager: ANR", MemoryStore.LOGS));
    assertEquals("python", ContentType.look("Traceback (most recent call last):\n  File \"a.py\", line 3", MemoryStore.LOGS));
    assertEquals("python", ContentType.look("def f(x):\n    return x", MemoryStore.CODE));
    assertEquals("", ContentType.look("hello", MemoryStore.TEXT));
  }

  @Test
  public void alike_items_are_grouped()
  throws Exception
  {
    File f = File.createTempFile("memory", ".json");
    f.delete();
    f.deleteOnExit();
    MemoryStore s = new MemoryStore(f);
    s.add("git status", MemoryStore.COMMANDS);
    s.add("ls -la", MemoryStore.COMMANDS);
    s.add("git push", MemoryStore.COMMANDS);
    List<MemoryStore.Item> is = s.items(MemoryStore.COMMANDS);
    // The newest group first, newest first inside.
    assertEquals("git push", is.get(0).text);
    assertEquals("git status", is.get(1).text);
    assertEquals("ls -la", is.get(2).text);
    // All categories: newest first only.
    assertEquals("ls -la", s.items(null).get(1).text);
  }
}
