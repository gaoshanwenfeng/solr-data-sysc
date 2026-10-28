package com.hotel.solr.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.hotel.solr.model.dto.SolrCoreStatusDto;
import com.hotel.solr.framework.GlobalExceptionHandler;
import com.hotel.solr.service.SolrAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SolrAdminControllerTest {

    @Mock
    private SolrAdminService solrAdminService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        mockMvc = MockMvcBuilders.standaloneSetup(new SolrAdminController(solrAdminService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void healthReturns200WhenUp() throws Exception {
        when(solrAdminService.health()).thenReturn(SolrCoreStatusDto.builder()
                .status("UP")
                .core("hotel")
                .solrUrl("http://localhost:8983/solr/hotel")
                .documentCount(3L)
                .elapsedTimeMs(5L)
                .build());

        mockMvc.perform(get("/api/solr/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"))
                .andExpect(jsonPath("$.data.core").value("hotel"))
                .andExpect(jsonPath("$.data.document_count").value(3));
    }

    @Test
    void healthReturns503WhenDown() throws Exception {
        when(solrAdminService.health()).thenReturn(SolrCoreStatusDto.builder()
                .status("DOWN")
                .core("hotel")
                .message("connection refused")
                .build());

        mockMvc.perform(get("/api/solr/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.data.status").value("DOWN"));
    }

    @Test
    void commitReturnsOk() throws Exception {
        mockMvc.perform(post("/api/solr/commit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(solrAdminService).commit();
    }
}
