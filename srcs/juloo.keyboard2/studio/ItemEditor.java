package juloo.keyboard2.studio;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.text.Editable;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;

/** Where the keyboard types while editing a Memory or clipboard item: an
    input connection on the keyboard's own text, used instead of the app's
    field. Handles what the keyboard sends: text, removals, key events
    (⌫, ⌦, arrows, Home/End, Enter), the editing actions (copy, cut, paste,
    select all), the selection. The Clipboard / Memory pane shows it, with
    its own cursor (the keyboard's windows can't hold the system's). */
public final class ItemEditor extends BaseInputConnection
{
  public interface Listener
  {
    /** The text or the selection changed. */
    void changed(ItemEditor e);
  }

  final Editable _text;
  final Listener _listener;
  final Context _ctx;

  public ItemEditor(View view, String text, Listener l)
  {
    super(view, true);
    _ctx = view.getContext();
    _text = new SpannableStringBuilder(text);
    Selection.setSelection(_text, _text.length());
    _listener = l;
  }

  @Override
  public Editable getEditable() { return _text; }

  public String text() { return _text.toString(); }

  public int sel_start() { return Math.min(Selection.getSelectionStart(_text), Selection.getSelectionEnd(_text)); }
  public int sel_end() { return Math.max(Selection.getSelectionStart(_text), Selection.getSelectionEnd(_text)); }

  /** The moving end of the selection (the cursor when nothing is
      selected). */
  public int tip() { return Selection.getSelectionEnd(_text); }

  void changed() { _listener.changed(this); }

  @Override
  public boolean commitText(CharSequence text, int pos)
  {
    boolean r = super.commitText(text, pos);
    changed();
    return r;
  }

  @Override
  public boolean deleteSurroundingText(int before, int after)
  {
    boolean r = super.deleteSurroundingText(before, after);
    changed();
    return r;
  }

  @Override
  public boolean setSelection(int start, int end)
  {
    boolean r = super.setSelection(start, end);
    changed();
    return r;
  }

  @Override
  public boolean setComposingText(CharSequence text, int pos)
  {
    boolean r = super.setComposingText(text, pos);
    changed();
    return r;
  }

  @Override
  public boolean finishComposingText()
  {
    boolean r = super.finishComposingText();
    changed();
    return r;
  }

  @Override
  public ExtractedText getExtractedText(ExtractedTextRequest req, int flags)
  {
    ExtractedText et = new ExtractedText();
    et.text = text();
    et.startOffset = 0;
    et.partialStartOffset = -1;
    et.partialEndOffset = -1;
    et.selectionStart = Selection.getSelectionStart(_text);
    et.selectionEnd = Selection.getSelectionEnd(_text);
    return et;
  }

  @Override
  public boolean sendKeyEvent(KeyEvent ev)
  {
    if (ev.getAction() != KeyEvent.ACTION_DOWN)
      return true;
    int s = sel_start(), e = sel_end(), n = _text.length();
    boolean shift = ev.isShiftPressed();
    switch (ev.getKeyCode())
    {
      case KeyEvent.KEYCODE_DEL:
        if (s != e) _text.delete(s, e);
        else if (s > 0) _text.delete(s - Character.charCount(Character.codePointBefore(_text, s)), s);
        break;
      case KeyEvent.KEYCODE_FORWARD_DEL:
        if (s != e) _text.delete(s, e);
        else if (s < n) _text.delete(s, s + Character.charCount(Character.codePointAt(_text, s)));
        break;
      case KeyEvent.KEYCODE_DPAD_LEFT:
        move(shift, (s == e || shift) ? step(Selection.getSelectionEnd(_text), -1) : s);
        break;
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        move(shift, (s == e || shift) ? step(Selection.getSelectionEnd(_text), 1) : e);
        break;
      case KeyEvent.KEYCODE_DPAD_UP:
      case KeyEvent.KEYCODE_MOVE_HOME:
      {
        int c = Selection.getSelectionEnd(_text);
        int ls = text().lastIndexOf('\n', c - 1) + 1;
        if (ev.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP && ls > 0)
        {
          int prev = text().lastIndexOf('\n', ls - 2) + 1;
          move(shift, Math.min(prev + (c - ls), ls - 1));
        }
        else
          move(shift, (ev.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) ? 0 : ls);
        break;
      }
      case KeyEvent.KEYCODE_DPAD_DOWN:
      case KeyEvent.KEYCODE_MOVE_END:
      {
        int c = Selection.getSelectionEnd(_text);
        int le = text().indexOf('\n', c);
        if (le < 0)
          le = n;
        if (ev.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN && le < n)
        {
          int ls = text().lastIndexOf('\n', c - 1) + 1;
          int next_end = text().indexOf('\n', le + 1);
          move(shift, Math.min(le + 1 + (c - ls), (next_end < 0) ? n : next_end));
        }
        else
          move(shift, (ev.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) ? n : le);
        break;
      }
      case KeyEvent.KEYCODE_ENTER:
      case KeyEvent.KEYCODE_NUMPAD_ENTER:
        _text.replace(s, e, "\n");
        break;
      case KeyEvent.KEYCODE_TAB:
        _text.replace(s, e, "\t");
        break;
      default:
      {
        int ch = ev.getUnicodeChar();
        if (ch == 0)
          return true;
        _text.replace(s, e, new String(Character.toChars(ch)));
        break;
      }
    }
    changed();
    return true;
  }

  int step(int c, int dir)
  {
    if (dir < 0)
      return (c <= 0) ? 0 : c - Character.charCount(Character.codePointBefore(_text, c));
    return (c >= _text.length()) ? _text.length()
      : c + Character.charCount(Character.codePointAt(_text, c));
  }

  /** The cursor to [to]; with Shift, the selection's end. */
  void move(boolean shift, int to)
  {
    to = Math.max(0, Math.min(_text.length(), to));
    if (shift)
      Selection.setSelection(_text, Selection.getSelectionStart(_text), to);
    else
      Selection.setSelection(_text, to);
  }

  @Override
  public boolean performContextMenuAction(int id)
  {
    int s = sel_start(), e = sel_end();
    ClipboardManager cm = (ClipboardManager)_ctx.getSystemService(Context.CLIPBOARD_SERVICE);
    switch (id)
    {
      case android.R.id.selectAll:
        Selection.setSelection(_text, 0, _text.length());
        break;
      case android.R.id.copy:
      case android.R.id.cut:
        if (s == e)
          return true;
        cm.setPrimaryClip(ClipData.newPlainText("Input Layer", _text.subSequence(s, e)));
        if (id == android.R.id.cut)
          _text.delete(s, e);
        break;
      case android.R.id.paste:
      {
        ClipData c = cm.getPrimaryClip();
        CharSequence t = (c == null || c.getItemCount() == 0) ? null : c.getItemAt(0).coerceToText(_ctx);
        if (t != null)
          _text.replace(s, e, t);
        break;
      }
      default:
        return false;
    }
    changed();
    return true;
  }

  public static final String CURSOR = "▏";

  /** Characters shown around the cursor of a long text. */
  static final int WINDOW = 240;

  /** The text as shown: the selection highlighted, a thin bar at the
      cursor ([CURSOR], one character); unless [whole], a long text is cut
      around the cursor. */
  public CharSequence display(int highlight, boolean whole)
  {
    String t = text();
    int s = sel_start(), e = sel_end();
    int from = 0, to = t.length();
    if (!whole && t.length() > WINDOW)
    {
      from = Math.max(0, Math.min(s - WINDOW / 2, t.length() - WINDOW));
      to = Math.min(t.length(), Math.max(e, from + WINDOW));
    }
    SpannableStringBuilder b = new SpannableStringBuilder(t.substring(from, to));
    int ds = s - from, de = e - from;
    if (ds != de)
      b.setSpan(new BackgroundColorSpan(highlight), ds, de, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    else
      b.insert(ds, CURSOR);
    if (to < t.length())
      b.append("…");
    if (from > 0)
      b.insert(0, "…");
    return b;
  }
}
