package gr.imsi.athenarc.xtremexpvisapi.controller;

import gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation.ConversationModels.Conversation;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation.ConversationModels.EvaluateRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.conversation.ConversationModels.EvaluateResponse;
import gr.imsi.athenarc.xtremexpvisapi.service.observability.ConversationEvalService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** A run's multi-turn conversation (its Langfuse session) and conversation-level evals on it. */
@RestController
@RequestMapping("/api/observability/sessions/{sessionId}/conversation")
public class ConversationController {

  private final ConversationEvalService conversationEvalService;

  public ConversationController(ConversationEvalService conversationEvalService) {
    this.conversationEvalService = conversationEvalService;
  }

  @GetMapping
  @Operation(
      summary = "The run's conversation",
      description =
          "Turns in order (from the SDK's turn spans, or one trace per turn), the latest"
              + " conversation-level scores, and the metrics that can be run. No turns means the run"
              + " is not a multi-turn conversation.")
  public ResponseEntity<Conversation> getConversation(@PathVariable String sessionId) {
    return ResponseEntity.ok(conversationEvalService.getConversation(sessionId));
  }

  @PostMapping("/evaluate")
  @Operation(
      summary = "Evaluate the whole conversation with an LLM judge",
      description =
          "Scores the transcript on each metric (0..1, higher is better), logs eval_<metric> on the"
              + " MLflow run and a judge_<metric> verdict on the trace, as the SDK does. A metric that"
              + " fails (bad JSON, timeout) is listed in `failed` without stopping the rest.")
  public ResponseEntity<EvaluateResponse> evaluate(
      @PathVariable String sessionId, @RequestBody EvaluateRequest request) {
    try {
      return ResponseEntity.ok(conversationEvalService.evaluate(sessionId, request));
    } catch (IllegalArgumentException e) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
    }
  }
}
