package adliya.uz.task1.config.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public final class CustomUserPrincipal implements UserDetails {

    private final String username;
    private final String password;
    private final List<GrantedAuthority> authorities;
    private final boolean enabled;
    private final boolean mustChangePassword;
    private final Instant credentialsUpdatedAt;

    public CustomUserPrincipal(
            String username,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            boolean enabled,
            boolean mustChangePassword
    ) {
        this(username, password, authorities, enabled, mustChangePassword, null);
    }

    public CustomUserPrincipal(
            String username,
            String password,
            Collection<? extends GrantedAuthority> authorities,
            boolean enabled,
            boolean mustChangePassword,
            Instant credentialsUpdatedAt
    ) {
        this.username = username;
        this.password = password;
        this.authorities = List.copyOf(authorities);
        this.enabled = enabled;
        this.mustChangePassword = mustChangePassword;
        this.credentialsUpdatedAt = credentialsUpdatedAt;
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

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    /** Moment of the last password change or reset; tokens issued earlier are no longer accepted. */
    public Instant credentialsUpdatedAt() {
        return credentialsUpdatedAt;
    }
}
