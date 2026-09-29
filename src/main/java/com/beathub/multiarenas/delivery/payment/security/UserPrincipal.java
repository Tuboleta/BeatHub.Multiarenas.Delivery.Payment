package com.beathub.multiarenas.delivery.payment.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.jsonwebtoken.Claims;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class UserPrincipal implements UserDetails {

    private final Long usuarioId;
    private final String username;
    @JsonIgnore
    private final String password;
    private final String email;
    private final String ssoId;
    private final Long personaId;
    private final Long currentArenaId;
    private final List<Long> arenaIds;
    private final List<String> scopes;
    private final Collection<? extends GrantedAuthority> authorities;
    private final boolean enabled;

    @SuppressWarnings("unchecked")
    public static UserPrincipal create(Claims claims) {
        Long usuarioId = null;
        Object rawUsuarioId = claims.get("usuarioId");
        if (rawUsuarioId == null) {
            rawUsuarioId = claims.get("id");
        }
        if (rawUsuarioId instanceof Number num) {
            usuarioId = num.longValue();
        } else if (rawUsuarioId instanceof String str && !str.isBlank()) {
            try {
                usuarioId = Long.parseLong(str);
            } catch (NumberFormatException ignored) {}
        }
        if (usuarioId == null && claims.getSubject() != null && !claims.getSubject().isBlank()) {
            try {
                usuarioId = Long.parseLong(claims.getSubject());
            } catch (NumberFormatException ignored) {}
        }

        String username = claims.get("username", String.class);
        String email = claims.get("email", String.class);
        String ssoId = claims.get("ssoId", String.class);
        Long personaId = null;
        Object rawPersonaId = claims.get("personaId");
        if (rawPersonaId instanceof Number num) {
            personaId = num.longValue();
        } else if (rawPersonaId instanceof String str && !str.isBlank()) {
            try {
                personaId = Long.parseLong(str);
            } catch (NumberFormatException ignored) {}
        }

        Long currentArenaId = null;
        Object rawCurrentArena = claims.get("currentArenaId");
        if (rawCurrentArena instanceof Number num) {
            currentArenaId = num.longValue();
        } else if (rawCurrentArena instanceof String str && !str.isBlank()) {
            try {
                currentArenaId = Long.parseLong(str);
            } catch (NumberFormatException ignored) {}
        }

        List<String> roles = claims.get("roles", List.class);
        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roles != null) {
            for (String role : roles) {
                if (role != null && !role.isBlank()) {
                    String roleName = role.toUpperCase();
                    if (!roleName.startsWith("ROLE_")) {
                        roleName = "ROLE_" + roleName;
                    }
                    authorities.add(new SimpleGrantedAuthority(roleName));
                }
            }
        }
        if (authorities.isEmpty()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_CLIENTE"));
        }

        List<String> scopes = claims.get("scopes", List.class);
        if (scopes == null) {
            scopes = Collections.singletonList("api:access");
        }

        List<?> rawArenaIds = claims.get("arenaIds", List.class);
        List<Long> arenaIds = new ArrayList<>();
        if (rawArenaIds != null) {
            for (Object item : rawArenaIds) {
                if (item instanceof Number num) {
                    arenaIds.add(num.longValue());
                } else if (item instanceof String str && !str.isBlank()) {
                    try {
                        arenaIds.add(Long.parseLong(str));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }

        return UserPrincipal.builder()
                .usuarioId(usuarioId)
                .username(username)
                .password(null)
                .email(email)
                .ssoId(ssoId)
                .personaId(personaId)
                .currentArenaId(currentArenaId)
                .arenaIds(arenaIds)
                .scopes(scopes)
                .authorities(authorities)
                .enabled(true)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
