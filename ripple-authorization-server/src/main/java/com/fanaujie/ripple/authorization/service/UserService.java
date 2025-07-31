package com.fanaujie.ripple.authorization.service;

import com.fanaujie.ripple.authorization.model.api.CommonResponse;
import com.fanaujie.ripple.database.model.User;
import com.fanaujie.ripple.database.model.UserProfile;
import com.fanaujie.ripple.database.mapper.UserProfileMapper;
import com.fanaujie.ripple.authorization.oauth.RippleUserManager;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final RippleUserManager userManager;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileMapper userProfileMapper;

    public UserService(RippleUserManager userManager, PasswordEncoder passwordEncoder, UserProfileMapper userProfileMapper) {
        this.userManager = userManager;
        this.passwordEncoder = passwordEncoder;
        this.userProfileMapper = userProfileMapper;
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
        User newUser = new User();
        newUser.setAccount(email);
        newUser.setPassword(this.passwordEncoder.encode(password));
        newUser.setEnabled(true);
        newUser.setRole(User.DEFAULT_ROLE_USER);
        userManager.createUser(newUser);

        // Create corresponding user profile
        UserProfile userProfile = new UserProfile();
        userProfile.setAccount(email);
        userProfile.setUserType(0); // Default user type
        userProfile.setNickName(email); // Default nickname is email
        userProfileMapper.insertUserProfile(userProfile);

        return ResponseEntity.ok().body(new CommonResponse("200", "User registered successfully"));
    }

    public void handleGoogleOAuth2User(OAuth2User oauth2User) {
        String openId = oauth2User.getAttribute("sub");
        String email = oauth2User.getAttribute("email");
        String name = oauth2User.getAttribute("name");
        String picture = oauth2User.getAttribute("picture");

        if (openId == null) {
            throw new IllegalArgumentException("OpenID is required for Google OAuth2 user");
        }

        // Check if user already exists
        if (!userManager.userExists(openId)) {
            // Create new user account
            User newUser = new User();
            newUser.setAccount(openId);
            newUser.setPassword(""); // No password for OAuth2 users
            newUser.setEnabled(true);
            newUser.setRole(User.DEFAULT_ROLE_USER);
            userManager.createUser(newUser);

            // Create corresponding user profile
            UserProfile userProfile = new UserProfile();
            userProfile.setAccount(openId);
            userProfile.setUserType(0); // Default user type
            userProfile.setNickName(name != null ? name : email); // Use name or fallback to email
            userProfile.setUserPortrait(picture); // Google profile picture URL
            userProfileMapper.insertUserProfile(userProfile);
        }
    }
}
