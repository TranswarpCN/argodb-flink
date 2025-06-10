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
import org.apache.flink.api.common.io.LocatableInputSplitAssigner;
import org.apache.flink.api.common.io.RichInputFormat;
import org.apache.flink.api.common.io.statistics.BaseStatistics;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.io.InputSplitAssigner;
import org.apache.flink.table.expressions.ResolvedExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;


@PublicEvolving
public abstract class BaseArgoInputFormat<T> extends RichInputFormat<T, ArgoInputSplit> implements ResultTypeQueryable<T> {

  private final Logger log = LoggerFactory.getLogger(getClass());

  private List<String> tableProjections;

  private transient ArgodbReader argoReader;
  private transient ArgoReaderIterator<T> resultIterator;
  private final RowResultConvertor<T> rowResultConvertor;

  private final ArgoSourceConfig sourceConfig;

  private ArgoScanInfo argoScanInfo;

  private boolean endReached = false;

  private final int[] needColx;

  public BaseArgoInputFormat(ArgoSourceConfig sourceConfig, RowResultConvertor<T> rowResultConvertor, List<ResolvedExpression> filters, int[] projectedFields, ArgoScanInfo argoScanInfo) {
    this.sourceConfig = sourceConfig;
    this.needColx = projectedFields;
    this.argoScanInfo = argoScanInfo;
    this.rowResultConvertor = rowResultConvertor;
  }

  public BaseArgoInputFormat(ArgoSourceConfig sourceConfig, RowResultConvertor<T> rowResultConvertor, List<ResolvedExpression> filters, int[] projectedFields) {
    this.sourceConfig = sourceConfig;
    this.rowResultConvertor = rowResultConvertor;
    this.needColx = projectedFields;
  }

  @Override
  public void configure(Configuration parameters) {
  }

  @Override
  public void open(ArgoInputSplit split) {
    endReached = false;
    argoReader = new ArgodbReader(sourceConfig, argoScanInfo);
    try {
      argoReader.openForRead(split.getArgoTablePartitionSplits().getHiveTablePartition());
      resultIterator = new ArgoReaderIterator<>(rowResultConvertor);
      resultIterator.rowIterator = argoReader.rowResultIterator;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }


  @Override
  public void close() {
    if (resultIterator != null) {
      resultIterator.close();
    }
  }

  @Override
  public BaseStatistics getStatistics(BaseStatistics cachedStatistics) throws IOException {
    return cachedStatistics;
  }

  @Override
  public InputSplitAssigner getInputSplitAssigner(ArgoInputSplit[] inputSplits) {
    return new LocatableInputSplitAssigner(inputSplits);
  }

  @Override
  public ArgoInputSplit[] createInputSplits(int minNumSplits) throws IOException {
    try {
      if (argoReader == null) argoReader = new ArgodbReader(sourceConfig, argoScanInfo);
      return argoReader.createInputSplits();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public boolean reachedEnd() {
    return endReached;
  }

  @Override
  public T nextRecord(T reuse) throws IOException {
    // check that current iterator has next rows
    if (this.resultIterator.hasNext()) {
      return resultIterator.next();
    } else {
      endReached = true;
      return null;
    }
  }
}
