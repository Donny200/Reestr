package adliya.uz.task1.config.security;

import adliya.uz.task1.entity.Permission;
import adliya.uz.task1.entity.User;
import adliya.uz.task1.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        Set<GrantedAuthority> authorities = new HashSet<>();

        authorities.add(new SimpleGrantedAuthority(user.getRole().getName()));

        for (Permission permission : user.getRole().getPermissions()) {
            authorities.add(new SimpleGrantedAuthority(permission.getCode()));
        }

        return new CustomUserPrincipal(
                user.getEmail(),
                user.getPassword(),
                authorities,
                Boolean.TRUE.equals(user.getEnabled()),
                Boolean.TRUE.equals(user.getMustChangePassword()),
                user.getCredentialsUpdatedAt()
        );
    }
}
