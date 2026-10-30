package com.hotel.solr.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.util.DataLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("DataLoader 基础数据读取与转换")
class RpBytesUtilsTest {

    @BeforeEach
    void setUp() {
        new DataLoader(new ObjectMapper());
    }

    @Test
    void loadsHotelsFromClasspath() {
        List<HotelRpDo> hotels = DataLoader.loadClasspath(DataLoader.HOTEL_RP_FILE);

        assertThat(hotels).isNotEmpty();
        assertThat(hotels.get(0).getId()).isNotBlank();
    }
}
