package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/** Thin client for a local Ollama server, used to replay a single LLM call with edited input. */
@Component
public class OllamaClient {

  private final RestTemplate restTemplate;
  private final String apiUrl;
  private final String defaultModel;

  public OllamaClient(
      RestTemplateBuilder restTemplateBuilder,
      @Value("${ollama.api.url:http://localhost:11434}") String apiUrl,
      @Value("${ollama.api.model:llama3.2}") String defaultModel) {
    this.restTemplate = restTemplateBuilder.build();
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

  private String resolveModel(String model) {
    if (model == null || model.isBlank()) {
      return defaultModel;
    }
    int slash = model.indexOf('/');
    return slash >= 0 ? model.substring(slash + 1) : model;
  }
}
