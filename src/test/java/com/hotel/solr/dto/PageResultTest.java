package com.hotel.solr.dto;

import com.hotel.solr.framework.PageResult;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class PageResultTest {

    @Test
    void calculatesTotalPagesWithRemainder() {
        PageResult<String> result = PageResult.of(11, 1, 10, Collections.emptyList());

        assertThat(result.getTotalPages()).isEqualTo(2);
    }

    @Test
    void returnsZeroPagesWhenEmpty() {
        PageResult<String> result = PageResult.of(0, 1, 10, Collections.emptyList());

        assertThat(result.getTotalPages()).isZero();
        assertThat(result.getItems()).isEmpty();
    }

    @Test
    void calculatesExactPages() {
        PageResult<String> result = PageResult.of(20, 2, 10, Collections.singletonList("hotel"));

        assertThat(result.getTotal()).isEqualTo(20);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(10);
        assertThat(result.getTotalPages()).isEqualTo(2);
        assertThat(result.getItems()).containsExactly("hotel");
    }
}
