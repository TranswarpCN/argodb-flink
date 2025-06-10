package io.transwarp.connector.argo.sink.writable;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * DateUtils. Thread-safe class
 */
public class ArgoDBDateUtils {

  public static final String INCEPTOR_DEFAULT_DATE_FORMAT = "yyyy-MM-dd";
  public static final String INCEPTOR_DEFAULT_DATE_FORMAT_WITH_TIME = "yyyy-MM-dd HH:mm:ss";
  public static final String ENGLISH_DATE_FORMAT = "dd-MMM-yy";
  public static final String ENGLISH_DATE_FORMAT_WITH_TIME = "dd-MMM-yy HH:mm:ss";
  public static final String ENGLISH_DATE_FORMAT2 = "dd-MMM-yyyy";
  public static final String ENGLISH_DATE_FORMAT2_WITH_TIME = "dd-MMM-yyyy HH:mm:ss";
  public static final String TD_DEFAULT_DATE_FORMAT = "yyyyMMdd";

  private static final ThreadLocal<SimpleDateFormat> dateFormatLocal = new ThreadLocal<SimpleDateFormat>() {
    @Override
    protected SimpleDateFormat initialValue() {
      return new SimpleDateFormat(INCEPTOR_DEFAULT_DATE_FORMAT);
    }
  };

  public static SimpleDateFormat getDateFormat() {
    return dateFormatLocal.get();
  }

  public static final int NANOS_PER_SEC = 1000000000;
  public static final int SECS_PER_MINTE = 60;
  public static final int MINUTES_PER_HOUR = 60;
  public static final int HOURS_PER_DAY = 24;
  public static final BigDecimal MAX_INT_BD = new BigDecimal(Integer.MAX_VALUE);
  public static final BigDecimal NANOS_PER_SEC_BD = new BigDecimal(NANOS_PER_SEC);

  public static int parseNumericValueWithRange(String fieldName,
                                               String strVal, int minValue, int maxValue) throws IllegalArgumentException {
    int result = 0;
    if (strVal != null) {
      result = Integer.parseInt(strVal);
      if (result < minValue || result > maxValue) {
        throw new IllegalArgumentException(String.format("%s value %d outside range [%d, %d]",
          fieldName, result, minValue, maxValue));
      }
    }
    return result;
  }

  public static long getIntervalDayTimeTotalSecondsFromTotalNanos(long totalNanos) {
    return totalNanos / NANOS_PER_SEC;
  }

  public static int getIntervalDayTimeNanosFromTotalNanos(long totalNanos) {
    return (int) (totalNanos % NANOS_PER_SEC);
  }

  /**
   * SimpleDateFormat in JAVA would treat a invalid date format pattern including digits
   * (e.g. 1000-01-01) as valid pattern and leads to a wrong result.
   * To avoid that, using this function to check the existence of digits in the pattern.
   * Returns true if the pattern is valid without digits, false otherwise.
   */
  public static boolean checkDateFormat(String pattern) {
    return !pattern.matches(".+[0-9]");
  }

  /**
   * Return true if the dateString have a time part, for example: "2018-02-18 12:12:12".
   * False otherwise.
   */
  public static boolean hasTimePart(String dateString) {
    return (dateString.indexOf(' ') != -1);
  }

  /**
   * Return a SimpleDateFormat according to the dateString.
   * We support yyyy-MM-dd, yyyy-MM-dd HH:mm:ss, yyyyMMdd, yyyyMMdd HH:mm:ss.
   * Including dd-MMM-yy, dd-MMM-yy HH:mm:ss, dd-MMM-yyyy, dd-MMM-yyyy HH:mm:ss if sprtEnglishDateFmt is true.
   *
   * @param dateString
   * @return
   * @throws IllegalArgumentException if dateString can not be converted to Date.
   */
  public static SimpleDateFormat getFormatFromDateStrInternal(String dateString, boolean sprtEnglishDateFmt) {
    String dateStr = dateString.trim();
    String ret;
    boolean hasTime = false;

    if (dateStr.indexOf(' ') != -1) {
      hasTime = true;
      dateStr = dateStr.split("\\s+")[0];
    }
    int dateStrLen = dateStr.length();

    if (dateStrLen == 10) {
      ret = hasTime ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd";
    } else if (dateStrLen == 8) {
      ret = hasTime ? "yyyyMMdd HH:mm:ss" : "yyyyMMdd";
    } else if (sprtEnglishDateFmt && dateStrLen == 9) {
      // to support 2015-1-12
      int firstDash = dateStr.indexOf('-');
      int secondDash = dateStr.indexOf('-', firstDash + 1);
      if (firstDash == 4 && secondDash == 6) {
        ret = hasTime ? "yyyy-MM-dd HH:mm:ss" : "yyyy-MM-dd";
      } else {
        ret = hasTime ? "dd-MMM-yy HH:mm:ss" : "dd-MMM-yy";
      }
    } else if (sprtEnglishDateFmt && dateStrLen == 11) {
      ret = hasTime ? "dd-MMM-yyyy HH:mm:ss" : "dd-MMM-yyyy";
    } else {
      throw new IllegalArgumentException("String " + dateString + " can not be converted to Date");
    }
    return new SimpleDateFormat(ret);
  }

  /**
   * Return a SimpleDateFormat according to the dateString.
   * We support yyyy-MM-dd, yyyy-MM-dd HH:mm:ss, yyyyMMdd, yyyyMMdd HH:mm:ss.
   * Including dd-MMM-yy, dd-MMM-yy HH:mm:ss, dd-MMM-yyyy, dd-MMM-yyyy HH:mm:ss if sprtEnglishDateFmt is true.
   *
   * @param dateString
   * @return
   * @throws IllegalArgumentException if dateString can not be converted to Date.
   */
  public static SimpleDateFormat getFormatter(String dateString, boolean sprtEnglishDateFmt) {
    SimpleDateFormat formatter = null;
    try {
      formatter = getFormatFromDateStrInternal(dateString, sprtEnglishDateFmt);
    } catch (IllegalArgumentException e) {
      //ignore: return null
    }
    return formatter;
  }

  public static SimpleDateFormat getFormatter(String dateString) {
    return getFormatter(dateString, true);
  }

  /**
   * Return a SimpleDateFormat according to the dateString, ignore the time part.
   * We support yyyy-MM-dd, yyyyMMdd.
   * Including dd-MMM-yy, dd-MMM-yyyy if sprtEnglishDateFmt is true.
   *
   * @param dateString
   * @return
   * @throws IllegalArgumentException if dateString can not be converted to Date.
   */
  public static SimpleDateFormat getFormatNoTimePartInternal(String dateString, boolean sprtEnglishDateFmt) {
    String dateStr = dateString.trim();
    String ret;

    if (dateStr.indexOf(' ') != -1) {
      dateStr = dateStr.split("\\s+")[0];
    }
    int dateStrLen = dateStr.length();

    if (dateStrLen == 10) {
      ret = "yyyy-MM-dd";
    } else if (dateStrLen == 8) {
      ret = "yyyyMMdd";
    } else if (sprtEnglishDateFmt && dateStrLen == 9) {
      // to support 2015-1-12
      int firstDash = dateStr.indexOf('-');
      int secondDash = dateStr.indexOf('-', firstDash + 1);
      if (firstDash == 4 && secondDash == 6) {
        ret = "yyyy-MM-dd";
      } else {
        ret = "dd-MMM-yy";
      }
    } else if (sprtEnglishDateFmt && dateStrLen == 11) {
      ret = "dd-MMM-yyyy";
    } else {
      throw new IllegalArgumentException("String " + dateString + " can not be converted to Date");
    }
    return new SimpleDateFormat(ret);
  }

  /**
   * Return a SimpleDateFormat according to the dateString.
   * We support yyyy-MM-dd, yyyyMMdd.
   * Including dd-MMM-yy, dd-MMM-yyyy if sprtEnglishDateFmt is true.
   *
   * @param dateString
   * @return
   * @throws IllegalArgumentException if dateString can not be converted to Date.
   */
  public static SimpleDateFormat getFormatterNoTimePart(String dateString, boolean sprtEnglishDateFmt) {
    SimpleDateFormat formatter = null;
    try {
      formatter = getFormatNoTimePartInternal(dateString, sprtEnglishDateFmt);
    } catch (IllegalArgumentException e) {
      //ignore: return null
    }
    return formatter;
  }

  public static SimpleDateFormat getFormatterNoTimePart(String dateString) {
    return getFormatterNoTimePart(dateString, true);
  }

  public static Calendar setFirstDayOfMonth(Calendar calendar, Date d) {
    calendar.setTime(d);
    calendar.set(Calendar.DATE, 1);
    return calendar;
  }

  public static Calendar setLastDayOfMonth(Calendar calendar, Date d) {
    calendar.setTime(d);
    int day = calendar.get(Calendar.DATE);
    calendar.add(Calendar.DATE, calendar.getActualMaximum(Calendar.DAY_OF_MONTH) - day);
    return calendar;
  }
}

