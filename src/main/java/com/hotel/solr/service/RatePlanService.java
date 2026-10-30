package com.hotel.solr.service;

import com.hotel.solr.config.SolrProperties;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.service.builder.HotelQueryBuilder;
import com.hotel.solr.util.DataLoader;
import org.apache.solr.client.solrj.SolrClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * rate plan core 的数据服务，公共逻辑见 {@link AbstractSolrService}。
 *
 * @author gaofeng
 */
@Service
public class RatePlanService extends AbstractSolrService {

    @Override
    public void initCoreData() {
        List<HotelRpDo> hotels = DataLoader.loadClasspath(DataLoader.RATE_PLAN_SET_FILE);
        for(HotelRpDo h : hotels) {
            create(h);
        }
    }

    public RatePlanService(@Qualifier("ratePlanSolrCoreClient") SolrClient solrClient,
                           SolrProperties properties,
                           HotelQueryBuilder queryBuilder) {
        super(solrClient, properties, queryBuilder);
    }
}
