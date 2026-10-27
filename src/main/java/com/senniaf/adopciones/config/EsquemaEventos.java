package com.senniaf.adopciones.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Ajuste de esquema al arrancar (Sprint 6).
 *
 * EL PROBLEMA
 * Hibernate 6 crea una restriccion CHECK en cada columna que guarda un
 * enum como texto. En las tablas de eventos queda algo asi:
 *
 *     tipo VARCHAR(20) CHECK (tipo IN ('CREACION','MODIFICACION', ...))
 *
 * Con ddl-auto=update, Hibernate agrega tablas y columnas nuevas, pero NO
 * vuelve a escribir esa restriccion cuando el enum crece. Este sprint
 * agrega DICTAMEN a TipoEvento: en una base creada antes, el primer
 * dictamen fallaria con "Check constraint violation" y un error 500.
 *
 * LA SOLUCION
 * Quitar la restriccion de la columna tipo en las dos tablas de eventos.
 * El valor lo sigue controlando el enum de Java: no hay forma de que el
 * codigo escriba un tipo que no exista. Y los Sprints 8 y 11, que agregan
 * mas tipos de evento, ya no van a tropezar con lo mismo.
 *
 * Si algo de esto falla, solo se registra en el log y la aplicacion
 * arranca igual. En ese caso, borrar la carpeta data resuelve el
 * problema a costa de perder los datos de prueba.
 */
@Component
@Order(10)
public class EsquemaEventos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EsquemaEventos.class);
    private static final List<String> TABLAS = List.of("EVENTOS_FAMILIA", "EVENTOS_EXPEDIENTE");

    private final JdbcTemplate jdbc;

    public EsquemaEventos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String tabla : TABLAS) {
            try {
                List<String> restricciones = jdbc.queryForList("""
                        SELECT tc.CONSTRAINT_NAME
                        FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS tc
                        JOIN INFORMATION_SCHEMA.CHECK_CONSTRAINTS cc
                          ON cc.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
                         AND cc.CONSTRAINT_NAME = tc.CONSTRAINT_NAME
                        WHERE tc.TABLE_NAME = ?
                          AND tc.CONSTRAINT_TYPE = 'CHECK'
                          AND UPPER(cc.CHECK_CLAUSE) LIKE '%TIPO%'
                        """, String.class, tabla);

                for (String nombre : restricciones) {
                    // El nombre sale del propio catalogo de la base, no de un usuario.
                    jdbc.execute("ALTER TABLE " + tabla + " DROP CONSTRAINT \"" + nombre + "\"");
                    log.info("Esquema: quitada la restriccion {} de {}.tipo para admitir los tipos de evento nuevos",
                            nombre, tabla);
                }
            } catch (Exception ex) {
                log.warn("Esquema: no se pudo revisar {} ({}). Si un dictamen da error 500 por "
                        + "'Check constraint', borra la carpeta data y vuelve a arrancar.", tabla, ex.getMessage());
            }
        }
    }
}
