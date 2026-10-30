package com.hotel.solr.service.builder;

import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.framework.util.RpByteUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class HotelQueryBuilderTest {

    private final HotelQueryBuilder builder = new HotelQueryBuilder();

    @Test
    void addsDynamicFunctionFieldsToFieldList() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-30");

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        int offsetDay = RpByteUtils.calOffsetBaseDate(req.getCheckinDate());
        assertThat(solrQuery.getFields()).isEqualTo(
                "*,fq_rp_prices:fq_rp_prices(rate_plan_prices, " + offsetDay + ", 3)"
                        + ",fq_rp_sts:fq_rp_sts(rate_plan_room_sts, " + offsetDay + ", 3)");
    }

    @Test
    void keepsDistanceFieldWhenLocationProvided() {
        HotelRpQueryReq req = query("2026-10-27", "2026-10-28");
        HotelRpQueryReq.LocationReq location = new HotelRpQueryReq.LocationReq();
        location.setLat(new BigDecimal("28.208292"));
        location.setLon(new BigDecimal("113.017107"));
        req.setLocation(location);

        SolrQuery solrQuery = builder.buildSolrQuery(req);

        int offsetDay = RpByteUtils.calOffsetBaseDate(req.getCheckinDate());
        assertThat(solrQuery.getFields()).isEqualTo(
                "*,dist:geodist(),fq_rp_prices:fq_rp_prices(rate_plan_prices, " + offsetDay + ", 1)"
                        + ",fq_rp_sts:fq_rp_sts(rate_plan_room_sts, " + offsetDay + ", 1)");
    }

    private HotelRpQueryReq query(String checkin, String checkout) {
        HotelRpQueryReq req = new HotelRpQueryReq();
        req.setCityId("1901");
        req.setCheckin(checkin);
        req.setCheckout(checkout);
        return builder.buildQueryReq(req);
    }
}
