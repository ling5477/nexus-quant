package com.guidinglight.nexusquant.api.web;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * ApiFieldError 表示参数校验失败时的单字段错误明细。
 * <p>
 * Why:
 * Step 2 要把 Bean Validation 的结果稳定输出给调用方，方便定位具体字段，
 * 因此不能再只返回一条扁平 message。
 */
@Schema(name = "ApiFieldError", description = "字段级错误明细")
public record ApiFieldError(
        @Schema(description = "字段名")
        String field,
        @Schema(description = "被拒绝的原始值；必要时会被安全裁剪")
        Object rejectedValue,
        @Schema(description = "失败原因")
        String reason
) {
}
