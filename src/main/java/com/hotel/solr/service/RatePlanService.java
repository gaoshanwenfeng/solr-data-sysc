package com.hotel.solr.service;

import com.hotel.solr.config.SolrProperties;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.service.builder.HotelQueryBuilder;
import com.hotel.solr.service.converter.HotelRpSolrConverter;
import com.hotel.solr.util.DataLoader;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.Group;
import org.apache.solr.client.solrj.response.GroupCommand;
import org.apache.solr.client.solrj.response.GroupResponse;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * rate plan core 的数据服务，公共逻辑见 {@link AbstractSolrService}。
 *
 * <p>分页查询按 {@code hotel_id} 分组：每页返回若干个酒店分组，
 * 每个分组取第一条文档作为该酒店的代表记录，total 为分组总数。</p>
 *
 * @author gaofeng
 */
@Service
public class RatePlanService extends AbstractSolrService {

    private static final String GROUP_FIELD = HotelRpSolrConverter.FIELD_HOTEL_ID;

    public RatePlanService(@Qualifier("ratePlanSolrCoreClient") SolrClient solrClient,
                           SolrProperties properties,
                           HotelQueryBuilder queryBuilder) {
        super(solrClient, properties, queryBuilder);
    }

    @Override
    public void initCoreData() {
        List<HotelRpDo> hotels = DataLoader.loadClasspath(DataLoader.RATE_PLAN_SET_FILE);
        for (HotelRpDo h : hotels) {
            create(h);
        }
    }

    @Override
    protected SolrQuery buildSolrQuery(HotelRpQueryReq queryReq) {
        SolrQuery solrQuery = super.buildSolrQuery(queryReq);
        solrQuery.set("group", true);
        solrQuery.set("group.field", GROUP_FIELD);
        // 分页需要分组总数，否则只能拿到匹配文档数
        solrQuery.set("group.ngroups", true);
        return solrQuery;
    }

    @Override
    protected List<SolrDocument> extractDocuments(QueryResponse response) {
        GroupResponse groupResponse = response.getGroupResponse();
        if (groupResponse == null) {
            return super.extractDocuments(response);
        }
        List<SolrDocument> documents = new ArrayList<>();
        for (GroupCommand command : groupResponse.getValues()) {
            for (Group group : command.getValues()) {
                SolrDocumentList result = group.getResult();
                if (result != null && !result.isEmpty()) {
                    documents.add(result.get(0));
                }
            }
        }
        return documents;
    }

    @Override
    protected long extractTotal(QueryResponse response) {
        GroupResponse groupResponse = response.getGroupResponse();
        if (groupResponse == null) {
            return super.extractTotal(response);
        }
        long total = 0;
        for (GroupCommand command : groupResponse.getValues()) {
            total += command.getNGroups() >= 0 ? command.getNGroups() : command.getMatches();
        }
        return total;
    }
}
