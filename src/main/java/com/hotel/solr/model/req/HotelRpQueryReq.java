package com.hotel.solr.model.req;


import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.Range;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
    @Min(value = 1)
    @Max(value = Integer.MAX_VALUE)
    private Integer priceFrom;

    @Min(value = 1)
    @Max(value = Integer.MAX_VALUE)
    private Integer priceTo;


    private List<String> tags;

    private String sort = "score desc";

    private Integer page = 1;

    private Integer size = 10;

    private LocationReq location;

    /**计算得出**/
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private int days = 1;

    @Getter
    @Setter
    static
    class LocationReq {
        private String lat;
        private String lon;
        private Integer distance = 5;

    }

}
