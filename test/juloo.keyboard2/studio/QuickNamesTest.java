package juloo.keyboard2.studio;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import juloo.keyboard2.KeyValue;
import org.junit.Test;

/** il-008: quick names, both ways. */
public class QuickNamesTest
{
  /** The owner's own examples. */
  @Test public void the_owners_names()
  {
    assertEquals("quicktoggle : layout : backspace", QuickNames.of_bar("split_backspace"));
    assertEquals("quicktoggle : layout : next", QuickNames.of_bar("layout"));
    assertEquals("quickequip : layout current", QuickNames.of_bar("layout_mode"));
    assertEquals("quickedit : resize : +", QuickNames.of_bar("height_up"));
    assertEquals("quickbehaviour : key behaviour : long press", QuickNames.of_key("studio_toggle_repeat"));
    assertEquals("quickbehaviour : key behaviour : long press", QuickNames.of_bar("toggle_repeat"));
    assertEquals("quickfunction : backspace : direction", QuickNames.of_key("studio_backspace_toggle"));
    assertEquals("quickfunction : copy", QuickNames.of_key("copy"));
    assertEquals("quickfunction : paste", QuickNames.of_key("paste"));
    assertEquals("quickfunction : type letter : a", QuickNames.of_key("a"));
    assertEquals("pageswitch : symbols", QuickNames.of_key("il_symbols"));
    assertEquals("pageswitch : symbols more", QuickNames.of_key("il_symbols_more"));
    assertEquals("quicktoggle : layout : numbers", QuickNames.of_key("switch_numeric"));
    assertEquals("quicktoggle : layout : greek", QuickNames.of_key("switch_greekmath"));
    assertEquals("quickopen : layout editor", QuickNames.of_key("studio_open_layout_editor"));
    assertEquals("quickopen : app : com.termux", QuickNames.of_key("app:com.termux"));
    assertEquals("quicktoggle : layout : Gaming", QuickNames.of_key("layout:Gaming"));
    assertEquals("pageswitch : symbols ~once", QuickNames.of_key("il_symbols~once"));
    assertEquals("quickfunction : select all", QuickNames.of_key("selectAll"));
    assertEquals("quickfunction : paste as plain text", QuickNames.of_key("pasteAsPlainText"));
    assertEquals("quickfunction : accent : aigu", QuickNames.of_key("accent_aigu"));
  }

  /** Every special key: one quick name each, and back to the same key. */
  @Test public void every_special_key_round_trips()
  {
    Map<String, String> seen = new HashMap<String, String>();
    StringBuilder bad = new StringBuilder();
    for (String n : KeyNames.SPECIAL)
    {
      String q = QuickNames.of_key(n);
      String other = seen.put(QuickNames.norm(q), n);
      if (other != null)
        bad.append("\n  same quick name ").append(q).append(": ").append(other).append(", ").append(n);
      KeyValue legacy = KeyValue.getKeyByName(n), quick = KeyValue.getKeyByName(QuickNames.stored(q));
      if (!legacy.equals(quick))
        bad.append("\n  ").append(n).append(" -> ").append(q).append(" -> ").append(QuickNames.to_key(q));
    }
    assertEquals("", bad.toString());
  }

  @Test public void every_quicksbar_action_round_trips()
  {
    StringBuilder bad = new StringBuilder();
    for (String[] p : QuickNames.BAR)
      if (!p[0].equals(QuickNames.to_bar(QuickNames.of_bar(p[0]))))
        bad.append(" ").append(p[0]);
    for (QuickSpec s : StudioConfig.DEFAULT_KEYS)
      for (String a : s.actions)
        if (a != null)
        {
          String back = QuickNames.to_bar(QuickNames.of_bar(a));
          // an alias (left, copy…) comes back as the key it stands for
          if (!a.equals(back) && !String.valueOf(Quicksbar.key_name(a)).equals(back))
            bad.append(" ").append(a).append("->").append(back);
        }
    assertEquals("", bad.toString());
    assertEquals("switch:theme", QuickNames.to_bar("quicktoggle : module : theme"));
  }

  /** A module written in quick names: resolved once, when the spec is made,
      so the bar's own checks (repeat while held, the quickedit's view) see
      plain actions (review 2026-11-06). */
  @Test public void quicksbar_specs_resolve_quick_names()
  {
    QuickSpec s = QuickSpec.of("w", "quickfunction:left", "c", "quickedit:focus", "n", "quickedit : resize : +",
        "s", "quickbehaviour:key behaviour:long press~once");
    assertEquals("left", s.get(QuickSpec.index_of("w")));
    assertEquals("select_container", s.get(QuickSpec.index_of("c")));
    assertEquals("height_up", s.get(QuickSpec.index_of("n")));
    assertEquals("toggle_repeat~once", s.get(QuickSpec.index_of("s")));
    assertTrue(Quicksbar.repeats(s.get(QuickSpec.index_of("w"))));
    // any special key by name works on the bar; plain text doesn't
    assertEquals("backspace", Quicksbar.key_name("backspace"));
    assertEquals("il_symbols~once", Quicksbar.key_name("il_symbols~once"));
    assertEquals("layout:Gaming", Quicksbar.key_name("layout:Gaming"));
    assertNull(Quicksbar.key_name("hello"));
  }

  /** As fast as the legacy key: the same key, resolved once at load. */
  @Test public void quick_names_are_the_same_keys()
  {
    String[][] same = {
      { "quickfunction:type letter:a", "a" },
      { "quickfunction : type letter : a", "a" },
      { "QuickFunction : Type Letter : A", "A" },
      { "quickfunction:type letter::", ":" },
      { "quickfunction:type letter:~", "~" },
      { "quickfunction:type letter:nbsp", "nbsp" },
      { "quickfunction:type text:hello", "hello" },
      { "pageswitch:symbols~once", "il_symbols~once" },
      { "quickopen:app:com.termux?tab=roadmap", "app:com.termux?tab=roadmap" },
      { "quicktoggle:layout:Gaming~word", "layout:Gaming~word" },
      { "quickfunction:undo", "studio_undo" },
      { "quickfunction:undo:android", "undo" },
      { "quickapp:com.termux", "app:com.termux" },
      { "quickapp : io.github.flareragnarok.remoteclient?tab=roadmap", "app:io.github.flareragnarok.remoteclient?tab=roadmap" },
    };
    for (String[] p : same)
      assertEquals(p[0], KeyValue.getKeyByName(p[1]), KeyValue.getKeyByName(p[0]));
    assertEquals("quickfunction:type letter:a", QuickNames.stored("quickfunction : type letter : a"));
    assertNull(QuickNames.to_key("quickfunction : no such thing"));
    assertFalse(QuickNames.is_quick("quick"));
    assertFalse(QuickNames.is_quick("quickly"));
  }
}
