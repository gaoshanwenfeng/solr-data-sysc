package com.hotel.solr.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.solr.dto.HotelBaseRecord;
import com.hotel.solr.dto.HotelDo;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HotelBaseDataLoaderTest {

    private static final Path SOURCE = Paths.get("doc", "hotel-data", "基础数据.json");
    private static final String SOURCE_SHA256 =
            "3a8b04bb1220e6b1146b618f7b97a01fc2107d374a0e9848880d2b67170a9444";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HotelBaseDataLoader loader = new HotelBaseDataLoader(objectMapper);

    @Test
    void loadsDefaultFileFromProjectRoot() {
        assertThat(loader.readDefault()).hasSize(17);
        assertThat(loader.loadDefault()).hasSize(17);
    }

    @Test
    void readsAll52FieldsOfEveryRecord() {
        List<HotelBaseRecord> records = loader.read(SOURCE);

        assertThat(HotelBaseRecord.class.getDeclaredFields()).hasSize(52);
        assertThat(records).hasSize(17);
        assertThat(records).allSatisfy(record -> {
            assertThat(record).hasNoNullFieldsOrProperties();
            assertThat(record.getRpRoomSts().getDate()).isNotBlank();
            assertThat(record.getRpRoomSts().getList()).isNotEmpty();
            assertThat(record.getRpPrices().getDate()).isNotBlank();
            assertThat(record.getRpPrices().getList()).isNotEmpty();
        });

        HotelBaseRecord first = records.get(0);
        assertThat(first.getId()).isEqualTo("4887");
        assertThat(first.getMhotelId()).isEqualTo("01901360");
        assertThat(first.getHotelName()).isEqualTo("长沙皇朝今朝大酒店");
        assertThat(first.getPostalCode()).isEqualTo("410001");
        assertThat(first.getPhone()).isEqualTo("0731-82281645");
        assertThat(first.getTimestamp()).isEqualTo("13/7/2018 10:46:35");
        assertThat(first.getRpRoomSts().getDate()).isEqualTo("2026-10-27");
        assertThat(first.getRpRoomSts().getList()).hasSize(40).startsWith(23, 1, 41);
        assertThat(first.getRpPrices().getDate()).isEqualTo("2026-10-27");
        assertThat(first.getRpPrices().getList()).hasSize(70).startsWith(16, 29, 3);
    }

    @Test
    void mapsFirstRecordWithExpectedValues() {
        HotelDo hotelDo = loader.load(SOURCE).get(0);

        assertThat(hotelDo.getId()).isEqualTo("4887");
        assertThat(hotelDo.getName()).isEqualTo("长沙皇朝今朝大酒店");
        assertThat(hotelDo.getAddress()).isEqualTo("芙蓉区车站北路138号（竹园路口）");
        assertThat(hotelDo.getCity()).isEqualTo("1901");
        assertThat(hotelDo.getStar()).isEqualTo(3);
        assertThat(hotelDo.getLongitude()).isEqualByComparingTo("113.010402918");
        assertThat(hotelDo.getLatitude()).isEqualByComparingTo("28.203585550");
        assertThat(hotelDo.getLongitude().scale()).isEqualTo(9);
        assertThat(hotelDo.getLatitude().scale()).isEqualTo(9);
        assertThat(hotelDo.getPrice()).isNull();
        assertThat(hotelDo.getTags()).isNull();
        assertThat(hotelDo.getCreateTime()).isNull();
        assertThat(hotelDo.getUpdateTime()).isNull();
    }

    @Test
    void convertsEveryRecordToHotelDo() {
        List<HotelDo> hotels = loader.load(SOURCE);

        assertThat(hotels).hasSize(17);
        assertThat(hotels).extracting(HotelDo::getId).doesNotHaveDuplicates();
        assertThat(hotels).allSatisfy(hotelDo -> {
            assertThat(hotelDo.getName()).isNotBlank();
            assertThat(hotelDo.getAddress()).isNotBlank();
            assertThat(hotelDo.getCity()).isIn("1801", "1901", "0301");
            assertThat(hotelDo.getStar()).isBetween(1, 5);
            assertThat(hotelDo.getLongitude()).isNotNull();
            assertThat(hotelDo.getLatitude()).isNotNull();
            assertThat(hotelDo.getLongitude().scale()).isGreaterThanOrEqualTo(6);
            assertThat(hotelDo.getLatitude().scale()).isGreaterThanOrEqualTo(6);
            assertThat(hotelDo.getLongitude()).isBetween(
                    new BigDecimal("-180"), new BigDecimal("180"));
            assertThat(hotelDo.getLatitude()).isBetween(
                    new BigDecimal("-90"), new BigDecimal("90"));
            assertThat(hotelDo.getRatePlanPrices()).isNotBlank();
            assertThat(hotelDo.getRatePlanRoomSts()).isNotBlank();
        });
    }

    @Test
    void serializesRatePlanObjectsAsOriginalJson() throws Exception {
        HotelDo hotelDo = loader.load(SOURCE).get(0);

        JsonNode prices = objectMapper.readTree(hotelDo.getRatePlanPrices());
        assertThat(prices.get("date").asText()).isEqualTo("2026-10-27");
        assertThat(prices.get("list").size()).isEqualTo(70);
        assertThat(prices.get("list").get(0).asInt()).isEqualTo(16);
        assertThat(prices.get("list").get(69).asInt()).isEqualTo(27);

        JsonNode roomSts = objectMapper.readTree(hotelDo.getRatePlanRoomSts());
        assertThat(roomSts.get("date").asText()).isEqualTo("2026-10-27");
        assertThat(roomSts.get("list").size()).isEqualTo(40);
        assertThat(roomSts.get("list").get(0).asInt()).isEqualTo(23);
        assertThat(roomSts.get("list").get(39).asInt()).isEqualTo(35);
    }

    @Test
    void fallsBackToBaiduCoordinatesWhenGoogleIsInvalid() {
        String json = "{\"RECORDS\":[{"
                + "\"id\":\"fallback-1\","
                + "\"hotel_name\":\"回退酒店\","
                + "\"google_lat\":\"invalid\","
                + "\"google_lon\":\"invalid\","
                + "\"baidu_lat\":\"31.230416000\","
                + "\"baidu_lon\":\"121.473701000\","
                + "\"star_rate\":\"5\","
                + "\"rp_room_sts\":{\"date\":\"2026-10-27\",\"list\":[1,2]},"
                + "\"rp_prices\":{\"date\":\"2026-10-27\",\"list\":[100,200]}"
                + "}]}";

        HotelDo hotelDo = loader.load(stream(json)).get(0);

        assertThat(hotelDo.getLongitude()).isEqualByComparingTo("121.473701000");
        assertThat(hotelDo.getLatitude()).isEqualByComparingTo("31.230416000");
        assertThat(hotelDo.getLongitude().scale()).isEqualTo(9);
        assertThat(hotelDo.getLatitude().scale()).isEqualTo(9);
        assertThat(hotelDo.getStar()).isEqualTo(5);
    }

    @Test
    void leavesCoordinatesNullWhenAllSourcesAreInvalid() {
        String json = "{\"RECORDS\":[{"
                + "\"id\":\"invalid-1\","
                + "\"hotel_name\":\"无效坐标酒店\","
                + "\"google_lat\":\"-1.000000000\","
                + "\"google_lon\":\"-1.000000000\","
                + "\"baidu_lat\":\"-1.000000000\","
                + "\"baidu_lon\":\"-1.000000000\","
                + "\"star_rate\":\"not-a-number\""
                + "}]}";

        HotelDo hotelDo = loader.load(stream(json)).get(0);

        assertThat(hotelDo.getLongitude()).isNull();
        assertThat(hotelDo.getLatitude()).isNull();
        assertThat(hotelDo.getStar()).isNull();
        assertThat(hotelDo.getRatePlanPrices()).isNull();
        assertThat(hotelDo.getRatePlanRoomSts()).isNull();
    }

    @Test
    void returnsEmptyListWhenRecordsAreMissing() {
        assertThat(loader.load(stream("{}"))).isEmpty();
    }

    @Test
    void rejectsMalformedJson() {
        assertThatThrownBy(() -> loader.load(stream("{")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("解析酒店基础数据失败");
    }

    @Test
    void doesNotModifySourceFile() throws Exception {
        byte[] before = Files.readAllBytes(SOURCE);
        assertThat(sha256(before)).isEqualTo(SOURCE_SHA256);

        loader.load(SOURCE);

        byte[] after = Files.readAllBytes(SOURCE);
        assertThat(sha256(after)).isEqualTo(SOURCE_SHA256);
        assertThat(after).isEqualTo(before);
    }

    private InputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }

    private String sha256(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder hex = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }
}
