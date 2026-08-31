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
 * On-demand "Explain this answer" entry point for RAG runs: reads the run's {@code
 * predictions.json} example at {@code exampleId} and proxies to the explainability-module's {@code
 * llmExplanation}/{@code rag_attribution} handler (llmSHAP chunk attribution + a counterfactual
 * check on the top-attributed chunk, run against a local Ollama model). Cached under {@code
 * ragExplanations} so re-clicking "Explain" on the same example doesn't recompute.
 */
@RestController
@CrossOrigin
@RequestMapping("/api/rag-explainability")
public class RagExplainabilityController {

  private static final Logger LOG = LoggerFactory.getLogger(RagExplainabilityController.class);

  private final ExplainabilityService explainabilityService;

  public RagExplainabilityController(ExplainabilityService explainabilityService) {
    this.explainabilityService = explainabilityService;
  }

  /**
   * Optional body to override defaults, e.g.:
   *
   * <pre>{@code
   * { "run_counterfactual": false }
   * }</pre>
   */
  @PostMapping("/{experimentId}/{runId}/{exampleId}")
  public JsonNode explainExample(
      @RequestBody(required = false) String explainabilityRequest,
      @PathVariable String experimentId,
      @PathVariable String runId,
      @PathVariable int exampleId,
      @RequestHeader(value = "Authorization", required = false) String authorization)
      throws JsonProcessingException, InvalidProtocolBufferException {
    LOG.info(
        "Received RAG chunk-attribution explainability request for run {} example {}",
        runId,
        exampleId);

    return explainabilityService.GetRagExplains(
        explainabilityRequest == null ? "{}" : explainabilityRequest,
        experimentId,
        runId,
        exampleId,
        authorization);
  }
}
