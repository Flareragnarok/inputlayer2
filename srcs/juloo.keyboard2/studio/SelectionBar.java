package juloo.keyboard2.studio;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import juloo.keyboard2.Config;
import juloo.keyboard2.R;

/** The selection bar: while typing in format (Scope swiped down) it shows
    what the typing produces, the format and look applied, as it goes to the
    cursor. Only shown then (or while the quick edit places it). It can
    also show a pinned selection: tap selects it again, ✕ drops it. */
public class SelectionBar extends LinearLayout
{
  public interface Listener
  {
    void select_pinned();
    void unpin();
  }

  Listener _listener;
  TextView _text;
  String _pinned = null;
  /** Typing in format's result, shown instead of the pinned text. */
  String _preview = null;
  /** Shown even when empty, while the quick edit places it. */
  boolean _placing = false;
  float _dp = StudioConfig.DEFAULT_SELECTION_BAR_DP;

  public SelectionBar(Context ctx, AttributeSet attrs)
  {
    super(ctx, attrs);
    setOrientation(HORIZONTAL);
    setGravity(Gravity.CENTER_VERTICAL);
    int label = Quick.attr_color(ctx, R.attr.colorLabel, 0xffffffff);
    _text = new TextView(ctx);
    _text.setTextColor(label);
    _text.setSingleLine(true);
    _text.setEllipsize(TextUtils.TruncateAt.MIDDLE);
    _text.setPadding(dp(12), 0, dp(8), 0);
    _text.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { if (_listener != null) _listener.select_pinned(); }
    });
    addView(_text, new LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
    _text.setGravity(Gravity.CENTER_VERTICAL);
    TextView close = new TextView(ctx);
    close.setText("✕");
    close.setTextColor(label);
    close.setGravity(Gravity.CENTER);
    close.setOnClickListener(new View.OnClickListener() {
      public void onClick(View v) { if (_listener != null) _listener.unpin(); }
    });
    addView(close, new LayoutParams(dp(40), ViewGroup.LayoutParams.MATCH_PARENT));
  }

  public void setup(Listener l) { _listener = l; }

  public void refresh(Config config)
  {
    _dp = config.studio.selection_bar_dp;
    update();
  }

  /** [text] pinned, [null] for nothing. */
  public void set_pinned(String text)
  {
    _pinned = text;
    update();
  }

  /** [text] being typed in format, [null] when done. */
  public void set_preview(String text)
  {
    _preview = text;
    update();
  }

  public void set_placing(boolean p)
  {
    _placing = p;
    update();
  }

  void update()
  {
    boolean show = _dp > 0 && (_pinned != null || _preview != null || _placing);
    setVisibility(show ? VISIBLE : GONE);
    if (!show)
      return;
    ViewGroup.LayoutParams p = getLayoutParams();
    float dp = (_dp > 0) ? _dp : StudioConfig.DEFAULT_SELECTION_BAR_DP;
    p.height = (int)(dp * getResources().getDisplayMetrics().density);
    setLayoutParams(p);
    if (_preview != null)
      _text.setText("✎ " + _preview.replace('\n', ' '));
    else if (_pinned != null)
      _text.setText("📌 " + _pinned.replace('\n', ' '));
    else
      _text.setText("Selection bar: shows what typing in format (Scope ↓) makes");
  }

  int dp(int v)
  {
    return (int)(v * getResources().getDisplayMetrics().density);
  }
}
