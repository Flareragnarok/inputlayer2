package juloo.keyboard2.studio;

import java.util.Iterator;
import org.json.JSONException;
import org.json.JSONObject;

/** A Studio module: a small JSON document of settings, never code. Every
    module has the fields [module] (its type), [name] and [version]; the other
    fields depend on the type. */
public final class Module
{
  public static final String[] TYPES = { "changeheight", "quicksettings", "font", "format", "keymap", "layout", "selection", "snippets", "spec", "theme", "vert" };
  /** Bigger files are refused on import. */
  public static final int MAX_JSON_LENGTH = 64 * 1024;

  public final String type;
  public final String name;
  public final int version;
  /** The whole document, including the three fields above. */
  public final JSONObject json;

  Module(JSONObject json) throws InvalidModule
  {
    // il-007: documents saved or exported under the old names.
    try { Renames.doc(json); } catch (org.json.JSONException _e) {}
    this.json = json;
    type = json.optString("module", "");
    name = json.optString("name", "");
    version = json.optInt("version", 1);
    if (!is_known_type(type))
      throw new InvalidModule("Unknown module type \"" + type + "\"");
    if (!is_valid_name(name))
      throw new InvalidModule("Invalid module name \"" + name + "\"");
  }

  public static Module parse(String s) throws InvalidModule
  {
    if (s.length() > MAX_JSON_LENGTH)
      throw new InvalidModule("File is too large");
    try
    {
      return new Module(new JSONObject(s));
    }
    catch (JSONException e)
    {
      throw new InvalidModule("Not valid JSON: " + e.getMessage());
    }
  }

  /** A new module of type [type] with the given type-specific fields. */
  public static Module create(String type, String name, JSONObject fields)
    throws InvalidModule
  {
    try
    {
      JSONObject j = new JSONObject();
      j.put("module", type);
      j.put("name", name);
      j.put("version", 1);
      Iterator<String> it = fields.keys();
      while (it.hasNext())
      {
        String k = it.next();
        if (!j.has(k))
          j.put(k, fields.get(k));
      }
      return new Module(j);
    }
    catch (JSONException e)
    {
      throw new InvalidModule(e.getMessage());
    }
  }

  public String to_json()
  {
    try
    {
      return json.toString(2);
    }
    catch (JSONException _e)
    {
      return json.toString();
    }
  }

  /** Suggested file name for exporting. */
  public String file_name()
  {
    return type + "-" + name.replace(' ', '_') + ".json";
  }

  public static boolean is_known_type(String t)
  {
    for (String k : TYPES)
      if (k.equals(t))
        return true;
    return false;
  }

  /** Names are shown in lists and used in file names. */
  public static boolean is_valid_name(String n)
  {
    return n != null && n.matches("[A-Za-z0-9][A-Za-z0-9 _.-]{0,39}");
  }

  public static final class InvalidModule extends Exception
  {
    public InvalidModule(String msg) { super(msg); }
  }
}
