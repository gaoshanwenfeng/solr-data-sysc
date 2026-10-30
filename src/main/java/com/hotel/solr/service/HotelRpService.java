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
 * hotel_rp core 的数据服务，公共逻辑见 {@link AbstractSolrService}。
 *
 * @author gaofeng
 */
@Service
public class HotelRpService extends AbstractSolrService {
    public HotelRpService(@Qualifier("hotelSolrCoreClient") SolrClient solrClient,
                          SolrProperties properties,
                          HotelQueryBuilder queryBuilder) {
        super(solrClient, properties, queryBuilder);
    }

    @Override
    public void initCoreData() {
        List<HotelRpDo> hotels = DataLoader.loadClasspath(DataLoader.HOTEL_RP_FILE);
        for (HotelRpDo h : hotels) {
            create(h);
        }
    }
}
