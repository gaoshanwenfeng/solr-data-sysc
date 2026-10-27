package com.hotel.solr.controller;

import com.hotel.solr.dto.ApiResponse;
import com.hotel.solr.dto.HealthStatus;
import com.hotel.solr.service.SolrAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/solr")
@RequiredArgsConstructor
@Tag(name = "Solr 运维", description = "Solr 连接健康检查与提交")
public class SolrAdminController {

    private final SolrAdminService solrAdminService;

    @GetMapping("/health")
    @Operation(summary = "检查 Solr 连接与 core 状态")
    public ResponseEntity<ApiResponse<HealthStatus>> health() {
        HealthStatus status = solrAdminService.health();
        boolean up = "UP".equals(status.getStatus());
        ApiResponse<HealthStatus> body = ApiResponse.<HealthStatus>builder()
                .code(up ? 0 : 503)
                .message(up ? "ok" : status.getMessage())
                .data(status)
                .build();
        return ResponseEntity.status(up ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }

    @PostMapping("/commit")
    @Operation(summary = "手动提交（commit）")
    public ApiResponse<Void> commit() {
        solrAdminService.commit();
        return ApiResponse.ok();
    }
}
