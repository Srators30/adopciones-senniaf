package com.senniaf.adopciones.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas unitarias del servicio de JWT: generacion, extraccion de datos
 * y deteccion de expiracion (parte de las evidencias de seguridad de HU-01).
 */
class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", "ClaveSecretaDePruebaDeAlMenos32CaracteresParaTest");
        ReflectionTestUtils.setField(jwtService, "expirationMinutes", 60L);
    }

    @Test
    @DisplayName("El token generado contiene el usuario y el rol, y es valido")
    void generaYValidaUnTokenCorrectamente() {
        String token = jwtService.generateToken("admin", "ADMINISTRADOR");

        assertNotNull(token);
        assertEquals("admin", jwtService.extractUsername(token));
        assertEquals("ADMINISTRADOR", jwtService.extractRole(token));
        assertTrue(jwtService.isTokenValid(token, "admin"));
        assertFalse(jwtService.isTokenExpired(token));
    }

    @Test
    @DisplayName("Un token con expiracion en el pasado se detecta como expirado")
    void unTokenExpiradoSeDetectaComoInvalido() {
        ReflectionTestUtils.setField(jwtService, "expirationMinutes", -1L);
        String token = jwtService.generateToken("admin", "ADMINISTRADOR");

        assertTrue(jwtService.isTokenExpired(token));
    }

    @Test
    @DisplayName("Un token no es valido si se verifica contra un usuario distinto")
    void unTokenNoEsValidoParaOtroUsuario() {
        String token = jwtService.generateToken("admin", "ADMINISTRADOR");

        assertFalse(jwtService.isTokenValid(token, "otroUsuario"));
    }
}
