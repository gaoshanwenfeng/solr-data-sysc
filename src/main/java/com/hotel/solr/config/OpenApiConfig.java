package com.hotel.solr.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI hotelSolrOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Hotel Solr API")
                .version("1.0.0")
                .description("基于 Spring Boot + SolrJ 8.0.0 的酒店 Solr 数据管理接口"));
    }
}
