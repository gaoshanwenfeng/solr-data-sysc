package com.hotel.solr.model.req;


import com.hotel.solr.model.enums.SortOrder;
import com.hotel.solr.model.enums.SortType;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * @author gaofeng
 */
@Getter
@Setter
public class HotelRpQueryReq {

    private String keyword;

    @NotBlank(message = "城市信息不可为空")
    private String cityId;
    @NotBlank(message = "入住日期不可空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "入住日期格式必须为 yyyy-MM-dd")
    private String checkin;
    @NotBlank(message = "离店日期不可空")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "离店日期格式必须为 yyyy-MM-dd")
    private String checkout;

    private Integer star;

    private Integer priceFrom;

    private Integer priceTo;

    private List<String> tags;

    /**
     * 排序类型：DISTANCE（距离）、PRICE（价格）、STAR（星级）、SCORE（相关度评分）。
     * 未指定时，携带 location 按距离排序，否则按评分排序。
     */
    private SortType sortType;

    /**
     * 排序方向：ASC（升序）、DESC（降序），仅对 PRICE 和 STAR 生效。
     * PRICE 默认 ASC；STAR 默认 DESC。
     */
    private SortOrder sortOrder;

    private Integer page = 1;

    private Integer size = 10;

    private LocationReq location;

    private String hotelId;

    /**
     * 计算得出
     **/
    private LocalDate checkinDate;
    private int offSetCheckin;
    private LocalDate checkoutDate;
    private int days = 1;

    @Getter
    @Setter
    public static class LocationReq {
        /**
         * 纬度，范围 [-90, 90]，小数点后至少 6 位（由 Service 校验精度）
         */
        @DecimalMin(value = "-90", message = "latitude 不能小于 -90")
        @DecimalMax(value = "90", message = "latitude 不能大于 90")
        private BigDecimal lat;
        /**
         * 经度，范围 [-180, 180]，小数点后至少 6 位（由 Service 校验精度）
         */
        @DecimalMin(value = "-180", message = "longitude 不能小于 -180")
        @DecimalMax(value = "180", message = "longitude 不能大于 180")
        private BigDecimal lon;
        @DecimalMin(value = "0.1", message = "distance 不可能小于0.1")
        @DecimalMax(value = "1000.00", message = "longitude 不能大于 2000.00")
        private double distance = 5.0d;
    }

}
