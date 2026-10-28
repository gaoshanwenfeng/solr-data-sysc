package com.hotel.solr.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author gaofeng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SolrCoreStatusDto {

    private String status;

    private String solrUrl;

    private String core;

    private Long documentCount;

    private Long elapsedTimeMs;

    private String message;
}
