package gr.imsi.athenarc.xtremexpvisapi.domain.observability;

import java.util.Date;

public class ReplayResult {
  private String traceId;
  private String observationId;
  private Object originalInput;
  private Object newInput;
  private Object originalOutput;
  private Object newOutput;
  private double diffRatio;
  private Date createdAt;

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

  public Object getOriginalInput() {
    return originalInput;
  }

  public void setOriginalInput(Object originalInput) {
    this.originalInput = originalInput;
  }

  public Object getNewInput() {
    return newInput;
  }

  public void setNewInput(Object newInput) {
    this.newInput = newInput;
  }

  public Object getOriginalOutput() {
    return originalOutput;
  }

  public void setOriginalOutput(Object originalOutput) {
    this.originalOutput = originalOutput;
  }

  public Object getNewOutput() {
    return newOutput;
  }

  public void setNewOutput(Object newOutput) {
    this.newOutput = newOutput;
  }

  public double getDiffRatio() {
    return diffRatio;
  }

  public void setDiffRatio(double diffRatio) {
    this.diffRatio = diffRatio;
  }

  public Date getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(Date createdAt) {
    this.createdAt = createdAt;
  }
}
