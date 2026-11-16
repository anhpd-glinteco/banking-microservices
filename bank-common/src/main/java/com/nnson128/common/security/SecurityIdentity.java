package com.nnson128.common.security;

import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

public final class SecurityIdentity {
    private SecurityIdentity() {}
    public static Long userId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null || !auth.getName().matches("[1-9][0-9]*")) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
        try { return Long.valueOf(auth.getName()); }
        catch (NumberFormatException ex) { throw new InsufficientAuthenticationException("Invalid subject"); }
    }
    public static boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
    public static void requireOwner(Long ownerId) {
        if (!Objects.equals(userId(), ownerId)) throw new AccessDeniedException("Forbidden");
    }
    public static void requireOwnerOrAdmin(Long ownerId) {
        if (!isAdmin()) requireOwner(ownerId);
    }
}
