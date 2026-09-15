package com.senniaf.adopciones.service;

import com.senniaf.adopciones.dto.LoginRequest;
import com.senniaf.adopciones.dto.LoginResponse;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.UsuarioRepository;
import com.senniaf.adopciones.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Logica de negocio de HU-01: validar credenciales y emitir el token de sesion.
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository,
                        PasswordEncoder passwordEncoder,
                        JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BadCredentialsException("Usuario o contrasena incorrectos"));

        if (!usuario.isEnabled()) {
            throw new BadCredentialsException("El usuario no esta habilitado");
        }

        if (!passwordEncoder.matches(request.getPassword(), usuario.getPassword())) {
            throw new BadCredentialsException("Usuario o contrasena incorrectos");
        }

        String token = jwtService.generateToken(usuario.getUsername(), usuario.getRol().name());
        return new LoginResponse(token, usuario.getUsername(), usuario.getRol().name());
    }
}
