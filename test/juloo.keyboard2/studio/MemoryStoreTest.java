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
    assertTrue(s.is_saved("git status"));
    assertFalse(s.is_saved("git log"));
    assertEquals(MemoryStore.DEFAULT_CATEGORIES.size(), new MemoryStore(f).categories().size());
  }

  @Test
  public void history_by_restart()
  throws Exception
  {
    File f = tmp();
    MemoryStore s = new MemoryStore(f);
    s.add_history("first boot", MemoryStore.TEXT, "boot-1", 1000);
    s.add_history("https://a.org", MemoryStore.LINKS, "boot-1", 1000);
    s.add_history("first boot", MemoryStore.TEXT, "boot-1", 1000); // Moves up
    s.add_history("second boot", MemoryStore.TEXT, "boot-2", 2000);
    s.add_history("first boot", MemoryStore.TEXT, "boot-2", 2000); // Copied again
    s.add("saved", MemoryStore.TEXT);
    List<MemoryStore.Session> ss = s.sessions();
    assertEquals(2, ss.size());
    assertEquals("boot-2", ss.get(0).key);       // Newest restart first
    assertEquals(2000, ss.get(0).started);
    assertEquals(2, ss.get(0).items.size());
    assertEquals(2, ss.get(1).items.size());
    assertEquals("first boot", ss.get(1).items.get(0).text);
    // Categories list a text once.
    assertEquals(3, s.items(MemoryStore.TEXT).size());
    // Saving a history item keeps it when the history is deleted.
    s.add("https://a.org", MemoryStore.LINKS);
    assertEquals(1, s.items(MemoryStore.LINKS).size());
    assertFalse(s.items(MemoryStore.LINKS).get(0).is_history());
    s.delete_session("boot-1");
    assertEquals(1, s.sessions().size());
    assertTrue(s.is_saved("https://a.org"));
    // Survives reloading.
    MemoryStore r = new MemoryStore(f);
    assertEquals(1, r.sessions().size());
    assertEquals(2000, r.sessions().get(0).started);
    r.delete_history();
    assertTrue(r.sessions().isEmpty());
    assertEquals(2, r.items(null).size()); // The two saved items
    // Moving a history item saves it.
    r.add_history("moved", MemoryStore.TEXT, "boot-3", 3000);
    r.move(r.sessions().get(0).items.get(0), MemoryStore.SNIPPETS);
    assertTrue(r.is_saved("moved"));
    assertTrue(r.sessions().isEmpty());
  }

  @Test
  public void guess()
  {
    assertEquals(MemoryStore.LINKS, MemoryStore.guess_category("https://github.com/x"));
    assertEquals(MemoryStore.MAILS, MemoryStore.guess_category("kaze.s@example.co.uk"));
    assertEquals(MemoryStore.MAILS, MemoryStore.guess_category(" mailto:me@x.org?subject=Hi "));
    assertEquals(MemoryStore.TEXT, MemoryStore.guess_category("write to me@x.org please"));
    assertEquals(MemoryStore.SYMBOLS, MemoryStore.guess_category("@@"));
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

  @Test
  public void pending_big_copies()
  throws Exception
  {
    File f = tmp();
    MemoryStore s = new MemoryStore(f);
    assertTrue(s.add_pending("big one", MemoryStore.TEXT, "boot-1", 1000));
    assertTrue(s.add_pending("big two", MemoryStore.TEXT, "boot-1", 1000));
    assertTrue(s.add_pending("big one", MemoryStore.TEXT, "boot-1", 1000)); // Moves up
    // Waiting ones are not in Memory yet.
    assertEquals(0, s.items(null).size());
    assertEquals(2, s.pending().size());
    assertEquals("big one", s.pending().get(0).text);
    // They wait across reloads.
    MemoryStore r = new MemoryStore(f);
    assertEquals(2, r.pending().size());
    r.keep_pending(r.pending().get(0));
    r.drop_pending(r.pending().get(0));
    assertEquals(0, r.pending().size());
    List<MemoryStore.Item> is = r.items(null);
    assertEquals(1, is.size());
    assertEquals("big one", is.get(0).text);
    assertTrue(is.get(0).is_history());
    assertEquals(1, r.sessions().size());
    assertEquals(0, new MemoryStore(f).pending().size());
    assertEquals(1, new MemoryStore(f).items(null).size());
  }

  @Test
  public void pending_is_bounded()
  throws Exception
  {
    MemoryStore s = new MemoryStore(tmp());
    for (int i = 0; i < MemoryStore.MAX_PENDING + 5; i++)
      s.add_pending("big " + i, MemoryStore.TEXT, "boot-1", 1000);
    assertEquals(MemoryStore.MAX_PENDING, s.pending().size());
    assertEquals("big " + (MemoryStore.MAX_PENDING + 4), s.pending().get(0).text);
  }
}
