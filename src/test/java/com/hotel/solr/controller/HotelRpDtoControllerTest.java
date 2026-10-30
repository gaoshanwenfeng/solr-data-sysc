package com.hotel.solr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.model.req.HotelPatchReq;
import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.framework.lang.PageResult;
import com.hotel.solr.framework.exception.BusinessException;
import com.hotel.solr.framework.mvc.GlobalExceptionHandler;
import com.hotel.solr.service.HotelRpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HotelRpDtoControllerTest {

    @Mock
    private HotelRpService hotelRpService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new HotelController(hotelRpService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createReturns201AndCamelCaseFields() throws Exception {
        when(hotelRpService.create(any(HotelRpDo.class))).thenAnswer(invocation -> {
            HotelRpDo hotelRpDo = invocation.getArgument(0);
            hotelRpDo.setCreateTime(Instant.parse("2026-10-27T00:00:00Z"));
            hotelRpDo.setUpdateTime(Instant.parse("2026-10-27T00:00:00Z"));
            return hotelRpDo;
        });

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"h1\",\"name\":\"外滩酒店\",\"cityId\":\"1901\","
                                + "\"cityName\":\"上海\",\"star\":5,"
                                + "\"price\":1299.5,\"tags\":[\"江景\"],"
                                + "\"ratePlanPrices\":\"[]\",\"ratePlanRoomSts\":\"{}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("h1"))
                .andExpect(jsonPath("$.data.ratePlanPrices").value("[]"))
                .andExpect(jsonPath("$.data.ratePlanRoomSts").value("{}"))
                .andExpect(jsonPath("$.data.createTime").value("2026-10-27T00:00:00Z"));

        ArgumentCaptor<HotelRpDo> captor = ArgumentCaptor.forClass(HotelRpDo.class);
        verify(hotelRpService).create(captor.capture());
        assertThat(captor.getValue().getRatePlanPrices()).isEqualTo("[]");
        assertThat(captor.getValue().getRatePlanRoomSts()).isEqualTo("{}");
    }

    @Test
    void createBindsCoordinatesWithSixDecimals() throws Exception {
        when(hotelRpService.create(any(HotelRpDo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"h1\",\"name\":\"外滩酒店\","
                                + "\"longitude\":121.473701,\"latitude\":31.230416}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.longitude").value(121.473701))
                .andExpect(jsonPath("$.data.latitude").value(31.230416));

        ArgumentCaptor<HotelRpDo> captor = ArgumentCaptor.forClass(HotelRpDo.class);
        verify(hotelRpService).create(captor.capture());
        HotelRpDo captured = captor.getValue();
        assertThat(captured.getLongitude()).isEqualByComparingTo("121.473701");
        assertThat(captured.getLatitude()).isEqualByComparingTo("31.230416");
        assertThat(captured.getLongitude().scale()).isEqualTo(6);
        assertThat(captured.getLatitude().scale()).isEqualTo(6);
    }

    @Test
    void createBindsCoordinatesWithTrailingZeros() throws Exception {
        when(hotelRpService.create(any(HotelRpDo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"h1\",\"name\":\"外滩酒店\","
                                + "\"longitude\":121.473700,\"latitude\":31.230400}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<HotelRpDo> captor = ArgumentCaptor.forClass(HotelRpDo.class);
        verify(hotelRpService).create(captor.capture());
        assertThat(captor.getValue().getLongitude()).isEqualByComparingTo("121.4737");
        assertThat(captor.getValue().getLatitude()).isEqualByComparingTo("31.2304");
        assertThat(captor.getValue().getLongitude().scale()).isEqualTo(6);
        assertThat(captor.getValue().getLatitude().scale()).isEqualTo(6);
    }

    @Test
    void createReturns400WhenLongitudeIsOutOfRange() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"h1\",\"name\":\"外滩酒店\","
                                + "\"longitude\":181.000000,\"latitude\":31.230416}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("longitude")));

        verify(hotelRpService, never()).create(any(HotelRpDo.class));
    }

    @Test
    void createReturns400WhenNameIsMissing() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"h1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("name")));

        verify(hotelRpService, never()).create(any(HotelRpDo.class));
    }

    @Test
    void createReturns400WhenStarIsOutOfRange() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"h1\",\"name\":\"酒店\",\"star\":9}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("star")));
    }

    @Test
    void replacePassesPathIdToService() throws Exception {
        when(hotelRpService.replace(eq("h1"), any(HotelRpDo.class)))
                .thenReturn(HotelRpDo.builder().id("h1").name("新名字").build());

        mockMvc.perform(put("/api/hotels/h1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名字\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("h1"));

        ArgumentCaptor<HotelRpDo> captor = ArgumentCaptor.forClass(HotelRpDo.class);
        verify(hotelRpService).replace(eq("h1"), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("新名字");
    }

    @Test
    void patchAcceptsCamelCaseFields() throws Exception {
        when(hotelRpService.patch(eq("h1"), any(HotelPatchReq.class)))
                .thenReturn(HotelRpDto.builder().id("h1").name("新名字").price(888.0f).build());

        mockMvc.perform(patch("/api/hotels/h1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名字\",\"price\":888,"
                                + "\"ratePlanPrices\":\"[]\",\"ratePlanRoomSts\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(888.0));

        ArgumentCaptor<HotelPatchReq> captor = ArgumentCaptor.forClass(HotelPatchReq.class);
        verify(hotelRpService).patch(eq("h1"), captor.capture());
        assertThat(captor.getValue().getRatePlanPrices()).isEqualTo("[]");
        assertThat(captor.getValue().getRatePlanRoomSts()).isEqualTo("{}");
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(hotelRpService.get("missing")).thenThrow(new BusinessException(404, "酒店不存在: missing"));

        mockMvc.perform(get("/api/hotels/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("酒店不存在: missing"));
    }

    @Test
    void deleteReturnsOk() throws Exception {
        mockMvc.perform(delete("/api/hotels/h1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(hotelRpService).delete("h1");
    }

    @Test
    void queryBindsCamelCaseParameters() throws Exception {
        when(hotelRpService.query(any(HotelRpQueryReq.class)))
                .thenReturn(PageResult.of(0, 2, 20, Collections.emptyList()));

        mockMvc.perform(post("/api/hotels/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"keyword\":\"上海\",\"cityId\":\"1901\","
                                + "\"checkin\":\"2026-10-27\",\"checkout\":\"2026-10-28\","
                                + "\"star\":5,\"priceFrom\":100,\"priceTo\":500,"
                                + "\"tags\":[\"江景\",\"豪华\"],\"sort\":\"price asc\","
                                + "\"page\":2,\"size\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20));

        ArgumentCaptor<HotelRpQueryReq> captor = ArgumentCaptor.forClass(HotelRpQueryReq.class);
        verify(hotelRpService).query(captor.capture());
        HotelRpQueryReq query = captor.getValue();
        assertThat(query.getKeyword()).isEqualTo("上海");
        assertThat(query.getCityId()).isEqualTo("1901");
        assertThat(query.getCheckin()).isEqualTo("2026-10-27");
        assertThat(query.getCheckout()).isEqualTo("2026-10-28");
        assertThat(query.getStar()).isEqualTo(5);
        assertThat(query.getPriceFrom()).isEqualTo(100);
        assertThat(query.getPriceTo()).isEqualTo(500);
        assertThat(query.getTags()).containsExactly("江景", "豪华");
        assertThat(query.getSort()).isEqualTo("price asc");
        assertThat(query.getPage()).isEqualTo(2);
        assertThat(query.getSize()).isEqualTo(20);
    }

    @Test
    void batchSaveReturnsCount() throws Exception {
        when(hotelRpService.saveBatch(anyList())).thenReturn(2);

        mockMvc.perform(post("/api/hotels/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hotels\":[{\"id\":\"h1\",\"name\":\"酒店1\"},"
                                + "{\"id\":\"h2\",\"name\":\"酒店2\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));
    }

    @Test
    void batchSaveRejectsEmptyList() throws Exception {
        mockMvc.perform(post("/api/hotels/batch")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"hotels\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("hotels")));

        verify(hotelRpService, never()).saveBatch(anyList());
    }

    @Test
    void batchDeleteReturnsCount() throws Exception {
        when(hotelRpService.deleteBatch(Arrays.asList("h1", "h2"))).thenReturn(2);

        mockMvc.perform(post("/api/hotels/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"h1\",\"h2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));

        verify(hotelRpService).deleteBatch(Arrays.asList("h1", "h2"));
    }
}
