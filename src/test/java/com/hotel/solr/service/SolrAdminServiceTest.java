package com.hotel.solr.service;

import com.hotel.solr.config.SolrProperties;
import com.hotel.solr.model.dto.SolrCoreStatusDto;
import com.hotel.solr.framework.exception.BusinessException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SolrAdminServiceTest {

    @Mock
    private SolrClient solrClient;

    @Mock
    private QueryResponse queryResponse;

    private SolrAdminService service;

    @BeforeEach
    void setUp() {
        SolrProperties properties = new SolrProperties();
        properties.setBaseUrl("http://localhost:8983/solr/");
        properties.setCore("hotel_rp");
        service = new SolrAdminService(solrClient, properties);
    }

    @Test
    void healthReturnsUpWithDocumentCount() throws Exception {
        SolrDocumentList list = new SolrDocumentList();
        list.setNumFound(42);
        when(solrClient.query(any(SolrParams.class))).thenReturn(queryResponse);
        when(queryResponse.getResults()).thenReturn(list);
        when(queryResponse.getElapsedTime()).thenReturn(7L);

        SolrCoreStatusDto status = service.health();

        assertThat(status.getStatus()).isEqualTo("UP");
        assertThat(status.getDocumentCount()).isEqualTo(42);
        assertThat(status.getElapsedTimeMs()).isEqualTo(7);
        assertThat(status.getCore()).isEqualTo("hotel");
        assertThat(status.getSolrUrl()).isEqualTo("http://localhost:8983/solr/hotel");
    }

    @Test
    void healthReturnsDownWhenSolrIsUnavailable() throws Exception {
        when(solrClient.query(any(SolrParams.class)))
                .thenThrow(new SolrServerException("connection refused"));

        SolrCoreStatusDto status = service.health();

        assertThat(status.getStatus()).isEqualTo("DOWN");
        assertThat(status.getMessage()).contains("connection refused");
        assertThat(status.getCore()).isEqualTo("hotel");
    }

    @Test
    void commitCallsSolrClient() throws Exception {
        service.commit();

        verify(solrClient).commit();
    }

    @Test
    void commitWrapsSolrFailure() throws Exception {
        doThrow(new IOException("commit failed")).when(solrClient).commit();

        assertThatThrownBy(() -> service.commit())
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", 502);
    }
}
