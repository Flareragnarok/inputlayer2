package juloo.keyboard2.studio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import juloo.keyboard2.R;

/** A tiny key of the comfort bar. Tap for the "c" action, or press and slide
    toward one of the 8 directions for the others, like the keyboard's keys.
    Some actions repeat while held. */
final class ComfortKey extends View implements Runnable
{
  interface Host
  {
    void perform(String action);
    /** Label and whether it uses the keyboard's icon font. */
    Label label_of(String action);
    float swipe_dist_px();
    long repeat_delay();
    long repeat_interval();
  }

  static final class Label
  {
    final String text;
    final boolean key_font;
    Label(String t, boolean f) { text = t; key_font = f; }
  }

  /** Direction offsets, indexed like [ComfortKeySpec.DIRECTIONS]. */
  static final float[][] OFFSETS = {
    { 0, 0 }, { 0, -1 }, { 1, -1 }, { 1, 0 }, { 1, 1 },
    { 0, 1 }, { -1, 1 }, { -1, 0 }, { -1, -1 } };

  final ComfortKeySpec _spec;
  final Host _host;
  final Label[] _labels = new Label[ComfortKeySpec.DIRECTIONS.length];
  final Paint _paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  final Paint _bg = new Paint(Paint.ANTI_ALIAS_FLAG);
  int _key_color;
  int _key_pressed_color;
  final RectF _rect = new RectF();
  final float _radius;
  final Handler _handler = new Handler();
  final Typeface _key_font;
  int _label_color;

  float _down_x, _down_y;
  /** Direction under the finger, [-1] when it is an unset direction. */
  int _dir = 0;
  boolean _repeated;

  ComfortKey(Context ctx, ComfortKeySpec spec, Host host, Typeface key_font)
  {
    super(ctx);
    _spec = spec;
    _host = host;
    _key_font = key_font;
    for (int i = 0; i < _labels.length; i++)
      if (spec.get(i) != null)
        _labels[i] = host.label_of(spec.get(i));
    TypedValue tv = new TypedValue();
    _label_color = 0xff888888;
    if (ctx.getTheme().resolveAttribute(R.attr.colorLabel, tv, true))
      _label_color = tv.data;
    // Like the keyboard's keys, a shade brighter.
    _key_color = brighter(attr_color(ctx, R.attr.colorKey, 0xff333333), 0.08f);
    _key_pressed_color = attr_color(ctx, R.attr.colorKeyActivated, 0xff1b1b1b);
    float radius = 5 * getResources().getDisplayMetrics().density;
    if (ctx.getTheme().resolveAttribute(R.attr.keyBorderRadius, tv, true))
      radius = tv.getDimension(getResources().getDisplayMetrics());
    _radius = radius;
    _paint.setTextAlign(Paint.Align.CENTER);
  }

  @Override
  protected void onDraw(Canvas canvas)
  {
    float w = getWidth(), h = getHeight();
    _rect.set(0, 0, w, h);
    _bg.setColor(isPressed() ? _key_pressed_color : _key_color);
    canvas.drawRoundRect(_rect, _radius, _radius, _bg);
    float cx = w / 2, cy = h / 2;
    for (int i = 0; i < _labels.length; i++)
    {
      Label l = _labels[i];
      if (l == null)
        continue;
      boolean center = (i == 0);
      _paint.setTypeface(l.key_font ? _key_font : Typeface.DEFAULT);
      _paint.setTextSize(h * (center ? 0.55f : 0.3f));
      // Long labels ("Paragraph") shrink to fit the key.
      float max_w = center ? w * 0.9f : w * 0.4f;
      float tw = _paint.measureText(l.text);
      if (tw > max_w)
        _paint.setTextSize(_paint.getTextSize() * max_w / tw);
      _paint.setColor(_label_color);
      _paint.setAlpha((isPressed() && i == _dir) ? 255 : (center ? 230 : 140));
      float x = cx + OFFSETS[i][0] * (w / 2 - h * 0.22f);
      float y = cy + OFFSETS[i][1] * (h / 2 - h * 0.2f);
      // Vertically center the text on [y].
      y -= (_paint.descent() + _paint.ascent()) / 2;
      canvas.drawText(l.text, x, y, _paint);
    }
  }

  static int attr_color(Context ctx, int attr, int def)
  {
    TypedValue tv = new TypedValue();
    return ctx.getTheme().resolveAttribute(attr, tv, true) ? tv.data : def;
  }

  /** [c] moved a fraction [f] toward white. */
  static int brighter(int c, float f)
  {
    int r = (c >> 16) & 0xff, g = (c >> 8) & 0xff, b = c & 0xff;
    r += (int)((255 - r) * f);
    g += (int)((255 - g) * f);
    b += (int)((255 - b) * f);
    return (c & 0xff000000) | (r << 16) | (g << 8) | b;
  }

  @Override
  public boolean onTouchEvent(MotionEvent ev)
  {
    switch (ev.getActionMasked())
    {
      case MotionEvent.ACTION_DOWN:
        _down_x = ev.getX();
        _down_y = ev.getY();
        _dir = 0;
        _repeated = false;
        setPressed(true);
        _handler.postDelayed(this, _host.repeat_delay());
        invalidate();
        return true;
      case MotionEvent.ACTION_MOVE:
        int d = direction(ev.getX() - _down_x, ev.getY() - _down_y);
        if (d != _dir)
        {
          _dir = d;
          invalidate();
        }
        return true;
      case MotionEvent.ACTION_UP:
        _handler.removeCallbacks(this);
        setPressed(false);
        if (!_repeated && _dir >= 0 && _spec.get(_dir) != null)
          _host.perform(_spec.get(_dir));
        invalidate();
        return true;
      case MotionEvent.ACTION_CANCEL:
        _handler.removeCallbacks(this);
        setPressed(false);
        invalidate();
        return true;
    }
    return false;
  }

  /** Index into [ComfortKeySpec.DIRECTIONS]: [0] near the starting point,
      the closest set direction otherwise, [-1] if none is close. */
  int direction(float dx, float dy)
  {
    return direction(_spec, dx, dy, _host.swipe_dist_px());
  }

  static int direction(ComfortKeySpec _spec, float dx, float dy, float min_dist)
  {
    if (Math.hypot(dx, dy) < min_dist)
      return 0;
    // 0 is up, clockwise, in eighths of a turn.
    double a = Math.atan2(dx, -dy) / (Math.PI / 4);
    int sector = (int)Math.round(a);
    int d = ((sector % 8) + 8) % 8 + 1;
    if (_spec.get(d) != null)
      return d;
    // Neighbouring directions, like the keyboard does.
    int next = d % 8 + 1, prev = (d + 6) % 8 + 1;
    double frac = a - Math.floor(a);
    // Past the middle of the sector, the finger is on its counter-clockwise side.
    int first = (frac >= 0.5) ? prev : next, second = (first == next) ? prev : next;
    if (_spec.get(first) != null)
      return first;
    if (_spec.get(second) != null)
      return second;
    return -1;
  }

  /** Repeat the action under the finger while held, if it repeats. */
  public void run()
  {
    String a = (_dir >= 0) ? _spec.get(_dir) : null;
    if (a == null || !ComfortBar.repeats(a))
      return;
    _repeated = true;
    _host.perform(a);
    _handler.postDelayed(this, _host.repeat_interval());
  }
}
