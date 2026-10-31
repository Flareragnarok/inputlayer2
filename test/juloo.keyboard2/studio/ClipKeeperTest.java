package juloo.keyboard2.studio;

import android.text.InputType;
import org.junit.Test;
import static org.junit.Assert.*;

public class ClipKeeperTest
{
  public ClipKeeperTest() {}

  @Test
  public void password_fields()
  {
    int text = InputType.TYPE_CLASS_TEXT;
    assertTrue(ClipKeeper.is_password(text | InputType.TYPE_TEXT_VARIATION_PASSWORD));
    assertTrue(ClipKeeper.is_password(text | InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD));
    assertTrue(ClipKeeper.is_password(text | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD));
    assertTrue(ClipKeeper.is_password(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD));
    assertFalse(ClipKeeper.is_password(text | InputType.TYPE_TEXT_VARIATION_URI));
    assertFalse(ClipKeeper.is_password(text | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
    assertFalse(ClipKeeper.is_password(InputType.TYPE_CLASS_NUMBER));
  }
}
