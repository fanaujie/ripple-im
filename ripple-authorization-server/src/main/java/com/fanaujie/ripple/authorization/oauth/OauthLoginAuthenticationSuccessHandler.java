package com.fanaujie.ripple.authorization.oauth;

import com.fanaujie.ripple.authorization.service.UserService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.AbstractAuthenticationTargetUrlRequestHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;

import java.io.IOException;

public class OauthLoginAuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final AuthenticationSuccessHandler delegate = new SavedRequestAwareAuthenticationSuccessHandler();
    private final UserService userService;

    public OauthLoginAuthenticationSuccessHandler(UserService userService) {
        this.userService = userService;
        ((AbstractAuthenticationTargetUrlRequestHandler) this.delegate).setDefaultTargetUrl("/signup-success");
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws ServletException, IOException {
        if (authentication instanceof OAuth2AuthenticationToken) {
            if (authentication.getPrincipal() instanceof OidcUser oidcUser) {
                this.userService.handleGoogleOAuth2User(oidcUser);
            }
        }
        this.delegate.onAuthenticationSuccess(request, response, authentication);
    }

}