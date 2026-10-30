package com.hotel.solr.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hotel.solr.util.DataLoader;
import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author gaofeng
 */
@Configuration
@EnableConfigurationProperties(SolrProperties.class)
public class SolrConfig {

    @Bean(destroyMethod = "close", name = "hotelSolrCoreClient")
    public SolrClient hotelSolrCoreClient(SolrProperties properties) {
        return new HttpSolrClient.Builder(properties.hotelCoreUrl())
                .withConnectionTimeout(properties.getConnectionTimeoutMs())
                .withSocketTimeout(properties.getSocketTimeoutMs())
                .build();
    }

    @Bean(destroyMethod = "close", name = "ratePlanSolrCoreClient")
    public SolrClient ratePlanSolrCoreClient(SolrProperties properties) {
        return new HttpSolrClient.Builder(properties.ratePlanCoreUrl())
                .withConnectionTimeout(properties.getConnectionTimeoutMs())
                .withSocketTimeout(properties.getSocketTimeoutMs())
                .build();
    }

    @Bean
    public DataLoader dataLoader(ObjectMapper objectMapper) {
        return new DataLoader(objectMapper);
    }
}
