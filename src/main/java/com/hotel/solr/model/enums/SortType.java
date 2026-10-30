package com.hotel.solr.model.enums;

/**
 * 查询排序类型。
 *
 * <p>未显式指定时：携带 location 的请求按距离排序，否则按 Solr 相关度评分排序。</p>
 *
 * @author gaofeng
 */
public enum SortType {

    /**
     * 距离由近到远，需要请求携带 location
     */
    DISTANCE,

    /**
     * 价格，按入住区间的价格计划计算，默认由低到高
     */
    PRICE,

    /**
     * 酒店星级，默认由高到低
     */
    STAR,

    /**
     * Solr 相关度评分由高到低
     */
    SCORE
}
