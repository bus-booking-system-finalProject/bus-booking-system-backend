package com.booking.userService.service;

import com.booking.userService.model.Role;
import com.booking.userService.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret-key}")
    private String SECRET_KEY;

    private static final long ACCESS_TOKEN_VALIDITY = 1000 * 60 * 60; // 1 hour
    private static final long REFRESH_TOKEN_VALIDITY = 1000 * 60 * 60 * 24 * 7; // 7 days

    // Generates an Access token
    public String generateAccessToken(UserDetails userDetails) {
        // --- Add roles to the access token ---
        Map<String, Object> extraClaims = new HashMap<>();
        if (userDetails instanceof User) {
             User user = (User) userDetails;
            extraClaims.put("role", user.getRole().name());
            
            // Inject OperatorId if role is OPERATOR or STAFF
            if ((user.getRole() == Role.OPERATOR || user.getRole() == Role.STAFF) 
                && user.getOperatorId() != null) {
                extraClaims.put("operatorId", user.getOperatorId().toString());
            }
        }
        
        return Jwts.builder()
                .claims(extraClaims) // Use the new claims
                .subject(userDetails.getUsername())
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_VALIDITY))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    // --- Method to generate a refresh token ---
    public String generateRefreshToken(UserDetails userDetails) {
        return Jwts.builder()
                .subject(userDetails.getUsername()) // Only needs the subject
                .issuedAt(new Date(System.currentTimeMillis()))
                // --- MODIFIED: Use long expiry ---
                .expiration(new Date(System.currentTimeMillis() + REFRESH_TOKEN_VALIDITY))
                .signWith(getSignInKey(), Jwts.SIG.HS256)
                .compact();
    }

    // Validates the token
    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return (username.equals(userDetails.getUsername())) && !isTokenExpired(token);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // Extracts information from the token
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSignInKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    // Decodes the Base64 secret key
    private SecretKey getSignInKey() {
        byte[] keyBytes = Decoders.BASE64.decode(SECRET_KEY);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}