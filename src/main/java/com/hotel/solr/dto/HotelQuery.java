package com.hotel.solr.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HotelQuery {

    private String keyword;

    private String city;

    private Integer star;

    private Float minPrice;

    private Float maxPrice;

    private List<String> tags;

    @Builder.Default
    private String sort = "score desc";

    @Builder.Default
    private Integer page = 1;

    @Builder.Default
    private Integer size = 10;
}
