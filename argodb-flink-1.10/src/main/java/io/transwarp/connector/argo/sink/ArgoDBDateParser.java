package io.transwarp.connector.argo.sink;

import io.transwarp.connector.argo.sink.writable.ArgoDBDateUtils;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;

public class ArgoDBDateParser {
  private SimpleDateFormat formatter = new SimpleDateFormat(ArgoDBDateUtils.INCEPTOR_DEFAULT_DATE_FORMAT);
  private ParsePosition pos = new ParsePosition(0);

  public ArgoDBDateParser() {
  }

  public ArgoDBDateParser(SimpleDateFormat formatter) {
    this.formatter = formatter;
  }

  public Date parseDate(String strValue) {
    Date result = new Date(0);
    if (parseDate(strValue, result)) {
      return result;
    }
    return null;
  }

  /**
   * Parse the date string using default formatter and return a HiveDate.
   *
   * @param strValue
   * @param result
   * @return
   */
  public boolean parseDate(String strValue, Date result) {
    return parseDate(strValue, result, false);
  }

  /**
   * Parse the date string using default formatter and return a date.
   *
   * @param strValue
   * @param result
   * @param lenient  False to parse the date strictly, input must match the format,
   *                 and throw Exception for invalid date. Otherwise
   *                 the parser may use heuristics to interpret inputs that do not
   *                 precisely match this object's format
   * @return
   */
  public boolean parseDate(String strValue, Date result, boolean lenient) {
    pos.setIndex(0);
    formatter.setLenient(lenient);
    Date parsedVal = formatter.parse(strValue, pos);
    if (parsedVal == null) {
      return false;
    }
    result.setTime(parsedVal.getTime());
    return true;
  }

  public boolean parseDate(String strValue, Date result, SimpleDateFormat formatter) {
    return parseDate(strValue, result, formatter, false);
  }

  /**
   * Parse the date string using given formatter and return a date.
   *
   * @param strValue
   * @param result
   * @param formatter
   * @param lenient   False to parse the date strictly, input must match the format,
   *                  and throw Exception for invalid date. Otherwise
   *                  the parser may use heuristics to interpret inputs that do not
   *                  precisely match this object's format
   * @return
   */
  public boolean parseDate(String strValue, Date result, SimpleDateFormat formatter, boolean lenient) {
    pos.setIndex(0);
    formatter.setLenient(lenient);
    Date parsedVal = formatter.parse(strValue, pos);
    if (parsedVal == null) {
      return false;
    }
    result.setTime(parsedVal.getTime());
    return true;
  }
}

