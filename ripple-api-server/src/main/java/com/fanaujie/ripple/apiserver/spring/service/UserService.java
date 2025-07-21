package com.fanaujie.ripple.apiserver.spring.service;

import com.fanaujie.ripple.apiserver.spring.model.api.CommonResponse;
import com.fanaujie.ripple.apiserver.spring.model.mapper.User;
import com.fanaujie.ripple.apiserver.spring.oauth.RippleUserManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final RippleUserManager userManager;
    private final PasswordEncoder passwordEncoder;

    public UserService(RippleUserManager userManager, PasswordEncoder passwordEncoder) {
        this.userManager = userManager;
        this.passwordEncoder = passwordEncoder;
    }

    public ResponseEntity<CommonResponse> signup(HttpServletRequest request) {
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        if (email == null || password == null || confirmPassword == null) {
            return ResponseEntity.badRequest().body(new CommonResponse("400", "Missing required fields"));
        }
        if (!password.equals(confirmPassword)) {
            return ResponseEntity.badRequest().body(new CommonResponse("400", "Passwords do not match"));
        }
        if (userManager.userExists(email)) {
            return ResponseEntity.badRequest().body(new CommonResponse("400", "User already exists"));
        }
        userManager.createUser(new User(0, email, this.passwordEncoder.encode(password), true, null));
        return ResponseEntity.ok().body(new CommonResponse("200", "User registered successfully"));
    }
}
