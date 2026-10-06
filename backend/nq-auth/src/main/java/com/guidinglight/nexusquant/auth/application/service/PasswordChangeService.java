package com.guidinglight.nexusquant.auth.application.service;

import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/** 修改密码时锁定用户，验证、哈希与代际更新在同一事务中完成。 */
public class PasswordChangeService {
    private final AuthUserRepository repository;
    private final PasswordEncoder encoder;

    public PasswordChangeService(AuthUserRepository repository, PasswordEncoder encoder) {
        this.repository = repository;
        this.encoder = encoder;
    }

    @Transactional
    public void changePassword(String username, long tokenVersion, String currentPassword, String newPassword) {
        var user = repository.findByUsernameForUpdate(username)
                .orElseThrow(() -> new BadCredentialsException("authentication required"));
        if (!user.enabled() || user.authVersion() != tokenVersion
                || currentPassword == null || !encoder.matches(currentPassword, user.passwordHash())) {
            throw new BadCredentialsException("invalid current password");
        }
        // BCrypt 输入有字节上限，避免静默截断；不添加字符组合或密码历史规则。
        if (newPassword == null || newPassword.length() < 8
                || newPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || "123456".equals(newPassword) || encoder.matches(newPassword, user.passwordHash())) {
            throw new IllegalArgumentException("new password must differ and contain 8 to 72 UTF-8 bytes");
        }
        if (!repository.changePassword(user.userId(), user.authVersion(), encoder.encode(newPassword))) {
            throw new BadCredentialsException("authentication changed concurrently");
        }
    }
}
