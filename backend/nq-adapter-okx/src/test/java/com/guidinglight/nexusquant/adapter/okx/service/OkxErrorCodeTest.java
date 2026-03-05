package com.guidinglight.nexusquant.adapter.okx.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * OkxErrorCodeTest 验证 OKX 错误码到规范化语义的映射。
 */
class OkxErrorCodeTest {

    @Test
    void shouldMap51603ToOrderNotFound() {
        assertEquals(OkxErrorCode.ORDER_NOT_FOUND, OkxErrorCode.fromRawCode("51603"));
        assertEquals(OkxErrorCode.UNKNOWN, OkxErrorCode.fromRawCode("unknown"));
    }
}
