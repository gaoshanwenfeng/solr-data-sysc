package com.hotel.solr.service;

import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.model.req.HotelPatchReq;
import com.hotel.solr.service.converter.HotelRpSolrConverter;
import com.hotel.solr.framework.util.RpByteUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

class HotelRpDtoSolrConverterTest {

    private final HotelRpSolrConverter converter = new HotelRpSolrConverter();

    @Test
    void mapsHotelFieldsToSolrDocument() {
        Instant createTime = Instant.parse("2026-10-27T00:00:00Z");
        Instant updateTime = Instant.parse("2026-10-27T01:00:00Z");
        HotelRpDo hotelRpDto = HotelRpDo.builder()
                .id("h-1")
                .name("上海外滩酒店")
                .address("中山东一路1号")
                .cityId("1901")
                .cityName("上海")
                .longitude(new BigDecimal("121.473701"))
                .latitude(new BigDecimal("31.230416"))
                .star(5)
                .price(1299.5f)
                .tags(Arrays.asList("江景", "豪华"))
                .ratePlanPrices("[{\"plan\":\"标准价\"}]")
                .ratePlanRoomSts("{\"大床房\":\"有房\"}")
                .createTime(createTime)
                .updateTime(updateTime)
                .build();

        SolrInputDocument doc = converter.toSolrDocument(hotelRpDto);

        assertThat(doc.getFieldValue("id")).isEqualTo("h-1");
        assertThat(doc.getFieldValue("name")).isEqualTo("上海外滩酒店");
        assertThat(doc.getFieldValue("address")).isEqualTo("中山东一路1号");
        assertThat(doc.getFieldValue("city_id")).isEqualTo("1901");
        assertThat(doc.getFieldValue("city_name")).isEqualTo("上海");
        assertThat(doc.getFieldValue("longitude")).isEqualTo(121.473701d);
        assertThat(doc.getFieldValue("latitude")).isEqualTo(31.230416d);
        assertThat(doc.getFieldValue("star")).isEqualTo(5);
        assertThat(doc.getFieldValue("price")).isEqualTo(1299.5f);
        assertThat(doc.getFieldValues("tags")).containsExactlyInAnyOrder("江景", "豪华");
        assertThat(doc.getFieldValue("rate_plan_prices")).isEqualTo("[{\"plan\":\"标准价\"}]");
        assertThat(doc.getFieldValue("rate_plan_room_sts")).isEqualTo("{\"大床房\":\"有房\"}");
        assertThat(doc.getFieldValue("create_time")).isEqualTo(Date.from(createTime));
        assertThat(doc.getFieldValue("update_time")).isEqualTo(Date.from(updateTime));
    }

    @Test
    void skipsNullFieldsWhenMappingToDocument() {
        HotelRpDo hotel = HotelRpDo.builder().id("h-1").name("酒店").build();

        SolrInputDocument doc = converter.toSolrDocument(hotel);

        assertThat(doc.getFieldValue("address")).isNull();
        assertThat(doc.getFieldValue("city_id")).isNull();
        assertThat(doc.getFieldValue("city_name")).isNull();
        assertThat(doc.getFieldValue("longitude")).isNull();
        assertThat(doc.getFieldValue("latitude")).isNull();
        assertThat(doc.getFieldValue("star")).isNull();
        assertThat(doc.getFieldValue("price")).isNull();
        assertThat(doc.getFieldValues("tags")).isNull();
        assertThat(doc.getFieldValue("rate_plan_prices")).isNull();
        assertThat(doc.getFieldValue("create_time")).isNull();
    }

    @Test
    void mapsSolrDocumentToHotel() {
        SolrDocument doc = new SolrDocument();
        doc.setField("id", "h-1");
        doc.setField("name", "上海外滩酒店");
        doc.setField("address", "中山东一路1号");
        doc.setField("city_id", "1901");
        doc.setField("city_name", "上海");
        doc.setField("longitude", 121.473701d);
        doc.setField("latitude", 31.230416d);
        doc.setField("star", 5);
        doc.setField("price", 1299.5f);
        doc.addField("tags", "江景");
        doc.addField("tags", "豪华");
        doc.setField("rate_plan_prices", "[]");
        doc.setField("rate_plan_room_sts", "{}");
        doc.setField("create_time", Date.from(Instant.parse("2026-10-27T00:00:00Z")));
        doc.setField("update_time", Date.from(Instant.parse("2026-10-27T01:00:00Z")));

        HotelRpDto hotelRpDto = converter.toHotel(doc);

        assertThat(hotelRpDto.getId()).isEqualTo("h-1");
        assertThat(hotelRpDto.getName()).isEqualTo("上海外滩酒店");
        assertThat(hotelRpDto.getAddress()).isEqualTo("中山东一路1号");
        assertThat(hotelRpDto.getCityId()).isEqualTo("1901");
        assertThat(hotelRpDto.getCityName()).isEqualTo("上海");
        assertThat(hotelRpDto.getLongitude()).isEqualByComparingTo("121.473701");
        assertThat(hotelRpDto.getLatitude()).isEqualByComparingTo("31.230416");
        assertThat(hotelRpDto.getLongitude().scale()).isEqualTo(6);
        assertThat(hotelRpDto.getLatitude().scale()).isEqualTo(6);
        assertThat(hotelRpDto.getStar()).isEqualTo(5);
        assertThat(hotelRpDto.getPrice()).isEqualTo(1299.5f);
        assertThat(hotelRpDto.getTags()).containsExactly("江景", "豪华");
        assertThat(hotelRpDto.getCreateTime()).isEqualTo(Instant.parse("2026-10-27T00:00:00Z"));
        assertThat(hotelRpDto.getUpdateTime()).isEqualTo(Instant.parse("2026-10-27T01:00:00Z"));
    }

    @Test
    void parsesStringDatesAndNumbersFromSolrDocument() {
        SolrDocument doc = new SolrDocument();
        doc.setField("id", "h-1");
        doc.setField("star", "4");
        doc.setField("price", "88.5");
        doc.setField("longitude", "121.473701");
        doc.setField("latitude", 31.230416d);
        doc.setField("create_time", "2026-10-27T00:00:00Z");

        HotelRpDto hotelRpDto = converter.toHotel(doc);

        assertThat(hotelRpDto.getStar()).isEqualTo(4);
        assertThat(hotelRpDto.getPrice()).isEqualTo(88.5f);
        assertThat(hotelRpDto.getLongitude()).isEqualByComparingTo("121.473701");
        assertThat(hotelRpDto.getLatitude()).isEqualByComparingTo("31.230416");
        assertThat(hotelRpDto.getCreateTime()).isEqualTo(Instant.parse("2026-10-27T00:00:00Z"));
    }

    @Test
    void padsCoordinatesToSixDecimalsWhenReading() {
        SolrDocument doc = new SolrDocument();
        doc.setField("id", "h-1");
        doc.setField("longitude", 121.0d);
        doc.setField("latitude", 31.5d);

        HotelRpDto hotelRpDto = converter.toHotel(doc);

        assertThat(hotelRpDto.getLongitude()).isEqualByComparingTo("121.000000");
        assertThat(hotelRpDto.getLongitude().scale()).isEqualTo(6);
        assertThat(hotelRpDto.getLatitude()).isEqualByComparingTo("31.500000");
        assertThat(hotelRpDto.getLatitude().scale()).isEqualTo(6);
    }

    @Test
    void queryConversionParsesOnlyRequestedStayRange() {
        LocalDate start = LocalDate.of(2026, 10, 27);
        SolrDocument doc = new SolrDocument();
        doc.setField("id", "h-1");
        doc.setField("rate_plan_prices",
                RpByteUtils.bytesToString(RpByteUtils.encode(start, new int[]{100, 200, 300})));
        doc.setField("rate_plan_room_sts",
                RpByteUtils.bytesToString(RpByteUtils.encode(start, new int[]{1, 2, 3})));

        HotelRpDto hotelRpDto = HotelRpSolrConverter.toHotelForQuery(doc, start.plusDays(1), 2);

        assertThat(hotelRpDto.getPriceList())
                .extracting(HotelRpDto.RpItemData::getDate)
                .containsExactly("2026-10-28", "2026-10-29");
        assertThat(hotelRpDto.getPriceList())
                .extracting(HotelRpDto.RpItemData::getValue)
                .containsExactly(200, 300);
        assertThat(hotelRpDto.getStsList())
                .extracting(HotelRpDto.RpItemData::getValue)
                .containsExactly(2, 3);
    }

    @Test
    void queryConversionIncludesLastPlannedDay() {
        LocalDate start = LocalDate.of(2026, 10, 27);
        SolrDocument doc = new SolrDocument();
        doc.setField("rate_plan_prices",
                RpByteUtils.bytesToString(RpByteUtils.encode(start, new int[]{100, 200, 300})));
        doc.setField("rate_plan_room_sts",
                RpByteUtils.bytesToString(RpByteUtils.encode(start, new int[]{1, 2, 3})));

        HotelRpDto hotelRpDto = HotelRpSolrConverter.toHotelForQuery(doc, start.plusDays(2), 1);

        assertThat(hotelRpDto.getPriceList())
                .extracting(HotelRpDto.RpItemData::getDate)
                .containsExactly("2026-10-29");
        assertThat(hotelRpDto.getPriceList())
                .extracting(HotelRpDto.RpItemData::getValue)
                .containsExactly(300);
        assertThat(hotelRpDto.getStsList())
                .extracting(HotelRpDto.RpItemData::getValue)
                .containsExactly(3);
    }

    @Test
    void queryConversionSkipsStayOutOfPlannedRange() {
        LocalDate start = LocalDate.of(2026, 10, 27);
        SolrDocument doc = new SolrDocument();
        doc.setField("rate_plan_prices",
                RpByteUtils.bytesToString(RpByteUtils.encode(start, new int[]{100, 200, 300})));
        doc.setField("rate_plan_room_sts",
                RpByteUtils.bytesToString(RpByteUtils.encode(start, new int[]{1, 2, 3})));

        HotelRpDto hotelRpDto = HotelRpSolrConverter.toHotelForQuery(doc, start.plusDays(3), 1);

        assertThat(hotelRpDto.getPriceList()).isNull();
        assertThat(hotelRpDto.getStsList()).isNull();
    }

    @Test
    void queryConversionHandlesMissingRatePlanFields() {
        SolrDocument doc = new SolrDocument();
        doc.setField("id", "h-1");

        HotelRpDto hotelRpDto = HotelRpSolrConverter.toHotelForQuery(
                doc, LocalDate.of(2026, 10, 27), 1);

        assertThat(hotelRpDto.getId()).isEqualTo("h-1");
        assertThat(hotelRpDto.getPriceList()).isNull();
        assertThat(hotelRpDto.getStsList()).isNull();
    }

    @Test
    void buildsAtomicDocumentWithOnlyProvidedFields() {
        HotelPatchReq patch = new HotelPatchReq();
        patch.setPrice(888.0f);
        patch.setTags(Collections.singletonList("新标签"));
        patch.setLongitude(new BigDecimal("121.473701"));
        patch.setLatitude(new BigDecimal("31.230416"));

        SolrInputDocument doc = converter.toAtomicDocument("h-1", patch);

        assertThat(doc.getFieldValue("id")).isEqualTo("h-1");
        assertThat(doc.getFieldValue("price"))
                .isEqualTo(Collections.singletonMap("set", 888.0f));
        assertThat(doc.getFieldValue("tags"))
                .isEqualTo(Collections.singletonMap("set", Collections.singletonList("新标签")));
        assertThat(doc.getFieldValue("longitude"))
                .isEqualTo(Collections.singletonMap("set", 121.473701d));
        assertThat(doc.getFieldValue("latitude"))
                .isEqualTo(Collections.singletonMap("set", 31.230416d));
        assertThat(doc.getFieldValue("name")).isNull();
        assertThat(doc.getFieldValue("address")).isNull();
        assertThat(doc.getFieldValue("update_time")).isNull();
    }

    @Test
    void atomicDocumentCanClearTagsWithEmptyList() {
        HotelPatchReq patch = new HotelPatchReq();
        patch.setTags(Collections.emptyList());

        SolrInputDocument doc = converter.toAtomicDocument("h-1", patch);

        assertThat(doc.getFieldValue("tags"))
                .isEqualTo(Collections.singletonMap("set", Collections.emptyList()));
    }
}
