package com.hotel.solr.model.req;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.hotel.solr.model.po.HotelRpDo;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

/**
 * @author gaofeng
 */
@Data
public class BatchHotelReq {

    @Valid
    @NotEmpty(message = "hotels 不能为空")
    @Size(max = 1000, message = "单次批量写入不能超过 1000 条")
    @JsonProperty("hotels")
    private List<HotelRpDo> hotelDocList;
}
