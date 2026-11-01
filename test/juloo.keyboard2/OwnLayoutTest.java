package juloo.keyboard2;

import org.junit.Test;
import static org.junit.Assert.*;

public class OwnLayoutTest
{
  public OwnLayoutTest() {}

  @Test
  public void own_layouts_for_covered_languages()
  {
    assertEquals("il_qwertz", Config.own_layout("de", "latn_qwertz_de"));
    assertEquals("il_qwertz", Config.own_layout("de-CH", "latn_qwertz_de"));
    assertEquals("il_azerty", Config.own_layout("fr", "latn_azerty_fr"));
    assertEquals("il_azerty", Config.own_layout("fr-CA", "latn_azerty_fr"));
    assertEquals("il_qwerty", Config.own_layout("en-US", "latn_qwerty_us"));
    assertEquals("il_qwerty", Config.own_layout("en-GB", "latn_qwerty_gb"));
    assertEquals("il_qwerty", Config.own_layout(null, null));
  }

  @Test
  public void other_languages_keep_theirs()
  {
    // Their letters aren't on our layouts.
    assertEquals("latn_qwerty_us", Config.own_layout("es", "latn_qwerty_us"));
    assertEquals("latn_qwertz_cz", Config.own_layout("cs", "latn_qwertz_cz"));
    assertEquals("latn_azerty_be", Config.own_layout("fr-BE", "latn_azerty_be"));
    assertEquals("latn_azerty_be", Config.own_layout("de-BE", "latn_azerty_be"));
    assertEquals("cyrl_jcuken_ru", Config.own_layout("ru", "cyrl_jcuken_ru"));
  }
}
