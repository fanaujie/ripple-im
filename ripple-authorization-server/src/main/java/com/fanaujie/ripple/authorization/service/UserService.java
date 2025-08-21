package com.fanaujie.ripple.authorization.service;

import com.fanaujie.ripple.authorization.model.api.CommonResponse;
import com.fanaujie.ripple.database.model.User;
import com.fanaujie.ripple.database.model.UserProfile;
import com.fanaujie.ripple.database.mapper.UserProfileMapper;
import com.fanaujie.ripple.authorization.oauth.RippleUserManager;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final RippleUserManager userManager;
    private final PasswordEncoder passwordEncoder;
    private final UserProfileMapper userProfileMapper;

    public UserService(
            RippleUserManager userManager,
            PasswordEncoder passwordEncoder,
            UserProfileMapper userProfileMapper) {
        this.userManager = userManager;
        this.passwordEncoder = passwordEncoder;
        this.userProfileMapper = userProfileMapper;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ResponseEntity<CommonResponse> signup(
            String account, String password, String confirmPassword) {

        User newUser = new User();
        newUser.setAccount(account);
        newUser.setPassword(this.passwordEncoder.encode(password));
        newUser.setEnabled(true);
        newUser.setRole(User.DEFAULT_ROLE_USER);
        userManager.createUser(newUser);

        // Create corresponding user profile
        UserProfile userProfile = new UserProfile();
        userProfile.setUserId(newUser.getId());
        userProfile.setUserType(0); // Default user type
        userProfile.setNickName(account); // Default nickname is email
        userProfileMapper.insertUserProfile(userProfile);

        return ResponseEntity.ok().body(new CommonResponse(200, "success"));
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
            userProfile.setUserId(newUser.getId());
            userProfile.setUserType(0); // Default user type
            userProfile.setNickName(name != null ? name : email); // Use name or fallback to email
            userProfile.setAvatar(picture); // Google profile picture URL
            userProfileMapper.insertUserProfile(userProfile);
        }
    }
}
