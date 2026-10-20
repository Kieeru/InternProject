package com.xcy.internproject.cache.ticket;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TicketRequestFingerprintTest {

    private final TicketRequestFingerprint fingerprint = new TicketRequestFingerprint();

    @Test
    void hashesLengthPrefixedTitleAndDescriptionAsLowercaseSha256() {
        assertThat(fingerprint.calculate("登录失败", "输入密码后报错"))
                .isEqualTo("7a8f963925ad5337b9bf8d028eb0f6b6aff592ec5948189687772f76faac8ecf");
    }

    @Test
    void changesWhenEitherTitleOrDescriptionChanges() {
        String original = fingerprint.calculate("登录失败", "输入密码后报错");

        assertThat(fingerprint.calculate("登录异常", "输入密码后报错"))
                .isNotEqualTo(original);
        assertThat(fingerprint.calculate("登录失败", "输入密码后超时"))
                .isNotEqualTo(original);
    }

    @Test
    void keepsFieldBoundariesUnambiguousWhenContentContainsNullCharacter() {
        assertThat(fingerprint.calculate("a", "\u0000b"))
                .isNotEqualTo(fingerprint.calculate("a\u0000", "b"));
    }
}
