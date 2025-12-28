package com.booking.userService.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Date;
import java.util.List;

@Data // Lombok: Adds getters, setters, toString(), etc.
@Builder // Lombok: Provides a builder pattern
@NoArgsConstructor // Lombok: Required for JPA
@AllArgsConstructor // Lombok: For the builder
@Entity // Tells JPA this is a table
@Table(name = "users") // Specifies the table name
@JsonIgnoreProperties({ "hibernateLazyInitializer", "handler", "password", "authorities", "accountNonExpired",
        "accountNonLocked", "credentialsNonExpired", "enabled" })
public class User implements UserDetails {

    @Id // Marks this as the Primary Key
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Auto-incrementing ID
    private Long id;

    @Column(nullable = false, unique = true) // Required and must be unique
    private String email;

    @Column(nullable = false) // Required
    private String password;

    @CreationTimestamp // Automatically sets the value on creation
    @Column(updatable = false, nullable = false)
    private Date createdAt;

    // --- Role field ---
    @Enumerated(EnumType.STRING) // Stores the enum as a string ("USER", "ADMIN")
    @Column(nullable = false)
    private Role role;

    // --- Refresh Token field ---
    @Column(length = 512) // Make it long enough for a JWT
    private String refreshToken;

    // --- UserDetails Methods ---
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority(role.name()));
    }

    @Override
    public String getUsername() {
        // Our "username" is the email
        return this.email;
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
        return true;
    }
}