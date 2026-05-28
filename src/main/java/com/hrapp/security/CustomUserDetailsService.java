package com.hrapp.security;

import com.hrapp.entity.User;
import com.hrapp.repository.UserRepository;
import com.hrapp.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Loads a user (by mobile number) and their roles for Spring Security.
 * <p>
 * Returns a Spring Security {@link org.springframework.security.core.userdetails.User}
 * populated with the user's password hash and {@code ROLE_*} authorities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final UserRoleRepository userRoleRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String mobile) throws UsernameNotFoundException {
        User user = userRepository.findByMobile(mobile)
                .orElseThrow(() -> {
                    log.warn("Login attempt for unknown mobile: {}", mobile);
                    return new UsernameNotFoundException("User not found with mobile: " + mobile);
                });

        List<SimpleGrantedAuthority> authorities = userRoleRepository
                .findByUserId(user.getId()).stream()
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getName()))
                .toList();

        return new org.springframework.security.core.userdetails.User(
                user.getMobile(),
                user.getPasswordHash(),
                authorities
        );
    }
}
