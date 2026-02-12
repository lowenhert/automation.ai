package com.lowenhert.patchmanagement.core.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;

@Slf4j
@Component
public class JwtTokenUtil {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private long jwtExpirationMs;

    private Key getSigningKey() {
        try {
            byte[] keyBytes = jwtSecret.getBytes();
            if (keyBytes.length < 32) {
                log.warn("JWT secret key length is less than 256 bits. Consider using a stronger key.");
                // Pad the key if it's too short (not ideal, but better than failing)
                byte[] paddedKey = new byte[32];
                System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
                return Keys.hmacShaKeyFor(paddedKey);
            }
            return Keys.hmacShaKeyFor(keyBytes);
        } catch (Exception e) {
            log.error("Failed to create signing key", e);
            throw new IllegalStateException("Failed to initialize JWT signing key", e);
        }
    }

    public String generateToken(String username) {
        try {
            log.debug("Generating JWT token for user: {}", username);
            String token = Jwts.builder()
                    .setSubject(username)
                    .setIssuedAt(new Date())
                    .setExpiration(new Date(System.currentTimeMillis() + jwtExpirationMs))
                    .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                    .compact();
            log.debug("Successfully generated token for user: {}", username);
            return token;
        } catch (Exception e) {
            log.error("Failed to generate JWT token for user: {}", username, e);
            throw new JwtTokenException("Failed to generate JWT token", e);
        }
    }

    public String getUsernameFromToken(String token) {
        try {
            log.debug("Extracting username from token");
            String username = Jwts.parserBuilder()
                    .setSigningKey(getSigningKey())
                    .build()
                    .parseClaimsJws(token)
                    .getBody()
                    .getSubject();
            log.debug("Extracted username: {}", username);
            return username;
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token for user: {}", e.getClaims().getSubject());
            throw new JwtTokenException("JWT token expired", e);
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", token);
            throw new JwtTokenException("Unsupported JWT token", e);
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", token);
            throw new JwtTokenException("Malformed JWT token", e);
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature for token: {}", token);
            throw new JwtTokenException("Invalid JWT signature", e);
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or null");
            throw new JwtTokenException("JWT token is empty or null", e);
        }
    }

    public boolean validateToken(String token) {
        try {
            log.debug("Validating JWT token");
            Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
            log.debug("Token validation successful");
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT token: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("Malformed JWT token: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("Invalid JWT signature: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT token is empty or null: {}", e.getMessage());
        }
        return false;
    }

    public static class JwtTokenException extends RuntimeException {
        /**
		 * 
		 */
		private static final long serialVersionUID = 1L;

		public JwtTokenException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}