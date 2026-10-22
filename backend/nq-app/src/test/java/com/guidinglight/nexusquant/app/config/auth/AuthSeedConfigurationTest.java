package com.guidinglight.nexusquant.app.config.auth;

import com.guidinglight.nexusquant.auth.domain.AuthUserProfile;
import com.guidinglight.nexusquant.auth.domain.port.AuthUserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthSeedConfigurationTest {
    @Test void verifiesInstalledLocalIdentityWithoutCredentialOrRoleWrites() throws Exception {
        var users=mock(AuthUserRepository.class);
        when(users.findByUsername("local")).thenReturn(Optional.of(new AuthUserProfile(1L,"local","synthetic-hash",List.of("VIEWER"),true,true,null,7)));
        new AuthSeedConfiguration().authSeedRunner(properties(),users).run(new org.springframework.boot.DefaultApplicationArguments());
        verify(users).findByUsername("local");verifyNoMoreInteractions(users);
    }

    @ParameterizedTest @ValueSource(strings={"MISSING","PASSWORD","ROLES","ENABLED"})
    void refusesProvisioningMismatchBeforeAnyMutation(String mismatch) {
        var users=mock(AuthUserRepository.class);
        var current=new AuthUserProfile(1L,"local",mismatch.equals("PASSWORD")?"different":"synthetic-hash",
                mismatch.equals("ROLES")?List.of("ADMIN"):List.of("VIEWER"),!mismatch.equals("ENABLED"));
        when(users.findByUsername("local")).thenReturn(mismatch.equals("MISSING")?Optional.empty():Optional.of(current));
        var failure=assertThrows(IllegalStateException.class,()->new AuthSeedConfiguration().authSeedRunner(properties(),users).run(new org.springframework.boot.DefaultApplicationArguments()));
        assertEquals("LOCAL_IDENTITY_PROVISIONING_REQUIRED",failure.getMessage());
        verify(users).findByUsername("local");verifyNoMoreInteractions(users);
    }

    private SecurityRuntimeProperties properties() {
        var user=new SecurityRuntimeProperties.LocalUserProperties();
        user.setUsername("local");user.setPasswordHash("synthetic-hash");user.setRoles(List.of("VIEWER"));
        var properties=new SecurityRuntimeProperties();properties.setUsers(List.of(user));return properties;
    }
}
