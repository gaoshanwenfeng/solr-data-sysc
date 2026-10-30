package com.hotel.solr.controller;

import com.hotel.solr.framework.lang.ApiResult;
import com.hotel.solr.framework.lang.PageResult;
import com.hotel.solr.model.dto.HotelRpDto;
import com.hotel.solr.model.po.HotelRpDo;
import com.hotel.solr.model.req.BatchDeleteReq;
import com.hotel.solr.model.req.BatchHotelReq;
import com.hotel.solr.model.req.HotelPatchReq;
import com.hotel.solr.model.req.HotelRpQueryReq;
import com.hotel.solr.service.HotelRpService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
@RequestMapping("/api/hotels")
@RequiredArgsConstructor
@Validated
@Tag(name = "酒店数据管理", description = "Solr 酒店文档的增删改查")
public class HotelController {

    private final HotelRpService hotelRpService;

    @PostMapping("/import")
    @Operation(summary = "手动提交（commit）")
    public ApiResult<Void> dataHotelRoImport() {
        hotelRpService.initCoreData();
        return ApiResult.ok();
    }


    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增或覆盖酒店")
    public ApiResult<HotelRpDo> create(@Valid @RequestBody HotelRpDo hotel) {
        return ApiResult.ok(hotelRpService.create(hotel));
    }

    @PutMapping("/{id}")
    @Operation(summary = "全量更新酒店（不存在时创建）")
    public ApiResult<HotelRpDo> replace(@PathVariable String id, @Valid @RequestBody HotelRpDo hotel) {
        return ApiResult.ok(hotelRpService.replace(id, hotel));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "部分更新酒店")
    public ApiResult<HotelRpDto> patch(@PathVariable String id, @Valid @RequestBody HotelPatchReq patch) {
        return ApiResult.ok(hotelRpService.patch(id, patch));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 读取酒店")
    public ApiResult<HotelRpDto> get(@PathVariable String id) {
        return ApiResult.ok(hotelRpService.get(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "按 id 删除酒店")
    public ApiResult<Void> delete(@PathVariable String id) {
        hotelRpService.delete(id);
        return ApiResult.ok();
    }

    @PostMapping("/query")
    @Operation(summary = "分页条件查询酒店")
    public ApiResult<PageResult<HotelRpDto>> query(@Valid @RequestBody HotelRpQueryReq req) {
        return ApiResult.ok(hotelRpService.query(req));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量新增或覆盖酒店")
    public ApiResult<Integer> batchSave(@Valid @RequestBody BatchHotelReq request) {
        return ApiResult.ok(hotelRpService.saveBatch(request.getHotelDocList()));
    }

    @PostMapping("/batch-delete")
    @Operation(summary = "批量删除酒店")
    public ApiResult<Integer> batchDelete(@Valid @RequestBody BatchDeleteReq request) {
        return ApiResult.ok(hotelRpService.deleteBatch(request.getIds()));
    }
}
