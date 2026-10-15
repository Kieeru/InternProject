package com.xcy.internproject.model;

import com.xcy.internproject.exception.InvalidPaginationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageRequestTest {

    @Test
    void acceptsValidBoundaryValues() {
        PageRequest minimum = PageRequest.of(1, 1);
        PageRequest maximum = PageRequest.of(Integer.MAX_VALUE, 100);

        assertThat(minimum.getPage()).isEqualTo(1);
        assertThat(minimum.getSize()).isEqualTo(1);
        assertThat(minimum.getOffset()).isZero();
        assertThat(maximum.getPage()).isEqualTo(Integer.MAX_VALUE);
        assertThat(maximum.getSize()).isEqualTo(100);
        assertThat(maximum.getOffset()).isEqualTo(214_748_364_600L);
    }

    @Test
    void rejectsPageBelowOne() {
        assertThatThrownBy(() -> PageRequest.of(0, 20))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessage("页码不能小于1");
    }

    @Test
    void rejectsSizeBelowOne() {
        assertThatThrownBy(() -> PageRequest.of(1, 0))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessage("每页条数必须在1到100之间");
    }

    @Test
    void rejectsSizeAboveOneHundred() {
        assertThatThrownBy(() -> PageRequest.of(1, 101))
                .isInstanceOf(InvalidPaginationException.class)
                .hasMessage("每页条数必须在1到100之间");
    }

    @Test
    void calculatesOffsetFromOneBasedPage() {
        PageRequest request = PageRequest.of(3, 20);

        assertThat(request.getOffset()).isEqualTo(40L);
    }
}
