package juloo.keyboard2.studio;

import java.io.File;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class UserWordsTest
{
  public UserWordsTest() {}

  static File tmp() throws Exception
  {
    File f = File.createTempFile("words", ".json");
    f.delete();
    f.deleteOnExit();
    return f;
  }

  @Test
  public void learns_and_ranks()
  throws Exception
  {
    UserWords w = new UserWords(tmp());
    w.learn("kotlin", null);
    assertTrue(w.suggest("ko", 3).isEmpty());          // Once isn't enough
    w.learn("kotlin", null);
    assertEquals(Arrays.asList("kotlin"), w.suggest("ko", 3));
    w.learn("koala", null); w.learn("koala", null); w.learn("koala", null);
    assertEquals(Arrays.asList("koala", "kotlin"), w.suggest("KO", 3)); // Most used first
    w.learn("Kotlin", null);
    assertEquals("Kotlin", w.suggest("kot", 1).get(0)); // As last typed
    assertEquals(3, w.count("kotlin"));
    assertTrue(w.suggest("x", 3).isEmpty());
  }

  @Test
  public void written_words_kept_as_written()
  throws Exception
  {
    UserWords w = new UserWords(tmp());
    String bold = UnicodeStyles.apply("bold", "bold");
    w.learn_written(bold);
    w.learn_written("oBAmA");
    assertEquals(Arrays.asList(bold), w.suggest("bo", 3));   // At once, by its plain letters
    assertEquals(Arrays.asList("oBAmA"), w.suggest("oba", 3));
    w.learn("obama", null);
    assertEquals("oBAmA", w.suggest("oba", 1).get(0));       // Stays as written
  }

  @Test
  public void next_words_and_persistence()
  throws Exception
  {
    File f = tmp();
    UserWords w = new UserWords(f);
    for (int i = 0; i < 3; i++)
    {
      w.learn("good", null);
      w.learn("morning", "good");
    }
    w.learn("night", "good");
    w.learn("night", "good");
    assertEquals(Arrays.asList("morning", "night"), w.predict("Good", 2));
    // Nothing known after this word: the most used words.
    assertEquals(3, w.predict("unknown", 3).size());
    assertFalse(new UserWords(f).suggest("go", 1).size() > 0); // Not flushed yet
    w.flush();
    UserWords r = new UserWords(f);
    assertEquals(Arrays.asList("morning", "night"), r.predict("good", 2));
    r.forget_all();
    assertEquals(0, new UserWords(f).size());
  }
}
