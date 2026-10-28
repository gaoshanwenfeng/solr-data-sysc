package com.hotel.solr.service;

import com.hotel.solr.config.SolrProperties;
import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.model.req.HotelPatchReq;
import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.framework.exception.BusinessException;
import com.hotel.solr.service.builder.HotelQueryBuilder;
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
class HotelRpDtoServiceTest {

    @Mock
    private SolrClient solrClient;

    @Mock
    private QueryResponse queryResponse;

    private SolrProperties properties;

    private HotelRpService service;

    @BeforeEach
    void setUp() {
        properties = new SolrProperties();
        service = new HotelRpService(solrClient, properties, new HotelQueryBuilder());
    }

    @Test
    void saveStoresDocumentAndCommits() throws Exception {
        HotelRpDo hotelRpDto = HotelRpDo.builder().id("h1").name("外滩酒店").build();

        HotelRpDo saved = service.create(hotelRpDto);

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
        assertThatThrownBy(() -> service.create(HotelRpDo.builder().name("没有 id").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("id")
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void saveRejectsBlankName() {
        assertThatThrownBy(() -> service.create(HotelRpDo.builder().id("h1").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("name")
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void saveWrapsSolrFailureAsBadGateway() throws Exception {
        doThrow(new IOException("connection refused"))
                .when(solrClient).add(any(SolrInputDocument.class));

        assertThatThrownBy(() -> service.create(HotelRpDo.builder().id("h1").name("酒店").build()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Solr")
                .hasFieldOrPropertyWithValue("status", 502);
    }

    @Test
    void saveDoesNotCommitWhenCommitAfterWriteIsDisabled() throws Exception {
        properties.setCommitAfterWrite(false);

        service.create(HotelRpDo.builder().id("h1").name("酒店").build());

        verify(solrClient, never()).commit();
    }

    @Test
    void saveAcceptsCoordinatesWithSixDecimals() throws Exception {
        HotelRpDo hotelRpDo = HotelRpDo.builder()
                .id("h1")
                .name("外滩酒店")
                .longitude(new BigDecimal("121.473701"))
                .latitude(new BigDecimal("31.230416"))
                .build();

        HotelRpDo saved = service.create(hotelRpDo);

        ArgumentCaptor<SolrInputDocument> captor = ArgumentCaptor.forClass(SolrInputDocument.class);
        verify(solrClient).add(captor.capture());
        assertThat(captor.getValue().getFieldValue("longitude")).isEqualTo(121.473701d);
        assertThat(captor.getValue().getFieldValue("latitude")).isEqualTo(31.230416d);
        assertThat(saved.getLongitude()).isEqualByComparingTo("121.473701");
        assertThat(saved.getLatitude()).isEqualByComparingTo("31.230416");
    }

    @Test
    void saveRejectsCoordinatesWithFewerThanSixDecimals() throws Exception {
        HotelRpDo hotelRpDto = HotelRpDo.builder()
                .id("h1")
                .name("外滩酒店")
                .longitude(new BigDecimal("121.4737"))
                .latitude(new BigDecimal("31.2304"))
                .build();

        assertThatThrownBy(() -> service.create(hotelRpDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("6")
                .hasFieldOrPropertyWithValue("status", 400);
        verify(solrClient, never()).add(any(SolrInputDocument.class));
    }

    @Test
    void saveRejectsCoordinatesWithoutPair() {
        HotelRpDo hotelRpDto = HotelRpDo.builder()
                .id("h1")
                .name("外滩酒店")
                .longitude(new BigDecimal("121.473701"))
                .build();

        assertThatThrownBy(() -> service.create(hotelRpDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同时")
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void saveRejectsLongitudeOutOfRange() {
        HotelRpDo hotelRpDto = HotelRpDo.builder()
                .id("h1")
                .name("外滩酒店")
                .longitude(new BigDecimal("181.000000"))
                .latitude(new BigDecimal("31.230416"))
                .build();

        assertThatThrownBy(() -> service.create(hotelRpDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("longitude")
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void saveRejectsLatitudeOutOfRange() {
        HotelRpDo hotelRpDto = HotelRpDo.builder()
                .id("h1")
                .name("外滩酒店")
                .longitude(new BigDecimal("121.473701"))
                .latitude(new BigDecimal("-90.000001"))
                .build();

        assertThatThrownBy(() -> service.create(hotelRpDto))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("latitude")
                .hasFieldOrPropertyWithValue("status", 400);
    }

    @Test
    void getReturnsHotel() throws Exception {
        stubQueryResult(solrDocument("h1"));

        HotelRpDto hotelRpDto = service.get("h1");

        assertThat(hotelRpDto.getId()).isEqualTo("h1");
        assertThat(hotelRpDto.getName()).isEqualTo("酒店h1");
        assertThat(hotelRpDto.getStar()).isEqualTo(5);
        assertThat(hotelRpDto.getCreateTime()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
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

        HotelRpDo result = service.replace("h1", HotelRpDo.builder().name("新名字").build());

        verify(solrClient).add(any(SolrInputDocument.class));
        verify(solrClient).commit();
        assertThat(result.getId()).isEqualTo("h1");
        assertThat(result.getCreateTime()).isEqualTo(Instant.parse("2026-10-01T00:00:00Z"));
    }

    @Test
    void replaceCreatesWhenMissing() throws Exception {
        stubQueryResult();

        HotelRpDo result = service.replace("h-new", HotelRpDo.builder().name("新酒店").build());

        verify(solrClient).add(any(SolrInputDocument.class));
        assertThat(result.getId()).isEqualTo("h-new");
        assertThat(result.getCreateTime()).isNotNull();
    }

    @Test
    void patchAppliesAtomicFieldsAndReturnsUpdatedHotel() throws Exception {
        stubQueryResult(solrDocument("h1"));
        HotelPatchReq patch = new HotelPatchReq();
        patch.setName("新名字");
        patch.setPrice(888.0f);

        HotelRpDto result = service.patch("h1", patch);

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

        assertThatThrownBy(() -> service.patch("missing", new HotelPatchReq()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", 404);
        verify(solrClient, never()).add(any(SolrInputDocument.class));
        verify(solrClient, never()).commit();
    }

    @Test
    void patchAppliesCoordinatesAsAtomicSet() throws Exception {
        stubQueryResult(solrDocument("h1"));
        HotelPatchReq patch = new HotelPatchReq();
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
        HotelPatchReq patch = new HotelPatchReq();
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
    void queryWrapsSolrFailure() throws Exception {
        when(solrClient.query(any(SolrParams.class)))
                .thenThrow(new SolrServerException("query failed"));
        HotelRpQueryReq query = new HotelRpQueryReq();
        query.setCityId("1901");
        query.setCheckin("2026-10-27");
        query.setCheckout("2026-10-28");

        assertThatThrownBy(() -> service.query(query))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("status", 502);
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void saveBatchStoresAllDocuments() throws Exception {
        int count = service.saveBatch(Arrays.asList(
                HotelRpDo.builder().id("h1").name("酒店1").build(),
                HotelRpDo.builder().id("h2").name("酒店2").build()));

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
