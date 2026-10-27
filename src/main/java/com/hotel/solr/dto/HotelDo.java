package com.hotel.solr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 酒店文档。JSON 字段全局使用 snake_case，与 Solr 字段保持一致。
 * @author gaofeng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelDo {

    /**
     * 唯一键，新增时必填（由 Service 校验）
     */
    private String id;

    @NotBlank(message = "name 不能为空")
    private String name;

    private String address;

    private String city;

    /**
     * 经度，范围 [-180, 180]，小数点后至少 6 位（由 Service 校验精度）
     */
    @DecimalMin(value = "-180", message = "longitude 不能小于 -180")
    @DecimalMax(value = "180", message = "longitude 不能大于 180")
    private BigDecimal longitude;

    /**
     * 纬度，范围 [-90, 90]，小数点后至少 6 位（由 Service 校验精度）
     */
    @DecimalMin(value = "-90", message = "latitude 不能小于 -90")
    @DecimalMax(value = "90", message = "latitude 不能大于 90")
    private BigDecimal latitude;

    @Min(value = 1, message = "star 最小为 1")
    @Max(value = 5, message = "star 最大为 5")
    private Integer star;

    @DecimalMin(value = "0", message = "price 不能为负数")
    private Float price;

    private List<String> tags;

    /**
     * 价格计划，JSON 字符串，仅存储不索引
     */
    private String ratePlanPrices;

    /**
     * 房型房态，JSON 字符串，仅存储不索引
     */
    private String ratePlanRoomSts;

    private Instant createTime;

    private Instant updateTime;
}
