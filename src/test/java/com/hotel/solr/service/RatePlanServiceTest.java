package com.hotel.solr.service;

import com.hotel.solr.config.SolrProperties;
import com.hotel.solr.framework.lang.PageResult;
import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.service.builder.HotelQueryBuilder;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.SolrParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RatePlanServiceTest {

    @Mock
    private SolrClient solrClient;

    @Mock
    private QueryResponse queryResponse;

    private RatePlanService service;

    @BeforeEach
    void setUp() {
        service = new RatePlanService(solrClient, new SolrProperties(), new HotelQueryBuilder());
    }

    @Test
    void queryUsesGroupingByHotelId() throws Exception {
        stubGroupResponse(docs(doc("rp-1", "hotel-1")), docs(doc("rp-2", "hotel-2")));

        service.query(queryReq());

        ArgumentCaptor<SolrQuery> captor = ArgumentCaptor.forClass(SolrQuery.class);
        verify(solrClient).query(captor.capture());
        SolrQuery solrQuery = captor.getValue();
        assertThat(solrQuery.get("group")).isEqualTo("true");
        assertThat(solrQuery.get("group.field")).isEqualTo("hotel_id");
        assertThat(solrQuery.get("group.ngroups")).isEqualTo("true");
    }

    @Test
    void queryReturnsOneDocumentPerGroupAndGroupCountAsTotal() throws Exception {
        stubGroupResponse(
                docs(doc("rp-1", "hotel-1"), doc("rp-2", "hotel-1")),
                docs(doc("rp-3", "hotel-2")));

        PageResult<HotelRpDto> result = service.query(queryReq());

        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getTotalPages()).isEqualTo(1);
        assertThat(result.getItems()).extracting(HotelRpDto::getHotelId)
                .containsExactly("hotel-1", "hotel-2");
    }

    @Test
    void queryFallsBackToPlainResultWhenGroupingIsAbsent() throws Exception {
        SolrDocumentList list = docs(doc("rp-1", "hotel-1"));
        list.setNumFound(1);
        when(solrClient.query(any(SolrParams.class))).thenReturn(queryResponse);
        when(queryResponse.getResults()).thenReturn(list);

        PageResult<HotelRpDto> result = service.query(queryReq());

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems()).extracting(HotelRpDto::getHotelId)
                .containsExactly("hotel-1");
    }

    private void stubGroupResponse(SolrDocumentList... groupResults) throws Exception {
        GroupCommand command = mock(GroupCommand.class);
        List<Group> groups = new ArrayList<>();
        for (SolrDocumentList groupResult : groupResults) {
            Group group = mock(Group.class);
            when(group.getResult()).thenReturn(groupResult);
            groups.add(group);
        }
        when(command.getValues()).thenReturn(groups);
        when(command.getNGroups()).thenReturn(groups.size());
        GroupResponse groupResponse = mock(GroupResponse.class);
        when(groupResponse.getValues()).thenReturn(Collections.singletonList(command));
        when(queryResponse.getGroupResponse()).thenReturn(groupResponse);
        when(solrClient.query(any(SolrParams.class))).thenReturn(queryResponse);
    }

    private SolrDocumentList docs(SolrDocument... documents) {
        SolrDocumentList list = new SolrDocumentList();
        list.setNumFound(documents.length);
        list.addAll(Arrays.asList(documents));
        return list;
    }

    private SolrDocument doc(String id, String hotelId) {
        SolrDocument document = new SolrDocument();
        document.setField("id", id);
        document.setField("hotel_id", hotelId);
        document.setField("name", "酒店" + hotelId);
        return document;
    }

    private HotelRpQueryReq queryReq() {
        HotelRpQueryReq req = new HotelRpQueryReq();
        req.setCityId("1901");
        req.setCheckin("2026-10-27");
        req.setCheckout("2026-10-28");
        return req;
    }
}
