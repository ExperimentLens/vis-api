package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Merges user-supplied overrides into an observation's original input for a counterfactual replay.
 */
public final class InputMerger {

  private InputMerger() {}

  @SuppressWarnings("unchecked")
  public static Object merge(Object original, Map<String, Object> overrides) {
    if (overrides == null || overrides.isEmpty()) {
      return original;
    }
    if (original instanceof Map) {
      Map<String, Object> merged = new LinkedHashMap<>((Map<String, Object>) original);
      merged.putAll(overrides);
      return merged;
    }
    if (overrides.containsKey("_raw")) {
      return overrides.get("_raw");
    }
    throw new IllegalArgumentException(
        "Observation input is not a map; supply overrides under key '_raw' to replace it wholesale");
  }
}
