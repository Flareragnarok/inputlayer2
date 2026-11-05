package juloo.keyboard2;

import android.content.res.Resources;
import android.view.KeyEvent;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class LayoutModifier
{
  static Config globalConfig;
  static KeyboardData.Row bottom_row;
  static KeyboardData.Row number_row_no_symbols;
  static KeyboardData.Row number_row_symbols;
  static KeyboardData num_pad;
  // Not used in this file but defined here for convenience.
  public static KeyboardData.Row split_middle_column;

  /** Update the layout according to the configuration.
   *  - Remove the switching key if it isn't needed
   *  - Remove "localized" keys from other locales (not in 'extra_keys')
   *  - Replace the action key to show the right label
   *  - Swap the enter and action keys
   *  - Add the optional numpad and number row
   *  - Add the extra keys
   */
  public static KeyboardData modify_layout(KeyboardData kw)
  {
    // Extra keys are removed from the set as they are encountered during the
    // first iteration then automatically added.
    final TreeMap<KeyValue, KeyboardData.PreferredPos> extra_keys = new TreeMap<KeyValue, KeyboardData.PreferredPos>();
    final Set<KeyValue> remove_keys = new HashSet<KeyValue>();
    // Make sure the config key is accessible to avoid being locked in a custom
    // layout.
    extra_keys.put(KeyValue.CONFIG, KeyboardData.PreferredPos.ANYWHERE);
    extra_keys.putAll(globalConfig.extra_keys_param);
    extra_keys.putAll(globalConfig.extra_keys_custom);
    // Number row and numpads are added after the modification pass to allow
    // removing the number keys from the main layout.
    KeyboardData.Row added_number_row = null;
    KeyboardData added_numpad = null;
    if (globalConfig.show_numpad)
    {
      added_numpad = modify_numpad(num_pad, kw);
      remove_keys.addAll(added_numpad.getKeys().keySet());
    }
    else if (globalConfig.add_number_row && !kw.embedded_number_row) // The numpad removes the number row
    {
      added_number_row = modify_number_row(globalConfig.number_row_symbols ? number_row_symbols : number_row_no_symbols, kw);
      if (globalConfig.split_layout)
        added_number_row = LayoutLandscapeModifier.transform_number_row(added_number_row);
      remove_keys.addAll(added_number_row.getKeys(0).keySet());
    }
    // Add the bottom row before computing the extra keys
    if (kw.bottom_row)
      kw = kw.insert_row(bottom_row, kw.rows.size());
    // Split the layout in landscape orientation
    if (globalConfig.split_layout)
      kw = LayoutLandscapeModifier.transform_to_landscape(kw);
    // Compose keys to add to the layout
    // 'extra_keys_keyset' reflects changes made to 'extra_keys'
    Set<KeyValue> extra_keys_keyset = extra_keys.keySet();
    // 'kw_keys' contains the keys present on the layout without any extra keys
    Set<KeyValue> kw_keys = kw.getKeys().keySet();
    if (globalConfig.extra_keys_subtype != null && kw.locale_extra_keys)
    {
      Set<KeyValue> present = new HashSet<KeyValue>(kw_keys);
      present.addAll(extra_keys_keyset);
      globalConfig.extra_keys_subtype.compute(extra_keys,
          new ExtraKeys.Query(kw.script, present));
    }
    kw = kw.mapKeys(new KeyboardData.MapKeyValues() {
      public KeyValue apply(KeyValue key, boolean localized)
      {
        if (localized && !extra_keys.containsKey(key))
          return null;
        if (remove_keys.contains(key))
          return null;
        return modify_key(key);
      }
    });
    if (added_numpad != null)
      kw = kw.addNumPad(added_numpad);
    // Add extra keys that are not on the layout (including 'loc' keys)
    extra_keys_keyset.removeAll(kw_keys);
    if (extra_keys.size() > 0)
      kw = kw.addExtraKeys(extra_keys.entrySet().iterator());
    // Avoid adding extra keys to the number row
    if (added_number_row != null)
      kw = kw.insert_row(added_number_row, 0);
    return studio_keys(kw);
  }

  /** Studio: Input Layer's swipes on backspace, space, Shift, Ctrl and the
      arrow key, the rows' heights, and last the owner's own key changes
      (layout mode). */
  static KeyboardData studio_keys(KeyboardData kw)
  {
    kw = widen_symbols(swap_symbols_and_shift(
          arrows_and_shift(space_nests(backspace_cut(kw)))));
    if (globalConfig.studio == null || globalConfig.studio.split_backspace)
      kw = split_backspace(kw);
    kw = ctrl_undo(kw);
    if (globalConfig.studio != null && globalConfig.studio.keymap != null)
      kw = globalConfig.studio.keymap.apply(kw);
    // Heights by position, after the quick edit rearranged the rows (it moves
    // a row's height with it).
    return scale_rows(kw);
  }

  /** Studio: Input Layer's symbols pages, with the same swipes on their
      keys as the letters. */
  public static KeyboardData modify_symbols(KeyboardData kw)
  {
    return studio_keys(kw);
  }

  /** Studio: the space bar's swipes drive the edit strip's nest configs:
      down resets all nests, up switches to the next saved config, left
      saves the current one. They replace upstream's layout switching (still
      on the quicksbar) and the left half of the cursor slider (the arrow
      keys move the cursor). */
  static KeyboardData space_nests(KeyboardData kw)
  {
    final KeyValue space = KeyValue.getKeyByName("space");
    final KeyValue reset = KeyValue.getKeyByName("studio_nests_reset");
    final KeyValue next = KeyValue.getKeyByName("studio_nests_next");
    final KeyValue save = KeyValue.getKeyByName("studio_nests_save");
    return kw.mapKeys(new KeyboardData.MapKey() {
      public KeyboardData.Key apply(KeyboardData.Key k)
      {
        if (k.keys[0] == null || !k.keys[0].equals(space) || k.keys.length <= 8)
          return k;
        return k.withKeyValue(8, reset).withKeyValue(7, next).withKeyValue(5, save);
      }
    });
  }

  /** Studio: Ctrl's up-left swipe undoes, up-right redoes (replacing
      upstream's placeholders there, which do nothing unless enabled). */
  static KeyboardData ctrl_undo(KeyboardData kw)
  {
    final KeyValue ctrl = KeyValue.getKeyByName("ctrl");
    final KeyValue undo = KeyValue.getKeyByName("studio_undo");
    final KeyValue redo = KeyValue.getKeyByName("studio_redo");
    return kw.mapKeys(new KeyboardData.MapKey() {
      public KeyboardData.Key apply(KeyboardData.Key k)
      {
        if (k.keys[0] == null || !k.keys[0].equals(ctrl) || k.keys.length <= 2)
          return k;
        return k.withKeyValue(1, undo).withKeyValue(2, redo);
      }
    });
  }

  /** Studio: the arrow key next to the space bar taps Sector's step (its
      tap was a Compose placeholder, off unless enabled). Shift gets Caps
      Lock on its up swipe and Compose on its right swipe, where free (or
      holding a Caps Lock placeholder), and its left swipe applies View's
      format as picked on the edit strip. */
  static KeyboardData arrows_and_shift(KeyboardData kw)
  {
    final KeyValue up = KeyValue.getKeyByName("up");
    final KeyValue down = KeyValue.getKeyByName("down");
    final KeyValue shift = KeyValue.getKeyByName("shift");
    final KeyValue capslock = KeyValue.getKeyByName("capslock");
    final KeyValue compose = KeyValue.getKeyByName("compose");
    final KeyValue apply_format = KeyValue.getKeyByName("studio_apply_format");
    // Its label shows Sector's direction.
    final KeyValue step = KeyValue.getKeyByName(
        juloo.keyboard2.studio.EditStrip.selection_forward ? "studio_sector_step"
        : "studio_sector_step_back");
    final KeyValue step_other = KeyValue.getKeyByName(
        juloo.keyboard2.studio.EditStrip.selection_forward ? "studio_sector_step_back"
        : "studio_sector_step");
    return kw.mapKeys(new KeyboardData.MapKey() {
      public KeyboardData.Key apply(KeyboardData.Key k)
      {
        if (k.keys.length <= 8)
          return k;
        if (up.equals(k.keys[7]) && down.equals(k.keys[8])
            && (k.keys[0] == null || k.keys[0].equals(compose) || k.keys[0].equals(step_other)))
          return k.withKeyValue(0, step);
        if (k.keys[0] != null && k.keys[0].equals(shift))
        {
          if (k.keys[7] == null)
            k = k.withKeyValue(7, capslock);
          if (k.keys[6] == null || k.keys[6].equals(capslock))
            k = k.withKeyValue(6, compose);
          if (k.keys[5] == null)
            k = k.withKeyValue(5, apply_format);
        }
        return k;
      }
    });
  }

  /** Studio: backspace. Swipe down cuts the selection (it then shows in the
      clipboard history), swipe up switches between removing before the
      cursor and forward removal (the tap and its label follow), which
      retires the forward delete on its up-right swipe. Swipe left applies
      Verse's look as picked on the edit strip. */
  static KeyboardData backspace_cut(KeyboardData kw)
  {
    final KeyValue backspace = KeyValue.getKeyByName("backspace");
    final KeyValue delete = KeyValue.getKeyByName("delete");
    final KeyValue tap = juloo.keyboard2.studio.EditStrip.forward_delete ? delete : backspace;
    // Cuts the pinned selection when there is one.
    final KeyValue cut = KeyValue.getKeyByName("studio_cut");
    final KeyValue toggle = KeyValue.getKeyByName("studio_backspace_toggle");
    final KeyValue apply = KeyValue.getKeyByName("studio_apply_look");
    return kw.mapKeys(new KeyboardData.MapKey() {
      public KeyboardData.Key apply(KeyboardData.Key k)
      {
        if (k.keys[0] == null || !k.keys[0].equals(backspace) || k.keys.length <= 8)
          return k;
        k = k.withKeyValue(0, tap);
        if (k.keys[8] == null)
          k = k.withKeyValue(8, cut);
        if (k.keys[7] == null)
          k = k.withKeyValue(7, toggle);
        if (k.keys[5] == null)
          k = k.withKeyValue(5, apply);
        if (delete.equals(k.keys[2]))
          k = k.withKeyValue(2, null);
        return k;
      }
    });
  }

  /** Studio (owner, build 68): in the row of ?123 and backspace, ?123 is a
      quarter key wider and backspace as much tighter. */
  static final float SYMBOLS_WIDER = 0.25f;

  static KeyboardData widen_symbols(KeyboardData kw)
  {
    KeyValue symbols = KeyValue.getKeyByName("il_symbols");
    KeyValue backspace = KeyValue.getKeyByName("backspace");
    ArrayList<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    for (KeyboardData.Row r : kw.rows)
    {
      int s = -1, b = -1;
      for (int i = 0; i < r.keys.size(); i++)
      {
        KeyValue t = r.keys.get(i).keys[0];
        if (symbols.equals(t)) s = i;
        else if (backspace.equals(t)) b = i;
      }
      if (s < 0 || b < 0 || r.keys.get(b).width < 1f + SYMBOLS_WIDER)
      {
        rows.add(r);
        continue;
      }
      ArrayList<KeyboardData.Key> ks = new ArrayList<KeyboardData.Key>(r.keys);
      ks.set(s, ks.get(s).withWidth(ks.get(s).width + SYMBOLS_WIDER));
      ks.set(b, ks.get(b).withWidth(ks.get(b).width - SYMBOLS_WIDER));
      rows.add(r.with_keys(ks));
    }
    return kw.with_rows(rows);
  }

  /** Studio (owner, builds 70, 72): a backspace at least 2 keys wide is cut
      in two. The left part, two thirds, is backspace with all its swipes;
      the right third, at the edge, is forward delete, ↓ key repeat on/off,
      ← select all. */
  static final float BACKSPACE_PART = 2f / 3f;

  static KeyboardData split_backspace(KeyboardData kw)
  {
    KeyValue backspace = KeyValue.getKeyByName("backspace");
    KeyValue delete = KeyValue.getKeyByName("delete");
    KeyValue repeat = KeyValue.getKeyByName("studio_toggle_repeat");
    KeyValue select_all = KeyValue.getKeyByName("selectAll");
    ArrayList<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    boolean changed = false;
    for (KeyboardData.Row r : kw.rows)
    {
      ArrayList<KeyboardData.Key> ks = new ArrayList<KeyboardData.Key>();
      for (KeyboardData.Key k : r.keys)
      {
        if (!backspace.equals(k.keys[0]) || k.width < 2f)
        {
          ks.add(k);
          continue;
        }
        float w = Math.round(k.width * BACKSPACE_PART * 100) / 100f;
        ks.add(k.withWidth(w));
        KeyboardData.Key h = k.withWidthAndShift(k.width - w, 0f);
        for (int i = 0; i < h.keys.length; i++)
          h = h.withKeyValue(i, (i == 0) ? delete : (i == 8) ? repeat
              : (i == 5) ? select_all : null);
        ks.add(h);
        changed = true;
      }
      rows.add(changed ? r.with_keys(ks) : r);
    }
    return changed ? kw.with_rows(rows) : kw;
  }

  /** Studio (owner, build 67): ?123 and Shift trade places, on every
      layout, and the symbols pages follow so that the key going back is
      where ?123 was. Ctrl is ?123's ↓. */
  static KeyboardData swap_symbols_and_shift(KeyboardData kw)
  {
    kw = swap_keys(kw, "shift", "il_symbols");
    kw = swap_keys(kw, "il_symbols_more", "switch_text"); // Symbols page.
    kw = swap_keys(kw, "il_symbols", "switch_text"); // More symbols page.
    final KeyValue symbols = KeyValue.getKeyByName("il_symbols");
    final KeyValue ctrl = KeyValue.getKeyByName("ctrl");
    return kw.mapKeys(new KeyboardData.MapKey() {
      public KeyboardData.Key apply(KeyboardData.Key k)
      {
        if (symbols.equals(k.keys[0]) && k.keys[8] == null)
          return k.withKeyValue(8, ctrl);
        return k;
      }
    });
  }

  /** The keys whose taps are [a] and [b] swap everything they hold (not
      their size), if there is one of each. */
  static KeyboardData swap_keys(KeyboardData kw, String a, String b)
  {
    final KeyValue ka = KeyValue.getKeyByName(a);
    final KeyValue kb = KeyValue.getKeyByName(b);
    KeyboardData.Key found_a = null, found_b = null;
    for (KeyboardData.Row r : kw.rows)
      for (KeyboardData.Key k : r.keys)
      {
        if (ka.equals(k.keys[0]))
        {
          if (found_a != null)
            return kw;
          found_a = k;
        }
        else if (kb.equals(k.keys[0]))
        {
          if (found_b != null)
            return kw;
          found_b = k;
        }
      }
    if (found_a == null || found_b == null)
      return kw;
    final KeyboardData.Key fa = found_a, fb = found_b;
    return kw.mapKeys(new KeyboardData.MapKey() {
      public KeyboardData.Key apply(KeyboardData.Key k)
      {
        KeyboardData.Key from = (k == fa) ? fb : (k == fb) ? fa : null;
        if (from == null)
          return k;
        for (int i = 0; i < k.keys.length; i++)
          k = k.withKeyValue(i, from.keys[i]);
        return k;
      }
    });
  }

  /** Studio: row heights from the active [changeheight] module. */
  static KeyboardData scale_rows(KeyboardData kw)
  {
    if (globalConfig.studio == null || globalConfig.studio.rows == null)
      return kw;
    ArrayList<KeyboardData.Row> rows = new ArrayList<KeyboardData.Row>();
    for (int i = 0; i < kw.rows.size(); i++)
      rows.add(kw.rows.get(i).scale_height(globalConfig.studio.row_factor(i)));
    return kw.with_rows(rows);
  }

  /** Handle the numpad layout. The [main_kw] is used to adapt the numpad to
      the main layout's script. */
  public static KeyboardData modify_numpad(KeyboardData kw, KeyboardData main_kw)
  {
    final int map_digit = KeyModifier.modify_numpad_script(main_kw.numpad_script);
    return kw.mapKeys(new KeyboardData.MapKeyValues() {
      public KeyValue apply(KeyValue key, boolean localized)
      {
        switch (key.getKind())
        {
          case Char:
            char prev_c = key.getChar();
            char c = prev_c;
            if (globalConfig.inverse_numpad)
              c = inverse_numpad_char(c);
            if (map_digit != -1)
            {
              KeyValue modified = ComposeKey.apply(map_digit, c);
              if (modified != null) // Was modified by script
                return modified;
            }
            if (prev_c != c) // Was inverted
              return key.withChar(c);
            return key; // Don't fallback into [modify_key]
        }
        return modify_key(key);
      }
    });
  }

  /** Modify the pin entry layout. [main_kw] is used to map the digits into the
      same script. */
  public static KeyboardData modify_pinentry(KeyboardData kw, KeyboardData main_kw)
  {
    KeyboardData.MapKeyValues m = numpad_script_map(main_kw.numpad_script);
    return m == null ? kw : kw.mapKeys(m);
  }

  /** Modify the number row according to [main_kw]'s script. */
  static KeyboardData.Row modify_number_row(KeyboardData.Row row,
      KeyboardData main_kw)
  {
    KeyboardData.MapKeyValues m = numpad_script_map(main_kw.numpad_script);
    return m == null ? row : row.mapKeys(m);
  }

  static KeyboardData.MapKeyValues numpad_script_map(String numpad_script)
  {
    final int map_digit = KeyModifier.modify_numpad_script(numpad_script);
    if (map_digit == -1)
      return null;
    return new KeyboardData.MapKeyValues() {
      public KeyValue apply(KeyValue key, boolean localized)
      {
        KeyValue modified = ComposeKey.apply(map_digit, key);
        return (modified != null) ? modified : key;
      }
    };
  }

  /** Modify keys on the main layout and on the numpad according to the config.
   */
  static KeyValue modify_key(KeyValue orig)
  {
    EditorConfig ec = globalConfig.editor_config;
    switch (orig.getKind())
    {
      case Event:
        switch (orig.getEvent())
        {
          case CHANGE_METHOD_PICKER:
            return globalConfig.change_method_key_replacement;
          case ACTION:
            return ec.action_key_replacement;
          case SWITCH_FORWARD:
            return (globalConfig.layouts.size() > 1) ? orig : null;
          case SWITCH_BACKWARD:
            return (globalConfig.layouts.size() > 2) ? orig : null;
          case SWITCH_VOICE_TYPING:
          case SWITCH_VOICE_TYPING_CHOOSER:
            return globalConfig.shouldOfferVoiceTyping ? orig : null;
        }
        break;
      case Keyevent:
        switch (orig.getKeyevent())
        {
          case KeyEvent.KEYCODE_ENTER:
            if (ec.enter_key_replacement != null)
              return ec.enter_key_replacement;
            break;
        }
        break;
    }
    return orig;
  }

  static char inverse_numpad_char(char c)
  {
    switch (c)
    {
      case '7': return '1';
      case '8': return '2';
      case '9': return '3';
      case '1': return '7';
      case '2': return '8';
      case '3': return '9';
      default: return c;
    }
  }

  public static void init(Config globalConfig_, Resources res)
  {
    globalConfig = globalConfig_;
    try
    {
      number_row_no_symbols = KeyboardData.load_row(res, R.xml.number_row_no_symbols);
      number_row_symbols = KeyboardData.load_row(res, R.xml.number_row);
      bottom_row = KeyboardData.load_row(res, R.xml.bottom_row);
      num_pad = KeyboardData.load_num_pad(res);
      split_middle_column = KeyboardData.load_row(res, R.xml.split_middle_column);
    }
    catch (Exception e)
    {
      throw new RuntimeException(e.getMessage()); // Not recoverable
    }
  }
}
