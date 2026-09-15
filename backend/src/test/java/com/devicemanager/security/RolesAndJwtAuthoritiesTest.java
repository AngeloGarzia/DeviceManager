package com.devicemanager.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RolesAndJwtAuthoritiesTest {

    @Test
    void isAdminLike_includesSuperAdmin() {
        assertThat(Roles.isAdminLike(Roles.ADMIN)).isTrue();
        assertThat(Roles.isAdminLike(Roles.SUPER_ADMIN)).isTrue();
        assertThat(Roles.isAdminLike(Roles.TECHNICIEN)).isFalse();
    }

    @Test
    void superAdmin_getsDualAuthorities() {
        List<SimpleGrantedAuthority> authorities =
                JwtAuthenticationFilter.authoritiesFor(Roles.SUPER_ADMIN);
        assertThat(authorities).extracting(SimpleGrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder(Roles.ROLE_SUPER_ADMIN, Roles.ROLE_ADMIN);
    }

    @Test
    void admin_getsSingleAuthority() {
        List<SimpleGrantedAuthority> authorities =
                JwtAuthenticationFilter.authoritiesFor(Roles.ADMIN);
        assertThat(authorities).extracting(SimpleGrantedAuthority::getAuthority)
                .containsExactly(Roles.ROLE_ADMIN);
    }
}
