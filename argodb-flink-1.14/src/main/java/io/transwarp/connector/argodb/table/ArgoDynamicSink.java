package io.transwarp.connector.argodb.table;

import io.transwarp.connector.argodb.ArgoDBSinkConfig;
import io.transwarp.holodesk.sink.ArgoDBSinkTable;
import org.apache.flink.api.common.serialization.SerializationSchema;
import org.apache.flink.table.catalog.CatalogTable;
import org.apache.flink.table.connector.format.EncodingFormat;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.types.DataType;

import javax.annotation.Nullable;

public class ArgoDynamicSink extends ArgoDynamicSinkBase {
  public ArgoDynamicSink(DataType physicalDataType,
                         EncodingFormat<SerializationSchema<RowData>> valueEncodingFormat,
                         int[] valueProjection,
                         ArgoDBSinkConfig argoConfig,
                         @Nullable Integer parallelism,
                         ArgoDBSinkTable table,
                         CatalogTable tableSchema,
                         boolean useNewSink) {
    super(physicalDataType, valueEncodingFormat, valueProjection, argoConfig, parallelism, table, tableSchema, useNewSink);
  }
}
