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
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping
    public ResponseEntity<UserProfileResponse> getUserProfile(Authentication authentication) {
        return userProfileService.getUserProfile(authentication.getName());
    }

    @PutMapping("/nickname")
    public ResponseEntity<CommonResponse> updateNickName(
            @Valid @RequestBody UpdateNickNameRequest request, Authentication authentication) {

        String account = authentication.getName();
        return userProfileService.updateNickName(account, request.getNickName());
    }

    @DeleteMapping("/portrait")
    public ResponseEntity<CommonResponse> deleteUserPortrait(Authentication authentication) {
        String account = authentication.getName();
        userProfileService.deleteUserPortrait(account);
        return ResponseEntity.status(200).body(new CommonResponse(200, "success"));
    }
}
