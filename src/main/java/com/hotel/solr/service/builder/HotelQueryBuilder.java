package com.hotel.solr.service.builder;

import com.hotel.solr.framework.exception.BusinessException;
import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.util.RpByteUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
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

    final String TEMPLATE_FILTER_QUERY_PRICE = "{!frange l=%d u=%d}rp_prices(rate_plan_prices, %d, %d, %d, %d)";
    final String TEMPLATE_FILTER_QUERY_ROOM_STS = "{!frange l=%d}rp_room_sts(rate_plan_room_sts, %d, %d, %d)";

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

        if (StringUtils.hasText(q.getCityId())) {
            solrQuery.addFilterQuery("city_id:" + ClientUtils.escapeQueryChars(q.getCityId().trim()));
        }
        if (q.getStar() != null) {
            solrQuery.addFilterQuery("star:" + q.getStar());
        }
        if (q.getPriceFrom() != null && q.getPriceTo() != null) {
            addPriceFilter(solrQuery, q.getCheckinDate(), q.getDays(), q.getPriceFrom(), q.getPriceTo());
        }
        //addRoomStsFilter(solrQuery, q.getCheckinDate(), q.getDays(), 1);

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
        solrQuery.addFilterQuery(String.format(TEMPLATE_FILTER_QUERY_PRICE, minPrice, maxPrice, offsetDay, days, minPrice, maxPrice));
    }

    private void addRoomStsFilter(SolrQuery solrQuery, LocalDate date, int days, int num) {
        int offsetDay = RpByteUtils.calOffsetBaseDate(date);
        if (days < 1) {
            days = 1;
        }
        solrQuery.addFilterQuery(String.format(TEMPLATE_FILTER_QUERY_ROOM_STS, num, offsetDay, days, num));
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
