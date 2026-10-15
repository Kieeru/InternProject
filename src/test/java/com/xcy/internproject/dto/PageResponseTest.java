package com.xcy.internproject.dto;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void roundsTotalPagesUp() {
        PageResponse<String> response = PageResponse.of(List.of("第一页"), 1, 20, 41);

        assertThat(response.getItems()).containsExactly("第一页");
        assertThat(response.getPage()).isEqualTo(1);
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotal()).isEqualTo(41);
        assertThat(response.getTotalPages()).isEqualTo(3);
    }

    @Test
    void returnsZeroPagesForEmptyResult() {
        PageResponse<String> response = PageResponse.of(List.of(), 1, 20, 0);

        assertThat(response.getTotalPages()).isZero();
    }
}
