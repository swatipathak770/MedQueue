package com.medqueue.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {
    private final SecretKey key;
    private final long expirationSeconds;

    public JwtService(@Value("${medqueue.jwt.secret}") String secret,
                      @Value("${medqueue.jwt.expiration-seconds:3600}") long expirationSeconds) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationSeconds = expirationSeconds;
    }
    public String generateToken(UserDetails user) {
        Instant now = Instant.now();
        return Jwts.builder().subject(user.getUsername()).issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expirationSeconds))).signWith(key).compact();
    }
    public long getExpirationSeconds() { return expirationSeconds; }
    public String extractUsername(String token) { return extractClaim(token, Claims::getSubject); }
    public boolean isTokenValid(String token, UserDetails user) {
        return user.getUsername().equals(extractUsername(token)) && extractClaim(token, Claims::getExpiration).after(new Date());
    }
    private <T> T extractClaim(String token, Function<Claims, T> resolver) {
        return resolver.apply(Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload());
    }
}
