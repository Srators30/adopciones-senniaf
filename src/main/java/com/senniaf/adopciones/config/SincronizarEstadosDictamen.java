package com.senniaf.adopciones.config;

import com.senniaf.adopciones.service.DictamenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Ajuste de datos al arrancar (Sprint 7).
 *
 * Desde el Sprint 7 el dictamen mueve el estado de la solicitud a Idonea
 * o No idonea. Las solicitudes dictaminadas antes de ese cambio quedaron
 * En evaluacion; esta clase las pone en el estado de su dictamen vigente
 * y lo deja escrito en su historial.
 *
 * Corre despues de EsquemaEventos (@Order 10), que primero prepara la
 * columna de estado para aceptar los dos valores nuevos. Cuando ya no
 * queda ninguna por ajustar, no hace nada.
 */
@Component
@Order(20)
public class SincronizarEstadosDictamen implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SincronizarEstadosDictamen.class);

    private final DictamenService dictamenes;

    public SincronizarEstadosDictamen(DictamenService dictamenes) {
        this.dictamenes = dictamenes;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int ajustadas = dictamenes.sincronizarEstados();
            if (ajustadas > 0) {
                log.info("Estados: {} solicitud(es) dictaminada(s) pasaron de En evaluacion al estado de su dictamen",
                        ajustadas);
            }
        } catch (Exception ex) {
            log.warn("Estados: no se pudieron ajustar las solicitudes dictaminadas ({}). "
                    + "Si persiste, borra la carpeta data y vuelve a arrancar.", ex.getMessage());
        }
    }
}
