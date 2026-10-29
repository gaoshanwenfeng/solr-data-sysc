package com.hotel.solr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.solr.model.bo.HotelInfoRecordBo;
import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.util.RpByteUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ExtendWith(SpringExtension.class)
@DisplayName("HotelBaseDataLoader 基础数据读取与转换")
class RpBytesUtilsTest {

    @Autowired
    private HotelRpDataLoader loader;

    private List<HotelRpDo> hotels;

    void loadHotels() {
        ObjectMapper objectMapper = new ObjectMapper();
        HotelRpDataLoader loader = new HotelRpDataLoader(objectMapper);
        hotels = loader.loadDefault();
    }





    @Test
    void getHotel() {
        String id = "";
        HotelRpDo hotel = null;
        for (HotelRpDo h: hotels) {

        }
    }
}
