package juloo.keyboard2.studio;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;
import juloo.keyboard2.KeyValue;
import juloo.keyboard2.studio.Behaviours.Mode;
import juloo.keyboard2.studio.Behaviours.Spec;
import org.json.JSONObject;
import org.junit.Test;

/** il-005: how switching keys switch back. */
public class BehavioursTest
{
  final List<String> reverted = new ArrayList<String>();
  final List<Object> priors = new ArrayList<Object>();
  final List<Runnable> timers = new ArrayList<Runnable>();
  final Behaviours b = new Behaviours(new Behaviours.Host() {
    public void revert(String sw, Object prior) { reverted.add(sw); priors.add(prior); }
    public Object state(String sw) { return Boolean.FALSE; }
    public Object later(Runnable r, long ms) { timers.add(r); return r; }
    public void cancel(Object h) { timers.remove(h); }
  });

  @Test public void specs()
  {
    assertEquals(Mode.ONCE, Spec.parse("once").mode);
    assertEquals(12, Spec.parse("12s").seconds);
    assertNull(Spec.parse("0s"));
    assertNull(Spec.parse("soon"));
    assertEquals("studio_toggle_repeat", Behaviours.base_of("studio_toggle_repeat~once"));
    assertTrue(Behaviours.is_variant("il_symbols~word"));
    assertFalse(Behaviours.is_variant("shift~once"));
    assertFalse(Behaviours.is_variant("il_symbols~later"));
  }

  /** The owner's example: repeat off, back to repeat after one key edit. */
  @Test public void repeat_once_comes_back_after_one_edit()
  {
    b.switched("studio_toggle_repeat", Spec.parse("once"), Boolean.TRUE);
    b.typed(false, false);
    b.typed(true, true);
    assertTrue("typing isn't the use of repeat off", reverted.isEmpty());
    b.edited();
    assertEquals("[studio_toggle_repeat]", reverted.toString());
    b.edited();
    assertEquals("only once", 1, reverted.size());
  }

  @Test public void quicksbar_repeat_is_the_same_switch()
  {
    b.switched("toggle_repeat", Spec.parse("once"), Boolean.TRUE);
    b.edited();
    assertEquals("[studio_toggle_repeat]", reverted.toString());
  }

  @Test public void backspace_once_after_one_delete()
  {
    b.switched("studio_backspace_toggle", Spec.parse("once"), Boolean.TRUE);
    b.typed(false, false);
    assertTrue(reverted.isEmpty());
    b.typed(true, false);
    assertEquals("[studio_backspace_toggle]", reverted.toString());
  }

  @Test public void page_once_after_one_key_word_at_word_end()
  {
    b.switched("il_symbols", Spec.parse("once"), Boolean.TRUE);
    b.typed(false, false);
    assertEquals("[il_symbols]", reverted.toString());
    b.switched("switch_numeric", Spec.parse("word"), Boolean.TRUE);
    b.typed(false, false);
    b.typed(false, false);
    assertEquals(1, reverted.size());
    b.typed(false, true);
    assertEquals("[il_symbols, switch_numeric]", reverted.toString());
  }

  @Test public void timed_and_manual_back()
  {
    b.switched("studio_backspace_toggle", Spec.parse("5s"), Boolean.TRUE);
    assertEquals(1, timers.size());
    timers.get(0).run();
    assertEquals("[studio_backspace_toggle]", reverted.toString());
    // pressing it again while it waits is the way back by hand: nothing left to do
    b.switched("studio_toggle_repeat", Spec.parse("30s"), Boolean.TRUE);
    b.switched("studio_toggle_repeat", Spec.TOGGLE, Boolean.TRUE);
    assertTrue(timers.isEmpty());
    assertTrue(b.waiting().isEmpty());
  }

  @Test public void another_page_or_abc_drops_the_way_back()
  {
    b.switched("il_symbols", Spec.parse("word"), Boolean.TRUE);
    b.switched("switch_numeric", Spec.TOGGLE, Boolean.TRUE);
    assertTrue(b.waiting().isEmpty());
    b.switched("switch_numeric", Spec.parse("once"), Boolean.TRUE);
    b.left_page();
    b.typed(false, false);
    assertTrue(reverted.isEmpty());
  }

  @Test public void toggle_never_switches_back()
  {
    b.switched("studio_toggle_repeat", Spec.TOGGLE, Boolean.TRUE);
    b.edited();
    b.typed(true, true);
    assertTrue(reverted.isEmpty());
  }

  /** The revert gets the state from before the switch, so a toggle goes
      back to exactly that, never to "the other one" (review 2026-11-06). */
  @Test public void revert_carries_the_prior_state()
  {
    b.switched("layout_mode", Spec.parse("once"), Boolean.FALSE);
    b.edited();
    assertEquals("[layout_mode]", reverted.toString());
    assertEquals(Boolean.FALSE, priors.get(0));
  }

  /** Edit mode left by Back (the keyboard's own doing): nothing waits. */
  @Test public void cancel_when_the_keyboard_leaves_the_mode_itself()
  {
    b.switched("layout_mode", Spec.parse("10s"), Boolean.FALSE);
    assertTrue(b.armed("layout_mode"));
    b.cancel("layout_mode");
    assertFalse(b.armed("layout_mode"));
    assertTrue(timers.isEmpty());
    b.edited();
    assertTrue(reverted.isEmpty());
  }

  @Test public void defaults_round_trip() throws Exception
  {
    JSONObject d = Behaviours.with_default(new JSONObject(), "toggle_repeat", Spec.parse("once"));
    assertEquals(Mode.ONCE, Behaviours.default_of(d, "studio_toggle_repeat").mode);
    d = Behaviours.with_default(d, "studio_toggle_repeat", Spec.TOGGLE);
    assertEquals(Mode.TOGGLE, Behaviours.default_of(d, "toggle_repeat").mode);
    assertEquals("{}", d.toString());
  }

  @Test public void variant_keys()
  {
    KeyValue k = KeyValue.getKeyByName("studio_toggle_repeat~once");
    assertEquals(KeyValue.Kind.Behaved, k.getKind());
    assertEquals("↻¹", k.getString());
    assertEquals("studio_toggle_repeat~once", KeyMap.name_of(k));
    assertEquals(KeyValue.Kind.Event, KeyValue.getKeyByName("studio_toggle_repeat").getKind());
    assertNotEquals(KeyValue.Kind.Behaved, KeyValue.getKeyByName("a~once").getKind());
  }
}
