package gr.imsi.athenarc.xtremexpvisapi.domain.observability;

import java.util.List;

public class ScoresResponse {
  private List<Score> data;
  private Meta meta;

  // Getters and Setters
  public List<Score> getData() {
    return data;
  }

  public void setData(List<Score> data) {
    this.data = data;
  }

  public Meta getMeta() {
    return meta;
  }

  public void setMeta(Meta meta) {
    this.meta = meta;
  }
}
