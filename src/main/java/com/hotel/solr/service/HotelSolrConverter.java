package com.hotel.solr.service;

import com.hotel.solr.dto.HotelDo;
import com.hotel.solr.dto.HotelPatchRequest;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrInputDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * 酒店 DTO 与 Solr 文档之间的显式映射。
 */
@Component
public class HotelSolrConverter {

    public static final String FIELD_ID = "id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_ADDRESS = "address";
    public static final String FIELD_CITY = "city";
    public static final String FIELD_LONGITUDE = "longitude";
    public static final String FIELD_LATITUDE = "latitude";
    public static final String FIELD_STAR = "star";
    public static final String FIELD_PRICE = "price";
    public static final String FIELD_TAGS = "tags";
    public static final String FIELD_RATE_PLAN_PRICES = "rate_plan_prices";
    public static final String FIELD_RATE_PLAN_ROOM_STS = "rate_plan_room_sts";
    public static final String FIELD_CREATE_TIME = "create_time";
    public static final String FIELD_UPDATE_TIME = "update_time";

    public SolrInputDocument toDocument(HotelDo hotelDo) {
        SolrInputDocument doc = new SolrInputDocument();
        addIfPresent(doc, FIELD_ID, hotelDo.getId());
        addIfPresent(doc, FIELD_NAME, hotelDo.getName());
        addIfPresent(doc, FIELD_ADDRESS, hotelDo.getAddress());
        addIfPresent(doc, FIELD_CITY, hotelDo.getCity());
        addIfPresent(doc, FIELD_LONGITUDE, toDouble(hotelDo.getLongitude()));
        addIfPresent(doc, FIELD_LATITUDE, toDouble(hotelDo.getLatitude()));
        addIfPresent(doc, FIELD_STAR, hotelDo.getStar());
        addIfPresent(doc, FIELD_PRICE, hotelDo.getPrice());
        if (hotelDo.getTags() != null) {
            for (String tag : hotelDo.getTags()) {
                if (tag != null) {
                    doc.addField(FIELD_TAGS, tag);
                }
            }
        }
        addIfPresent(doc, FIELD_RATE_PLAN_PRICES, hotelDo.getRatePlanPrices());
        addIfPresent(doc, FIELD_RATE_PLAN_ROOM_STS, hotelDo.getRatePlanRoomSts());
        if (hotelDo.getCreateTime() != null) {
            doc.setField(FIELD_CREATE_TIME, Date.from(hotelDo.getCreateTime()));
        }
        if (hotelDo.getUpdateTime() != null) {
            doc.setField(FIELD_UPDATE_TIME, Date.from(hotelDo.getUpdateTime()));
        }
        return doc;
    }

    public HotelDo toHotel(SolrDocument document) {
        HotelDo hotelDo = new HotelDo();
        hotelDo.setId(asString(document.getFieldValue(FIELD_ID)));
        hotelDo.setName(asString(document.getFieldValue(FIELD_NAME)));
        hotelDo.setAddress(asString(document.getFieldValue(FIELD_ADDRESS)));
        hotelDo.setCity(asString(document.getFieldValue(FIELD_CITY)));
        hotelDo.setLongitude(asBigDecimal(document.getFieldValue(FIELD_LONGITUDE)));
        hotelDo.setLatitude(asBigDecimal(document.getFieldValue(FIELD_LATITUDE)));
        hotelDo.setStar(asInteger(document.getFieldValue(FIELD_STAR)));
        hotelDo.setPrice(asFloat(document.getFieldValue(FIELD_PRICE)));
        Collection<Object> tagValues = document.getFieldValues(FIELD_TAGS);
        if (tagValues != null) {
            hotelDo.setTags(tagValues.stream().map(String::valueOf).collect(Collectors.toList()));
        }
        hotelDo.setRatePlanPrices(asString(document.getFieldValue(FIELD_RATE_PLAN_PRICES)));
        hotelDo.setRatePlanRoomSts(asString(document.getFieldValue(FIELD_RATE_PLAN_ROOM_STS)));
        hotelDo.setCreateTime(asInstant(document.getFieldValue(FIELD_CREATE_TIME)));
        hotelDo.setUpdateTime(asInstant(document.getFieldValue(FIELD_UPDATE_TIME)));
        return hotelDo;
    }

    public SolrInputDocument toAtomicDocument(String id, HotelPatchRequest patch) {
        SolrInputDocument doc = new SolrInputDocument();
        doc.setField(FIELD_ID, id);
        addAtomicIfPresent(doc, FIELD_NAME, patch.getName());
        addAtomicIfPresent(doc, FIELD_ADDRESS, patch.getAddress());
        addAtomicIfPresent(doc, FIELD_CITY, patch.getCity());
        addAtomicIfPresent(doc, FIELD_LONGITUDE, toDouble(patch.getLongitude()));
        addAtomicIfPresent(doc, FIELD_LATITUDE, toDouble(patch.getLatitude()));
        addAtomicIfPresent(doc, FIELD_STAR, patch.getStar());
        addAtomicIfPresent(doc, FIELD_PRICE, patch.getPrice());
        if (patch.getTags() != null) {
            doc.setField(FIELD_TAGS, Collections.singletonMap("set", patch.getTags()));
        }
        addAtomicIfPresent(doc, FIELD_RATE_PLAN_PRICES, patch.getRatePlanPrices());
        addAtomicIfPresent(doc, FIELD_RATE_PLAN_ROOM_STS, patch.getRatePlanRoomSts());
        return doc;
    }

    private void addIfPresent(SolrInputDocument doc, String field, Object value) {
        if (value != null) {
            doc.setField(field, value);
        }
    }

    private void addAtomicIfPresent(SolrInputDocument doc, String field, Object value) {
        if (value != null) {
            doc.setField(field, Collections.singletonMap("set", value));
        }
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private Float asFloat(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return Float.valueOf(String.valueOf(value));
    }

    private Double toDouble(BigDecimal value) {
        return value == null ? null : value.doubleValue();
    }

    /**
     * 经纬度按至少 6 位小数返回，避免响应中丢失末尾的 0。
     */
    private BigDecimal asBigDecimal(Object value) {
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

    private Instant asInstant(Object value) {
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
}
