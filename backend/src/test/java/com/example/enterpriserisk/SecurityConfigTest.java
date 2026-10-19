package com.example.enterpriserisk;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void riskStatusWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/risk-status"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"));
    }

    @Test
    void riskStatusWithValidJwtReturnsSubject() throws Exception {
        mockMvc.perform(get("/api/risk-status").with(jwt().jwt(j -> j.subject("auth0|user-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("protected"))
                .andExpect(jsonPath("$.subject").value("auth0|user-123"));
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void undeclaredPathsAreDenied() throws Exception {
        mockMvc.perform(get("/admin").with(jwt()))
                .andExpect(status().isForbidden());
    }
}
