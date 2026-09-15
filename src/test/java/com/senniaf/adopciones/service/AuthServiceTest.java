package com.senniaf.adopciones.service;

import com.senniaf.adopciones.dto.LoginRequest;
import com.senniaf.adopciones.dto.LoginResponse;
import com.senniaf.adopciones.model.Rol;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.UsuarioRepository;
import com.senniaf.adopciones.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de HU-01: "Acceso seguro y control de roles".
 * Cubren los criterios de aceptacion de login valido, credenciales
 * invalidas, usuario inexistente y usuario deshabilitado.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        // El valor de password aqui simula un hash BCrypt ya almacenado; nunca es la clave real.
        usuario = new Usuario("tecnico1", "$2a$10$hashSimuladoDePruebaXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX", Rol.TECNICO);
    }

    @Test
    @DisplayName("Login con credenciales validas retorna un token y el rol del usuario")
    void loginConCredencialesValidasRetornaToken() {
        LoginRequest request = new LoginRequest("tecnico1", "Tecnico123!");

        when(usuarioRepository.findByUsername("tecnico1")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("Tecnico123!", usuario.getPassword())).thenReturn(true);
        when(jwtService.generateToken("tecnico1", "TECNICO")).thenReturn("token-simulado");

        LoginResponse response = authService.login(request);

        assertEquals("token-simulado", response.getToken());
        assertEquals("tecnico1", response.getUsername());
        assertEquals("TECNICO", response.getRol());
        verify(jwtService).generateToken("tecnico1", "TECNICO");
    }

    @Test
    @DisplayName("Login con contrasena incorrecta lanza BadCredentialsException y no genera token")
    void loginConContrasenaIncorrectaLanzaExcepcion() {
        LoginRequest request = new LoginRequest("tecnico1", "claveIncorrecta");

        when(usuarioRepository.findByUsername("tecnico1")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("claveIncorrecta", usuario.getPassword())).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
        verify(jwtService, never()).generateToken(anyString(), anyString());
    }

    @Test
    @DisplayName("Login con usuario inexistente lanza BadCredentialsException")
    void loginConUsuarioInexistenteLanzaExcepcion() {
        LoginRequest request = new LoginRequest("noexiste", "cualquierClave");

        when(usuarioRepository.findByUsername("noexiste")).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }

    @Test
    @DisplayName("Login con usuario deshabilitado lanza BadCredentialsException")
    void loginConUsuarioDeshabilitadoLanzaExcepcion() {
        usuario.setHabilitado(false);
        LoginRequest request = new LoginRequest("tecnico1", "Tecnico123!");

        when(usuarioRepository.findByUsername("tecnico1")).thenReturn(Optional.of(usuario));

        assertThrows(BadCredentialsException.class, () -> authService.login(request));
    }
}
