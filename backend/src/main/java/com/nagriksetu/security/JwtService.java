package com.nagriksetu.security;

import com.nagriksetu.config.NagrikProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ISSUER = "nagrik-setu";

    public sealed interface ParseResult permits Valid, Expired, Invalid {
    }

    public record Valid(long userId) implements ParseResult {
    }

    public record Expired() implements ParseResult {
    }

    public record Invalid() implements ParseResult {
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }

    private final SecretKey key;
    private final long expiryMinutes;

    public JwtService(NagrikProperties properties) {
        String secret = properties.security().jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "NAGRIK_JWT_SECRET must be set to a random value of at least 32 bytes (e.g. `openssl rand -base64 48`)");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = properties.security().jwtExpiryMinutes();
    }

    public IssuedToken issue(long userId, String role) {
        Instant now = Instant.now();
        Instant expires = now.plus(expiryMinutes, ChronoUnit.MINUTES);
        String token = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuer(ISSUER)
                .subject(Long.toString(userId))
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expires))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expires);
    }

    public ParseResult parse(String token) {
        try {
            Claims claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                    .parseSignedClaims(token).getPayload();
            return Optional.ofNullable(claims.getSubject())
                    .<ParseResult>map(sub -> new Valid(Long.parseLong(sub)))
                    .orElse(new Invalid());
        } catch (ExpiredJwtException e) {
            return new Expired();
        } catch (JwtException | IllegalArgumentException e) {
            return new Invalid();
        }
    }
}
