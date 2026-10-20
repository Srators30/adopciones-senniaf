package com.senniaf.adopciones.controller;

import com.senniaf.adopciones.dto.CrearUsuarioRequest;
import com.senniaf.adopciones.model.Rol;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.UsuarioRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Administracion de cuentas (HU-01). Cuelga de /api/admin/**, asi que
 * SecurityConfig exige rol ADMINISTRADOR: un TECNICO o un JUEZ que llame
 * a esta ruta recibe 403 aunque su token sea valido.
 *
 * Sustituye al almacen en JSON del frontend: las cuentas se crean aqui,
 * en la misma tabla contra la que despues valida el login.
 */
@RestController
@RequestMapping("/api/admin/usuarios")
public class UsuarioAdminController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioAdminController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> crear(@Valid @RequestBody CrearUsuarioRequest peticion,
                                                      @AuthenticationPrincipal Usuario administrador) {

        // 409 Conflict: el recurso ya existe. El frontend lo traduce a
        // "Ya existe una cuenta con ese usuario".
        if (usuarioRepository.findByUsername(peticion.getUsername()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Ya existe una cuenta con ese usuario"));
        }

        Usuario nuevo = new Usuario(
                peticion.getUsername(),
                passwordEncoder.encode(peticion.getPassword()),  // nunca en texto plano
                Rol.valueOf(peticion.getRol()),
                peticion.getNombreCompleto());
        nuevo.setHabilitado(peticion.isHabilitado());

        usuarioRepository.save(nuevo);

        // TODO (Sprint 2): registrar en auditoria que 'administrador'
        // creo esta cuenta, con fecha y hora.

        Map<String, Object> creado = new LinkedHashMap<>();
        creado.put("username", nuevo.getUsername());
        creado.put("nombreCompleto", nuevo.getNombreCompleto());
        creado.put("rol", nuevo.getRol().name());
        creado.put("habilitado", nuevo.isEnabled());
        creado.put("creadoPor", administrador.getUsername());

        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    /** Listado sin contrasenas, para la futura pantalla de administracion. */
    @GetMapping
    public List<Map<String, Object>> listar() {
        return usuarioRepository.findAll().stream()
                .map(u -> {
                    Map<String, Object> fila = new LinkedHashMap<>();
                    fila.put("username", u.getUsername());
                    fila.put("nombreCompleto", u.getNombreCompleto());
                    fila.put("rol", u.getRol().name());
                    fila.put("habilitado", u.isEnabled());
                    return fila;
                })
                .toList();
    }
}
