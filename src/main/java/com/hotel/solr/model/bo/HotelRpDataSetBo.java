package com.hotel.solr.model.bo;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * classpath 根目录 hotel-rp-set.json 的根结构。
 *
 * @author gaofeng
 */
@Data
public class HotelRpDataSetBo {

    @JsonProperty("RECORDS")
    private List<HotelInfoRecordBo> records;
}
