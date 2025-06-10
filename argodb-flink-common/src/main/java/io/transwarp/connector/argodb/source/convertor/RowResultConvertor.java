package io.transwarp.connector.argodb.source.convertor;


import io.transwarp.holodesk.core.result.RowResult;

import java.io.Serializable;


public interface RowResultConvertor<T> extends Serializable {
  T convertor(RowResult row);
}
