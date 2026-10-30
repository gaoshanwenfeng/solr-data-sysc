package com.hotel.solr.model.req;

import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

/**
 * 部分更新请求，字段为 null 表示不修改。
 *
 * @author gaofeng
 */
public class HotelPatchReq {

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

    public @Size(min = 1, message = "name 不能为空") String getName() {
        return name;
    }

    public void setName(@Size(min = 1, message = "name 不能为空") String name) {
        this.name = name;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getCityId() {
        return cityId;
    }

    public void setCityId(String cityId) {
        this.cityId = cityId;
    }

    public String getCityName() {
        return cityName;
    }

    public void setCityName(String cityName) {
        this.cityName = cityName;
    }

    public @DecimalMin(value = "-180", message = "longitude 不能小于 -180") @DecimalMax(value = "180", message = "longitude 不能大于 180") BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(@DecimalMin(value = "-180", message = "longitude 不能小于 -180") @DecimalMax(value = "180", message = "longitude 不能大于 180") BigDecimal longitude) {
        this.longitude = longitude;
    }

    public @DecimalMin(value = "-90", message = "latitude 不能小于 -90") @DecimalMax(value = "90", message = "latitude 不能大于 90") BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(@DecimalMin(value = "-90", message = "latitude 不能小于 -90") @DecimalMax(value = "90", message = "latitude 不能大于 90") BigDecimal latitude) {
        this.latitude = latitude;
    }

    public @Min(value = 1, message = "star 最小为 1") @Max(value = 5, message = "star 最大为 5") Integer getStar() {
        return star;
    }

    public void setStar(@Min(value = 1, message = "star 最小为 1") @Max(value = 5, message = "star 最大为 5") Integer star) {
        this.star = star;
    }

    public @DecimalMin(value = "0", message = "price 不能为负数") Float getPrice() {
        return price;
    }

    public void setPrice(@DecimalMin(value = "0", message = "price 不能为负数") Float price) {
        this.price = price;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getRatePlanPrices() {
        return ratePlanPrices;
    }

    public void setRatePlanPrices(String ratePlanPrices) {
        this.ratePlanPrices = ratePlanPrices;
    }

    public String getRatePlanRoomSts() {
        return ratePlanRoomSts;
    }

    public void setRatePlanRoomSts(String ratePlanRoomSts) {
        this.ratePlanRoomSts = ratePlanRoomSts;
    }
}
