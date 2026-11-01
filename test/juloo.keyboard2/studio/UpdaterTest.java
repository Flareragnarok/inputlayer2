package juloo.keyboard2.studio;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class UpdaterTest
{
  public UpdaterTest() {}

  static String release(String tag, boolean draft, boolean pre, String asset)
  {
    return "{\"tag_name\":\"" + tag + "\",\"name\":\"Build " + tag + "\","
      + "\"draft\":" + draft + ",\"prerelease\":" + pre + ",\"body\":\"notes " + tag + "\","
      + "\"assets\":[{\"name\":\"" + asset + "\",\"size\":4000000,"
      + "\"browser_download_url\":\"https://example.invalid/" + asset + "\"}]}";
  }

  @Test
  public void parse_releases()
  throws Exception
  {
    String json = "[" + String.join(",",
        release("build-7", false, false, "studio.apk"),
        release("build-9", true, false, "studio.apk"),   // draft
        release("build-8", false, true, "studio.apk"),   // prerelease: a beta
        release("2.1.0", false, false, "studio.apk"),    // not a Studio tag
        release("build-6", false, false, "notes.txt"),   // no APK
        release("build-12", false, false, "studio.apk")) + "]";
    List<Updater.Release> rs = Updater.parse_releases(json);
    assertEquals(3, rs.size());
    assertEquals(12, rs.get(0).build);
    assertEquals(8, rs.get(1).build);
    assertTrue(rs.get(1).beta);
    assertFalse(rs.get(0).beta);
    assertEquals(7, rs.get(2).build);
    // Stable only: the beta goes.
    List<Updater.Release> stable = Updater.channel(rs, false);
    assertEquals(2, stable.size());
    assertEquals(7, stable.get(1).build);
    assertEquals(3, Updater.channel(rs, true).size());
    assertEquals("notes build-12", rs.get(0).notes);
    assertEquals("https://example.invalid/studio.apk", rs.get(0).apk_url);
    assertEquals(4000000, rs.get(0).apk_size);
  }

  @Test
  public void tags_and_repos()
  {
    assertEquals(42, Updater.build_of_tag("build-42"));
    assertEquals(-1, Updater.build_of_tag("build-x"));
    assertEquals(-1, Updater.build_of_tag("v2.1.0"));
    assertEquals(-1, Updater.build_of_tag(null));
    assertTrue(Updater.is_valid_repo("owner/input-layer"));
    assertFalse(Updater.is_valid_repo("input-layer"));
    assertFalse(Updater.is_valid_repo("owner/repo/../x"));
    assertFalse(Updater.is_valid_repo("https://github.com/owner/repo"));
  }
}
