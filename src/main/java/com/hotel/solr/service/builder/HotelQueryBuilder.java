package com.hotel.solr.service.builder;

import com.hotel.solr.framework.exception.BusinessException;
import com.hotel.solr.framework.util.RpByteUtils;
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
    private static final String DEFAULT_SORT = "score desc";

    private static final Map<String, SolrQuery.ORDER> SORT_ORDERS;

    static {
        Map<String, SolrQuery.ORDER> orders = new LinkedHashMap<>();
        orders.put("score", SolrQuery.ORDER.desc);
        orders.put("star", SolrQuery.ORDER.desc);
        orders.put("update_time", SolrQuery.ORDER.desc);
        orders.put("create_time", SolrQuery.ORDER.desc);
        SORT_ORDERS = Collections.unmodifiableMap(orders);
    }

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
            if (q.getPriceFrom() != null && q.getPriceTo() != null) {
                addPriceFilter(solrQuery, q.getCheckinDate(), q.getDays(), q.getPriceFrom(), q.getPriceTo());
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
        solrQuery.setSort(resolveSort(q.getSort()));
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

    private SolrQuery.SortClause resolveSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return SolrQuery.SortClause.create("score", SolrQuery.ORDER.desc);
        }
        String[] parts = sort.trim().toLowerCase(Locale.ROOT).split("\\s+");
        if (parts.length > 2 || !SORT_ORDERS.containsKey(parts[0])) {
            throw new BusinessException(400, "不支持的排序: " + sort
                    + "，可选字段: score, price, star, update_time, create_time");
        }
        SolrQuery.ORDER order = parts.length == 2
                ? parseOrder(parts[1], sort)
                : SORT_ORDERS.get(parts[0]);
        return SolrQuery.SortClause.create(parts[0], order);
    }

    private SolrQuery.ORDER parseOrder(String value, String rawSort) {
        if ("asc".equals(value)) {
            return SolrQuery.ORDER.asc;
        }
        if ("desc".equals(value)) {
            return SolrQuery.ORDER.desc;
        }
        throw new BusinessException(400, "不支持的排序: " + rawSort + "，方向只能是 asc 或 desc");
    }
}
