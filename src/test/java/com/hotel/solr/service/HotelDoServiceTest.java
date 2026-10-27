package com.hotel.solr.service;

import com.hotel.solr.config.SolrProperties;
import com.hotel.solr.dto.HotelDo;
import com.hotel.solr.dto.HotelPatchRequest;
import com.hotel.solr.dto.HotelQuery;
import com.hotel.solr.dto.PageResult;
import com.hotel.solr.exception.BusinessException;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HotelDoServiceTest {

    @Mock
    private SolrClient solrClient;

    @Mock
    private QueryResponse queryResponse;

    private SolrProperties properties;

    private HotelService service;

    @BeforeEach
    void setUp() {
        properties = new SolrProperties();
        service = new HotelService(solrClient, properties, new HotelSolrConverter(), new HotelQueryBuilder());
    }

    @Test
    void saveStoresDocumentAndCommits() throws Exception {
        HotelDo hotelDo = HotelDo.builder().id("h1").name("外滩酒店").build();

        HotelDo saved = service.create(hotelDo);

        ArgumentCaptor<SolrInputDocument> captor = ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(solrClient).add(captor.capture());
        verify(solrClient).commit();
        assertThat(saved.getCreateTime()).isNotNull();
        assertThat(saved.getUpdateTime()).isNotNull();
        assertThat(captor.getValue().getFieldValue("id")).isEqualTo("h1");
        assertThat(captor.getValue().getFieldValue("create_time")).isNotNull();
    }

    @Test
    void saveRejectsBlankId() {
        assertThatThrownBy(() -> service.create(HotelDo.builder().name("没有 id").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("id")
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void saveRejectsBlankName() {
        assertThatThrownBy(() -> service.create(HotelDo.builder().id("h1").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("name")
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void saveWrapsSolrFailureAsBadGateway() throws Exception {
        doThrow(new IOException("connection refused"))
                .when(solrClient).add(any(SolrInputDocument.class));

        assertThatThrownBy(() -> service.create(HotelDo.builder().id("h1").name("酒店").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Solr")
                .hasFieldOrPropertyWithValue("status", 502);
    }

    @Test
    void saveDoesNotCommitWhenCommitAfterWriteIsDisabled() throws Exception {
        properties.setCommitAfterWrite(false);

        service.create(HotelDo.builder().id("h1").name("酒店").build());

        verify(solrClient, never()).commit();
    }

    @Test
    void saveAcceptsCoordinatesWithSixDecimals() throws Exception {
        HotelDo hotelDo = HotelDo.builder()
                .id("h1")
                .name("外滩酒店")
                .longitude(new BigDecimal("121.473701"))
                .latitude(new BigDecimal("31.230416"))
                .build();

        HotelDo saved = service.create(hotelDo);

        ArgumentCaptor<SolrInputDocument> captor = ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(solrClient).add(captor.capture());
        assertThat(captor.getValue().getFieldValue("longitude")).isEqualTo(121.473701d);
        assertThat(captor.getValue().getFieldValue("latitude")).isEqualTo(31.230416d);
        assertThat(saved.getLongitude()).isEqualByComparingTo("121.473701");
        assertThat(saved.getLatitude()).isEqualByComparingTo("31.230416");
    }

    @Test
    void saveRejectsCoordinatesWithFewerThanSixDecimals() throws Exception {
        HotelDo hotelDo = HotelDo.builder()
                .id("h1")
                .name("外滩酒店")
                .longitude(new BigDecimal("121.4737"))
                .latitude(new BigDecimal("31.2304"))
                .build();

        assertThatThrownBy(() -> service.create(hotelDo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("6")
                .hasFieldOrPropertyWithValue("status", 400);
        verify(solrClient, never()).add(any(SolrInputDocument.class));
    }

    @Test
    void saveRejectsCoordinatesWithoutPair() {
        HotelDo hotelDo = HotelDo.builder()
                .id("h1")
                .name("外滩酒店")
                .longitude(new BigDecimal("121.473701"))
                .build();

        assertThatThrownBy(() -> service.create(hotelDo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同时")
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void saveRejectsLongitudeOutOfRange() {
        HotelDo hotelDo = HotelDo.builder()
                .id("h1")
                .name("外滩酒店")
                .longitude(new BigDecimal("181.000000"))
                .latitude(new BigDecimal("31.230416"))
                .build();

        assertThatThrownBy(() -> service.create(hotelDo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("longitude")
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void saveRejectsLatitudeOutOfRange() {
        HotelDo hotelDo = HotelDo.builder()
                .id("h1")
                .name("外滩酒店")
                .longitude(new BigDecimal("121.473701"))
                .latitude(new BigDecimal("-90.000001"))
                .build();

        assertThatThrownBy(() -> service.create(hotelDo))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("latitude")
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void getReturnsHotel() throws Exception {
        stubQueryResult(solrDocument("h1"));

        HotelDo hotelDo = service.get("h1");

        assertThat(hotelDo.getId()).isEqualTo("h1");
        assertThat(hotelDo.getName()).isEqualTo("酒店h1");
        assertThat(hotelDo.getStar()).isEqualTo(5);
        assertThat(hotelDo.getCreateTime()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void getThrowsNotFoundWhenMissing() throws Exception {
        stubQueryResult();

        assertThatThrownBy(() -> service.get("missing"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", 404);
    }

    @Test
    void replacePreservesCreateTime() throws Exception {
        stubQueryResult(solrDocument("h1"));

        HotelDo result = service.replace("h1", HotelDo.builder().name("新名字").build());

        verify(solrClient).add(any(SolrInputDocument.class));
        verify(solrClient).commit();
        assertThat(result.getId()).isEqualTo("h1");
        assertThat(result.getCreateTime()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void replaceCreatesWhenMissing() throws Exception {
        stubQueryResult();

        HotelDo result = service.replace("h-new", HotelDo.builder().name("新酒店").build());

        verify(solrClient).add(any(SolrInputDocument.class));
        assertThat(result.getId()).isEqualTo("h-new");
        assertThat(result.getCreateTime()).isNotNull();
    }

    @Test
    void patchAppliesAtomicFieldsAndReturnsUpdatedHotel() throws Exception {
        stubQueryResult(solrDocument("h1"));
        HotelPatchRequest patch = new HotelPatchRequest();
        patch.setName("新名字");
        patch.setPrice(888.0f);

        HotelDo result = service.patch("h1", patch);

        ArgumentCaptor<SolrInputDocument> captor = ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(solrClient).add(captor.capture());
        verify(solrClient).commit();
        assertThat(result).isNotNull();
        SolrInputDocument doc = captor.getValue();
        assertThat(doc.getFieldValue("id")).isEqualTo("h1");
        assertThat(doc.getFieldValue("name")).isEqualTo(Collections.singletonMap("set", "新名字"));
        assertThat(doc.getFieldValue("price")).isEqualTo(Collections.singletonMap("set", 888.0f));
        assertThat((Map<String, Object>) doc.getFieldValue("update_time")).containsKey("set");
    }

    @Test
    void patchThrowsNotFoundWhenMissing() throws Exception {
        stubQueryResult();

        assertThatThrownBy(() -> service.patch("missing", new HotelPatchRequest()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", 404);
        verify(solrClient, never()).add(any(SolrInputDocument.class));
        verify(solrClient, never()).commit();
    }

    @Test
    void patchAppliesCoordinatesAsAtomicSet() throws Exception {
        stubQueryResult(solrDocument("h1"));
        HotelPatchRequest patch = new HotelPatchRequest();
        patch.setLongitude(new BigDecimal("121.473701"));
        patch.setLatitude(new BigDecimal("31.230416"));

        service.patch("h1", patch);

        ArgumentCaptor<SolrInputDocument> captor = ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(solrClient).add(captor.capture());
        assertThat(captor.getValue().getFieldValue("longitude"))
                .isEqualTo(Collections.singletonMap("set", 121.473701d));
        assertThat(captor.getValue().getFieldValue("latitude"))
                .isEqualTo(Collections.singletonMap("set", 31.230416d));
    }

    @Test
    void patchRejectsCoordinatesWithFewerThanSixDecimals() throws Exception {
        stubQueryResult(solrDocument("h1"));
        HotelPatchRequest patch = new HotelPatchRequest();
        patch.setLongitude(new BigDecimal("121.4737"));
        patch.setLatitude(new BigDecimal("31.2304"));

        assertThatThrownBy(() -> service.patch("h1", patch))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("6")
                .hasFieldOrPropertyWithValue("status", 400);
        verify(solrClient, never()).add(any(SolrInputDocument.class));
        verify(solrClient, never()).commit();
    }

    @Test
    void deleteExistingRemovesAndCommits() throws Exception {
        stubQueryResult(solrDocument("h1"));

        service.delete("h1");

        verify(solrClient).deleteById("h1");
        verify(solrClient).commit();
    }

    @Test
    void deleteMissingThrowsNotFound() throws Exception {
        stubQueryResult();

        assertThatThrownBy(() -> service.delete("missing"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", 404);
        verify(solrClient, never()).deleteById("missing");
    }

    @Test
    void queryReturnsMappedPage() throws Exception {
        SolrDocumentList list = new SolrDocumentList();
        list.setNumFound(2);
        list.add(solrDocument("h1"));
        list.add(solrDocument("h2"));
        stubQueryResponse(list);

        PageResult<HotelDo> page = service.query(HotelQuery.builder().page(1).size(10).build());

        assertThat(page.getTotal()).isEqualTo(2);
        assertThat(page.getTotalPages()).isEqualTo(1);
        assertThat(page.getPage()).isEqualTo(1);
        assertThat(page.getSize()).isEqualTo(10);
        assertThat(page.getItems()).extracting(HotelDo::getId).containsExactly("h1", "h2");
    }

    @Test
    void queryWrapsSolrFailure() throws Exception {
        when(solrClient.query(any(SolrParams.class)))
                .thenThrow(new SolrServerException("query failed"));

        assertThatThrownBy(() -> service.query(new HotelQuery()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", 502);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void saveBatchStoresAllDocuments() throws Exception {
        int count = service.saveBatch(Arrays.asList(
                HotelDo.builder().id("h1").name("酒店1").build(),
                HotelDo.builder().id("h2").name("酒店2").build()));

        ArgumentCaptor<Collection<SolrInputDocument>> captor =
                ArgumentCaptor.forClass((Class) Collection.class);
        verify(solrClient).add(captor.capture());
        verify(solrClient).commit();
        assertThat(count).isEqualTo(2);
        assertThat(captor.getValue()).hasSize(2);
    }

    @Test
    void saveBatchRejectsEmptyList() {
        assertThatThrownBy(() -> service.saveBatch(Collections.emptyList()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void deleteBatchDeletesAllIds() throws Exception {
        int count = service.deleteBatch(Arrays.asList("h1", "h2"));

        verify(solrClient).deleteById(Arrays.asList("h1", "h2"));
        verify(solrClient).commit();
        assertThat(count).isEqualTo(2);
    }

    @Test
    void deleteBatchRejectsEmptyList() {
        assertThatThrownBy(() -> service.deleteBatch(Collections.emptyList()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", 400);
    }

    private void stubQueryResult(SolrDocument... documents) throws Exception {
        SolrDocumentList list = new SolrDocumentList();
        list.setNumFound(documents.length);
        for (SolrDocument document : documents) {
            list.add(document);
        }
        stubQueryResponse(list);
    }

    private void stubQueryResponse(SolrDocumentList list) throws Exception {
        when(solrClient.query(any(SolrParams.class))).thenReturn(queryResponse);
        when(queryResponse.getResults()).thenReturn(list);
    }

    private SolrDocument solrDocument(String id) {
        SolrDocument doc = new SolrDocument();
        doc.setField("id", id);
        doc.setField("name", "酒店" + id);
        doc.setField("city_id", "1901");
        doc.setField("city_name", "上海");
        doc.setField("star", 5);
        doc.setField("price", 100.0f);
        doc.setField("create_time", Date.from(Instant.parse("2026-10-01T00:00:00Z")));
        return doc;
    }
}
