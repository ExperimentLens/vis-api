package gr.imsi.athenarc.xtremexpvisapi.domain.queryv2;

import java.util.regex.Pattern;

/**
 * Quoting for request values spliced into DuckDB SQL. Column names and file paths arrive in
 * client requests, so every identifier and string literal must pass through here; otherwise a
 * crafted column name such as {@code a=a)UNION SELECT ...} becomes part of the query.
 */
public final class SqlQuoting {

  private static final Pattern PLAIN_IDENTIFIER = Pattern.compile("[a-zA-Z_][a-zA-Z0-9_]*");

  private SqlQuoting() {}

  /**
   * A column name as a SQL identifier. Plain names stay bare, so the generated SQL is unchanged
   * for them; anything else is double-quoted with embedded quotes doubled, so it can't end the
   * identifier early. (DuckDB matches identifiers case-insensitively either way.)
   */
  public static String identifier(String name) {
    if (PLAIN_IDENTIFIER.matcher(name).matches()) {
      return name;
    }
    return "\"" + name.replace("\"", "\"\"") + "\"";
  }

  /** A string as a single-quoted SQL literal, with embedded quotes doubled. */
  public static String literal(String value) {
    return "'" + value.replace("'", "''") + "'";
  }
}
