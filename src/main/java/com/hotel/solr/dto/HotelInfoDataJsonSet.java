package com.hotel.solr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * classpath 根目录 dih-hotel-rp.json 的根结构。
 * @author gaofeng
 */
@Data
public class HotelInfoDataJsonSet {

    @JsonProperty("RECORDS")
    private List<HotelBaseRecord> records;
}
