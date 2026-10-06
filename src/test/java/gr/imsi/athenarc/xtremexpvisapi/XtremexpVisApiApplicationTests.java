package gr.imsi.athenarc.xtremexpvisapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Boots the full application context. The application normally gets these values from
 * .env (loaded in main(), which tests bypass), so the test supplies harmless stand-ins.
 */
@SpringBootTest(
    properties = {
      "ZENOH_USERNAME=test",
      "ZENOH_PASSWORD=test",
      "EXTREMEXP_OUTPUT_DIR=target/test-output",
      "EXTREMEXP_WORKFLOWS_API_URL=http://localhost:0",
      "EXTREMEXP_WORKFLOWS_API_KEY=test",
      "EXTREMEXP_EXPERIMENTATION_API_URL=http://localhost:0",
      "EXTREMEXP_EXPERIMENTATION_API_KEY=test",
      "MLFLOW_TRACKING_TOKEN=",
      "LANGFUSE_PUBLIC_KEY=test",
      "LANGFUSE_SECRET_KEY=test",
    })
class XtremexpVisApiApplicationTests {

  @Test
  void contextLoads() {}
}
