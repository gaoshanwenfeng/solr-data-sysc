package com.hotel.solr.service.builder;

import com.hotel.solr.framework.exception.BusinessException;
import com.hotel.solr.framework.util.RpByteUtils;
import com.hotel.solr.model.enums.SortOrder;
import com.hotel.solr.model.enums.SortType;
import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.service.converter.HotelRpSolrConverter;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 将外部查询条件转换为安全的 SolrQuery。
 *
 * @author gaofeng
 */
@Slf4j
@Component
public class HotelQueryBuilder {
    private Logger logger = LoggerFactory.getLogger(HotelQueryBuilder.class);

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;

    private static final String DISTANCE_SORT_FIELD = "geodist()";
    private static final String SCORE_SORT_FIELD = "score";
    /**
     * 排序表达式中的函数不能带空格：sort 参数用逗号分隔多个排序子句。
     */
    private static final String SORT_PRICE_FUNCTION = "fq_rp_prices(rate_plan_prices,%d,%d)";

    final String TEMPLATE_FILTER_QUERY_PRICE = "{!frange l=%d u=%d}fq_rp_prices(rate_plan_prices, %d, %d)";
    final String TEMPLATE_FILTER_QUERY_ROOM_STS = "{!frange l=%d}fq_rp_sts(rate_plan_room_sts, %d, %d)";
    /**
     * 返回字段使用 {@code 别名:函数} 的写法，结果文档中可直接按 fq_rp_prices / fq_rp_sts 读取。
     */
    final String FUNCTION_PRICE = "fq_rp_prices(rate_plan_prices, %d, %d)";
    final String FUNCTION_ROOM_STS = "fq_rp_sts(rate_plan_room_sts, %d, %d)";
    final String COMMON_FIELD_LIST = "id,hotel_id,rate_plan_id,name,address,city_id,location,city_name,star,rate_plan_prices,rate_plan_room_sts,create_time,update_time";

    private static String toLocationStr(HotelRpQueryReq.LocationReq location) {
        if (location.getLat() == null || location.getLon() == null) {
            return null;
        }
        return location.getLat().toPlainString() + "," + location.getLon().toPlainString();
    }

    public HotelRpQueryReq initQueryReq(HotelRpQueryReq q) {
        if (!StringUtils.hasText(q.getCheckin()) || !StringUtils.hasText(q.getCheckout())) {
            throw new BusinessException(400, "入住日期和离店日期不能为空");
        }
        LocalDate checkinDate = LocalDate.parse(q.getCheckin());
        LocalDate checkoutDate = LocalDate.parse(q.getCheckout());
        int days = (int) ChronoUnit.DAYS.between(checkinDate, checkoutDate);
        if (days < 1) {
            throw new BusinessException(400, "离店日期必须晚于入住日期");
        }
        q.setCheckinDate(checkinDate);
        q.setOffSetCheckin((int) ChronoUnit.DAYS.between(RpByteUtils.BASE_DATE, checkinDate));
        q.setCheckoutDate(checkoutDate);
        q.setDays(days);
        return q;
    }

    public SolrQuery buildSolrQuery(HotelRpQueryReq q) {
        SolrQuery solrQuery = new SolrQuery();
        if (StringUtils.hasText(q.getKeyword())) {
            solrQuery.setQuery(escapeKeyword(q.getKeyword()));
            solrQuery.set("defType", "edismax");
            solrQuery.set("qf", "name^2 address");
        } else {
            solrQuery.setQuery("*:*");
        }
        ensureFieldList(solrQuery);


        if (StringUtils.hasText(q.getCityId())) {
            solrQuery.addFilterQuery("city_id:" + ClientUtils.escapeQueryChars(q.getCityId().trim()));
        }
        if (q.getStar() != null) {
            solrQuery.addFilterQuery("star:" + q.getStar());
        }
        if (q.getHotelId() != null) {
            solrQuery.addFilterQuery(HotelRpSolrConverter.FIELD_HOTEL_ID + ":" + q.getHotelId());
        }
        if (q.getCheckinDate() != null && q.getDays() > 0) {
            addRoomStsFilter(solrQuery, q.getCheckinDate(), q.getDays(), 1);
            solrQuery.addField(HotelRpSolrConverter.FIELD_V_RP_STS_MIN +
                    ":" + String.format(FUNCTION_ROOM_STS, q.getOffSetCheckin(), q.getDays()));
            boolean priceFiltered = q.getPriceFrom() != null && q.getPriceTo() != null;
            if (priceFiltered) {
                addPriceFilter(solrQuery, q.getCheckinDate(), q.getDays(), q.getPriceFrom(), q.getPriceTo());
            }
            // 价格排序时也需要返回平均价，供前端展示排序依据
            if (priceFiltered || q.getSortType() == SortType.PRICE) {
                solrQuery.addField(HotelRpSolrConverter.FIELD_V_RP_PRICES_AVG +
                        ":" + String.format(FUNCTION_PRICE, q.getOffSetCheckin(), q.getDays()));
            }
        }

        if (q.getLocation() != null) {
            addLocationFilter(solrQuery, q.getLocation());
            solrQuery.addField(HotelRpSolrConverter.FIELD_V_DIST + ":geodist()");
        }

        if (q.getTags() != null && q.getTags().size() > 1) {
            addTagFilter(solrQuery, q.getTags());
        }

        int page = q.getPage() == null ? 1 : Math.max(1, q.getPage());
        int size = q.getSize() == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(MAX_PAGE_SIZE, Math.max(1, q.getSize()));
        solrQuery.setStart((page - 1) * size);
        solrQuery.setRows(size);
        solrQuery.setSort(resolveSort(q));
        log.info(" Solr查询 QueryString: {}", solrQuery.toQueryString());
        return solrQuery;
    }

    private void addLocationFilter(SolrQuery solrQuery, HotelRpQueryReq.LocationReq location) {
        if (location == null || location.getLat() == null || location.getLon() == null) {
            return;
        }
        String point = toLocationStr(location);
        // 1) 距离过滤，geofilt 走空间索引
        solrQuery.addFilterQuery("{!geofilt sfield=" + HotelRpSolrConverter.FIELD_LOCATION
                + " pt=" + point + " d=" + location.getDistance() + "}");

        // 2) geodist() 无参形式需要的请求级参数，不能只写在 {!geofilt ...} 里
        solrQuery.set("sfield", HotelRpSolrConverter.FIELD_LOCATION);
        solrQuery.set("pt", point);
    }

    /**
     * 没有其它 fl 限制时返回全部 stored 字段，避免结果只剩函数虚拟字段。
     */
    private void ensureFieldList(SolrQuery solrQuery) {
        if (solrQuery.getFields() == null) {
            solrQuery.setFields("*");
        }
    }

    private String escapeKeyword(String keyword) {
        return Arrays.stream(keyword.trim().split("\\s+"))
                .filter(token -> !token.isEmpty())
                .map(ClientUtils::escapeQueryChars)
                .collect(Collectors.joining(" "));
    }

    private void addPriceFilter(SolrQuery solrQuery, LocalDate date, int days, Integer minPrice, Integer maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BusinessException(400, "min_price 不能大于 max_price");
        }
        int offsetDay = RpByteUtils.calOffsetBaseDate(date);
        if (days < 1) {
            days = 1;
        }
        solrQuery.addFilterQuery(String.format(TEMPLATE_FILTER_QUERY_PRICE, minPrice, maxPrice, offsetDay, days));
    }

    private void addRoomStsFilter(SolrQuery solrQuery, LocalDate date, int days, int num) {
        int offsetDay = RpByteUtils.calOffsetBaseDate(date);
        if (days < 1) {
            days = 1;
        }
        if (num < 1) {
            num = 1;
        }
        solrQuery.addFilterQuery(String.format(TEMPLATE_FILTER_QUERY_ROOM_STS, num, offsetDay, days));
    }

    private void addTagFilter(SolrQuery solrQuery, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }
        List<String> terms = tags.stream()
                .filter(StringUtils::hasText)
                .map(tag -> "tags:" + ClientUtils.escapeQueryChars(tag.trim()))
                .collect(Collectors.toList());
        if (!terms.isEmpty()) {
            solrQuery.addFilterQuery("(" + String.join(" OR ", terms) + ")");
        }
    }

    /**
     * 解析排序：显式 sortType 优先；未指定时携带 location 按距离，否则按相关度评分。
     * PRICE 和 STAR 支持 sortOrder 指定方向，距离与评分方向固定。
     */
    private SolrQuery.SortClause resolveSort(HotelRpQueryReq q) {
        SortType sortType = q.getSortType() == null
                ? (hasLocation(q) ? SortType.DISTANCE : SortType.SCORE)
                : q.getSortType();
        switch (sortType) {
            case DISTANCE:
                if (!hasLocation(q)) {
                    throw new BusinessException(400, "按距离排序需要提供 location 信息");
                }
                return SolrQuery.SortClause.create(DISTANCE_SORT_FIELD, SolrQuery.ORDER.asc);
            case PRICE:
                return SolrQuery.SortClause.create(priceSortField(q), resolveOrder(q, SolrQuery.ORDER.asc));
            case STAR:
                return SolrQuery.SortClause.create(HotelRpSolrConverter.FIELD_STAR,
                        resolveOrder(q, SolrQuery.ORDER.desc));
            case SCORE:
            default:
                return SolrQuery.SortClause.create(SCORE_SORT_FIELD, SolrQuery.ORDER.desc);
        }
    }

    private SolrQuery.ORDER resolveOrder(HotelRpQueryReq q, SolrQuery.ORDER defaultOrder) {
        if (q.getSortOrder() == null) {
            return defaultOrder;
        }
        return q.getSortOrder() == SortOrder.DESC ? SolrQuery.ORDER.desc : SolrQuery.ORDER.asc;
    }

    private boolean hasLocation(HotelRpQueryReq q) {
        HotelRpQueryReq.LocationReq location = q.getLocation();
        return location != null && location.getLat() != null && location.getLon() != null;
    }

    private String priceSortField(HotelRpQueryReq q) {
        int offsetDay = RpByteUtils.calOffsetBaseDate(q.getCheckinDate());
        int days = q.getDays() < 1 ? 1 : q.getDays();
        return String.format(SORT_PRICE_FUNCTION, offsetDay, days);
    }
}
