package com.hotel.solr.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.solr.dto.HotelInfoDataJsonSet;
import com.hotel.solr.dto.HotelBaseRecord;
import com.hotel.solr.dto.HotelDo;
import com.hotel.solr.dto.RatePlanData;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 只读加载 doc/hotel-data/基础数据.json，并把源记录转换为 {@link HotelDo}。
 *
 * <p>源文件只通过输入流读取，不会写入或修改。转换规则：</p>
 * <ul>
 *   <li>hotel_name / address / city_id 分别映射到 name / address / city；</li>
 *   <li>坐标优先使用 google_lat/google_lon，整对无效时回退 baidu_lat/baidu_lon，
 *       两对都无效则留空；</li>
 *   <li>star_rate 转换为星级；</li>
 *   <li>rp_prices / rp_room_sts 原样序列化为 JSON 字符串；</li>
 *   <li>其余字段保留在 {@link HotelBaseRecord} 中，不写入 HotelDo。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class HotelBaseDataLoader {

    public static final String DEFAULT_FILE = "doc/hotel-data/基础数据.json";

    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MISSING_COORDINATE = new BigDecimal("-1");
    private static final int MIN_COORDINATE_SCALE = 6;

    private final ObjectMapper objectMapper;

    /**
     * 读取默认路径下的基础数据，仅解析原始记录。
     */
    public List<HotelBaseRecord> readDefault() {
        return read(Paths.get(DEFAULT_FILE));
    }

    /**
     * 读取指定文件，仅解析原始记录。
     */
    public List<HotelBaseRecord> read(Path path) {
        Objects.requireNonNull(path, "path");
        try (InputStream input = Files.newInputStream(path)) {
            return read(input);
        } catch (IOException e) {
            throw new IllegalStateException("读取酒店基础数据失败: " + path, e);
        }
    }

    /**
     * 从输入流读取原始记录。调用方负责关闭流。
     */
    public List<HotelBaseRecord> read(InputStream input) {
        Objects.requireNonNull(input, "input");
        try {
            HotelInfoDataJsonSet document = objectMapper.readValue(input, HotelInfoDataJsonSet.class);
            if (document == null || document.getRecords() == null) {
                return Collections.emptyList();
            }
            return document.getRecords();
        } catch (IOException e) {
            throw new IllegalStateException("解析酒店基础数据失败", e);
        }
    }

    /**
     * 加载默认路径并转换为 HotelDo 列表。
     */
    public List<HotelDo> loadDefault() {
        return load(Paths.get(DEFAULT_FILE));
    }

    /**
     * 加载指定文件并转换为 HotelDo 列表。
     */
    public List<HotelDo> load(Path path) {
        return read(path).stream().map(this::convert).collect(Collectors.toList());
    }

    /**
     * 从输入流加载并转换为 HotelDo 列表。调用方负责关闭流。
     */
    public List<HotelDo> load(InputStream input) {
        return read(input).stream().map(this::convert).collect(Collectors.toList());
    }

    /**
     * 将单条原始记录转换为 HotelDo。
     */
    public HotelDo convert(HotelBaseRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("酒店基础数据记录不能为空");
        }
        CoordinatePair coordinates = resolveCoordinates(record);
        return HotelDo.builder()
                .id(trimToNull(record.getId()))
                .name(trimToNull(record.getHotelName()))
                .address(trimToNull(record.getAddress()))
                .city(trimToNull(record.getCityId()))
                .longitude(coordinates.longitude)
                .latitude(coordinates.latitude)
                .star(parseInteger(record.getStarRate()))
                .ratePlanPrices(toJson(record.getRpPrices()))
                .ratePlanRoomSts(toJson(record.getRpRoomSts()))
                .build();
    }

    private CoordinatePair resolveCoordinates(HotelBaseRecord record) {
        CoordinatePair google = parseCoordinatePair(record.getGoogleLat(), record.getGoogleLon());
        if (google != null) {
            return google;
        }
        CoordinatePair baidu = parseCoordinatePair(record.getBaiduLat(), record.getBaiduLon());
        return baidu != null ? baidu : CoordinatePair.EMPTY;
    }

    private CoordinatePair parseCoordinatePair(String latitudeValue, String longitudeValue) {
        BigDecimal latitude = parseDecimal(latitudeValue);
        BigDecimal longitude = parseDecimal(longitudeValue);
        if (latitude == null || longitude == null
                || isMissing(latitude, longitude)
                || !inRange(latitude, MIN_LATITUDE, MAX_LATITUDE)
                || !inRange(longitude, MIN_LONGITUDE, MAX_LONGITUDE)) {
            return null;
        }
        return new CoordinatePair(padScale(longitude), padScale(latitude));
    }

    private boolean isMissing(BigDecimal latitude, BigDecimal longitude) {
        return latitude.compareTo(MISSING_COORDINATE) == 0
                && longitude.compareTo(MISSING_COORDINATE) == 0;
    }

    private boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    private BigDecimal padScale(BigDecimal value) {
        return value.scale() < MIN_COORDINATE_SCALE
                ? value.setScale(MIN_COORDINATE_SCALE)
                : value;
    }

    private BigDecimal parseDecimal(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        String text = trimToNull(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String toJson(RatePlanData data) {
        if (data == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化价格计划失败", e);
        }
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static final class CoordinatePair {

        private static final CoordinatePair EMPTY = new CoordinatePair(null, null);

        private final BigDecimal longitude;

        private final BigDecimal latitude;

        private CoordinatePair(BigDecimal longitude, BigDecimal latitude) {
            this.longitude = longitude;
            this.latitude = latitude;
        }
    }
}
