package com.fanaujie.ripple.authorization.service;

import com.fanaujie.ripple.authorization.dto.CommonResponse;
import com.fanaujie.ripple.protobuf.snowflakeid.GenerateIdResponse;
import com.fanaujie.ripple.snowflakeid.client.SnowflakeIdClient;
import com.fanaujie.ripple.storage.model.User;
import com.fanaujie.ripple.storage.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    private final UserRepository userStorage;
    private final PasswordEncoder passwordEncoder;
    private final SnowflakeIdClient snowflakeIdClient;

    public UserService(
            UserRepository userStorage,
            PasswordEncoder passwordEncoder,
            @Value("${snowflake.server.host}") String snowflakeServerHost,
            @Value("${snowflake.server.port}") int snowflakeServerPort) {
        this.userStorage = userStorage;
        this.passwordEncoder = passwordEncoder;
        this.snowflakeIdClient = new SnowflakeIdClient(snowflakeServerHost, snowflakeServerPort);
    }

    public ResponseEntity<CommonResponse> signup(
            String account, String password, String confirmPassword) {

        User newUser = new User();
        newUser.setUserId(this.getNextUserId());
        newUser.setAccount(account);
        newUser.setPassword(this.passwordEncoder.encode(password));
        newUser.setRole(User.DEFAULT_ROLE_USER);
        this.userStorage.insertUser(newUser, account, "");
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
        if (!this.userStorage.userExists(openId)) {
            // Create new user account
            User newUser = new User();
            newUser.setUserId(this.getNextUserId());
            newUser.setAccount(openId);
            newUser.setPassword(""); // No password for OAuth2 users
            newUser.setRole(User.DEFAULT_ROLE_USER);
            this.userStorage.insertUser(newUser, name != null ? name : email, picture);
        }
    }

    private long getNextUserId() {
        try {
            CompletableFuture<GenerateIdResponse> future = snowflakeIdClient.requestSnowflakeId();
            GenerateIdResponse response = future.get(2, TimeUnit.SECONDS);
            return response.getId();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate user ID", e);
        }
    }
}
