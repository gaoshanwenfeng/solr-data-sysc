package com.hotel.solr.service;

import com.hotel.solr.dto.HotelQuery;
import com.hotel.solr.exception.BusinessException;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.util.ClientUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 将外部查询条件转换为安全的 SolrQuery。
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

    public SolrQuery build(HotelQuery source) {
        HotelQuery query = source == null ? new HotelQuery() : source;
        SolrQuery solrQuery = new SolrQuery();

        if (StringUtils.hasText(query.getKeyword())) {
            solrQuery.setQuery(escapeKeyword(query.getKeyword()));
            solrQuery.set("defType", "edismax");
            solrQuery.set("qf", "name^2 address");
        } else {
            solrQuery.setQuery("*:*");
        }

        if (StringUtils.hasText(query.getCity())) {
            solrQuery.addFilterQuery("city:" + ClientUtils.escapeQueryChars(query.getCity().trim()));
        }
        if (query.getStar() != null) {
            solrQuery.addFilterQuery("star:" + query.getStar());
        }
        addPriceFilter(solrQuery, query.getMinPrice(), query.getMaxPrice());
        addTagFilter(solrQuery, query.getTags());

        int page = query.getPage() == null ? 1 : Math.max(1, query.getPage());
        int size = query.getSize() == null
                ? DEFAULT_PAGE_SIZE
                : Math.min(MAX_PAGE_SIZE, Math.max(1, query.getSize()));
        solrQuery.setStart((page - 1) * size);
        solrQuery.setRows(size);
        solrQuery.setSort(resolveSort(query.getSort()));
        return solrQuery;
    }

    private String escapeKeyword(String keyword) {
        return Arrays.stream(keyword.trim().split("\\s+"))
                .filter(token -> !token.isEmpty())
                .map(ClientUtils::escapeQueryChars)
                .collect(Collectors.joining(" "));
    }

    private void addPriceFilter(SolrQuery solrQuery, Float minPrice, Float maxPrice) {
        if (minPrice != null && maxPrice != null && minPrice > maxPrice) {
            throw new BusinessException(400, "min_price 不能大于 max_price");
        }
        if (minPrice != null && maxPrice != null) {
            solrQuery.addFilterQuery("price:[" + minPrice + " TO " + maxPrice + "]");
        } else if (minPrice != null) {
            solrQuery.addFilterQuery("price:[" + minPrice + " TO *]");
        } else if (maxPrice != null) {
            solrQuery.addFilterQuery("price:[* TO " + maxPrice + "]");
        }
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
