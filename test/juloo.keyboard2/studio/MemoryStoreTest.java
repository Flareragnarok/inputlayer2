package juloo.keyboard2.studio;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class MemoryStoreTest
{
  public MemoryStoreTest() {}

  static File tmp() throws Exception
  {
    File f = File.createTempFile("memory", ".json");
    f.delete();
    f.deleteOnExit();
    return f;
  }

  @Test
  public void add_list_persist()
  throws Exception
  {
    File f = tmp();
    MemoryStore s = new MemoryStore(f);
    assertTrue(s.add("https://example.org", MemoryStore.LINKS));
    assertTrue(s.add("¯\\_(ツ)_/¯", MemoryStore.SYMBOLS));
    assertTrue(s.add("https://example.org", MemoryStore.LINKS)); // Moves to top, no duplicate
    assertFalse(s.add("", MemoryStore.TEXT));
    assertFalse(s.add("x", "No such category"));
    assertEquals(2, s.items(null).size());
    assertEquals("https://example.org", s.items(null).get(0).text);
    assertEquals(1, s.items(MemoryStore.LINKS).size());
    // Reload from the file.
    MemoryStore r = new MemoryStore(f);
    assertEquals(2, r.items(null).size());
    assertEquals(MemoryStore.SYMBOLS, r.items(null).get(1).category);
  }

  @Test
  public void categories()
  throws Exception
  {
    File f = tmp();
    MemoryStore s = new MemoryStore(f);
    assertTrue(s.add_category("Commands"));
    assertFalse(s.add_category("commands"));
    assertFalse(s.add_category("links"));
    assertFalse(s.add_category("   "));
    assertFalse(s.add_category("a very very long category name"));
    s.add("ls -la", "Commands");
    s.add("ls -la", MemoryStore.TEXT);
    assertEquals(MemoryStore.DEFAULT_CATEGORIES.size() + 1, s.categories().size());
    MemoryStore.Item it = s.items("Commands").get(0);
    s.move(it, MemoryStore.SNIPPETS);
    assertEquals(1, s.items(MemoryStore.SNIPPETS).size());
    s.add("git status", "Commands");
    s.remove_category("Commands");
    s.remove_category(MemoryStore.LINKS); // Default categories stay
    assertEquals(MemoryStore.DEFAULT_CATEGORIES, s.categories());
    assertEquals(2, s.items(MemoryStore.TEXT).size()); // git status moved to Text
    assertTrue(s.contains("git status"));
    assertFalse(s.contains("git log"));
    assertEquals(MemoryStore.DEFAULT_CATEGORIES.size(), new MemoryStore(f).categories().size());
  }

  @Test
  public void guess()
  {
    assertEquals(MemoryStore.LINKS, MemoryStore.guess_category("https://github.com/x"));
    assertEquals(MemoryStore.LINKS, MemoryStore.guess_category(" www.example.org "));
    assertEquals(MemoryStore.SYMBOLS, MemoryStore.guess_category("→ ★ ✓"));
    assertEquals(MemoryStore.SNIPPETS, MemoryStore.guess_category("line one\nline two"));
    assertEquals(MemoryStore.TEXT, MemoryStore.guess_category("call back at 5"));
    assertEquals(MemoryStore.TEXT, MemoryStore.guess_category("see https://x.org later"));
  }

  @Test
  public void unreadable_file_kept_aside()
  throws Exception
  {
    File f = tmp();
    FileOutputStream out = new FileOutputStream(f);
    out.write("not json".getBytes("UTF-8"));
    out.close();
    MemoryStore s = new MemoryStore(f);
    assertTrue(s.items(null).isEmpty());
    File aside = new File(f.getPath() + ".unreadable");
    assertTrue(aside.exists());
    aside.delete();
  }
}
