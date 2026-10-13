package com.example.enterpriserisk;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import com.example.enterpriserisk.controller.RiskController;

class RiskControllerTest {

    @Test
    void riskStatusReturnsAuthenticatedSubject() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("auth0|user-123")
                .build();

        Map<String, Object> result = new RiskController().riskStatus(jwt);

        assertThat(result)
                .containsEntry("status", "protected")
                .containsEntry("subject", "auth0|user-123")
                .containsEntry("control", "JWT validation and authenticated access");
    }
}
