package com.hotel.solr.service.builder;

import com.hotel.solr.framework.exception.BusinessException;
import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.service.converter.HotelRpSolrConverter;
import com.hotel.solr.util.RpByteUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 将外部查询条件转换为安全的 SolrQuery。
 *
 * @author gaofeng
 */
@Component
public class HotelQueryBuilder {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private static final String DEFAULT_SORT = "score desc";

    private static final Map<String, SolrQuery.ORDER> SORT_ORDERS;

    static {
        Map<String, SolrQuery.ORDER> orders = new LinkedHashMap<>();
        orders.put("score", SolrQuery.ORDER.desc);
        orders.put("price", SolrQuery.ORDER.desc);
        orders.put("star", SolrQuery.ORDER.desc);
        orders.put("update_time", SolrQuery.ORDER.desc);
        orders.put("create_time", SolrQuery.ORDER.desc);
        SORT_ORDERS = Collections.unmodifiableMap(orders);
    }

    final String TEMPLATE_FILTER_QUERY_PRICE = "{!frange l=%d u=%d}fq_rp_prices(rate_plan_prices, %d, %d)";
    final String TEMPLATE_FILTER_QUERY_ROOM_STS = "{!frange l=%d}fq_rp_sts(rate_plan_room_sts, %d, %d)";
    final String COMMON_FIELD_LIST =  "id,hotel_id,rate_plan_id,name,address,city_id,location,city_name,star,rate_plan_prices,rate_plan_room_sts,create_time,update_time";

    public HotelRpQueryReq buildQueryReq(HotelRpQueryReq q) {
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
        addRoomStsFilter(solrQuery, q.getCheckinDate(), q.getDays(), 1);

        if (StringUtils.hasText(q.getCityId())) {
            solrQuery.addFilterQuery("city_id:" + ClientUtils.escapeQueryChars(q.getCityId().trim()));
        }
        if (q.getStar() != null) {
            solrQuery.addFilterQuery("star:" + q.getStar());
        }
        if (q.getHotelId() != null) {
            solrQuery.addFilterQuery(HotelRpSolrConverter.FIELD_HOTEL_ID + ":" + q.getHotelId());
        }
        if (q.getPriceFrom() != null && q.getPriceTo() != null) {
            addPriceFilter(solrQuery, q.getCheckinDate(), q.getDays(), q.getPriceFrom(), q.getPriceTo());
        }
        if (q.getLocation() != null) {
            addLocationFilter(solrQuery, q.getLocation());
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
        return solrQuery;
    }

    private void addLocationFilter(SolrQuery solrQuery, HotelRpQueryReq.LocationReq location) {
        if (location == null || location.getLat() == null || location.getLon() == null) {
            return;
        }
        String lat = location.getLat().toPlainString();
        String lon = location.getLon().toPlainString();

        // 1) 距离过滤，geofilt 走空间索引
        solrQuery.addFilterQuery("{!geofilt sfield=" + HotelRpSolrConverter.FIELD_LOCATION + " pt=" + lat + "," + lon + " d=" + location.getDistance() + "}");

        // 2) geodist() 无参形式需要的请求级参数
        /**
         * sfield / pt 必须放在请求参数上，不能只写在 {!geofilt ...} 里。
         */
        solrQuery.set("sfield", HotelRpSolrConverter.FIELD_LOCATION);
        solrQuery.set("pt", lat + "," + lon);
        /**
         * 3. 距离虚拟字段
         *   setFields("*", "dist:geodist()") 里的 * 不能省。 如果只 addField("dist:geodist()")，fl 就只剩
         *   dist，name、rate_plan_prices 等字段全部不返回，转换器会得到空对象。
          */
        solrQuery.setFields("*", "dist:geodist()");
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

    private static String toLocation(BigDecimal longitude, BigDecimal latitude) {
        if (longitude == null || latitude == null) {
            return null;
        }
        return latitude.toPlainString() + "," + longitude.toPlainString();
    }
}
