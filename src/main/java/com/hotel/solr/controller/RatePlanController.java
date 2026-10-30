package com.hotel.solr.controller;

import com.hotel.solr.framework.lang.ApiResult;
import com.hotel.solr.framework.lang.PageResult;
import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.service.RatePlanService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 【功能描述】
 *
 * @author gaofeng
 * @date 2026/10/30-17:47
 */
@RestController
@RequestMapping("/api/rp")
@RequiredArgsConstructor
@Validated
@Tag(name = "酒店数据管理", description = "Solr 酒店文档的增删改查")
public class RatePlanController {
    private final RatePlanService ratePlanService;

    @PostMapping("/import")
    @Operation(summary = "手动提交（commit）")
    public ApiResult<Void> dataHotelRoImport() {
        ratePlanService.initCoreData();
        return ApiResult.ok();
    }

    @PostMapping("/query")
    @Operation(summary = "分页条件查询酒店")
    public ApiResult<PageResult<HotelRpDto>> query(@Valid @RequestBody HotelRpQueryReq req) {
        return ApiResult.ok(ratePlanService.query(req));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 读取记录")
    public ApiResult<HotelRpDto> get(@PathVariable String id) {
        return ApiResult.ok(ratePlanService.get(id));
    }
}
