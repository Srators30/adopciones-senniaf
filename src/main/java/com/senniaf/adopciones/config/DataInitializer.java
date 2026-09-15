package com.senniaf.adopciones.config;

import com.senniaf.adopciones.model.Rol;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.UsuarioRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Crea usuarios de ejemplo (uno por rol) al iniciar la aplicacion, unicamente
 * si no existen todavia. Util para pruebas manuales en Postman/Swagger y para
 * las pruebas de integracion. En un entorno real este mecanismo se reemplaza
 * por un modulo de administracion de usuarios.
 */
@Component
public class DataInitializer implements CommandLineRunner {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        crearSiNoExiste("admin", "Admin123!", Rol.ADMINISTRADOR);
        crearSiNoExiste("tecnico1", "Tecnico123!", Rol.TECNICO);
        crearSiNoExiste("juez1", "Juez123!", Rol.JUEZ);
    }

    private void crearSiNoExiste(String username, String passwordSinHashear, Rol rol) {
        usuarioRepository.findByUsername(username).orElseGet(() -> {
            Usuario usuario = new Usuario(username, passwordEncoder.encode(passwordSinHashear), rol);
            return usuarioRepository.save(usuario);
        });
    }
}
