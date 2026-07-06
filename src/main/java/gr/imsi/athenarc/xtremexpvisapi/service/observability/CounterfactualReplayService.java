package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import gr.imsi.athenarc.xtremexpvisapi.domain.observability.Observation;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ReplayRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.ReplayResult;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.TraceDetail;
import java.util.Date;
import java.util.Map;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * Replays a single GENERATION observation from a trace with an edited input, so a user can see
 * what the agent would have proposed/decided had the prompt been different. This is a reasoning
 * counterfactual only: it re-runs the LLM call, not the underlying training/evaluation code, so
 * any metrics quoted in the counterfactual output are the model's own claims, not re-measured.
 */
@Service
public class CounterfactualReplayService {

  private final ObservabilityServiceFactory observabilityServiceFactory;
  private final OllamaClient ollamaClient;

  public CounterfactualReplayService(
      ObservabilityServiceFactory observabilityServiceFactory, OllamaClient ollamaClient) {
    this.observabilityServiceFactory = observabilityServiceFactory;
    this.ollamaClient = ollamaClient;
  }

  public ReplayResult runCounterfactual(String traceId, ReplayRequest request) {
    TraceDetail trace = observabilityServiceFactory.getObservabilityService().getTrace(traceId);

    Observation target =
        trace.getObservations().stream()
            .filter(o -> o.getId().equals(request.getObservationId()))
            .findFirst()
            .orElseThrow(
                () ->
                    new NoSuchElementException(
                        "Observation "
                            + request.getObservationId()
                            + " not found in trace "
                            + traceId));

    if (target.getModel() == null || target.getModel().isBlank()) {
      throw new IllegalArgumentException(
          "Observation "
              + target.getId()
              + " ("
              + target.getType()
              + ") has no model attached; only GENERATION observations can be replayed");
    }

    Object newInput = InputMerger.merge(target.getInput(), request.getOverrides());
    String prompt = extractPrompt(newInput);

    String newOutput = ollamaClient.generate(prompt, target.getModel());

    double diffRatio =
        DiffUtils.tokenDiffRatio(String.valueOf(target.getOutput()), newOutput);

    ReplayResult result = new ReplayResult();
    result.setTraceId(traceId);
    result.setObservationId(target.getId());
    result.setOriginalInput(target.getInput());
    result.setNewInput(newInput);
    result.setOriginalOutput(target.getOutput());
    result.setNewOutput(newOutput);
    result.setDiffRatio(diffRatio);
    result.setCreatedAt(new Date());
    return result;
  }

  @SuppressWarnings("unchecked")
  private String extractPrompt(Object input) {
    if (input instanceof String) {
      return (String) input;
    }
    if (input instanceof Map) {
      Object prompt = ((Map<String, Object>) input).get("prompt");
      if (prompt != null) {
        return String.valueOf(prompt);
      }
    }
    throw new IllegalArgumentException(
        "Could not find a 'prompt' string in the (merged) observation input; "
            + "supply an override for the 'prompt' key or '_raw' to replace the input wholesale");
  }
}
