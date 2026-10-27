package com.hotel.solr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 基础数据 JSON 中的单条酒店记录。
 *
 * <p>源数据字段全部为字符串，这里保持原始类型，避免空字符串无法反序列化为数字。
 * 该模型只用于读取，不会回写源文件。</p>
 * @author gaofeng
 */
@Data
public class HotelBaseRecord {

    private String id;

    @JsonProperty("mhotel_id")
    private String mhotelId;

    @JsonProperty("hotel_name")
    private String hotelName;

    private String address;

    @JsonProperty("postal_code")
    private String postalCode;

    @JsonProperty("star_rate")
    private String starRate;

    private String category;

    private String phone;

    private String fax;

    @JsonProperty("establishment_date")
    private String establishmentDate;

    @JsonProperty("renovation_date")
    private String renovationDate;

    @JsonProperty("group_id")
    private String groupId;

    @JsonProperty("brand_id")
    private String brandId;

    @JsonProperty("is_economic")
    private String isEconomic;

    @JsonProperty("is_apartment")
    private String isApartment;

    @JsonProperty("arrival_time")
    private String arrivalTime;

    @JsonProperty("departure_time")
    private String departureTime;

    @JsonProperty("google_lat")
    private String googleLat;

    @JsonProperty("google_lon")
    private String googleLon;

    @JsonProperty("baidu_lat")
    private String baiduLat;

    @JsonProperty("baidu_lon")
    private String baiduLon;

    @JsonProperty("city_id")
    private String cityId;

    @JsonProperty("city_name")
    private String cityName;

    @JsonProperty("city_id2")
    private String cityId2;

    private String district;

    @JsonProperty("credit_cards")
    private String creditCards;

    @JsonProperty("business_zone")
    private String businessZone;

    @JsonProperty("business_zone2")
    private String businessZone2;

    @JsonProperty("intro_editor")
    private String introEditor;

    private String description;

    @JsonProperty("airport_pick_up_service")
    private String airportPickUpService;

    @JsonProperty("general_amenities")
    private String generalAmenities;

    @JsonProperty("recreation_amenities")
    private String recreationAmenities;

    @JsonProperty("service_amenities")
    private String serviceAmenities;

    @JsonProperty("summary_score")
    private String summaryScore;

    @JsonProperty("summary_rate")
    private String summaryRate;

    @JsonProperty("instant_confirm_score")
    private String instantConfirmScore;

    @JsonProperty("instant_confirm_rate")
    private String instantConfirmRate;

    @JsonProperty("booking_success_score")
    private String bookingSuccessScore;

    @JsonProperty("booking_success_rate")
    private String bookingSuccessRate;

    @JsonProperty("complaint_score")
    private String complaintScore;

    @JsonProperty("complaint_rate")
    private String complaintRate;

    @JsonProperty("has_coupon")
    private String hasCoupon;

    private String themes;

    @JsonProperty("room_total_amount")
    private String roomTotalAmount;

    @JsonProperty("hotel_status")
    private String hotelStatus;

    @JsonProperty("_timestamp")
    private String timestamp;

    private String tag;

    @JsonProperty("tencent_lat")
    private String tencentLat;

    @JsonProperty("tencent_lon")
    private String tencentLon;

    private String facilities;

    @JsonProperty("rp_room_sts")
    private RatePlanData rpRoomSts;

    @JsonProperty("rp_prices")
    private RatePlanData rpPrices;
}
