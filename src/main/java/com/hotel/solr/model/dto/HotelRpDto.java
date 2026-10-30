package com.hotel.solr.model.dto;

import lombok.*;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 酒店文档。JSON 字段全局使用 camelCase，与 Java 字段名保持一致。
 * @author gaofeng
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelRpDto {

    /**
     * 唯一键，新增时必填（由 Service 校验）
     */
    private String id;
    private String hotelId;
    private String ratePlanId;

    @NotBlank(message = "name 不能为空")
    private String name;

    private String address;

    private String cityId;
    private String cityName;
    /**
     * 经纬度 location
     * "location":"30.528017,114.18986
     */
    private String location;

    @Min(value = 1, message = "star 最小为 1")
    @Max(value = 5, message = "star 最大为 5")
    private Integer star;

    @DecimalMin(value = "0", message = "price 不能为负数")
    private Float price;

    private List<String> tags;

    /**
     * 价格计划，{@code RpByteUtils} 编码后的紧凑字符串，解析而来
     */
    private List<RpItemData> priceList;

    /**
     * 房型房态，{@code RpByteUtils} 编码后的紧凑字符串，解析而来
     */
    private  List<RpItemData> stsList;

    private Instant createTime;

    private Instant updateTime;

    /**
     * 查询如果有 经纬地标+范围，需返回距离
     */
    private Float distance;
    private Float avgPrice;
    @Getter
    @Setter
    public static class RpItemData {
        private String date;
        private int value;
    }
}
