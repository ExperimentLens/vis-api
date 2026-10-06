package gr.imsi.athenarc.xtremexpvisapi.domain.observability;

/** One issue the judge model flagged on a trace. */
public class DetectedIssue {
  private String traceId;
  private String category;
  private String severity;
  private String explanation;

  public String getTraceId() {
    return traceId;
  }

  public void setTraceId(String traceId) {
    this.traceId = traceId;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getSeverity() {
    return severity;
  }

  public void setSeverity(String severity) {
    this.severity = severity;
  }

  public String getExplanation() {
    return explanation;
  }

  public void setExplanation(String explanation) {
    this.explanation = explanation;
  }
}
