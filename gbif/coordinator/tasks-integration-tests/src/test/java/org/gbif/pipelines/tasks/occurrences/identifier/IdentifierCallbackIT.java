package org.gbif.pipelines.tasks.occurrences.identifier;

import static org.gbif.api.model.pipelines.StepRunner.DISTRIBUTED;
import static org.gbif.api.model.pipelines.StepType.VERBATIM_TO_IDENTIFIER;
import static org.gbif.api.model.pipelines.StepType.VERBATIM_TO_INTERPRETED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.UUID;
import org.apache.http.impl.client.CloseableHttpClient;
import org.gbif.api.vocabulary.EndpointType;
import org.gbif.common.messaging.api.messages.PipelinesVerbatimMessage;
import org.gbif.common.messaging.api.messages.PipelinesVerbatimMessage.ValidationResult;
import org.gbif.crawler.constants.PipelinesNodePaths.Fn;
import org.gbif.pipelines.common.PipelinesVariables.Pipeline.Interpretation.RecordType;
import org.gbif.pipelines.tasks.CloseableHttpClientStub;
import org.gbif.pipelines.tasks.MessagePublisherStub;
import org.gbif.pipelines.tasks.resources.CuratorServer;
import org.gbif.registry.ws.client.DatasetClient;
import org.gbif.registry.ws.client.pipelines.PipelinesHistoryClient;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class IdentifierCallbackIT {

  @ClassRule public static final CuratorServer CURATOR_SERVER = CuratorServer.getInstance();
  private static final String INTERPRETED_LABEL = VERBATIM_TO_IDENTIFIER.getLabel();
  private static final String DATASET_UUID = "9bed66b3-4caa-42bb-9c93-71d7ba109dad";
  private static final long EXECUTION_ID = 1L;
  private static final MessagePublisherStub PUBLISHER = MessagePublisherStub.create();
  @Mock private static PipelinesHistoryClient historyClient;
  @Mock private static DatasetClient datasetClient;
  @Mock private static CloseableHttpClient httpClient;

  @After
  public void after() {
    PUBLISHER.close();
  }

  @Test
  public void testInvalidMessageRunner() {

    // State
    IdentifierConfiguration config = new IdentifierConfiguration();
    config.stepConfig.repositoryPath = getClass().getResource("/dataset/occurrence/").getFile();
    config.pipelinesConfig = "pipelines.yaml";

    IdentifierCallback callback =
        IdentifierCallback.builder()
            .config(config)
            .publisher(PUBLISHER)
            .curator(CURATOR_SERVER.getCurator())
            .historyClient(historyClient)
            .httpClient(httpClient)
            .datasetClient(datasetClient)
            .build();

    UUID uuid = UUID.fromString(DATASET_UUID);
    int attempt = 60;
    String crawlId = DATASET_UUID;
    ValidationResult validationResult = new ValidationResult(true, true, false, 0L, null);

    PipelinesVerbatimMessage message =
        new PipelinesVerbatimMessage(
            uuid,
            attempt,
            Collections.singleton(RecordType.ALL.name()),
            Collections.singleton(VERBATIM_TO_INTERPRETED.name()),
            DISTRIBUTED.name(),
            EndpointType.DWC_ARCHIVE,
            null,
            validationResult,
            null,
            EXECUTION_ID,
            null);

    // When
    callback.handleMessage(message);

    // Should
    Path path =
        Paths.get(config.stepConfig.repositoryPath + DATASET_UUID + "/" + attempt + "/interpreted");
    assertFalse(path.toFile().exists());
    assertFalse(CURATOR_SERVER.checkExists(crawlId, INTERPRETED_LABEL));
    assertFalse(
        CURATOR_SERVER.checkExists(crawlId, Fn.SUCCESSFUL_MESSAGE.apply(INTERPRETED_LABEL)));
    assertFalse(CURATOR_SERVER.checkExists(crawlId, Fn.MQ_CLASS_NAME.apply(INTERPRETED_LABEL)));
    assertFalse(CURATOR_SERVER.checkExists(crawlId, Fn.MQ_MESSAGE.apply(INTERPRETED_LABEL)));
    assertEquals(0, PUBLISHER.getMessages().size());
  }

  @Test
  public void testInvalidChildSystemProcess() {

    // State
    IdentifierConfiguration config = new IdentifierConfiguration();
    config.stepConfig.repositoryPath = getClass().getResource("/dataset/occurrence/").getFile();
    config.pipelinesConfig = "pipelines.yaml";
    config.stepConfig.coreSiteConfig = "";
    config.stepConfig.hdfsSiteConfig = "";

    config.sparkConfig.recordsPerThread = 100000;
    config.sparkConfig.parallelismMin = 10;
    config.sparkConfig.parallelismMax = 100;
    config.sparkConfig.memoryOverhead = 1280;
    config.sparkConfig.executorMemoryGbMin = 4;
    config.sparkConfig.executorMemoryGbMax = 12;
    config.sparkConfig.executorCores = 5;
    config.sparkConfig.executorNumbersMin = 6;
    config.sparkConfig.executorNumbersMax = 10;
    config.sparkConfig.driverMemory = "1G";

    config.distributedConfig.deployMode = "cluster";
    config.distributedConfig.mainClass =
        "org.gbif.pipelines.ingest.pipelines.VerbatimToInterpretedPipeline";
    config.distributedConfig.jarPath = "a://b/a/c/ingest-gbif.jar";

    CloseableHttpClient closeableHttpClient = new CloseableHttpClientStub(200, "[]");

    IdentifierCallback callback =
        IdentifierCallback.builder()
            .config(config)
            .publisher(PUBLISHER)
            .curator(CURATOR_SERVER.getCurator())
            .historyClient(historyClient)
            .httpClient(closeableHttpClient)
            .datasetClient(datasetClient)
            .build();

    UUID uuid = UUID.fromString(DATASET_UUID);
    int attempt = 60;
    String crawlId = DATASET_UUID;
    ValidationResult validationResult = new ValidationResult(true, true, false, 0L, null);

    PipelinesVerbatimMessage message =
        new PipelinesVerbatimMessage(
            uuid,
            attempt,
            Collections.singleton(RecordType.ALL.name()),
            Collections.singleton(VERBATIM_TO_IDENTIFIER.name()),
            DISTRIBUTED.name(),
            EndpointType.DWC_ARCHIVE,
            null,
            validationResult,
            null,
            EXECUTION_ID,
            null);

    // When
    callback.handleMessage(message);

    // Should
    assertTrue(CURATOR_SERVER.checkExists(crawlId, INTERPRETED_LABEL));
    assertTrue(CURATOR_SERVER.checkExists(crawlId, Fn.ERROR_MESSAGE.apply(INTERPRETED_LABEL)));
    assertTrue(CURATOR_SERVER.checkExists(crawlId, Fn.MQ_CLASS_NAME.apply(INTERPRETED_LABEL)));
    assertTrue(CURATOR_SERVER.checkExists(crawlId, Fn.MQ_MESSAGE.apply(INTERPRETED_LABEL)));
    assertEquals(0, PUBLISHER.getMessages().size());

    // Clean
    CURATOR_SERVER.deletePath(crawlId, INTERPRETED_LABEL);
  }
}
