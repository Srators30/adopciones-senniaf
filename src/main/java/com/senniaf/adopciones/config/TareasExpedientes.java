package com.senniaf.adopciones.config;

import com.senniaf.adopciones.service.CierreAutomaticoService;
import com.senniaf.adopciones.service.EstadoJuridicoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Tareas de los expedientes (Sprint 8).
 *
 * Al arrancar:
 *   1. Pone En proteccion a los expedientes que no tienen etapa juridica
 *      (los creados antes del Sprint 8; HU-07.1, criterio 2).
 *   2. Cierra los expedientes vencidos (ver CierreAutomaticoService).
 *
 * Y todos los dias a las 2:00 vuelve a revisar los vencidos, para cuando
 * la aplicacion queda corriendo en un servidor.
 *
 * Corre despues de EsquemaEventos (@Order 10), que prepara las columnas
 * para los valores nuevos. Si algo falla solo se registra en el log.
 */
@Configuration
@EnableScheduling
@Order(15)
public class TareasExpedientes implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TareasExpedientes.class);

    private final EstadoJuridicoService etapas;
    private final CierreAutomaticoService cierres;

    public TareasExpedientes(EstadoJuridicoService etapas, CierreAutomaticoService cierres) {
        this.etapas = etapas;
        this.cierres = cierres;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            int ajustados = etapas.asignarEtapaInicial();
            if (ajustados > 0) {
                log.info("Etapa juridica: {} expediente(s) existentes quedaron En proteccion", ajustados);
            }
        } catch (Exception ex) {
            log.warn("Etapa juridica: no se pudo asignar la etapa inicial ({}). "
                    + "Si persiste, borra la carpeta data y vuelve a arrancar.", ex.getMessage());
        }
        revisarCierres();
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void revisarCierres() {
        try {
            int cerrados = cierres.cerrarVencidos();
            if (cerrados > 0) {
                log.info("Cierre automatico: {} expediente(s) cerrados tras {} dias sin actividad en etapa final",
                        cerrados, cierres.getPlazoDias());
            }
        } catch (Exception ex) {
            log.warn("Cierre automatico: no se pudo revisar ({})", ex.getMessage());
        }
    }
}
