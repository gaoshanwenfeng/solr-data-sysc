package com.hotel.solr.service;

import com.hotel.solr.config.SolrProperties;
import com.hotel.solr.dto.HealthStatus;
import com.hotel.solr.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SolrAdminService {

    private final SolrClient solrClient;
    private final SolrProperties properties;

    public HealthStatus health() {
        HealthStatus.HealthStatusBuilder builder = HealthStatus.builder()
                .solrUrl(properties.coreUrl())
                .core(properties.getCore());
        try {
            SolrQuery query = new SolrQuery();
            query.setQuery("*:*");
            query.setRows(0);
            QueryResponse response = solrClient.query(query);
            SolrDocumentList results = response.getResults();
            builder.status("UP")
                    .documentCount(results == null ? 0L : results.getNumFound())
                    .elapsedTimeMs(response.getElapsedTime());
        } catch (Exception e) {
            log.warn("Solr 健康检查失败: {}", e.getMessage());
            builder.status("DOWN").message(e.getMessage());
        }
        return builder.build();
    }

    public void commit() {
        try {
            solrClient.commit();
        } catch (SolrServerException | IOException e) {
            throw new BusinessException(502, "Solr 提交失败: " + e.getMessage(), e);
        }
    }
}
