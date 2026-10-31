package juloo.keyboard2.studio;

import android.content.SharedPreferences;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** In-memory [SharedPreferences], enough for [ModuleStore]. */
final class FakePrefs implements SharedPreferences
{
  final Map<String, Object> _m = new HashMap<String, Object>();

  public Map<String, ?> getAll() { return _m; }
  public String getString(String k, String d) { return _m.containsKey(k) ? (String)_m.get(k) : d; }
  public Set<String> getStringSet(String k, Set<String> d) { return d; }
  public int getInt(String k, int d) { return d; }
  public long getLong(String k, long d) { return d; }
  public float getFloat(String k, float d) { return d; }
  public boolean getBoolean(String k, boolean d) { return d; }
  public boolean contains(String k) { return _m.containsKey(k); }
  public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}
  public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {}

  public Editor edit()
  {
    return new Editor() {
      final Map<String, Object> put = new HashMap<String, Object>();
      final java.util.List<String> removed = new java.util.ArrayList<String>();
      public Editor putString(String k, String v) { put.put(k, v); return this; }
      public Editor putStringSet(String k, Set<String> v) { put.put(k, v); return this; }
      public Editor putInt(String k, int v) { put.put(k, v); return this; }
      public Editor putLong(String k, long v) { put.put(k, v); return this; }
      public Editor putFloat(String k, float v) { put.put(k, v); return this; }
      public Editor putBoolean(String k, boolean v) { put.put(k, v); return this; }
      public Editor remove(String k) { removed.add(k); return this; }
      public Editor clear() { _m.clear(); return this; }
      public boolean commit() { apply(); return true; }
      public void apply()
      {
        for (String k : removed) _m.remove(k);
        _m.putAll(put);
      }
    };
  }
}
