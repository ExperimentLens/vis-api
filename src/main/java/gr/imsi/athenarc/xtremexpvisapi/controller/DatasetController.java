package gr.imsi.athenarc.xtremexpvisapi.controller;

import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.Dataset;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetCreateRequest;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetDetail;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetItem;
import gr.imsi.athenarc.xtremexpvisapi.domain.observability.dataset.DatasetModels.DatasetItemRequest;
import gr.imsi.athenarc.xtremexpvisapi.service.observability.DatasetService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.server.ResponseStatusException;

/** Test datasets: list and create them, add cases (from traces or by hand), edit and archive cases. */
@RestController
@RequestMapping("/api/observability/datasets")
public class DatasetController {

  // Langfuse errors look like {"message":"Dataset not found","error":"LangfuseNotFoundError"}.
  private static final Pattern MESSAGE = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]{1,300})\"");

  private final DatasetService datasetService;

  public DatasetController(DatasetService datasetService) {
    this.datasetService = datasetService;
  }

  @GetMapping
  @Operation(summary = "List datasets with their item counts")
  public ResponseEntity<List<Dataset>> listDatasets() {
    return ResponseEntity.ok(call(datasetService::listDatasets));
  }

  @PostMapping
  @Operation(summary = "Create a dataset")
  public ResponseEntity<Dataset> createDataset(@RequestBody DatasetCreateRequest request) {
    if (request.name() == null || request.name().isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A dataset needs a name.");
    }
    return ResponseEntity.ok(call(() -> datasetService.createDataset(request)));
  }

  @GetMapping("/by-trace/{traceId}")
  @Operation(summary = "Datasets that already contain a case built from this trace")
  public ResponseEntity<List<String>> datasetsContainingTrace(@PathVariable String traceId) {
    return ResponseEntity.ok(call(() -> datasetService.datasetsContainingTrace(traceId)));
  }

  @GetMapping("/{name}")
  @Operation(summary = "A dataset with its items (active and archived) and the runs recorded on it")
  public ResponseEntity<DatasetDetail> getDataset(@PathVariable String name) {
    return ResponseEntity.ok(call(() -> datasetService.getDataset(name)));
  }

  @PostMapping("/{name}/items")
  @Operation(
      summary = "Add a case",
      description =
          "Set sourceTraceId to record which trace the case came from. With caseId set, the item"
              + " gets the id the SDK's upload_dataset would give that case, so uploading it again"
              + " from code updates it instead of adding a duplicate.")
  public ResponseEntity<DatasetItem> addItem(
      @PathVariable String name, @RequestBody DatasetItemRequest request) {
    return ResponseEntity.ok(call(() -> datasetService.upsertItem(name, null, request)));
  }

  @PutMapping("/{name}/items/{itemId}")
  @Operation(summary = "Edit a case, or archive / restore it with status ARCHIVED / ACTIVE")
  public ResponseEntity<DatasetItem> updateItem(
      @PathVariable String name,
      @PathVariable String itemId,
      @RequestBody DatasetItemRequest request) {
    return ResponseEntity.ok(call(() -> datasetService.upsertItem(name, itemId, request)));
  }

  /** Passes the trace store's own status and message through instead of a bare 500. */
  private static <T> T call(Supplier<T> action) {
    try {
      return action.get();
    } catch (HttpStatusCodeException e) {
      Matcher message = MESSAGE.matcher(e.getResponseBodyAsString());
      throw new ResponseStatusException(
          e.getStatusCode(), message.find() ? message.group(1) : e.getStatusText());
    } catch (ResourceAccessException e) {
      throw new ResponseStatusException(
          HttpStatus.BAD_GATEWAY, "Could not reach Langfuse: " + e.getMessage());
    }
  }
}
