/*
 * Copyright (c) 2023 Airbyte, Inc., all rights reserved.
 */

package io.airbyte.integrations.base.destination.typing_deduping;

import static io.airbyte.integrations.base.destination.typing_deduping.TypeAndDedupeTransaction.SOFT_RESET_SUFFIX;

import java.time.Instant;
import java.util.Optional;

public interface SqlGenerator {

  StreamId buildStreamId(String namespace, String name, String rawNamespaceOverride);

  default ColumnId buildColumnId(final String name) {
    return buildColumnId(name, "");
  }

  ColumnId buildColumnId(String name, String suffix);

  /**
   * Generate a SQL statement to create a fresh table to match the given stream.
   * <p>
   * The generated SQL should throw an exception if the table already exists and {@code force} is
   * false.
   *
   * @param suffix A suffix to add to the stream name. Useful for full refresh overwrite syncs, where
   *        we write the entire sync to a temp table.
   * @param force If true, will overwrite an existing table. If false, will throw an exception if the
   *        table already exists. If you're passing a non-empty prefix, you likely want to set this to
   *        true.
   */
  Sql createTable(final StreamConfig stream, final String suffix, boolean force);

  /**
   * Used to create either the airbyte_internal or final schemas if they don't exist
   *
   * @param schema the schema to create
   * @return SQL to create the schema if it does not exist
   */
  Sql createSchema(final String schema);

  /**
   * Generate a SQL statement to copy new data from the raw table into the final table.
   * <p>
   * Responsible for:
   * <ul>
   * <li>Pulling new raw records from a table (i.e. records with null _airbyte_loaded_at)</li>
   * <li>Extracting the JSON fields and casting to the appropriate types</li>
   * <li>Handling errors in those casts</li>
   * <li>Merging those typed records into an existing table</li>
   * <li>Updating the raw records with SET _airbyte_loaded_at = now()</li>
   * </ul>
   * <p>
   * Implementing classes are recommended to break this into smaller methods, which can be tested in
   * isolation. However, this interface only requires a single mega-method.
   *
   * @param finalSuffix the suffix of the final table to write to. If empty string, writes to the
   *        final table directly. Useful for full refresh overwrite syncs, where we write the entire
   *        sync to a temp table and then swap it into the final table at the end.
   *
   * @param minRawTimestamp The latest _airbyte_extracted_at for which all raw records with that
   *        timestamp have already been typed+deduped. Implementations MAY use this value in a
   *        {@code _airbyte_extracted_at > minRawTimestamp} filter on the raw table to improve query
   *        performance.
   * @param useExpensiveSaferCasting often the data coming from the source can be faithfully
   *        represented in the destination without issue, and using a "CAST" expression works fine,
   *        however sometimes we get badly typed data. In these cases we can use a more expensive
   *        query which handles casting exceptions.
   */
  Sql updateTable(final StreamConfig stream, String finalSuffix, Optional<Instant> minRawTimestamp, final boolean useExpensiveSaferCasting);

  /**
   * Drop the previous final table, and rename the new final table to match the old final table.
   * <p>
   * This method may assume that the stream is an OVERWRITE stream, and that the final suffix is
   * non-empty. Callers are responsible for verifying those are true.
   */
  Sql overwriteFinalTable(StreamId stream, String finalSuffix);

  /**
   * Creates a sql query which will create a v2 raw table from the v1 raw table, then performs a soft
   * reset.
   *
   * @param streamId the stream to migrate
   * @param namespace the namespace of the v1 raw table
   * @param tableName name of the v2 raw table
   * @return a string containing the necessary sql to migrate
   */
  Sql migrateFromV1toV2(StreamId streamId, String namespace, String tableName);

  /**
   * Typically we need to create a soft reset temporary table and clear loaded at values
   *
   * @return
   */
  default Sql prepareTablesForSoftReset(final StreamConfig stream) {
    final Sql createTempTable = createTable(stream, SOFT_RESET_SUFFIX, true);
    final Sql clearLoadedAt = clearLoadedAt(stream.id());
    return Sql.concat(createTempTable, clearLoadedAt);
  }

  Sql clearLoadedAt(final StreamId streamId);

  /**
   * Implementation specific if there is no option to retry again with safe casted SQL or the specific
   * cause of the exception can be retried or not.
   *
   * @return true if the exception should be retried with a safer query
   */
  default boolean shouldRetry(final Exception e) {
    return true;
  }

}
