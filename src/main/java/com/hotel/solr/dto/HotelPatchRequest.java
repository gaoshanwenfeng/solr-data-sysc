package com.hotel.solr.dto;

import lombok.Data;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/**
 * 部分更新请求，字段为 null 表示不修改。
 */
@Data
public class HotelPatchRequest {

    @Size(min = 1, message = "name 不能为空")
    private String name;

    private String address;

    private String cityId;
    private String cityName;

    @DecimalMin(value = "-180", message = "longitude 不能小于 -180")
    @DecimalMax(value = "180", message = "longitude 不能大于 180")
    private BigDecimal longitude;

    @DecimalMin(value = "-90", message = "latitude 不能小于 -90")
    @DecimalMax(value = "90", message = "latitude 不能大于 90")
    private BigDecimal latitude;

    @Min(value = 1, message = "star 最小为 1")
    @Max(value = 5, message = "star 最大为 5")
    private Integer star;

    @DecimalMin(value = "0", message = "price 不能为负数")
    private Float price;

    private List<String> tags;

    private String ratePlanPrices;

    private String ratePlanRoomSts;
}
