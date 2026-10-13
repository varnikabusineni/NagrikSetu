package com.nagriksetu.security;

import com.nagriksetu.user.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates "Authorization: Bearer" tokens. The user is reloaded from the database on each
 * request so disabled accounts and role changes take effect immediately.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = "nagrik.authError";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final TransactionTemplate readOnlyTx;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                   org.springframework.transaction.PlatformTransactionManager txManager) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.readOnlyTx = new TransactionTemplate(txManager);
        this.readOnlyTx.setReadOnly(true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            switch (jwtService.parse(header.substring(7).trim())) {
                case JwtService.Valid valid -> {
                    UserPrincipal principal = readOnlyTx.execute(status -> userRepository.findById(valid.userId())
                            .filter(u -> u.isEnabled())
                            .map(UserPrincipal::from)
                            .orElse(null));
                    if (principal != null) {
                        var auth = new UsernamePasswordAuthenticationToken(principal, null, principal.authorities());
                        SecurityContextHolder.getContext().setAuthentication(auth);
                    } else {
                        request.setAttribute(AUTH_ERROR_ATTRIBUTE, "SESSION_INVALID");
                    }
                }
                case JwtService.Expired expired -> request.setAttribute(AUTH_ERROR_ATTRIBUTE, "SESSION_EXPIRED");
                case JwtService.Invalid invalid -> request.setAttribute(AUTH_ERROR_ATTRIBUTE, "SESSION_INVALID");
            }
        }
        chain.doFilter(request, response);
    }
}
