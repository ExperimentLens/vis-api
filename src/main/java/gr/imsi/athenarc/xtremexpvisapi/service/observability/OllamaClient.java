package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Thin client for a local Ollama server, used to replay a single LLM call with edited input,
 * and to run a local model as a judge over a trace (issue detection). */
@Component
public class OllamaClient {

  private final RestTemplate restTemplate;
  private final String apiUrl;
  private final String defaultModel;

  public OllamaClient(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${ollama.api.url:http://localhost:11434}") String apiUrl,
      @Value("${ollama.api.model:llama3.2}") String defaultModel) {
    // Local models can take a while, especially on CPU — a generous read
    // timeout keeps a single slow trace from looking like a network failure.
    this.restTemplate =
        restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(120))
            .build();
    this.apiUrl = apiUrl;
    this.defaultModel = defaultModel;
  }

  /**
   * Sends a single non-streaming completion request to Ollama.
   *
   * @param prompt the full prompt text to send
   * @param model model tag as recorded on the observation (e.g. "ollama/llama3.2"); a leading
   *     "ollama/" prefix is stripped since Ollama itself only knows the bare tag
   * @return the raw text response from the model
   */
  public String generate(String prompt, String model) {
    String resolvedModel = resolveModel(model);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", resolvedModel);
    body.put("prompt", prompt);
    body.put("stream", false);

    @SuppressWarnings("unchecked")
    Map<String, Object> response =
        restTemplate.postForObject(apiUrl + "/api/generate", body, Map.class);

    if (response == null || !response.containsKey("response")) {
      throw new IllegalStateException("Ollama returned no response for model " + resolvedModel);
    }
    return String.valueOf(response.get("response"));
  }

  /**
   * Sends a single non-streaming chat request to Ollama with a system + user message, asking for
   * a strict JSON response (Ollama's {@code format: "json"} mode). Used for judge-style calls
   * (issue detection) where the reply needs to be parsed as structured data, not read as prose.
   *
   * @return the raw JSON string from the model's reply (the caller parses it)
   */
  public String chatJson(String systemPrompt, String userPrompt, String model) {
    String resolvedModel = resolveModel(model);

    Map<String, Object> systemMessage = new LinkedHashMap<>();
    systemMessage.put("role", "system");
    systemMessage.put("content", systemPrompt);

    Map<String, Object> userMessage = new LinkedHashMap<>();
    userMessage.put("role", "user");
    userMessage.put("content", userPrompt);

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("model", resolvedModel);
    body.put("messages", List.of(systemMessage, userMessage));
    body.put("stream", false);
    body.put("format", "json");

    @SuppressWarnings("unchecked")
    Map<String, Object> response = restTemplate.postForObject(apiUrl + "/api/chat", body, Map.class);

    if (response == null || !(response.get("message") instanceof Map)) {
      throw new IllegalStateException("Ollama returned no chat message for model " + resolvedModel);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> message = (Map<String, Object>) response.get("message");

    Object content = message.get("content");
    if (content == null) {
      throw new IllegalStateException("Ollama chat message had no content for model " + resolvedModel);
    }
    return String.valueOf(content);
  }

  private String resolveModel(String model) {
    if (model == null || model.isBlank()) {
      return defaultModel;
    }
    int slash = model.indexOf('/');
    return slash >= 0 ? model.substring(slash + 1) : model;
  }
}
