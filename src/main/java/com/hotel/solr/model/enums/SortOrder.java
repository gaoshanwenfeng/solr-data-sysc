package com.hotel.solr.model.enums;

/**
 * 排序方向。
 *
 * <p>仅对 {@link SortType#PRICE} 和 {@link SortType#STAR} 生效：
 * 价格默认从低到高，星级默认从高到低；距离与评分的方向固定。</p>
 *
 * @author gaofeng
 */
public enum SortOrder {

    /**
     * 升序
     */
    ASC,

    /**
     * 降序
     */
    DESC
}
