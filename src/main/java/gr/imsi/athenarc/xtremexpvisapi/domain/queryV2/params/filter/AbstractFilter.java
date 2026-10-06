package gr.imsi.athenarc.xtremexpvisapi.domain.queryv2.params.filter;

import gr.imsi.athenarc.xtremexpvisapi.domain.queryv2.SqlQuoting;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Data;

@Data
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(value = EqualsFilter.class, name = "equals"),
  @JsonSubTypes.Type(value = RangeFilter.class, name = "range"),
  @JsonSubTypes.Type(value = InequalityFilter.class, name = "inequality"),
  @JsonSubTypes.Type(value = StringFilter.class, name = "string")
})
public abstract class AbstractFilter {

  private String column;
  private String type;

  public AbstractFilter() {}

  public AbstractFilter(String column) {
    this.column = column;
  }

  // Abstract method for SQL generation
  public abstract String toSql();

  /** Strings become quoted literals; numbers and booleans are rendered as-is. */
  protected String escapeSqlValue(Object value) {
    if (value == null) return "NULL";
    if (value instanceof String) {
      return SqlQuoting.literal(value.toString());
    }
    return value.toString();
  }

  /** The filter's column as a safely quoted identifier. */
  protected String columnPreparation(Object column) {
    return SqlQuoting.identifier(column.toString());
  }
}
