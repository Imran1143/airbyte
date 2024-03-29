/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.cdk.integrations.destination.s3;

import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;
import io.airbyte.cdk.integrations.destination.s3.avro.AvroConstants;
import io.airbyte.cdk.integrations.destination.s3.avro.JsonFieldNameUpdater;
import io.airbyte.cdk.integrations.destination.s3.util.AvroRecordHelper;
import io.airbyte.cdk.integrations.standardtest.destination.comparator.TestDataComparator;
import io.airbyte.commons.json.Jsons;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.avro.Schema.Type;
import org.apache.avro.file.DataFileReader;
import org.apache.avro.file.SeekableByteArrayInput;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericData.Record;
import org.apache.avro.generic.GenericDatumReader;

public abstract class S3BaseAvroDestinationAcceptanceTest extends S3AvroParquetDestinationAcceptanceTest {

  protected S3BaseAvroDestinationAcceptanceTest() {
    super(S3Format.AVRO);
  }

  @Override
  protected JsonNode getFormatConfig() {
    return Jsons.jsonNode(Map.of(
        "format_type", "Avro",
        "compression_codec", Map.of(
            "codec", "zstandard",
            "compression_level", 5,
            "include_checksum", true)));
  }

  @Override
  protected List<JsonNode> retrieveRecords(final TestDestinationEnv testEnv,
                                           final String streamName,
                                           final String namespace,
                                           final JsonNode streamSchema)
      throws Exception {
    final JsonFieldNameUpdater nameUpdater = AvroRecordHelper.getFieldNameUpdater(streamName, namespace, streamSchema);

    final List<S3ObjectSummary> objectSummaries = getAllSyncedObjects(streamName, namespace);
    final List<JsonNode> jsonRecords = new LinkedList<>();

    for (final S3ObjectSummary objectSummary : objectSummaries) {
      final S3Object object = s3Client.getObject(objectSummary.getBucketName(), objectSummary.getKey());
      try (final DataFileReader<Record> dataFileReader = new DataFileReader<>(
          new SeekableByteArrayInput(object.getObjectContent().readAllBytes()),
          new GenericDatumReader<>())) {
        final ObjectReader jsonReader = MAPPER.reader();
        while (dataFileReader.hasNext()) {
          final GenericData.Record record = dataFileReader.next();
          final byte[] jsonBytes = AvroConstants.JSON_CONVERTER.convertToJson(record);
          JsonNode jsonRecord = jsonReader.readTree(jsonBytes);
          jsonRecord = nameUpdater.getJsonWithOriginalFieldNames(jsonRecord);
          jsonRecords.add(AvroRecordHelper.pruneAirbyteJson(jsonRecord));
        }
      }
    }

    return jsonRecords;
  }

  @Override
  protected TestDataComparator getTestDataComparator() {
    return new S3AvroParquetTestDataComparator();
  }

  @Override
  protected Map<String, Set<Type>> retrieveDataTypesFromPersistedFiles(final String streamName, final String namespace) throws Exception {

    final List<S3ObjectSummary> objectSummaries = getAllSyncedObjects(streamName, namespace);
    Map<String, Set<Type>> resultDataTypes = new HashMap<>();

    for (final S3ObjectSummary objectSummary : objectSummaries) {
      final S3Object object = s3Client.getObject(objectSummary.getBucketName(), objectSummary.getKey());
      try (final DataFileReader<Record> dataFileReader = new DataFileReader<>(
          new SeekableByteArrayInput(object.getObjectContent().readAllBytes()),
          new GenericDatumReader<>())) {
        while (dataFileReader.hasNext()) {
          final GenericData.Record record = dataFileReader.next();
          Map<String, Set<Type>> actualDataTypes = getTypes(record);
          resultDataTypes.putAll(actualDataTypes);
        }
      }
    }
    return resultDataTypes;
  }

}
