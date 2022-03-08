package org.gbif.pipelines.ingest.pipelines;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;
import org.apache.beam.sdk.testing.NeedsRunner;
import org.apache.beam.sdk.testing.TestPipeline;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.pipelines.common.beam.options.EsIndexingPipelineOptions;
import org.gbif.pipelines.common.beam.options.InterpretationPipelineOptions;
import org.gbif.pipelines.common.beam.options.PipelinesOptionsFactory;
import org.gbif.pipelines.core.io.SyncDataFileWriter;
import org.gbif.pipelines.estools.service.EsService;
import org.gbif.pipelines.ingest.pipelines.utils.ElasticsearchServer;
import org.gbif.pipelines.ingest.pipelines.utils.InterpretedAvroWriter;
import org.gbif.pipelines.io.avro.EventCoreRecord;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.IdentifierRecord;
import org.gbif.pipelines.transforms.core.EventCoreTransform;
import org.gbif.pipelines.transforms.core.VerbatimTransform;
import org.gbif.pipelines.transforms.specific.IdentifierTransform;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
@Category(NeedsRunner.class)
public class InterpretedToEsIndexPipelineIT {

  private static final String ID = "777";

  @Rule public final transient TestPipeline p = TestPipeline.create();

  @ClassRule public static final ElasticsearchServer ES_SERVER = new ElasticsearchServer();

  @Before
  public void cleanIndexes() {
    EsService.deleteAllIndexes(ES_SERVER.getEsClient());
  }

  @Test
  public void pipelineTest() throws Exception {

    // State
    String outputFile = getClass().getResource("/").getFile();
    String idxName = "interpretedtoesindexextendedpipelineit";

    String postfix = "777";

    String input = outputFile + "data3/ingest";

    String[] argsWriter = {
      "--datasetId=d596fccb-2319-42eb-b13b-986c932780ad",
      "--attempt=147",
      "--runner=SparkRunner",
      "--metaFileName=interpreted-to-index.yml",
      "--inputPath=" + input,
      "--targetPath=" + input,
      "--numberOfShards=1",
      "--interpretationTypes=ALL"
    };
    InterpretationPipelineOptions optionsWriter =
        PipelinesOptionsFactory.createInterpretation(argsWriter);

    try (SyncDataFileWriter<ExtendedRecord> writer =
        InterpretedAvroWriter.createAvroWriter(
            optionsWriter, VerbatimTransform.create(), postfix)) {

      Map<String, String> core = new HashMap<>();
      core.put(DwcTerm.datasetID.qualifiedName(), "datasetID");
      core.put(DwcTerm.institutionID.qualifiedName(), "institutionID");
      core.put(DwcTerm.datasetName.qualifiedName(), "datasetName");
      core.put(DwcTerm.eventID.qualifiedName(), "eventID");
      core.put(DwcTerm.parentEventID.qualifiedName(), "parentEventID");
      core.put(DwcTerm.samplingProtocol.qualifiedName(), "samplingProtocol");

      ExtendedRecord extendedRecord =
          ExtendedRecord.newBuilder()
              .setId(ID)
              .setCoreRowType(DwcTerm.Event.qualifiedName())
              .setCoreTerms(core)
              .build();

      writer.append(extendedRecord);
    }
    try (SyncDataFileWriter<IdentifierRecord> writer =
        InterpretedAvroWriter.createAvroWriter(
            optionsWriter, IdentifierTransform.builder().create(), postfix)) {
      IdentifierRecord identifierRecord =
          IdentifierRecord.newBuilder().setId(ID).setInternalId(ID).build();
      writer.append(identifierRecord);
    }
    try (SyncDataFileWriter<EventCoreRecord> writer =
        InterpretedAvroWriter.createAvroWriter(
            optionsWriter, EventCoreTransform.builder().create(), postfix)) {
      EventCoreRecord eventCoreRecord = EventCoreRecord.newBuilder().setId(ID).build();
      writer.append(eventCoreRecord);
    }

    // When
    String[] args = {
      "--datasetId=d596fccb-2319-42eb-b13b-986c932780ad",
      "--attempt=147",
      "--runner=TestSparkRunner",
      "--metaFileName=interpreted-to-index.yml",
      "--inputPath=" + input,
      "--targetPath=" + input,
      "--esHosts=" + String.join(",", ES_SERVER.getEsConfig().getRawHosts()),
      "--esIndexName=interpretedtoesindexextendedpipelineit",
      "--esAlias=occurrence_interpretedtoesindexextendedpipelineit",
      "--indexNumberShards=1",
      "--indexNumberReplicas=0"
    };
    EsIndexingPipelineOptions options = PipelinesOptionsFactory.createIndexing(args);
    InterpretedToEsIndexPipeline.run(options, opt -> p);

    EsService.refreshIndex(ES_SERVER.getEsClient(), idxName);

    // Should
    assertTrue(EsService.existsIndex(ES_SERVER.getEsClient(), idxName));
    assertEquals(1, EsService.countIndexDocuments(ES_SERVER.getEsClient(), idxName));
  }
}
