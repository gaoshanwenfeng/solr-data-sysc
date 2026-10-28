package com.hotel.solr.model.bo;

import java.util.List;

/**
 * 基础数据中的价格/房态结构：{"date":"yyyy-MM-dd","list":[...]}。
 * @author gaofeng
 */
public class RatePlanDataBo {

    private String date;

    private List<Integer> list;

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<Integer> getList() {
        return list;
    }

    public void setList(List<Integer> list) {
        this.list = list;
    }

}
