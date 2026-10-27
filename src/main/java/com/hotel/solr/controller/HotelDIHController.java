package com.hotel.solr.controller;

import com.hotel.solr.dto.*;
import com.hotel.solr.service.HotelService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;

/**
 * @author gaofeng
 */
@RestController
@RequestMapping("/api/hotel/dih/")
@RequiredArgsConstructor
@Validated
@Tag(name = "酒店数据管理", description = "Solr 酒店文档的增删改查")
public class HotelDIHController {

    private final HotelService hotelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "新增或覆盖酒店")
    public ApiResponse<HotelDo> create(@Valid @RequestBody HotelDo hotelDo) {
        return ApiResponse.ok(hotelService.save(hotelDo));
    }

    @PutMapping("/{id}")
    @Operation(summary = "全量更新酒店（不存在时创建）")
    public ApiResponse<HotelDo> replace(@PathVariable String id, @Valid @RequestBody HotelDo hotelDo) {
        return ApiResponse.ok(hotelService.replace(id, hotelDo));
    }

    @PatchMapping("/{id}")
    @Operation(summary = "部分更新酒店")
    public ApiResponse<HotelDo> patch(@PathVariable String id, @Valid @RequestBody HotelPatchRequest patch) {
        return ApiResponse.ok(hotelService.patch(id, patch));
    }

    @GetMapping("/{id}")
    @Operation(summary = "按 id 读取酒店")
    public ApiResponse<HotelDo> get(@PathVariable String id) {
        return ApiResponse.ok(hotelService.get(id));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "按 id 删除酒店")
    public ApiResponse<Void> delete(@PathVariable String id) {
        hotelService.delete(id);
        return ApiResponse.ok();
    }

    @GetMapping
    @Operation(summary = "分页条件查询酒店")
    public ApiResponse<PageResult<HotelDo>> query(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Integer star,
            @RequestParam(name = "min_price", required = false) Float minPrice,
            @RequestParam(name = "max_price", required = false) Float maxPrice,
            @RequestParam(required = false) List<String> tags,
            @RequestParam(defaultValue = "score desc") String sort,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size) {
        HotelQuery query = HotelQuery.builder()
                .keyword(keyword)
                .city(city)
                .star(star)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .tags(tags)
                .sort(sort)
                .page(page)
                .size(size)
                .build();
        return ApiResponse.ok(hotelService.query(query));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量新增或覆盖酒店")
    public ApiResponse<Integer> batchSave(@Valid @RequestBody BatchHotelRequest request) {
        return ApiResponse.ok(hotelService.saveBatch(request.getHotelDos()));
    }

    @PostMapping("/batch-delete")
    @Operation(summary = "批量删除酒店")
    public ApiResponse<Integer> batchDelete(@Valid @RequestBody BatchDeleteRequest request) {
        return ApiResponse.ok(hotelService.deleteBatch(request.getIds()));
    }
}
