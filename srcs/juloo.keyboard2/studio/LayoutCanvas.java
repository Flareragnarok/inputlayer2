package juloo.keyboard2.studio;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;

/** The layout editor's keyboard (il-006): keys drawn to scale, a tap
    selects a key (or a row, on its free space), dragging the selected key's
    right edge sets its width, dragging a row's bottom edge its height. */
public final class LayoutCanvas extends View
{
  public interface Listener
  {
    void selected(int row, int key);
    /** A drag is about to change the layout (keep an undo step). */
    void will_change();
    void changed();
  }

  LayoutDoc _doc;
  Listener _listener;
  public int sel_row = -1, sel_key = -1;

  final Paint _key = new Paint(Paint.ANTI_ALIAS_FLAG), _sel = new Paint(Paint.ANTI_ALIAS_FLAG),
        _text = new Paint(Paint.ANTI_ALIAS_FLAG), _small = new Paint(Paint.ANTI_ALIAS_FLAG),
        _edge = new Paint(Paint.ANTI_ALIAS_FLAG), _rowsel = new Paint(Paint.ANTI_ALIAS_FLAG);
  final float _row_h, _gap, _handle;

  static final int DRAG_NONE = 0, DRAG_WIDTH = 1, DRAG_HEIGHT = 2;
  int _drag = DRAG_NONE;
  float _drag_x0, _drag_v0;
  int _drag_row;

  public LayoutCanvas(Context ctx)
  {
    super(ctx);
    _row_h = dp(54);
    _gap = dp(2);
    _handle = dp(14);
    _key.setColor(0xff3a403b);
    _sel.setColor(0xff2d6a4f);
    _rowsel.setColor(0x336cc39b);
    _edge.setColor(0xff6cc39b);
    _edge.setStrokeWidth(dp(3));
    _text.setColor(0xffe4e8e3);
    _text.setTextAlign(Paint.Align.CENTER);
    _text.setTextSize(dp(17));
    _small.setColor(0xff98a39b);
    _small.setTextAlign(Paint.Align.CENTER);
    _small.setTextSize(dp(9));
  }

  /** Labels by key name: a name is parsed once, not on every frame. */
  final java.util.HashMap<String, String> _labels = new java.util.HashMap<String, String>();

  String label(String name)
  {
    if (name == null)
      return "";
    String l = _labels.get(name);
    if (l == null)
    {
      l = LayoutDoc.label(name);
      _labels.put(name, l);
    }
    return l;
  }

  public void set(LayoutDoc d, Listener l)
  {
    _doc = d;
    _listener = l;
    _labels.clear();
    if (sel_row >= d.rows.size()) { sel_row = -1; sel_key = -1; }
    if (sel_row >= 0 && sel_key >= d.rows.get(sel_row).keys.size()) sel_key = -1;
    requestLayout();
    invalidate();
  }

  float unit()
  {
    float w = (_doc == null) ? 10f : Math.max(1f, _doc.width());
    return getWidth() / w;
  }

  @Override protected void onMeasure(int ws, int hs)
  {
    float h = 0;
    if (_doc != null)
      for (LayoutDoc.Row r : _doc.rows) h += r.height * _row_h;
    setMeasuredDimension(MeasureSpec.getSize(ws), (int)Math.max(h, _row_h) + (int)dp(4));
  }

  /** Bounds of key [i] of row [r]. */
  RectF rect(int r, int i)
  {
    float u = unit(), y = 0;
    for (int k = 0; k < r; k++) y += _doc.rows.get(k).height * _row_h;
    LayoutDoc.Row row = _doc.rows.get(r);
    float x = 0;
    for (int k = 0; k < i; k++) x += (row.keys.get(k).width + row.keys.get(k).shift) * u;
    LayoutDoc.Key key = row.keys.get(i);
    x += key.shift * u;
    return new RectF(x, y, x + key.width * u, y + row.height * _row_h);
  }

  float row_top(int r)
  {
    float y = 0;
    for (int k = 0; k < r; k++) y += _doc.rows.get(k).height * _row_h;
    return y;
  }

  @Override protected void onDraw(Canvas c)
  {
    if (_doc == null)
      return;
    final float u = unit();
    final float[][] at = { {0,0}, {-1,-1}, {1,-1}, {-1,1}, {1,1}, {-1,0}, {1,0}, {0,-1}, {0,1} };
    float y = 0;
    for (int r = 0; r < _doc.rows.size(); r++)
    {
      LayoutDoc.Row row = _doc.rows.get(r);
      float bottom = y + row.height * _row_h;
      if (r == sel_row)
      {
        c.drawRect(0, y, getWidth(), bottom, _rowsel);
        // the row's height handle: its bottom edge
        c.drawLine(getWidth() * 0.3f, bottom - dp(2), getWidth() * 0.7f, bottom - dp(2), _edge);
      }
      float x = 0; // one pass per row, not a prefix walk per key
      for (int i = 0; i < row.keys.size(); i++)
      {
        LayoutDoc.Key k = row.keys.get(i);
        x += k.shift * u;
        RectF in = new RectF(x + _gap, y + _gap, x + k.width * u - _gap, bottom - _gap);
        x += k.width * u;
        boolean sel = (r == sel_row && i == sel_key);
        c.drawRoundRect(in, dp(5), dp(5), sel ? _sel : _key);
        c.drawText(label(k.names[0]), in.centerX(), in.centerY() + dp(6), _text);
        // the swipes, small, where they are
        for (int s = 1; s < 9; s++)
          if (k.names[s] != null)
            c.drawText(label(k.names[s]), in.centerX() + at[s][0] * in.width() * 0.32f,
                in.centerY() + at[s][1] * in.height() * 0.33f + dp(3), _small);
        if (sel)
          c.drawLine(in.right, in.top + dp(6), in.right, in.bottom - dp(6), _edge);
      }
      y = bottom;
    }
  }

  @Override public boolean onTouchEvent(MotionEvent e)
  {
    if (_doc == null)
      return false;
    float x = e.getX(), y = e.getY();
    switch (e.getActionMasked())
    {
      case MotionEvent.ACTION_DOWN:
        _drag = DRAG_NONE;
        if (sel_row >= 0 && sel_key >= 0)
        {
          RectF b = rect(sel_row, sel_key);
          if (Math.abs(x - b.right) < _handle && y >= b.top && y <= b.bottom)
          {
            start(DRAG_WIDTH, x, _doc.key(sel_row, sel_key).width);
            return true;
          }
        }
        if (sel_row >= 0)
        {
          float bottom = row_top(sel_row) + _doc.rows.get(sel_row).height * _row_h;
          if (Math.abs(y - bottom) < _handle)
          {
            _drag_row = sel_row;
            start(DRAG_HEIGHT, y, _doc.rows.get(sel_row).height);
            return true;
          }
        }
        pick(x, y);
        return true;
      case MotionEvent.ACTION_MOVE:
        if (_drag == DRAG_WIDTH)
        {
          _doc.set_width(sel_row, sel_key, _drag_v0 + (x - _drag_x0) / unit());
          invalidate();
          _listener.changed();
        }
        else if (_drag == DRAG_HEIGHT)
        {
          _doc.set_height(_drag_row, _drag_v0 + (y - _drag_x0) / _row_h);
          requestLayout();
          invalidate();
          _listener.changed();
        }
        return true;
      case MotionEvent.ACTION_UP:
      case MotionEvent.ACTION_CANCEL:
        _drag = DRAG_NONE;
        return true;
    }
    return false;
  }

  void start(int kind, float at, float v0)
  {
    _listener.will_change();
    _drag = kind;
    _drag_x0 = at;
    _drag_v0 = v0;
    getParent().requestDisallowInterceptTouchEvent(true);
  }

  void pick(float x, float y)
  {
    for (int r = 0; r < _doc.rows.size(); r++)
    {
      float top = row_top(r), bottom = top + _doc.rows.get(r).height * _row_h;
      if (y < top || y >= bottom)
        continue;
      for (int i = 0; i < _doc.rows.get(r).keys.size(); i++)
      {
        RectF b = rect(r, i);
        if (x >= b.left && x < b.right)
        {
          select(r, i);
          return;
        }
      }
      select(r, -1); // the row's free space: the row
      return;
    }
  }

  public void select(int r, int i)
  {
    sel_row = r;
    sel_key = i;
    invalidate();
    _listener.selected(r, i);
  }

  float dp(float v)
  {
    return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics());
  }
}
