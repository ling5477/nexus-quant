package com.guidinglight.nexusquant.account.application.command;

/**
 * CredentialPermissionProbeCommand 表示 permission probe 的非敏感控制输入。
 *
 * <p>Why: credential material 必须从 credentialId 派生并由服务端读取，不能由请求体提交。
 * 本命令只允许 reason、dryRun、mode 和 Paper safety confirmation 这类控制字段。</p>
 */
public record CredentialPermissionProbeCommand(
        String reason,
        Boolean dryRun,
        String mode,
        Boolean paperSafetyConfirmed
) {
}
