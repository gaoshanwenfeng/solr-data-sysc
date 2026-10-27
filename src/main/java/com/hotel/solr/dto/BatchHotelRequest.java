package com.hotel.solr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.Size;
import java.util.List;

@Data
public class BatchHotelRequest {

    @Valid
    @NotEmpty(message = "hotels 不能为空")
    @Size(max = 1000, message = "单次批量写入不能超过 1000 条")
    @JsonProperty("hotels")
    private List<HotelDo> hotelDos;
}
