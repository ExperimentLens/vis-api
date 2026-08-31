package gr.imsi.athenarc.xtremexpvisapi.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.protobuf.InvalidProtocolBufferException;
import gr.imsi.athenarc.xtremexpvisapi.service.explainability.ExplainabilityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Explainability entry point for LLM runs.
 *
 * <p>LLM runs don't carry the ML artifacts (model.pkl/model.pt, X_test/Y_test/... CSVs) that {@link
 * ExplainabilityController} relies on for feature-level explanations. What they do have is run
 * params (hyperparameters, e.g. temperature, top_p, max_tokens) and metrics, which is enough to
 * train a surrogate model mapping hyperparameters -> metric and explain it with PDP/ALE. This
 * controller is the starting point for LLM explainability; further explanation types (e.g.
 * prompt-level explanations) can be added here as they're built out in the explainability-module.
 */
@RestController
@CrossOrigin
@RequestMapping("/api/llm-explainability")
public class LLMExplainabilityController {

  private static final Logger LOG = LoggerFactory.getLogger(LLMExplainabilityController.class);

  private final ExplainabilityService explainabilityService;

  public LLMExplainabilityController(ExplainabilityService explainabilityService) {
    this.explainabilityService = explainabilityService;
  }

  /**
   * Hyperparameter explainability for LLM runs (pdp / ale). Expected body, e.g.:
   *
   * <pre>{@code
   * {
   *   "explanation_type": "llmHyperparameterExplanation",
   *   "explanation_method": "pdp",
   *   "feature1": "temperature",
   *   "target_metric": "accuracy"
   * }
   * }</pre>
   */
  @PostMapping("/{experimentId}/{runId}")
  public JsonNode getHyperparameterExplanation(
      @RequestBody String explainabilityRequest,
      @PathVariable String experimentId,
      @PathVariable String runId,
      @RequestHeader(value = "Authorization", required = false) String authorization)
      throws JsonProcessingException, InvalidProtocolBufferException {
    LOG.info("Received LLM hyperparameter explainability request: \n{}", explainabilityRequest);

    return explainabilityService.GetLLMHyperparameterExplains(
        explainabilityRequest, experimentId, runId, authorization);
  }
}
