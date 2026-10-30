package com.hotel.solr.service.converter;

import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.model.req.HotelPatchReq;
import com.hotel.solr.framework.util.LocalDateUtils;
import com.hotel.solr.framework.util.RpByteUtils;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 酒店 DTO 与 Solr 文档之间的显式映射。
 *
 * @author gaofeng
 */
public class HotelRpSolrConverter {

    public static final String FIELD_ID = "id";
    public static final String FIELD_HOTEL_ID = "hotel_id";
    public static final String FIELD_RATE_PLAIN_ID = "rate_plan_id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_CITY_ID = "city_id";
    public static final String FIELD_CITY_NAME = "city_name";
    public static final String FIELD_LOCATION = "location";
    public static final String FIELD_STAR = "star";
    public static final String FIELD_TAGS = "tags";
    public static final String FIELD_RATE_PLAN_PRICES = "rate_plan_prices";
    public static final String FIELD_RATE_PLAN_ROOM_STS = "rate_plan_room_sts";
    public static final String FIELD_CREATE_TIME = "create_time";
    public static final String FIELD_UPDATE_TIME = "update_time";
    public static final String FIELD_V_DIST = "dist";
    public static final String FIELD_V_RP_PRICES_AVG = "rp_prices_avg";
    public static final String FIELD_V_RP_STS_MIN = "rp_sts_min";

    public static SolrInputDocument toSolrDocument(HotelRpDo hotel) {
        SolrInputDocument doc = new SolrInputDocument();
        addIfPresent(doc, FIELD_ID, hotel.getId());
        addIfPresent(doc, FIELD_HOTEL_ID, hotel.getHotelId());
        addIfPresent(doc, FIELD_RATE_PLAIN_ID, hotel.getRatePlanId());
        addIfPresent(doc, FIELD_NAME, hotel.getName());
        addIfPresent(doc, FIELD_ADDRESS, hotel.getAddress());
        addIfPresent(doc, FIELD_CITY_ID, hotel.getCityId());
        if (hotel.getLatitude() != null && hotel.getLongitude() != null) {
            addIfPresent(doc, FIELD_LOCATION, toLocation(hotel.getLatitude(), hotel.getLongitude()));
        }

        addIfPresent(doc, FIELD_CITY_NAME, hotel.getCityName());
        addIfPresent(doc, FIELD_STAR, hotel.getStar());
        if (hotel.getTags() != null) {
            for (String tag : hotel.getTags()) {
                if (tag != null) {
                    doc.addField(FIELD_TAGS, tag);
                }
            }
        }
        addIfPresent(doc, FIELD_RATE_PLAN_PRICES, hotel.getRatePlanPrices());
        addIfPresent(doc, FIELD_RATE_PLAN_ROOM_STS, hotel.getRatePlanRoomSts());
        if (hotel.getCreateTime() != null) {
            doc.setField(FIELD_CREATE_TIME, Date.from(hotel.getCreateTime()));
        }
        if (hotel.getUpdateTime() != null) {
            doc.setField(FIELD_UPDATE_TIME, Date.from(hotel.getUpdateTime()));
        }
        return doc;
    }

    /**
     * 查询结果转换：价格计划、房态只解析本次入住区间内的数据。
     *
     * @param document    Solr 文档
     * @param checkinDate 入住日期
     * @param days        入住天数（离店日期 - 入住日期）
     * @return 酒店数据
     */
    public static HotelRpDto toHotelForQuery(SolrDocument document, LocalDate checkinDate, int days) {
        HotelRpDto hotelRpDto = toBaseDto(document);
        parserRpPriceField(checkinDate, days,
                asString(document.getFieldValue(FIELD_RATE_PLAN_PRICES)), hotelRpDto);
        parserRpRoomStsField(checkinDate, days,
                asString(document.getFieldValue(FIELD_RATE_PLAN_ROOM_STS)), hotelRpDto);
        return hotelRpDto;
    }

    public static HotelRpDto toHotel(SolrDocument document) {
        HotelRpDto hotelRpDto = toBaseDto(document);
        parserRpPriceField(asString(document.getFieldValue(FIELD_RATE_PLAN_PRICES)), hotelRpDto);
        parserRpRoomStsField(asString(document.getFieldValue(FIELD_RATE_PLAN_ROOM_STS)), hotelRpDto);
        return hotelRpDto;
    }

    private static HotelRpDto toBaseDto(SolrDocument document) {
        HotelRpDto hotelRpDto = new HotelRpDto();
        hotelRpDto.setId(asString(document.getFieldValue(FIELD_ID)));
        hotelRpDto.setHotelId(asString(document.getFieldValue(FIELD_HOTEL_ID)));
        hotelRpDto.setRatePlanId(asString(document.getFieldValue(FIELD_RATE_PLAIN_ID)));
        hotelRpDto.setName(asString(document.getFieldValue(FIELD_NAME)));
        hotelRpDto.setAddress(asString(document.getFieldValue(FIELD_ADDRESS)));
        hotelRpDto.setCityId(asString(document.getFieldValue(FIELD_CITY_ID)));
        hotelRpDto.setCityName(asString(document.getFieldValue(FIELD_CITY_NAME)));
        hotelRpDto.setStar(asInteger(document.getFieldValue(FIELD_STAR)));
        hotelRpDto.setLocation(asString(document.getFieldValue(FIELD_LOCATION)));
        Collection<Object> tagValues = document.getFieldValues(FIELD_TAGS);
        if (tagValues != null) {
            hotelRpDto.setTags(tagValues.stream().map(String::valueOf).collect(Collectors.toList()));
        }


        hotelRpDto.setDistance(asFloat(document.getFieldValue(FIELD_V_DIST)));
        hotelRpDto.setAvgPrice(asFloat(document.getFieldValue(FIELD_V_RP_PRICES_AVG)));

        hotelRpDto.setCreateTime(asInstant(document.getFieldValue(FIELD_CREATE_TIME)));
        hotelRpDto.setUpdateTime(asInstant(document.getFieldValue(FIELD_UPDATE_TIME)));
        return hotelRpDto;
    }

    public static SolrInputDocument toAtomicDocument(String id, HotelPatchReq patch) {
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField(FIELD_ID, id);
        addAtomicIfPresent(doc, FIELD_NAME, patch.getName());
        addAtomicIfPresent(doc, FIELD_ADDRESS, patch.getAddress());
        addIfPresent(doc, FIELD_CITY_ID, patch.getCityId());
        addIfPresent(doc, FIELD_CITY_NAME, patch.getCityName());
        if (patch.getLatitude() != null && patch.getLongitude() != null) {
            addIfPresent(doc, FIELD_LOCATION, toLocation(patch.getLatitude(), patch.getLongitude()));
        }
        addAtomicIfPresent(doc, FIELD_STAR, patch.getStar());
        if (patch.getTags() != null) {
            doc.setField(FIELD_TAGS, Collections.singletonMap("set", patch.getTags()));
        }
        addAtomicIfPresent(doc, FIELD_RATE_PLAN_PRICES, patch.getRatePlanPrices());
        addAtomicIfPresent(doc, FIELD_RATE_PLAN_ROOM_STS, patch.getRatePlanRoomSts());
        return doc;
    }

    private static void addIfPresent(SolrInputDocument doc, String field, Object value) {
        if (value != null) {
            doc.setField(field, value);
        }
    }

    private static void addAtomicIfPresent(SolrInputDocument doc, String field, Object value) {
        if (value != null) {
            doc.setField(field, Collections.singletonMap("set", value));
        }
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private static Float asFloat(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return Float.valueOf(String.valueOf(value));
    }

    private static Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    /**
     * 经纬度按至少 6 位小数返回，避免响应中丢失末尾的 0。
     */
    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        BigDecimal decimal;
        if (value instanceof BigDecimal) {
            decimal = (BigDecimal) value;
        } else if (value instanceof Number) {
            decimal = new BigDecimal(value.toString());
        } else {
            decimal = new BigDecimal(String.valueOf(value));
        }
        return decimal.scale() < 6 ? decimal.setScale(6) : decimal;
    }

    private static Instant asInstant(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date) {
            return ((Date) value).toInstant();
        }
        if (value instanceof Calendar) {
            return ((Calendar) value).toInstant();
        }
        if (value instanceof Instant) {
            return (Instant) value;
        }
        return Instant.parse(String.valueOf(value));
    }

    private static void parserRpPriceField(String str, HotelRpDto hotelRpDto) {
        byte[] bytes = stringToBytes(str);
        if (bytes == null) {
            return;
        }
        int dataCount = RpByteUtils.getDataCount(bytes);
        List<HotelRpDto.RpItemData> ls = new ArrayList<>();
        for (int i = 0; i < dataCount; i++) {
            HotelRpDto.RpItemData item = new HotelRpDto.RpItemData();
            item.setDate(LocalDateUtils.format(RpByteUtils.getLocalDate(bytes).plusDays(i), null));
            item.setValue(RpByteUtils.getData(bytes, i));
            ls.add(item);
        }
        hotelRpDto.setPriceList(ls);
    }

    private static void parserRpRoomStsField(String str, HotelRpDto hotelRpDto) {
        byte[] bytes = stringToBytes(str);
        if (bytes == null) {
            return;
        }
        int dataCount = RpByteUtils.getDataCount(bytes);
        List<HotelRpDto.RpItemData> ls = new ArrayList<>();
        for (int i = 0; i < dataCount; i++) {
            HotelRpDto.RpItemData item = new HotelRpDto.RpItemData();
            item.setDate(LocalDateUtils.format(RpByteUtils.getLocalDate(bytes).plusDays(i), null));
            item.setValue(RpByteUtils.getData(bytes, i));
            ls.add(item);
        }
        hotelRpDto.setStsList(ls);
    }

    private static void parserRpPriceField(LocalDate date, int days, String str, HotelRpDto hotelRpDto) {
        List<HotelRpDto.RpItemData> ls = parseStayRange(date, days, str);
        if (ls != null) {
            hotelRpDto.setPriceList(ls);
        }
    }

    private static void parserRpRoomStsField(LocalDate date, int days, String str, HotelRpDto hotelRpDto) {
        List<HotelRpDto.RpItemData> ls = parseStayRange(date, days, str);
        if (ls != null) {
            hotelRpDto.setStsList(ls);
        }
    }

    /**
     * 解析入住区间 [入住日期, 入住日期 + days) 内的数据，区间超出计划范围时返回 null。
     */
    private static List<HotelRpDto.RpItemData> parseStayRange(LocalDate date, int days, String str) {
        byte[] bytes = stringToBytes(str);
        if (bytes == null || date == null || days < 1) {
            return null;
        }
        int dataOffset = RpByteUtils.getDateOffset(bytes);
        int dataCount = RpByteUtils.getDataCount(bytes);
        int checkinDateOffset = RpByteUtils.calOffsetBaseDate(date);
        int dataEnd = dataOffset + dataCount;
        if (checkinDateOffset < dataOffset || checkinDateOffset + days > dataEnd) {
            // 入住区间超出房态计划，视为不匹配的数据
            return null;
        }
        int dataIdx = checkinDateOffset - dataOffset;
        List<HotelRpDto.RpItemData> ls = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            HotelRpDto.RpItemData item = new HotelRpDto.RpItemData();
            item.setDate(LocalDateUtils.format(RpByteUtils.getLocalDate(bytes).plusDays(dataIdx + i), null));
            item.setValue(RpByteUtils.getData(bytes, dataIdx + i));
            ls.add(item);
        }
        return ls;
    }

    private static byte[] stringToBytes(String str) {
        if (str == null || str.isEmpty()) {
            return null;
        }
        return RpByteUtils.stringToBytes(str);
    }

    private static String toLocation(BigDecimal latitude, BigDecimal longitude) {
        if (longitude == null || latitude == null) {
            return null;
        }
        return latitude.toPlainString() + "," + longitude.toPlainString();
    }
}
