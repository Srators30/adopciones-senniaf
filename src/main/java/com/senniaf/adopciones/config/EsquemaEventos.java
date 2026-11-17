package com.senniaf.adopciones.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Ajuste de esquema al arrancar (Sprint 6, corregido despues del Sprint 7).
 *
 * EL PROBLEMA
 * Hibernate guarda los enum de Java como texto (@Enumerated(STRING)), pero
 * en la base H2 no siempre crea la columna como texto libre. Segun la
 * version de Hibernate hace una de estas dos cosas:
 *
 *   a) VARCHAR con una restriccion CHECK (tipo IN ('CREACION', ...)).
 *   b) Una columna de tipo ENUM ('CREACION', ...) propia de H2.
 *      Hibernate 6.5, el que trae Spring Boot 3.3, hace esta.
 *
 * En los dos casos la lista de valores queda fija el dia en que se creo la
 * tabla, y con ddl-auto=update Hibernate NO la vuelve a escribir cuando el
 * enum crece. El Sprint 6 agrego DICTAMEN a TipoEvento: en una base creada
 * antes, guardar el evento del dictamen falla y la API responde 500.
 *
 * La primera version de esta clase solo quitaba la restriccion CHECK (caso
 * a). En H2 con Hibernate 6.5 la columna es ENUM (caso b), asi que no
 * encontraba nada que quitar y el error seguia.
 *
 * LA SOLUCION
 * Al arrancar:
 *   1. Quita las restricciones CHECK de la columna tipo de los eventos.
 *   2. Convierte a VARCHAR(40) toda columna de tipo ENUM de la base. Los
 *      valores guardados se conservan: H2 convierte cada uno a su nombre.
 *
 * El valor lo sigue controlando el enum de Java: el codigo no puede
 * escribir un tipo que no exista. Y los sprints que agreguen valores a
 * otros enum (por ejemplo, el estado juridico del Sprint 8) ya no van a
 * tropezar con lo mismo.
 *
 * Si algo de esto falla, solo se registra en el log y la aplicacion arranca
 * igual. En ese caso, borrar la carpeta data resuelve el problema a costa
 * de perder los datos de prueba.
 */
@Component
@Order(10)
public class EsquemaEventos implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EsquemaEventos.class);
    private static final List<String> TABLAS_EVENTOS = List.of("EVENTOS_FAMILIA", "EVENTOS_EXPEDIENTE");

    private final JdbcTemplate jdbc;

    public EsquemaEventos(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        quitarRestriccionesCheck();
        convertirColumnasEnum();
    }

    /** Caso a): VARCHAR con CHECK (tipo IN (...)). */
    private void quitarRestriccionesCheck() {
        for (String tabla : TABLAS_EVENTOS) {
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
                    log.info("Esquema: quitada la restriccion {} de {}.tipo", nombre, tabla);
                }
            } catch (Exception ex) {
                avisar(tabla + " (restricciones)", ex);
            }
        }
    }

    /** Caso b): columnas de tipo ENUM propio de H2. */
    private void convertirColumnasEnum() {
        Set<String> columnas = new LinkedHashSet<>();
        try {
            List<Map<String, Object>> filas = jdbc.queryForList("""
                    SELECT TABLE_NAME, COLUMN_NAME
                    FROM INFORMATION_SCHEMA.COLUMNS
                    WHERE TABLE_SCHEMA = 'PUBLIC'
                      AND UPPER(DATA_TYPE) = 'ENUM'
                    """);
            for (Map<String, Object> f : filas) {
                columnas.add(f.get("TABLE_NAME") + "." + f.get("COLUMN_NAME"));
            }
        } catch (Exception ex) {
            avisar("INFORMATION_SCHEMA.COLUMNS", ex);
        }

        for (String columna : columnas) {
            String[] partes = columna.split("\\.", 2);
            String tabla = partes[0];
            String nombre = partes[1];
            try {
                // Nombres sacados del catalogo de la base, no de un usuario.
                jdbc.execute("ALTER TABLE \"" + tabla + "\" ALTER COLUMN \"" + nombre
                        + "\" SET DATA TYPE VARCHAR(40)");
                log.info("Esquema: {} pasa de ENUM a VARCHAR(40) para admitir valores nuevos", columna);
            } catch (Exception ex) {
                avisar(columna, ex);
            }
        }
    }

    private void avisar(String que, Exception ex) {
        log.warn("Esquema: no se pudo ajustar {} ({}). Si guardar un dictamen o un evento da error 500, "
                + "detén la aplicación, borra la carpeta data y vuelve a arrancar.", que, ex.getMessage());
    }
}
