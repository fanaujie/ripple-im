package com.fanaujie.ripple.storage.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonDeserialize
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS)
public class User implements UserDetails {
    public static final String DEFAULT_ROLE_USER = "ROLE_user";
    private long userId;
    private String account;
    private String password;
    private String role;
    private byte status;

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        if (Objects.isNull(role) || role.isEmpty()) {
            return List.of(new SimpleGrantedAuthority(DEFAULT_ROLE_USER));
        }
        return List.of(new SimpleGrantedAuthority(role));
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    @JsonIgnore
    public boolean isEnabled() {
        return status == 0;
    }

    @Override
    @JsonIgnore
    public String getUsername() {
        return account;
    }
}
