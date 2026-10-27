package com.hotel.solr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hotel.solr.dto.HotelDo;
import com.hotel.solr.dto.HotelPatchRequest;
import com.hotel.solr.dto.HotelQuery;
import com.hotel.solr.dto.PageResult;
import com.hotel.solr.exception.BusinessException;
import com.hotel.solr.exception.GlobalExceptionHandler;
import com.hotel.solr.service.HotelService;
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
class HotelDoControllerTest {

    @Mock
    private HotelService hotelService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new HotelController(hotelService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void createReturns201AndSnakeCaseFields() throws Exception {
        when(hotelService.save(any(HotelDo.class))).thenAnswer(invocation -> {
            HotelDo hotelDo = invocation.getArgument(0);
            hotelDo.setCreateTime(Instant.parse("2026-10-27T00:00:00Z"));
            hotelDo.setUpdateTime(Instant.parse("2026-10-27T00:00:00Z"));
            return hotelDo;
        });

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"h1\",\"name\":\"外滩酒店\",\"city\":\"上海\",\"star\":5,"
                                + "\"price\":1299.5,\"tags\":[\"江景\"],"
                                + "\"rate_plan_prices\":\"[]\",\"rate_plan_room_sts\":\"{}\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value("h1"))
                .andExpect(jsonPath("$.data.rate_plan_prices").value("[]"))
                .andExpect(jsonPath("$.data.rate_plan_room_sts").value("{}"))
                .andExpect(jsonPath("$.data.create_time").value("2026-10-27T00:00:00Z"));

        ArgumentCaptor<HotelDo> captor = ArgumentCaptor.forClass(HotelDo.class);
        verify(hotelService).save(captor.capture());
        assertThat(captor.getValue().getRatePlanPrices()).isEqualTo("[]");
        assertThat(captor.getValue().getRatePlanRoomSts()).isEqualTo("{}");
    }

    @Test
    void createBindsCoordinatesWithSixDecimals() throws Exception {
        when(hotelService.save(any(HotelDo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"h1\",\"name\":\"外滩酒店\","
                                + "\"longitude\":121.473701,\"latitude\":31.230416}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.longitude").value(121.473701))
                .andExpect(jsonPath("$.data.latitude").value(31.230416));

        ArgumentCaptor<HotelDo> captor = ArgumentCaptor.forClass(HotelDo.class);
        verify(hotelService).save(captor.capture());
        HotelDo captured = captor.getValue();
        assertThat(captured.getLongitude()).isEqualByComparingTo("121.473701");
        assertThat(captured.getLatitude()).isEqualByComparingTo("31.230416");
        assertThat(captured.getLongitude().scale()).isEqualTo(6);
        assertThat(captured.getLatitude().scale()).isEqualTo(6);
    }

    @Test
    void createBindsCoordinatesWithTrailingZeros() throws Exception {
        when(hotelService.save(any(HotelDo.class))).thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"h1\",\"name\":\"外滩酒店\","
                                + "\"longitude\":121.473700,\"latitude\":31.230400}"))
                .andExpect(status().isCreated());

        ArgumentCaptor<HotelDo> captor = ArgumentCaptor.forClass(HotelDo.class);
        verify(hotelService).save(captor.capture());
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

        verify(hotelService, never()).save(any(HotelDo.class));
    }

    @Test
    void createReturns400WhenNameIsMissing() throws Exception {
        mockMvc.perform(post("/api/hotels")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":\"h1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value(containsString("name")));

        verify(hotelService, never()).save(any(HotelDo.class));
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
        when(hotelService.replace(eq("h1"), any(HotelDo.class)))
                .thenReturn(HotelDo.builder().id("h1").name("新名字").build());

        mockMvc.perform(put("/api/hotels/h1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名字\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("h1"));

        ArgumentCaptor<HotelDo> captor = ArgumentCaptor.forClass(HotelDo.class);
        verify(hotelService).replace(eq("h1"), captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("新名字");
    }

    @Test
    void patchAcceptsSnakeCaseFields() throws Exception {
        when(hotelService.patch(eq("h1"), any(HotelPatchRequest.class)))
                .thenReturn(HotelDo.builder().id("h1").name("新名字").price(888.0f).build());

        mockMvc.perform(patch("/api/hotels/h1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"新名字\",\"price\":888,"
                                + "\"rate_plan_prices\":\"[]\",\"rate_plan_room_sts\":\"{}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.price").value(888.0));

        ArgumentCaptor<HotelPatchRequest> captor = ArgumentCaptor.forClass(HotelPatchRequest.class);
        verify(hotelService).patch(eq("h1"), captor.capture());
        assertThat(captor.getValue().getRatePlanPrices()).isEqualTo("[]");
        assertThat(captor.getValue().getRatePlanRoomSts()).isEqualTo("{}");
    }

    @Test
    void getReturns404WhenMissing() throws Exception {
        when(hotelService.get("missing")).thenThrow(new BusinessException(404, "酒店不存在: missing"));

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

        verify(hotelService).delete("h1");
    }

    @Test
    void queryBindsSnakeCaseParameters() throws Exception {
        when(hotelService.query(any(HotelQuery.class)))
                .thenReturn(PageResult.of(0, 2, 20, Collections.emptyList()));

        mockMvc.perform(get("/api/hotels")
                        .param("keyword", "上海")
                        .param("city", "上海")
                        .param("star", "5")
                        .param("min_price", "100")
                        .param("max_price", "500")
                        .param("tags", "江景", "豪华")
                        .param("sort", "price asc")
                        .param("page", "2")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(20));

        ArgumentCaptor<HotelQuery> captor = ArgumentCaptor.forClass(HotelQuery.class);
        verify(hotelService).query(captor.capture());
        HotelQuery query = captor.getValue();
        assertThat(query.getKeyword()).isEqualTo("上海");
        assertThat(query.getCity()).isEqualTo("上海");
        assertThat(query.getStar()).isEqualTo(5);
        assertThat(query.getMinPrice()).isEqualTo(100.0f);
        assertThat(query.getMaxPrice()).isEqualTo(500.0f);
        assertThat(query.getTags()).containsExactly("江景", "豪华");
        assertThat(query.getSort()).isEqualTo("price asc");
        assertThat(query.getPage()).isEqualTo(2);
        assertThat(query.getSize()).isEqualTo(20);
    }

    @Test
    void batchSaveReturnsCount() throws Exception {
        when(hotelService.saveBatch(anyList())).thenReturn(2);

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

        verify(hotelService, never()).saveBatch(anyList());
    }

    @Test
    void batchDeleteReturnsCount() throws Exception {
        when(hotelService.deleteBatch(Arrays.asList("h1", "h2"))).thenReturn(2);

        mockMvc.perform(post("/api/hotels/batch-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ids\":[\"h1\",\"h2\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(2));

        verify(hotelService).deleteBatch(Arrays.asList("h1", "h2"));
    }
}
