package juloo.keyboard2.studio;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.LinearLayout;

/** The keyboard's stack of parts. While the quickedit is focusing, it takes
    the taps meant for the parts (they select instead of typing), except on
    the quickedit itself. */
public class FocusLayout extends LinearLayout
{
  public interface Catcher
  {
    /** Whether touches starting at [ev] are taken. */
    boolean catches(MotionEvent ev);
    /** A tap at [x], [y] (this view's coordinates). */
    void tapped(float x, float y);
  }

  Catcher _catcher = null;
  boolean _caught = false;

  public FocusLayout(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
  }

  public void set_catcher(Catcher c) { _catcher = c; }

  @Override
  public boolean onInterceptTouchEvent(MotionEvent ev)
  {
    if (ev.getActionMasked() == MotionEvent.ACTION_DOWN)
      _caught = _catcher != null && _catcher.catches(ev);
    return _caught;
  }

  @Override
  public boolean onTouchEvent(MotionEvent ev)
  {
    if (!_caught)
      return super.onTouchEvent(ev);
    if (ev.getActionMasked() == MotionEvent.ACTION_UP)
    {
      _caught = false;
      _catcher.tapped(ev.getX(), ev.getY());
    }
    else if (ev.getActionMasked() == MotionEvent.ACTION_CANCEL)
      _caught = false;
    return true;
  }
}
