package com.hotel.solr.framework.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 【功能描述】
 *
 * @author gaofeng
 * @date 2026/10/28-22:09
 */
public class LocalDateUtils {

    /**
     * 功能说明 按照指定格式，返回时间字符串
     *
     * @param localDate 时间
     * @param formatter 格式化
     * @return 字符串
     */
    public static String format(LocalDate localDate, DateTimeFormatter formatter) {
        if (formatter == null) {
            formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        }
        return localDate.format(formatter);
    }
}
