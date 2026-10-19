package gr.imsi.athenarc.xtremexpvisapi.service.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LangfuseDatasetServiceTest {

  // Expected values come from the SDK: uuid.uuid5(uuid.NAMESPACE_URL, f"experimentlens-dataset:{name}:{case}").
  @Test
  void itemIdMatchesTheSdk() {
    assertEquals(
        "1a4d825d-2dfb-59de-a994-c8150e71c8f7",
        LangfuseDatasetService.sdkItemId("capitals-golden", "br"));
  }

  @Test
  void itemIdMatchesTheSdkForNonAsciiNames() {
    assertEquals(
        "e498b865-ab5e-5c9b-9e41-30ff105572db",
        LangfuseDatasetService.sdkItemId("Ελληνικά set", "q 1"));
  }
}
