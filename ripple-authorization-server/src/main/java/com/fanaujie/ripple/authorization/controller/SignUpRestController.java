package com.fanaujie.ripple.authorization.controller;

import com.fanaujie.ripple.authorization.model.api.CommonResponse;
import com.fanaujie.ripple.authorization.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SignUpRestController {

    private final UserService userService;

    public SignUpRestController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping(path = "/signup")
    public ResponseEntity<CommonResponse> signup(HttpServletRequest req) {
        return this.userService.signup(req);
    }
}
