package com.senniaf.adopciones.service;

import com.senniaf.adopciones.model.DictamenIdoneidad;
import com.senniaf.adopciones.model.EstadoFamilia;
import com.senniaf.adopciones.model.FamiliaSolicitante;
import com.senniaf.adopciones.model.ResultadoIdoneidad;
import com.senniaf.adopciones.model.Rol;
import com.senniaf.adopciones.model.TipoEvento;
import com.senniaf.adopciones.model.Usuario;
import com.senniaf.adopciones.repository.DictamenIdoneidadRepository;
import com.senniaf.adopciones.repository.FamiliaRepository;
import com.senniaf.adopciones.service.DictamenService.DictamenRechazado;
import com.senniaf.adopciones.service.DictamenService.Motivo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de HU-06.1 (emitir el dictamen) y HU-06.2
 * (fundamentarlo). Cada prueba corresponde a un criterio de aceptacion
 * del documento de planificacion.
 *
 * No levantan Spring ni la base de datos: los repositorios son
 * simulados, asi que corren en milisegundos y prueban solo las reglas.
 */
@ExtendWith(MockitoExtension.class)
class DictamenServiceTest {

    private static final String NUMERO = "FAM-2026-0001";
    private static final String OBSERVACION_LARGA =
            "La vivienda no cuenta con espacio para un menor y no hay red de apoyo.";

    @Mock private DictamenIdoneidadRepository dictamenes;
    @Mock private FamiliaRepository familias;
    @Mock private FamiliaService familiaService;

    @InjectMocks private DictamenService servicio;

    private FamiliaSolicitante familia;
    private Usuario tecnico;

    @BeforeEach
    void preparar() {
        familia = new FamiliaSolicitante();
        familia.setNumeroSolicitud(NUMERO);
        familia.setEstado(EstadoFamilia.EN_EVALUACION);
        tecnico = new Usuario("tecnico1", "$2a$10$hashSimuladoDePrueba", Rol.TECNICO);
    }

    private void familiaEncontrada() {
        when(familias.buscarParaDictamen(NUMERO)).thenReturn(Optional.of(familia));
    }

    private void guardadoDevuelveLoMismo() {
        when(dictamenes.save(any(DictamenIdoneidad.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    // ------------------------------------------------------------- HU-06.1

    @Test
    @DisplayName("HU-06.1 c3: un dictamen IDONEA registra la fecha de idoneidad de hoy")
    void idoneaRegistraFechaDeIdoneidad() {
        familiaEncontrada();
        guardadoDevuelveLoMismo();

        DictamenIdoneidad d = servicio.emitir(NUMERO,
                Map.of("resultado", "IDONEA", "observacion", "Cumple los requisitos."), tecnico);

        assertEquals(ResultadoIdoneidad.IDONEA, d.getResultado());
        assertEquals(LocalDate.now(), d.getFechaIdoneidad());
        assertEquals("tecnico1", d.getEmitidoPor());
        assertEquals("TECNICO", d.getRolEmisor());
        assertTrue(d.isVigente());
    }

    @Test
    @DisplayName("HU-06.1 c3: un dictamen NO_IDONEA no tiene fecha de idoneidad")
    void noIdoneaNoTieneFechaDeIdoneidad() {
        familiaEncontrada();
        guardadoDevuelveLoMismo();

        DictamenIdoneidad d = servicio.emitir(NUMERO,
                Map.of("resultado", "NO_IDONEA", "observacion", OBSERVACION_LARGA), tecnico);

        assertNull(d.getFechaIdoneidad());
    }

    @Test
    @DisplayName("HU-06.1: el dictamen queda en el historial de la solicitud")
    void dictamenQuedaEnElHistorial() {
        familiaEncontrada();
        guardadoDevuelveLoMismo();

        servicio.emitir(NUMERO, Map.of("resultado", "IDONEA", "observacion", "Cumple."), tecnico);

        verify(familiaService).registrar(eq(familia), eq(TipoEvento.DICTAMEN), anyString(), eq(tecnico));
    }

    @Test
    @DisplayName("HU-06.1 c1: una solicitud que no esta En evaluacion no admite dictamen")
    void soloEnEvaluacionAdmiteDictamen() {
        familia.setEstado(EstadoFamilia.REGISTRADA);
        familiaEncontrada();

        DictamenRechazado ex = assertThrows(DictamenRechazado.class, () -> servicio.emitir(NUMERO,
                Map.of("resultado", "IDONEA", "observacion", "Cumple."), tecnico));

        assertEquals(Motivo.ESTADO_NO_PERMITIDO, ex.getMotivo());
        verify(dictamenes, never()).save(any());
    }

    @Test
    @DisplayName("HU-06.1 c2: un resultado distinto de IDONEA o NO_IDONEA se rechaza")
    void resultadoInvalidoSeRechaza() {
        familiaEncontrada();

        DictamenRechazado ex = assertThrows(DictamenRechazado.class, () -> servicio.emitir(NUMERO,
                Map.of("resultado", "APTA", "observacion", "Cumple."), tecnico));

        assertEquals(Motivo.DATOS_INVALIDOS, ex.getMotivo());
        assertTrue(ex.getCampos().contains("resultado"));
    }

    @Test
    @DisplayName("Un dictamen vigente no se sobrescribe")
    void dictamenVigenteNoSeSobrescribe() {
        familiaEncontrada();
        when(dictamenes.existsByFamiliaIdAndVigenteTrue(any())).thenReturn(true);

        DictamenRechazado ex = assertThrows(DictamenRechazado.class, () -> servicio.emitir(NUMERO,
                Map.of("resultado", "IDONEA", "observacion", "Cumple."), tecnico));

        assertEquals(Motivo.YA_DICTAMINADA, ex.getMotivo());
        verify(dictamenes, never()).save(any());
    }

    @Test
    @DisplayName("Una solicitud inexistente se informa como no encontrada")
    void solicitudInexistente() {
        when(familias.buscarParaDictamen("FAM-2026-9999")).thenReturn(Optional.empty());

        DictamenRechazado ex = assertThrows(DictamenRechazado.class, () -> servicio.emitir("FAM-2026-9999",
                Map.of("resultado", "IDONEA", "observacion", "Cumple."), tecnico));

        assertEquals(Motivo.NO_ENCONTRADA, ex.getMotivo());
    }

    // ------------------------------------------------------------- HU-06.2

    @Test
    @DisplayName("HU-06.2 c1: todo dictamen exige una observacion")
    void observacionObligatoria() {
        familiaEncontrada();

        DictamenRechazado ex = assertThrows(DictamenRechazado.class, () -> servicio.emitir(NUMERO,
                Map.of("resultado", "IDONEA", "observacion", "   "), tecnico));

        assertEquals(Motivo.DATOS_INVALIDOS, ex.getMotivo());
        assertTrue(ex.getCampos().contains("observacion"));
    }

    @Test
    @DisplayName("HU-06.2 c2: NO_IDONEA exige al menos 30 caracteres de observacion")
    void noIdoneaExigeObservacionLarga() {
        familiaEncontrada();

        DictamenRechazado ex = assertThrows(DictamenRechazado.class, () -> servicio.emitir(NUMERO,
                Map.of("resultado", "NO_IDONEA", "observacion", "No cumple."), tecnico));

        assertEquals(Motivo.DATOS_INVALIDOS, ex.getMotivo());
        assertTrue(ex.getCampos().contains("observacion"));
    }

    @Test
    @DisplayName("HU-06.2 c2: con 30 caracteres exactos un NO_IDONEA se acepta")
    void noIdoneaConTreintaCaracteresSeAcepta() {
        familiaEncontrada();
        guardadoDevuelveLoMismo();
        String treinta = "a".repeat(DictamenService.MINIMO_OBSERVACION_NO_IDONEA);

        DictamenIdoneidad d = servicio.emitir(NUMERO,
                Map.of("resultado", "NO_IDONEA", "observacion", treinta), tecnico);

        assertEquals(ResultadoIdoneidad.NO_IDONEA, d.getResultado());
    }
}
