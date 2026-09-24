package com.pr_reviewer.prreviewer.auth;

import com.nimbusds.openid.connect.sdk.assurance.Status;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AuthController {

    @GetMapping("/api/me")
    public Map<String, Object> getCurrentUser(OAuth2AuthenticationToken authToken) {
        if(authToken == null) {
            return Map.of("authenticated", false);
        }

        OAuth2User principal = authToken.getPrincipal();
        return Map.of(
                "authenticated", true,
                "username", principal.getAttribute("login"),
                "avatarUrl", principal.getAttribute("avatar_url")
        );
    }

    @GetMapping("/api/health-check")
    public Status healthCheck() {
        return new Status("ok");
    }
}
