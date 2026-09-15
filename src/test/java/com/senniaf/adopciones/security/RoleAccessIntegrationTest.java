package com.senniaf.adopciones.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.senniaf.adopciones.dto.LoginRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pruebas de integracion de extremo a extremo para HU-01: levantan el
 * contexto completo de Spring (incluye DataInitializer, que crea los
 * usuarios de prueba admin / tecnico1 / juez1) y verifican login + control
 * de acceso por rol contra endpoints reales via MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleAccessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String obtenerToken(String username, String password) throws Exception {
        LoginRequest request = new LoginRequest(username, password);

        String responseBody = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(responseBody).get("token").asText();
    }

    @Test
    @DisplayName("Sin token, el acceso a una ruta protegida es rechazado (401)")
    void sinTokenElAccesoEsRechazado() throws Exception {
        mockMvc.perform(get("/api/admin/ping"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Un Tecnico no puede acceder a rutas exclusivas de Administrador (403)")
    void unTecnicoNoAccedeARutasDeAdministrador() throws Exception {
        String token = obtenerToken("tecnico1", "Tecnico123!");

        mockMvc.perform(get("/api/admin/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Un Administrador puede acceder a su propia ruta (200)")
    void unAdministradorAccedeASuRuta() throws Exception {
        String token = obtenerToken("admin", "Admin123!");

        mockMvc.perform(get("/api/admin/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un Juez accede a su ruta pero no a la de Administrador")
    void unJuezAccedeASuRutaYNoALaDeAdministrador() throws Exception {
        String token = obtenerToken("juez1", "Juez123!");

        mockMvc.perform(get("/api/juez/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/ping")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Login con credenciales invalidas retorna 401")
    void loginConCredencialesInvalidasRetorna401() throws Exception {
        LoginRequest request = new LoginRequest("admin", "claveIncorrecta");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }
}
