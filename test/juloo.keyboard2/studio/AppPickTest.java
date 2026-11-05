package juloo.keyboard2.studio;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** quickopen : app's search. */
public class AppPickTest
{
  @Test public void search_by_label_or_package()
  {
    List<String[]> all = Arrays.asList(new String[]{ "Termux", "com.termux" },
        new String[]{ "Remote Client", "io.github.flareragnarok.remoteclient" },
        new String[]{ "Chrome", "com.android.chrome" });
    assertEquals(3, AppPick.filter(all, "").size());
    assertEquals("com.termux", AppPick.filter(all, "TERM").get(0)[1]);
    assertEquals("Remote Client", AppPick.filter(all, "flareragnarok").get(0)[0]);
    assertEquals(2, AppPick.filter(all, "com.").size());
    assertTrue(AppPick.filter(all, "nothing").isEmpty());
  }
}
