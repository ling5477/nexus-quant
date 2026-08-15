package com.guidinglight.nexusquant.account.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * CredentialPermissionProbeRequestBody 表示 permission probe 的非敏感请求体。
 *
 * <p>Why: credentialId、credentialType、actor 和 credential material 都必须由服务端派生；
 * 请求体只允许提交 reason、dryRun、mode 和 safety confirmation，未知字段直接拒绝，
 * 防止 apiKey、secret、headers 或 signature 被误接收。</p>
 */
@Schema(name = "CredentialPermissionProbeRequestBody", description = "credential permission probe 非敏感控制输入")
public class CredentialPermissionProbeRequestBody {

    @Schema(description = "操作原因；不得包含 credential material")
    private String reason;

    @Schema(description = "必须为 true；本轮只允许 dry run / no-real-exchange probe")
    private Boolean dryRun;

    @Schema(description = "严格 allowlist：PAPER/READ_ONLY_DIAGNOSTIC 或 GATEY_PILOT_READINESS；LIVE 拒绝")
    private String mode;

    @Schema(description = "必须为 true；表示调用方确认 dry-run/no-mutation safety gate")
    private Boolean paperSafetyConfirmed;

    public CredentialPermissionProbeRequestBody() {
    }

    public String reason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Boolean dryRun() {
        return dryRun;
    }

    public void setDryRun(Boolean dryRun) {
        this.dryRun = dryRun;
    }

    public String mode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Boolean paperSafetyConfirmed() {
        return paperSafetyConfirmed;
    }

    public void setPaperSafetyConfirmed(Boolean paperSafetyConfirmed) {
        this.paperSafetyConfirmed = paperSafetyConfirmed;
    }

    /**
     * 拒绝所有未声明字段。
     *
     * <p>Why: permission probe request body 不允许接收 apiKey、secret、signature、headers、
     * raw request 或 credential material。即使 ObjectMapper 全局配置忽略 unknown field，
     * 这里也会在 DTO 边界强制拒绝。</p>
     */
    @JsonAnySetter
    public void rejectUnknownField(String fieldName, Object ignoredValue) {
        throw new IllegalArgumentException("permission probe request contains unsupported field: " + fieldName);
    }
}
