package com.hotel.solr.service;

import com.hotel.solr.dto.HotelQuery;
import com.hotel.solr.exception.BusinessException;
import org.apache.solr.client.solrj.SolrQuery;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotelDoQueryBuilderTest {

    private final HotelQueryBuilder builder = new HotelQueryBuilder();

    @Test
    void buildsMatchAllQueryWithPagingAndDefaultSort() {
        SolrQuery query = builder.build(HotelQuery.builder().page(2).size(20).build());

        assertThat(query.getQuery()).isEqualTo("*:*");
        assertThat(query.getStart()).isEqualTo(20);
        assertThat(query.getRows()).isEqualTo(20);
        assertThat(query.getSortField()).isEqualTo("score desc");
    }

    @Test
    void usesEdismaxForKeywordAndEscapesSpecialCharacters() {
        SolrQuery query = builder.build(HotelQuery.builder().keyword("上海 外滩+酒店").build());

        assertThat(query.getQuery()).isEqualTo("上海 外滩\\+酒店");
        assertThat(query.get("defType")).isEqualTo("edismax");
        assertThat(query.get("qf")).isEqualTo("name^2 address");
    }

    @Test
    void addsCityStarPriceAndTagFilters() {
        SolrQuery query = builder.build(HotelQuery.builder()
                .city("上海")
                .star(5)
                .minPrice(100f)
                .maxPrice(500f)
                .tags(Arrays.asList("江景", "豪华"))
                .build());

        assertThat(query.getFilterQueries()).containsExactly(
                "city:上海",
                "star:5",
                "price:[100.0 TO 500.0]",
                "(tags:江景 OR tags:豪华)");
    }

    @Test
    void supportsOpenEndedPriceRange() {
        assertThat(builder.build(HotelQuery.builder().minPrice(100f).build()).getFilterQueries())
                .containsExactly("price:[100.0 TO *]");
        assertThat(builder.build(HotelQuery.builder().maxPrice(500f).build()).getFilterQueries())
                .containsExactly("price:[* TO 500.0]");
    }

    @Test
    void escapesFilterValues() {
        SolrQuery query = builder.build(HotelQuery.builder()
                .city("上海+浦东")
                .tags(Collections.singletonList("a b"))
                .build());

        assertThat(query.getFilterQueries())
                .containsExactly("city:上海\\+浦东", "(tags:a\\ b)");
    }

    @Test
    void rejectsInvalidSortField() {
        assertThatThrownBy(() -> builder.build(HotelQuery.builder().sort("name asc").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排序");
    }

    @Test
    void rejectsInvalidSortDirection() {
        assertThatThrownBy(() -> builder.build(HotelQuery.builder().sort("price upward").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排序");
    }

    @Test
    void acceptsWhitelistedSort() {
        SolrQuery query = builder.build(HotelQuery.builder().sort("price asc").build());

        assertThat(query.getSortField()).isEqualTo("price asc");
    }

    @Test
    void rejectsMinPriceGreaterThanMaxPrice() {
        assertThatThrownBy(() -> builder.build(HotelQuery.builder().minPrice(500f).maxPrice(100f).build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("min_price");
    }

    @Test
    void clampsPaging() {
        SolrQuery query = builder.build(HotelQuery.builder().page(0).size(1000).build());

        assertThat(query.getStart()).isZero();
        assertThat(query.getRows()).isEqualTo(200);
    }
}
