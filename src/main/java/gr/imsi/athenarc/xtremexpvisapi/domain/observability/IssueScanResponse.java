package gr.imsi.athenarc.xtremexpvisapi.domain.observability;

import java.util.List;

public class IssueScanResponse {
  private List<DetectedIssue> issues;
  private List<String> failedTraceIds;
  private int scannedCount;

  public List<DetectedIssue> getIssues() {
    return issues;
  }

  public void setIssues(List<DetectedIssue> issues) {
    this.issues = issues;
  }

  public List<String> getFailedTraceIds() {
    return failedTraceIds;
  }

  public void setFailedTraceIds(List<String> failedTraceIds) {
    this.failedTraceIds = failedTraceIds;
  }

  public int getScannedCount() {
    return scannedCount;
  }

  public void setScannedCount(int scannedCount) {
    this.scannedCount = scannedCount;
  }
}
