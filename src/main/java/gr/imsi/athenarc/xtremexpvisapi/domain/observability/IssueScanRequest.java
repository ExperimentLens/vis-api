package gr.imsi.athenarc.xtremexpvisapi.domain.observability;

import java.util.List;

/**
 * Request to scan a batch of traces for issues using a local Ollama model, following MLflow's
 * CLEARS framework (Correctness, Latency, Execution, Adherence, Relevance, Safety).
 */
public class IssueScanRequest {
  private List<IssueScanTraceInput> traces;
  private List<String> categories;
  private String model;

  public List<IssueScanTraceInput> getTraces() {
    return traces;
  }

  public void setTraces(List<IssueScanTraceInput> traces) {
    this.traces = traces;
  }

  public List<String> getCategories() {
    return categories;
  }

  public void setCategories(List<String> categories) {
    this.categories = categories;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }
}
