package com.guidinglight.nexusquant.research.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * ConfigArchiveRequestBody 描述研究 / 回测配置归档请求体。
 * <p>
 * Why:
 * archive command 只允许调用方说明归档原因；归档操作者由服务端认证上下文解析，
 * 避免外部请求伪造 archived_by。archiveReason 不得包含密钥、token 或其他访问材料。
 */
@Schema(name = "ConfigArchiveRequestBody", description = "配置归档请求体")
public record ConfigArchiveRequestBody(
        @Size(max = 1024, message = "archiveReason length must be less than or equal to 1024")
        @Schema(description = "归档原因，可空；不得包含密钥、token、API secret、私钥、助记词等敏感信息")
        String archiveReason
) {
}
