/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.cdk.integrations.destination.gcs;

import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.JsonNode;
import io.airbyte.cdk.integrations.standardtest.destination.ProtocolVersion;
import io.airbyte.commons.json.Jsons;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public abstract class GcsBaseJsonlGzipDestinationAcceptanceTest extends GcsBaseJsonlDestinationAcceptanceTest {

  @Override
  public ProtocolVersion getProtocolVersion() {
    return ProtocolVersion.V1;
  }

  @Override
  protected JsonNode getFormatConfig() {
    // config without compression defaults to GZIP
    return Jsons.jsonNode(Map.of("format_type", outputFormat));
  }

  protected BufferedReader getReader(final S3Object s3Object) throws IOException {
    return new BufferedReader(new InputStreamReader(new GZIPInputStream(s3Object.getObjectContent()), StandardCharsets.UTF_8));
  }

}
