package com.hotel.solr.config;

import org.apache.solr.client.solrj.SolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(SolrProperties.class)
public class SolrConfig {

    @Bean(destroyMethod = "close")
    public SolrClient solrClient(SolrProperties properties) {
        return new HttpSolrClient.Builder(properties.coreUrl())
                .withConnectionTimeout(properties.getConnectionTimeoutMs())
                .withSocketTimeout(properties.getSocketTimeoutMs())
                .build();
    }
}
