package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.EstadoExpediente;
import com.senniaf.adopciones.model.EstadoJuridico;
import com.senniaf.adopciones.model.Expediente;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.repository.EventoExpedienteRepository;
import com.senniaf.adopciones.repository.ExpedienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas del cierre automatico (decision del PO, Sprint 8): solo en
 * etapa final y tras 7 dias sin actividad; la actividad reinicia el plazo.
 */
@ExtendWith(MockitoExtension.class)
class CierreAutomaticoServiceTest {

    @Mock private ExpedienteRepository expedientes;
    @Mock private EventoExpedienteRepository eventos;
    @Mock private ExpedienteService expedienteService;

    private CierreAutomaticoService servicio;
    private Expediente expediente;

    @BeforeEach
    void preparar() {
        servicio = new CierreAutomaticoService(expedientes, eventos, expedienteService, 7);
        expediente = new Expediente();
        expediente.setNumeroExpediente("EXP-2026-0001");
        expediente.setNombres("Ana");
        expediente.setApellidos("Prueba");
        expediente.setEstado(EstadoExpediente.EN_REVISION);
        expediente.setEstadoJuridico(EstadoJuridico.ADOPTADO);
        expediente.setFechaEstadoJuridico(LocalDateTime.now().minusDays(20));
    }

    private void candidatos() {
        when(expedientes.findByEstadoJuridicoInAndEstadoNot(EstadoJuridico.finales(), EstadoExpediente.CERRADO))
                .thenReturn(List.of(expediente));
    }

    private void ultimaActividadHace(int dias) {
        when(eventos.ultimaActividad(any(), eq(TipoEvento.CONSULTA)))
                .thenReturn(LocalDateTime.now().minusDays(dias));
    }

    @Test
    @DisplayName("Con 8 dias sin actividad en etapa final, el sistema lo cierra y lo deja escrito")
    void cierraElVencido() {
        candidatos();
        ultimaActividadHace(8);

        int cerrados = servicio.cerrarVencidos();

        assertEquals(1, cerrados);
        assertEquals(EstadoExpediente.CERRADO, expediente.getEstado());
        assertNotNull(expediente.getFechaCierreAutomatico());
        verify(expedienteService).registrar(eq(expediente), eq(TipoEvento.CAMBIO_ESTADO),
                anyString(), eq(CierreAutomaticoService.USUARIO_SISTEMA), eq(CierreAutomaticoService.ROL_SISTEMA));
    }

    @Test
    @DisplayName("La actividad reinicia el plazo: llego a etapa final hace 20 dias pero lo trabajaron ayer")
    void actividadReiniciaElPlazo() {
        candidatos();
        ultimaActividadHace(1);

        assertEquals(0, servicio.cerrarVencidos());
        assertEquals(EstadoExpediente.EN_REVISION, expediente.getEstado());
        verify(expedienteService, never()).registrar(any(), any(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Sin eventos de trabajo, el plazo cuenta desde que llego a la etapa final")
    void sinEventosUsaLaFechaDeLaEtapa() {
        when(eventos.ultimaActividad(any(), eq(TipoEvento.CONSULTA))).thenReturn(null);

        assertEquals(expediente.getFechaEstadoJuridico(), servicio.ultimaActividad(expediente));
    }

    @Test
    @DisplayName("Antes de la etapa final no corre plazo: nunca se cierra solo")
    void antesDeLaEtapaFinalNoHayPlazo() {
        expediente.setEstadoJuridico(EstadoJuridico.DECLARADO_ADOPTABLE);

        assertNull(servicio.fechaLimite(expediente));
        assertFalse(servicio.estadoEn(expediente).containsKey("fechaLimite"));
    }

    @Test
    @DisplayName("La seccion muestra cuantos dias le quedan a cada uno")
    void resumenConDiasRestantes() {
        candidatos();
        ultimaActividadHace(3);
        when(expedientes.findByFechaCierreAutomaticoIsNotNullAndEstadoOrderByFechaCierreAutomaticoDesc(
                EstadoExpediente.CERRADO)).thenReturn(List.of());

        Map<String, Object> r = servicio.resumen();

        assertEquals(7, r.get("plazoDias"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> por = (List<Map<String, Object>>) r.get("porCerrarse");
        assertEquals(1, por.size());
        assertEquals(4L, por.get(0).get("diasRestantes"));
        assertTrue(((List<?>) r.get("cerrados")).isEmpty());
    }
}
