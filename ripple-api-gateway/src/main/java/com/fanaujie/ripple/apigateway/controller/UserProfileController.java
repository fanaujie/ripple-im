package com.fanaujie.ripple.apigateway.controller;

import com.fanaujie.ripple.apigateway.dto.CommonResponse;
import com.fanaujie.ripple.apigateway.dto.UpdateNickNameRequest;

import com.fanaujie.ripple.apigateway.dto.UserProfileResponse;
import com.fanaujie.ripple.apigateway.service.UserProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getUserProfile(@AuthenticationPrincipal Jwt jwt) {
        return userProfileService.getUserProfile(Long.parseLong(jwt.getSubject()));
    }

    @PutMapping("/nickname")
    public ResponseEntity<CommonResponse> updateNickName(
            @Valid @RequestBody UpdateNickNameRequest request, @AuthenticationPrincipal Jwt jwt) {
        return userProfileService.updateNickName(
                Long.parseLong(jwt.getSubject()), request.getNickName());
    }

    @DeleteMapping("/avatar")
    public ResponseEntity<CommonResponse> deleteAvatar(@AuthenticationPrincipal Jwt jwt) {
        userProfileService.deleteAvatar(Long.parseLong(jwt.getSubject()));
        return ResponseEntity.status(200).body(new CommonResponse(200, "success"));
    }
}
