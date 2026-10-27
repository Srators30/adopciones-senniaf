package com.senniaf.adopciones.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Configuracion central de seguridad (HU-01):
 * - Sesiones sin estado (stateless), autenticacion via JWT.
 * - Contrasenas con BCrypt.
 * - Reglas de autorizacion por rol.
 *
 * El frontend (login, registro de menor y creacion de usuarios) se sirve
 * desde src/main/resources/static, en el mismo puerto 8080, asi que no
 * hace falta configurar CORS.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(CustomUserDetailsService userDetailsService,
                           JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.userDetailsService = userDetailsService;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        // Pantallas y recursos del frontend. El navegador los pide
                        // sin encabezado Authorization, asi que deben ser publicos.
                        // No contienen datos de nadie: los expedientes viajan por la
                        // API, que si exige token y rol.
                        //
                        // "/*.html" en vez de nombrar cada pagina: antes cada pantalla
                        // nueva daba 401 hasta que alguien se acordaba de agregarla aqui
                        // (paso en los Sprints 3 y 5). "/img/**" es para la decoracion
                        // del Sprint 6. "/error" evita que un recurso inexistente, como
                        // favicon.ico, se convierta en 401 en lugar de 404.
                        .requestMatchers("/", "/*.html", "/css/**", "/js/**", "/img/**",
                                         "/favicon.ico", "/error").permitAll()

                        // La consola H2 queda abierta solo para desarrollo: se cierra en
                        // el Sprint 11 junto con el paso de los secretos a variables de entorno.
                        .requestMatchers("/api/auth/**", "/h2-console/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMINISTRADOR")
                        .requestMatchers("/api/tecnico/**").hasAnyRole("ADMINISTRADOR", "TECNICO")
                        .requestMatchers("/api/juez/**").hasRole("JUEZ")
                        .anyRequest().authenticated()
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Sin token -> 401 (no te identificaste).
                // Con token pero sin el rol -> 403 (lo maneja Spring aparte).
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                // Solo necesario para poder ver la consola H2 en un iframe durante el desarrollo.
                .headers(headers -> headers.frameOptions(frame -> frame.disable()));

        return http.build();
    }
}
