package gr.imsi.athenarc.xtremexpvisapi.domain.observability;

import java.util.Date;

public class Score {
  private String id;
  private String traceId;
  private String name;
  // Nullable: a CATEGORICAL score carries its value in stringValue instead,
  // and Langfuse returns value=null for those — a primitive double can't
  // hold that.
  private Double value;
  private String stringValue;
  private String dataType;
  private String observationId;
  private Date timestamp;
  private String comment;

  // Getters and Setters
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
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

  public String getDataType() {
    return dataType;
  }

  public void setDataType(String dataType) {
    this.dataType = dataType;
  }

  public String getObservationId() {
    return observationId;
  }

  public void setObservationId(String observationId) {
    this.observationId = observationId;
  }

  public Date getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Date timestamp) {
    this.timestamp = timestamp;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }
}
