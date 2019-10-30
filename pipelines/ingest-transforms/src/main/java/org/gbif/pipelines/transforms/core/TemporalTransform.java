package org.gbif.pipelines.transforms.core;

import java.time.Instant;

import org.gbif.pipelines.core.Interpretation;
import org.gbif.pipelines.core.interpreters.core.TemporalInterpreter;
import org.gbif.pipelines.io.avro.ExtendedRecord;
import org.gbif.pipelines.io.avro.TemporalRecord;
import org.gbif.pipelines.transforms.Transform;

import org.apache.beam.sdk.metrics.Counter;
import org.apache.beam.sdk.metrics.Metrics;
import org.apache.beam.sdk.transforms.MapElements;
import org.apache.beam.sdk.values.KV;
import org.apache.beam.sdk.values.TypeDescriptor;

import static org.gbif.pipelines.common.PipelinesVariables.Metrics.TEMPORAL_RECORDS_COUNT;
import static org.gbif.pipelines.common.PipelinesVariables.Pipeline.Interpretation.RecordType.TEMPORAL;

/**
 * Beam level transformations for the DWC Event, reads an avro, writes an avro, maps from value to keyValue and
 * transforms form {@link ExtendedRecord} to {@link TemporalRecord}.
 * <p>
 * ParDo runs sequence of interpretations for {@link TemporalRecord} using {@link ExtendedRecord}
 * as a source and {@link TemporalInterpreter} as interpretation steps
 *
 * @see <a href="https://dwc.tdwg.org/terms/#event">https://dwc.tdwg.org/terms/#event</a>
 */
public class TemporalTransform extends Transform<ExtendedRecord, TemporalRecord> {

  private final Counter counter = Metrics.counter(TemporalTransform.class, TEMPORAL_RECORDS_COUNT);

  private TemporalTransform() {
    super(TemporalRecord.class, TEMPORAL);
  }

  public static TemporalTransform create() {
    return new TemporalTransform();
  }

  /** Maps {@link TemporalRecord} to key value, where key is {@link TemporalRecord#getId} */
  public MapElements<TemporalRecord, KV<String, TemporalRecord>> toKv() {
    return MapElements.into(new TypeDescriptor<KV<String, TemporalRecord>>() {})
        .via((TemporalRecord tr) -> KV.of(tr.getId(), tr));
  }

  @ProcessElement
  public void processElement(@Element ExtendedRecord source, OutputReceiver<TemporalRecord> out) {

    TemporalRecord tr = TemporalRecord.newBuilder()
        .setId(source.getId())
        .setCreated(Instant.now().toEpochMilli())
        .build();

    Interpretation.from(source)
        .to(tr)
        .when(er -> !er.getCoreTerms().isEmpty())
        .via(TemporalInterpreter::interpretTemporal);

    out.output(tr);

    counter.inc();
  }

}
