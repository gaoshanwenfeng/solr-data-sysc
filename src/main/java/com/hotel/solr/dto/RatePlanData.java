package com.hotel.solr.dto;

import lombok.Data;

import java.util.List;

/**
 * 基础数据中的价格/房态结构：{"date":"yyyy-MM-dd","list":[...]}。
 */
@Data
public class RatePlanData {

    private String date;

    private List<Integer> list;
}
