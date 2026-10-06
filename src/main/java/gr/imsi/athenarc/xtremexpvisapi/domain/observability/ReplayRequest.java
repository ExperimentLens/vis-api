package gr.imsi.athenarc.xtremexpvisapi.domain.observability;

import java.util.Map;

public class ReplayRequest {
  private String observationId;
  private Map<String, Object> overrides;

  public String getObservationId() {
    return observationId;
  }

  public void setObservationId(String observationId) {
    this.observationId = observationId;
  }

  public Map<String, Object> getOverrides() {
    return overrides;
  }

  public void setOverrides(Map<String, Object> overrides) {
    this.overrides = overrides;
  }
}
