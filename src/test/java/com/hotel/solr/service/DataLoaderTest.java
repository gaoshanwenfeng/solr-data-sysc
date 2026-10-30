package com.hotel.solr.service;

import com.hotel.solr.model.bo.HotelInfoRecordBo;
import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.framework.util.RpByteUtils;
import com.hotel.solr.util.DataLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DisplayName("HotelBaseDataLoader 基础数据读取与转换")
class DataLoaderTest {

    @Resource
    private HotelRpService hotelRpService;
    private List<HotelRpDo> hotels;

    @Test
    void readsAll53FieldsOfEveryRecord() {
        List<HotelInfoRecordBo> records = DataLoader.readClasspath(DataLoader.HOTEL_RP_FILE);

        assertThat(HotelInfoRecordBo.class.getDeclaredFields()).hasSize(53);
        assertThat(records).hasSize(17);
        assertThat(records).allSatisfy(record -> {
            // 源数据中 fax 为可选字段，多数记录不提供
            assertThat(record).hasNoNullFieldsOrPropertiesExcept("fax");
            assertThat(record.getRpRoomSts().getDate()).isNotBlank();
            assertThat(record.getRpRoomSts().getList()).isNotEmpty();
            assertThat(record.getRpPrices().getDate()).isNotBlank();
            assertThat(record.getRpPrices().getList()).isNotEmpty();
        });

        HotelInfoRecordBo first = records.get(0);
        assertThat(first.getId()).isEqualTo("4887");
        assertThat(first.getMhotelId()).isEqualTo("01901360");
        assertThat(first.getHotelName()).isEqualTo("长沙皇朝今朝大酒店");
        assertThat(first.getPostalCode()).isEqualTo("410001");
        assertThat(first.getPhone()).isEqualTo("0731-82**1645");
        assertThat(first.getTimestamp()).isEqualTo("13/7/2018 10:46:35");
        assertThat(first.getRpRoomSts().getDate()).isEqualTo("2026-10-27");
        assertThat(first.getRpRoomSts().getList()).hasSize(40).startsWith(23, 1, 41);
        assertThat(first.getRpPrices().getDate()).isEqualTo("2026-10-27");
        assertThat(first.getRpPrices().getList()).hasSize(70).startsWith(16, 29, 3);
    }

    @Test
    void mapsFirstRecordWithExpectedValues() {
        HotelRpDo hotelRpDto = DataLoader.loadClasspath(DataLoader.HOTEL_RP_FILE).get(0);

        assertThat(hotelRpDto.getId()).isEqualTo("4887");
        assertThat(hotelRpDto.getName()).isEqualTo("长沙皇朝今朝大酒店");
        assertThat(hotelRpDto.getAddress()).isEqualTo("芙蓉区车站北路138号（竹园路口）");
        assertThat(hotelRpDto.getCityId()).isEqualTo("1901");
        assertThat(hotelRpDto.getCityName()).isEqualTo("长沙市");
        assertThat(hotelRpDto.getStar()).isEqualTo(3);
        assertThat(hotelRpDto.getLongitude()).isEqualByComparingTo("113.010402918");
        assertThat(hotelRpDto.getLatitude()).isEqualByComparingTo("28.203585550");
        assertThat(hotelRpDto.getLongitude().scale()).isEqualTo(9);
        assertThat(hotelRpDto.getLatitude().scale()).isEqualTo(9);
        assertThat(hotelRpDto.getTags()).isNull();
        assertThat(hotelRpDto.getCreateTime()).isNull();
        assertThat(hotelRpDto.getUpdateTime()).isNull();
    }

    @Test
    void convertsEveryRecordToHotelDo() {
        List<HotelRpDo> hotels = DataLoader.loadClasspath(DataLoader.HOTEL_RP_FILE);

        assertThat(hotels).hasSize(17);
        assertThat(hotels).extracting(HotelRpDo::getId).doesNotHaveDuplicates();
        assertThat(hotels).allSatisfy(hotelDo -> {
            assertThat(hotelDo.getName()).isNotBlank();
            assertThat(hotelDo.getAddress()).isNotBlank();
            assertThat(hotelDo.getCityId()).isIn("1801", "1901", "0301");
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
    void encodesRatePlanWithDateAndOriginalValues() {
        HotelRpDo hotelRpDto = DataLoader.loadClasspath(DataLoader.HOTEL_RP_FILE).get(0);

        byte[] prices = RpByteUtils.stringToBytes(hotelRpDto.getRatePlanPrices());
        assertThat(RpByteUtils.BASE_DATE.plusDays(RpByteUtils.getDateOffset(prices)))
                .isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(RpByteUtils.getDataCount(prices)).isEqualTo(70);
        assertThat(RpByteUtils.getData(prices, 0)).isEqualTo(16);
        assertThat(RpByteUtils.getData(prices, 69)).isEqualTo(27);

        byte[] roomSts = RpByteUtils.stringToBytes(hotelRpDto.getRatePlanRoomSts());
        assertThat(RpByteUtils.BASE_DATE.plusDays(RpByteUtils.getDateOffset(roomSts)))
                .isEqualTo(LocalDate.of(2026, 10, 27));
        assertThat(RpByteUtils.getDataCount(roomSts)).isEqualTo(40);
        assertThat(RpByteUtils.getData(roomSts, 0)).isEqualTo(23);
        assertThat(RpByteUtils.getData(roomSts, 39)).isEqualTo(35);
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

        HotelRpDo hotelRpDto = DataLoader.load(stream(json)).get(0);

        assertThat(hotelRpDto.getLongitude()).isEqualByComparingTo("121.473701000");
        assertThat(hotelRpDto.getLatitude()).isEqualByComparingTo("31.230416000");
        assertThat(hotelRpDto.getLongitude().scale()).isEqualTo(9);
        assertThat(hotelRpDto.getLatitude().scale()).isEqualTo(9);
        assertThat(hotelRpDto.getStar()).isEqualTo(5);
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

        HotelRpDo hotelRpDto = DataLoader.load(stream(json)).get(0);

        assertThat(hotelRpDto.getLongitude()).isNull();
        assertThat(hotelRpDto.getLatitude()).isNull();
        assertThat(hotelRpDto.getStar()).isNull();
        assertThat(hotelRpDto.getRatePlanPrices()).isNull();
        assertThat(hotelRpDto.getRatePlanRoomSts()).isNull();
    }




    private InputStream stream(String json) {
        return new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
    }


    @Test
    void getHotel() {
        HotelRpDto h = hotelRpService.get("12163");
        assertThat(h).isNotNull();
        System.out.println("prices : " + h.getPriceList());
        System.out.println("roomSts : " + h.getStsList());
    }
}
