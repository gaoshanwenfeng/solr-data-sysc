package com.hotel.solr.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.solr.framework.util.RpByteUtils;
import com.hotel.solr.model.bo.HotelInfoRecordBo;
import com.hotel.solr.model.bo.HotelRpDataSetBo;
import com.hotel.solr.model.bo.RatePlanDataBo;
import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.po.HotelRpDo;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 只读加载 classpath 根目录下的 hotel-rp-set.json，并把源记录转换为 {@link HotelRpDto}。
 *
 * <p>源文件只通过输入流读取，不会写入或修改。转换规则：</p>
 * <ul>
 *   <li>hotel_name / address / city_id / city_name 分别映射到 name / address / cityId / cityName；</li>
 *   <li>坐标优先使用 google_lat/google_lon，整对无效时回退 baidu_lat/baidu_lon，
 *       两对都无效则留空；</li>
 *   <li>star_rate 转换为星级；</li>
 *   <li>rp_prices / rp_room_sts 使用 {@link RpByteUtils} 编码为紧凑字符串；</li>
 *   <li>其余字段保留在 {@link HotelInfoRecordBo} 中，不写入 HotelDo。</li>
 * </ul>
 *
 * @author gaofeng
 */
public class DataLoader {

    public static final String HOTEL_RP_FILE = "hotel-rp-set.json";
    public static final String RATE_PLAN_SET_FILE = "rate-plan-set.json";
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MISSING_COORDINATE = new BigDecimal("-1");
    private static final int MIN_COORDINATE_SCALE = 6;

    private static ObjectMapper objectMapper;

    public DataLoader(ObjectMapper objectMapper) {
        DataLoader.objectMapper = objectMapper;
    }

    /**
     * 读取 classpath 中指定资源，仅解析原始记录。
     */
    public static List<HotelInfoRecordBo> readClasspath(String resource) {
        Objects.requireNonNull(resource, "resource");
        ClassLoader classLoader = DataLoader.class.getClassLoader();
        try (InputStream input = classLoader.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("读取酒店基础数据失败: classpath:" + resource);
            }
            return read(input);
        } catch (IOException e) {
            throw new IllegalStateException("读取酒店基础数据失败: classpath:" + resource, e);
        }
    }

    /**
     * 读取指定文件，仅解析原始记录。
     */
    public static List<HotelInfoRecordBo> read(Path path) {
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
    public static List<HotelInfoRecordBo> read(InputStream input) {
        Objects.requireNonNull(input, "input");
        try {
            HotelRpDataSetBo document = objectMapper.readValue(input, HotelRpDataSetBo.class);
            if (document == null || document.getRecords() == null) {
                return Collections.emptyList();
            }
            return document.getRecords();
        } catch (IOException e) {
            throw new IllegalStateException("解析酒店基础数据失败", e);
        }
    }


    /**
     * 加载 classpath 中指定资源并转换为 HotelDo 列表。
     */
    public static List<HotelRpDo> loadClasspath(String resource) {
        return readClasspath(resource).stream().map(DataLoader::convert).collect(Collectors.toList());
    }

    /**
     * 加载指定文件并转换为 HotelDo 列表。
     */
    public static List<HotelRpDo> load(Path path) {
        return read(path).stream().map(DataLoader::convert).collect(Collectors.toList());
    }

    /**
     * 从输入流加载并转换为 HotelDo 列表。调用方负责关闭流。
     */
    public static List<HotelRpDo> load(InputStream input) {
        return read(input).stream().map(DataLoader::convert).collect(Collectors.toList());
    }

    /**
     * 将单条原始记录转换为 HotelDo。
     */
    public static HotelRpDo convert(HotelInfoRecordBo record) {
        if (record == null) {
            throw new IllegalArgumentException("酒店基础数据记录不能为空");
        }
        CoordinatePair coordinates = resolveCoordinates(record);

        return HotelRpDo.builder()
                .id(trimToNull(record.getId()))
                .ratePlanId(trimToNull(record.getId()))
                .hotelId(trimToNull(record.getMhotelId()))
                .name(trimToNull(record.getHotelName()))
                .address(trimToNull(record.getAddress()))
                .cityId(trimToNull(record.getCityId()))
                .cityName(trimToNull(record.getCityName()))
                .longitude(coordinates.longitude)
                .latitude(coordinates.latitude)
                .star(parseInteger(record.getStarRate()))
                .ratePlanPrices(encodeRatePlan(record.getRpPrices()))
                .ratePlanRoomSts(encodeRatePlan(record.getRpRoomSts()))
                .build();
    }

    /**
     * 将价格/房态数据编码为紧凑字符串；数据缺失时返回 null。
     */
    private static String encodeRatePlan(RatePlanDataBo data) {
        if (data == null || trimToNull(data.getDate()) == null || data.getList() == null) {
            return null;
        }
        int[] values = data.getList().stream()
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .toArray();
        LocalDate date = LocalDate.parse(data.getDate().trim());
        return RpByteUtils.bytesToString(RpByteUtils.encode(date, values));
    }

    private static CoordinatePair resolveCoordinates(HotelInfoRecordBo record) {
        CoordinatePair google = parseCoordinatePair(record.getGoogleLat(), record.getGoogleLon());
        if (google != null) {
            return google;
        }
        CoordinatePair baidu = parseCoordinatePair(record.getBaiduLat(), record.getBaiduLon());
        if (baidu != null) {
            return baidu;
        }
        return CoordinatePair.EMPTY;
    }

    private static CoordinatePair parseCoordinatePair(String latitudeValue, String longitudeValue) {
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

    private static boolean isMissing(BigDecimal latitude, BigDecimal longitude) {
        return latitude.compareTo(MISSING_COORDINATE) == 0
                && longitude.compareTo(MISSING_COORDINATE) == 0;
    }

    private static boolean inRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.compareTo(min) >= 0 && value.compareTo(max) <= 0;
    }

    private static BigDecimal padScale(BigDecimal value) {
        return value.scale() < MIN_COORDINATE_SCALE
                ? value.setScale(MIN_COORDINATE_SCALE)
                : value;
    }

    private static BigDecimal parseDecimal(String value) {
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

    private static Integer parseInteger(String value) {
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

    private static String trimToNull(String value) {
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
