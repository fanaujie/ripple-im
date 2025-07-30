package com.fanaujie.ripple.authorization.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class LoginMvcController {

    @GetMapping("/login")
    public String login() {
        return "login";
    }


    @GetMapping("/signup")
    public String signup() {
        return "signup";
    }

    @GetMapping("/signup-success")
    public String googleSignupSuccess() {
        return "signup-success";
    }
}
