package com.hotel.solr.service.builder;

import com.hotel.solr.framework.exception.BusinessException;
import com.hotel.solr.framework.util.RpByteUtils;
import com.hotel.solr.model.enums.SortOrder;
import com.hotel.solr.model.enums.SortType;
import com.hotel.solr.model.req.HotelRpQueryReq;
import org.apache.solr.client.solrj.SolrQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotelQueryBuilderTest {

    private final HotelQueryBuilder builder = new HotelQueryBuilder();

    @Test
    void addsDynamicFunctionFieldsToFieldList() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-30");

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        int offsetDay = RpByteUtils.calOffsetBaseDate(req.getCheckinDate());
        assertThat(solrQuery.getFields()).isEqualTo(
                "*,rp_sts_min:fq_rp_sts(rate_plan_room_sts, " + offsetDay + ", 3)");
    }

    @Test
    void keepsDistanceFieldWhenLocationProvided() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-28");
        req.setLocation(location());

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        int offsetDay = RpByteUtils.calOffsetBaseDate(req.getCheckinDate());
        assertThat(solrQuery.getFields()).isEqualTo(
                "*,rp_sts_min:fq_rp_sts(rate_plan_room_sts, " + offsetDay + ", 1)"
                        + ",dist:geodist()");
    }

    @Test
    void defaultsToScoreSortWithoutLocation() {
        SolrQuery solrQuery = builder.buildSolrQuery(query("2026-10-27", "2026-10-28"));

        assertThat(solrQuery.getSortField()).isEqualTo("score desc");
    }

    @Test
    void defaultsToDistanceSortWhenLocationProvided() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-28");
        req.setLocation(location());

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        assertThat(solrQuery.getSortField()).isEqualTo("geodist() asc");
    }

    @Test
    void sortsByDistanceWhenRequested() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-28");
        req.setLocation(location());
        req.setSortType(SortType.DISTANCE);

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        assertThat(solrQuery.getSortField()).isEqualTo("geodist() asc");
    }

    @Test
    void sortsByPriceWhenRequested() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-28");
        req.setSortType(SortType.PRICE);

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        int offsetDay = RpByteUtils.calOffsetBaseDate(req.getCheckinDate());
        assertThat(solrQuery.getSortField()).isEqualTo(
                "fq_rp_prices(rate_plan_prices," + offsetDay + ",1) asc");
        assertThat(solrQuery.getFields()).contains("rp_prices_avg:");
    }

    @Test
    void sortsByPriceDescendingWhenRequested() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-28");
        req.setSortType(SortType.PRICE);
        req.setSortOrder(SortOrder.DESC);

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        int offsetDay = RpByteUtils.calOffsetBaseDate(req.getCheckinDate());
        assertThat(solrQuery.getSortField()).isEqualTo(
                "fq_rp_prices(rate_plan_prices," + offsetDay + ",1) desc");
    }

    @Test
    void sortsByStarDescendingByDefault() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-28");
        req.setSortType(SortType.STAR);

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        assertThat(solrQuery.getSortField()).isEqualTo("star desc");
    }

    @Test
    void sortsByStarAscendingWhenRequested() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-28");
        req.setSortType(SortType.STAR);
        req.setSortOrder(SortOrder.ASC);

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        assertThat(solrQuery.getSortField()).isEqualTo("star asc");
    }

    @Test
    void sortsByScoreWhenRequested() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-28");
        req.setLocation(location());
        req.setSortType(SortType.SCORE);

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        assertThat(solrQuery.getSortField()).isEqualTo("score desc");
    }

    @Test
    void rejectsDistanceSortWithoutLocation() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-28");
        req.setSortType(SortType.DISTANCE);

        assertThatThrownBy(() -> builder.buildSolrQuery(req))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", 400);
    }

    private HotelRpQueryReq.LocationReq location() {
        HotelRpQueryReq.LocationReq location = new HotelRpQueryReq.LocationReq();
        location.setLat(new BigDecimal("28.208292"));
        location.setLon(new BigDecimal("113.017107"));
        return location;
    }

    private HotelRpQueryReq query(String checkin, String checkout) {
        HotelRpQueryReq req = new HotelRpQueryReq();
        req.setCityId("1901");
        req.setCheckin(checkin);
        req.setCheckout(checkout);
        return builder.initQueryReq(req);
    }
}
