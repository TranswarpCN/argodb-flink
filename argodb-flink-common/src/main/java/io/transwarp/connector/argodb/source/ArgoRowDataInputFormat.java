/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.transwarp.connector.argodb.source;

import io.transwarp.connector.argodb.ArgoSourceConfig;
import io.transwarp.connector.argodb.source.convertor.RowResultConvertor;
import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.table.data.RowData;
import org.apache.flink.table.expressions.ResolvedExpression;

import java.util.List;


@PublicEvolving
public class ArgoRowDataInputFormat extends BaseArgoInputFormat<RowData> {


  public ArgoRowDataInputFormat(ArgoSourceConfig sourceConfig, RowResultConvertor<RowData> rowResultConvertor, List<ResolvedExpression> filters, int[] projectedFields, ArgoScanInfo argoScanInfo) {
    super(sourceConfig, rowResultConvertor, filters, projectedFields, argoScanInfo);
  }

  public ArgoRowDataInputFormat(ArgoSourceConfig sourceConfig, RowResultConvertor<RowData> rowResultConvertor, List<ResolvedExpression> filters, int[] projectedFields) {
    super(sourceConfig, rowResultConvertor, filters, projectedFields);
  }


  @Override
  public TypeInformation<RowData> getProducedType() {
    return TypeInformation.of(RowData.class);
  }
}
