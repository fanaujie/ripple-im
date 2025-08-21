package com.fanaujie.ripple.authorization.controller;

import com.fanaujie.ripple.authorization.model.api.CommonResponse;
import com.fanaujie.ripple.authorization.oauth.RippleUserManager;
import com.fanaujie.ripple.authorization.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SignUpRestController {

    private final RippleUserManager userManager;
    private final UserService userService;

    public SignUpRestController(RippleUserManager userManager, UserService userService) {
        this.userManager = userManager;
        this.userService = userService;
    }

    @PostMapping(path = "/signup")
    public ResponseEntity<CommonResponse> signup(HttpServletRequest request) {
        String account = request.getParameter("account");
        String password = request.getParameter("password");
        String confirmPassword = request.getParameter("confirmPassword");
        if (account == null || password == null || confirmPassword == null) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Missing required fields"));
        }
        if (!password.equals(confirmPassword)) {
            return ResponseEntity.badRequest()
                    .body(new CommonResponse(400, "Passwords do not match"));
        }
        if (userManager.userExists(account)) {
            return ResponseEntity.badRequest().body(new CommonResponse(400, "User already exists"));
        }
        return this.userService.signup(account, password, confirmPassword);
    }
}
