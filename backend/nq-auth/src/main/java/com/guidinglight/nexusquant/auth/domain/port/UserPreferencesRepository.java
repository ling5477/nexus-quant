package com.guidinglight.nexusquant.auth.domain.port;

import com.guidinglight.nexusquant.auth.domain.UserPreferences;

import java.util.Optional;

/** 用户偏好持久化端口；通过乐观版本避免并发丢失更新，不能替代账户或交易授权。 */
public interface UserPreferencesRepository {
    /** 按用户读取偏好；未保存返回空，由应用服务提供默认值，不产生写入副作用。 */
    Optional<UserPreferences> find(long userId);

    /** 版本不匹配返回空，避免两个浏览器会话静默覆盖对方的偏好。 */
    Optional<UserPreferences> save(long userId, UserPreferences preferences);
}
