package gr.imsi.athenarc.xtremexpvisapi.domain.observability;

/**
 * Request body for creating a human annotation (Langfuse score) on a trace or observation.
 * Exactly one of value/stringValue is meaningful, chosen by dataType: NUMERIC/BOOLEAN carry a
 * numeric value (BOOLEAN as 1/0), CATEGORICAL carries stringValue.
 */
public class ScoreCreateRequest {
  private String traceId;
  private String observationId;
  private String name;
  private String dataType;
  private Double value;
  private String stringValue;
  private String comment;

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getObservationId() {
    return observationId;
  }

  public void setObservationId(String observationId) {
    this.observationId = observationId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDataType() {
    return dataType;
  }

  public void setDataType(String dataType) {
    this.dataType = dataType;
  }

  public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

  public String getStringValue() {
    return stringValue;
  }

  public void setStringValue(String stringValue) {
    this.stringValue = stringValue;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }
}
