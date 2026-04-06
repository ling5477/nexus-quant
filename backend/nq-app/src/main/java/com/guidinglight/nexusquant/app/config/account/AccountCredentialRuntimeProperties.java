package com.guidinglight.nexusquant.app.config.account;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * AccountCredentialRuntimeProperties 承接账户凭证写侧最小运行时配置。
 * <p>
 * Why:
 * RC1-4 正式路径必须以 DB 密文存储作为唯一主数据源，
 * 因此主密钥、key version 和 verification mode 需要显式配置，而不是散落到 service 或 env 读取逻辑中。
 */
@Validated
@ConfigurationProperties(prefix = "nq.account.credentials")
public class AccountCredentialRuntimeProperties {

    @NotBlank
    private String masterKey;

    @Min(1)
    private int keyVersion = 1;

    @NotBlank
    private String verificationMode = "STRUCTURAL";

    public String getMasterKey() {
        return masterKey;
    }

    public void setMasterKey(String masterKey) {
        this.masterKey = masterKey;
    }

    public int getKeyVersion() {
        return keyVersion;
    }

    public void setKeyVersion(int keyVersion) {
        this.keyVersion = keyVersion;
    }

    public String getVerificationMode() {
        return verificationMode;
    }

    public void setVerificationMode(String verificationMode) {
        this.verificationMode = verificationMode;
    }
}
