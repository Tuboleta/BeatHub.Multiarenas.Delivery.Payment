package com.beathub.multiarenas.delivery.payment.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class SecurityUtils {

    private SecurityUtils() {
    }

    public static Optional<UserPrincipal> getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return Optional.of(principal);
        }
        return Optional.empty();
    }

    public static Optional<UserPrincipal> getCurrentUser() {
        return getCurrentUserPrincipal();
    }

    public static Optional<Long> getCurrentUsuarioId() {
        return getCurrentUserPrincipal().map(UserPrincipal::getUsuarioId);
    }

    public static Optional<Long> getCurrentUserId() {
        return getCurrentUsuarioId();
    }

    public static Optional<String> getCurrentUsername() {
        return getCurrentUserPrincipal().map(UserPrincipal::getUsername);
    }

    public static Optional<String> getCurrentEmail() {
        return getCurrentUserPrincipal().map(UserPrincipal::getEmail);
    }

    public static Optional<Long> getCurrentArenaId() {
        return getCurrentUserPrincipal().map(UserPrincipal::getCurrentArenaId);
    }

    public static List<String> getCurrentUserRoles() {
        return getCurrentUserPrincipal()
                .map(p -> p.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .orElse(Collections.emptyList());
    }

    public static List<Long> getCurrentUserArenas() {
        return getCurrentUserPrincipal()
                .map(UserPrincipal::getArenaIds)
                .orElse(Collections.emptyList());
    }

    public static boolean hasRole(String role) {
        String formattedRole = role.startsWith("ROLE_") ? role : "ROLE_" + role;
        return getCurrentUserRoles().contains(formattedRole);
    }

    public static boolean hasArenaAccess(Long arenaId) {
        if (hasRole("ROLE_SUPER_ADMIN")) {
            return true;
        }
        return getCurrentUserArenas().contains(arenaId);
    }
}
