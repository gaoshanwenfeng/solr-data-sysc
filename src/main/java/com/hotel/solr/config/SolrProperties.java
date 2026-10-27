package com.hotel.solr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "solr")
public class SolrProperties {

    /**
     * Solr 基础地址，例如 http://localhost:8983/solr
     */
    private String baseUrl = "http://localhost:8983/solr";

    /**
     * core 名称，例如 hotel
     */
    private String core = "hotel";

    private int connectionTimeoutMs = 5000;

    private int socketTimeoutMs = 15000;

    /**
     * 写操作后是否立即 commit
     */
    private boolean commitAfterWrite = true;

    /**
     * 当前 core 的完整访问地址
     */
    public String coreUrl() {
        String base = baseUrl == null ? "" : baseUrl.replaceAll("/+$", "");
        return base + "/" + core;
    }
}
