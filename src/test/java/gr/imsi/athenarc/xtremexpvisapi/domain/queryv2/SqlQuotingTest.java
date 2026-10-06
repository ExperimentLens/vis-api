package gr.imsi.athenarc.xtremexpvisapi.domain.queryv2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gr.imsi.athenarc.xtremexpvisapi.domain.queryv2.params.filter.AbstractFilter;
import gr.imsi.athenarc.xtremexpvisapi.domain.queryv2.params.filter.EqualsFilter;
import gr.imsi.athenarc.xtremexpvisapi.domain.queryv2.params.filter.StringFilter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Runs filters against a real DuckDB, the way DataServiceV2 does, to prove quoting holds. */
class SqlQuotingTest {

  @TempDir Path tempDir;

  @Test
  void plainNamesStayBareAndEverythingElseIsQuoted() {
    assertEquals("accuracy", SqlQuoting.identifier("accuracy"));
    assertEquals("\"mean radius\"", SqlQuoting.identifier("mean radius"));
    assertEquals("\"a\"\"b\"", SqlQuoting.identifier("a\"b"));
    assertEquals("'it''s'", SqlQuoting.literal("it's"));
  }

  @Test
  void filtersMatchNormalValuesIncludingAwkwardColumnNames() throws Exception {
    Path csv = writeCsv("people.csv");

    assertEquals(1, count(csv, equalsFilter("name", "alice")));
    assertEquals(1, count(csv, equalsFilter("mean radius", 2.5)));
    assertEquals(1, count(csv, equalsFilter("odd\"col", "x")));
    assertEquals(2, count(csv, stringFilter("name", "b")));
  }

  @Test
  void aValueCannotWidenTheFilter() throws Exception {
    Path csv = writeCsv("people.csv");

    assertEquals(0, count(csv, equalsFilter("name", "alice' OR '1'='1")));
    assertEquals(0, count(csv, stringFilter("name", "%' OR '1'='1")));
  }

  @Test
  void aColumnNameIsAlwaysTreatedAsAName() throws Exception {
    Path csv = writeCsv("people.csv");

    // Unquoted, this used to be spliced in as an expression that matches every row.
    SQLException error =
        assertThrows(SQLException.class, () -> count(csv, equalsFilter("name=name)OR(1=1", "z")));
    assertTrue(error.getMessage().contains("name=name)OR(1=1"), error.getMessage());
  }

  @Test
  void aFilePathWithAQuoteStaysOneLiteral() throws Exception {
    Path csv = writeCsv("o'brien.csv");

    assertEquals(3, count(csv, null));
  }

  private Path writeCsv(String fileName) throws Exception {
    Path csv = tempDir.resolve(fileName);
    Files.writeString(csv, "name,mean radius,\"odd\"\"col\"\nalice,2.5,x\nbob,3.1,y\nbobby,4.0,z\n");
    return csv;
  }

  private static EqualsFilter equalsFilter(String column, Object value) {
    EqualsFilter filter = new EqualsFilter();
    filter.setColumn(column);
    filter.setValue(value);
    return filter;
  }

  private static StringFilter stringFilter(String column, String value) {
    StringFilter filter = new StringFilter();
    filter.setColumn(column);
    filter.setValue(value);
    return filter;
  }

  private static long count(Path csv, AbstractFilter filter) throws SQLException {
    String sql =
        "SELECT COUNT(*) FROM read_csv("
            + SqlQuoting.literal(csv.toString())
            + ")"
            + (filter == null ? "" : " WHERE " + filter.toSql());
    try (Connection connection = DriverManager.getConnection("jdbc:duckdb:");
        Statement statement = connection.createStatement();
        ResultSet result = statement.executeQuery(sql)) {
      result.next();
      return result.getLong(1);
    }
  }
}
