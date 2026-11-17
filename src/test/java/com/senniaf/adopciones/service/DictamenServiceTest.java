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

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias de HU-06.1 (emitir el dictamen), HU-06.2
 * (fundamentarlo), HU-06.3 (lista de espera) y HU-06.4 (cambios). Cada prueba corresponde a un criterio de aceptacion
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
    private Usuario admin;

    @BeforeEach
    void preparar() {
        familia = new FamiliaSolicitante();
        familia.setNumeroSolicitud(NUMERO);
        familia.setEstado(EstadoFamilia.EN_EVALUACION);
        tecnico = new Usuario("tecnico1", "$2a$10$hashSimuladoDePrueba", Rol.TECNICO);
        admin = new Usuario("admin", "$2a$10$hashSimuladoDePrueba", Rol.ADMINISTRADOR);
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

    // ------------------------------------------------------------- HU-06.4

    private static final String JUSTIFICACION =
            "Se recibio el informe psicologico actualizado de la familia.";

    private DictamenIdoneidad vigenteNoIdonea() {
        DictamenIdoneidad d = new DictamenIdoneidad(familia, ResultadoIdoneidad.NO_IDONEA,
                OBSERVACION_LARGA, "tecnico1", "TECNICO");
        when(dictamenes.findFirstByFamiliaIdAndVigenteTrue(any())).thenReturn(Optional.of(d));
        return d;
    }

    private Map<String, Object> cambio(String resultado, String justificacion) {
        Map<String, Object> m = new HashMap<>();
        m.put("resultado", resultado);
        m.put("observacion", "La familia cumple los requisitos tras la nueva evaluacion.");
        if (justificacion != null) m.put("justificacion", justificacion);
        return m;
    }

    @Test
    @DisplayName("HU-06.4 c1: cambiar un dictamen crea uno nuevo y el anterior queda sustituido")
    void cambioCreaNuevoYConservaElAnterior() {
        familiaEncontrada();
        DictamenIdoneidad anterior = vigenteNoIdonea();
        guardadoDevuelveLoMismo();

        DictamenIdoneidad nuevo = servicio.cambiar(NUMERO, cambio("IDONEA", JUSTIFICACION), admin);

        assertFalse(anterior.isVigente());
        assertNotNull(anterior.getFechaSustitucion());
        assertEquals("admin", anterior.getSustituidoPor());
        assertEquals(ResultadoIdoneidad.NO_IDONEA, anterior.getResultado());
        assertTrue(nuevo.isVigente());
        assertEquals(ResultadoIdoneidad.IDONEA, nuevo.getResultado());
        assertEquals(JUSTIFICACION, nuevo.getJustificacionCambio());
    }

    @Test
    @DisplayName("Decision del PO: al cambiar a IDONEA, la fecha de idoneidad es la del cambio")
    void cambioAIdoneaUsaLaFechaDelCambio() {
        familiaEncontrada();
        vigenteNoIdonea();
        guardadoDevuelveLoMismo();

        DictamenIdoneidad nuevo = servicio.cambiar(NUMERO, cambio("IDONEA", JUSTIFICACION), admin);

        assertEquals(LocalDate.now(), nuevo.getFechaIdoneidad());
    }

    @Test
    @DisplayName("HU-06.4 c3: el cambio queda en el historial con el periodo del dictamen anterior")
    void cambioQuedaEnElHistorial() {
        familiaEncontrada();
        vigenteNoIdonea();
        guardadoDevuelveLoMismo();

        servicio.cambiar(NUMERO, cambio("IDONEA", JUSTIFICACION), admin);

        verify(familiaService).registrar(eq(familia), eq(TipoEvento.DICTAMEN),
                contains("Fue No idónea del"), eq(admin));
    }

    @Test
    @DisplayName("HU-06.4 c2: un cambio sin justificacion se rechaza y no toca nada")
    void cambioSinJustificacionSeRechaza() {
        familiaEncontrada();
        DictamenIdoneidad anterior = vigenteNoIdonea();

        DictamenRechazado ex = assertThrows(DictamenRechazado.class,
                () -> servicio.cambiar(NUMERO, cambio("IDONEA", null), admin));

        assertEquals(Motivo.DATOS_INVALIDOS, ex.getMotivo());
        assertTrue(ex.getCampos().contains("justificacion"));
        assertTrue(anterior.isVigente());
        verify(dictamenes, never()).save(any());
    }

    @Test
    @DisplayName("HU-06.4 c2: una justificacion de menos de 20 caracteres se rechaza")
    void justificacionCortaSeRechaza() {
        familiaEncontrada();
        vigenteNoIdonea();

        DictamenRechazado ex = assertThrows(DictamenRechazado.class,
                () -> servicio.cambiar(NUMERO, cambio("IDONEA", "Error."), admin));

        assertTrue(ex.getCampos().contains("justificacion"));
    }

    @Test
    @DisplayName("Un cambio al mismo resultado se rechaza")
    void cambioAlMismoResultadoSeRechaza() {
        familiaEncontrada();
        vigenteNoIdonea();

        DictamenRechazado ex = assertThrows(DictamenRechazado.class,
                () -> servicio.cambiar(NUMERO, cambio("NO_IDONEA", JUSTIFICACION), admin));

        assertEquals(Motivo.DATOS_INVALIDOS, ex.getMotivo());
        assertTrue(ex.getCampos().contains("resultado"));
    }

    @Test
    @DisplayName("Decision del PO: una solicitud cerrada no admite cambios de dictamen")
    void solicitudCerradaNoAdmiteCambio() {
        familia.setEstado(EstadoFamilia.CERRADA);
        familiaEncontrada();

        DictamenRechazado ex = assertThrows(DictamenRechazado.class,
                () -> servicio.cambiar(NUMERO, cambio("IDONEA", JUSTIFICACION), admin));

        assertEquals(Motivo.ESTADO_NO_PERMITIDO, ex.getMotivo());
        verify(dictamenes, never()).save(any());
    }

    @Test
    @DisplayName("No se puede cambiar un dictamen que todavia no existe")
    void sinDictamenNoHayCambio() {
        familiaEncontrada();
        when(dictamenes.findFirstByFamiliaIdAndVigenteTrue(any())).thenReturn(Optional.empty());

        DictamenRechazado ex = assertThrows(DictamenRechazado.class,
                () -> servicio.cambiar(NUMERO, cambio("IDONEA", JUSTIFICACION), admin));

        assertEquals(Motivo.SIN_DICTAMEN, ex.getMotivo());
    }

    // ------------------------------------------------------------- HU-06.3

    private FamiliaSolicitante familiaConId(long id, String numero) {
        FamiliaSolicitante f = new FamiliaSolicitante();
        ReflectionTestUtils.setField(f, "id", id);
        f.setNumeroSolicitud(numero);
        f.setNombres("Familia");
        f.setApellidos(numero);
        f.setEstado(EstadoFamilia.EN_EVALUACION);
        return f;
    }

    @Test
    @DisplayName("HU-06.3 c2 y c3: la lista numera en el orden de la consulta y calcula los dias de espera")
    void listaNumeraYCalculaDias() {
        FamiliaSolicitante a = familiaConId(1L, "FAM-2026-0001");
        FamiliaSolicitante b = familiaConId(2L, "FAM-2026-0002");
        when(dictamenes.listaDeEspera(ResultadoIdoneidad.IDONEA, EstadoFamilia.CERRADA)).thenReturn(List.of(
                new DictamenIdoneidad(a, ResultadoIdoneidad.IDONEA, "Cumple.", "tecnico1", "TECNICO"),
                new DictamenIdoneidad(b, ResultadoIdoneidad.IDONEA, "Cumple.", "tecnico1", "TECNICO")));

        List<Map<String, Object>> lista = servicio.listaDeEspera();

        assertEquals(2, lista.size());
        assertEquals(1, lista.get(0).get("posicion"));
        assertEquals("FAM-2026-0001", lista.get(0).get("numeroSolicitud"));
        assertEquals(2, lista.get(1).get("posicion"));
        assertEquals(0L, lista.get(0).get("diasEnEspera"));
    }

    @Test
    @DisplayName("HU-06.3 c5: la ficha conoce su posicion, y una familia fuera de la lista no tiene")
    void posicionEnLaLista() {
        FamiliaSolicitante a = familiaConId(1L, "FAM-2026-0001");
        FamiliaSolicitante b = familiaConId(2L, "FAM-2026-0002");
        FamiliaSolicitante fuera = familiaConId(3L, "FAM-2026-0003");
        when(dictamenes.listaDeEspera(ResultadoIdoneidad.IDONEA, EstadoFamilia.CERRADA)).thenReturn(List.of(
                new DictamenIdoneidad(a, ResultadoIdoneidad.IDONEA, "Cumple.", "tecnico1", "TECNICO"),
                new DictamenIdoneidad(b, ResultadoIdoneidad.IDONEA, "Cumple.", "tecnico1", "TECNICO")));

        assertEquals(Optional.of(2), servicio.posicion(b));
        assertTrue(servicio.posicion(fuera).isEmpty());
    }

    // --------------------------------- Sprint 7: el estado sigue al dictamen

    @Test
    @DisplayName("Decision del PO: emitir IDONEA pone la solicitud en estado Idonea")
    void emitirIdoneaMueveElEstado() {
        familiaEncontrada();
        guardadoDevuelveLoMismo();

        servicio.emitir(NUMERO, Map.of("resultado", "IDONEA", "observacion", "Cumple."), tecnico);

        assertEquals(EstadoFamilia.IDONEA, familia.getEstado());
    }

    @Test
    @DisplayName("Decision del PO: emitir NO_IDONEA pone la solicitud en estado No idonea")
    void emitirNoIdoneaMueveElEstado() {
        familiaEncontrada();
        guardadoDevuelveLoMismo();

        servicio.emitir(NUMERO, Map.of("resultado", "NO_IDONEA", "observacion", OBSERVACION_LARGA), tecnico);

        assertEquals(EstadoFamilia.NO_IDONEA, familia.getEstado());
    }

    @Test
    @DisplayName("Decision del PO: cambiar el dictamen cambia tambien el estado")
    void cambioMueveElEstado() {
        familia.setEstado(EstadoFamilia.NO_IDONEA);
        familiaEncontrada();
        vigenteNoIdonea();
        guardadoDevuelveLoMismo();

        servicio.cambiar(NUMERO, cambio("IDONEA", JUSTIFICACION), admin);

        assertEquals(EstadoFamilia.IDONEA, familia.getEstado());
    }

    // ------------------------------------- Sprint 7: reabrir una solicitud

    private static final String MOTIVO = "La familia presento una apelacion con documentos nuevos.";

    @Test
    @DisplayName("Reabrir una solicitud cerrada la devuelve al estado de su dictamen vigente")
    void reabrirVuelveAlEstadoDelDictamen() {
        familia.setEstado(EstadoFamilia.CERRADA);
        familiaEncontrada();
        vigenteNoIdonea();

        servicio.reabrir(NUMERO, Map.of("motivo", MOTIVO), admin);

        assertEquals(EstadoFamilia.NO_IDONEA, familia.getEstado());
        verify(familiaService).registrar(eq(familia), eq(TipoEvento.CAMBIO_ESTADO),
                contains("Solicitud reabierta"), eq(admin));
    }

    @Test
    @DisplayName("Reabrir una solicitud que nunca tuvo dictamen la deja En evaluacion")
    void reabrirSinDictamenVuelveAEvaluacion() {
        familia.setEstado(EstadoFamilia.CERRADA);
        familiaEncontrada();
        when(dictamenes.findFirstByFamiliaIdAndVigenteTrue(any())).thenReturn(Optional.empty());

        servicio.reabrir(NUMERO, Map.of("motivo", MOTIVO), admin);

        assertEquals(EstadoFamilia.EN_EVALUACION, familia.getEstado());
    }

    @Test
    @DisplayName("Reabrir exige motivo, y solo se reabre lo que esta cerrado")
    void reabrirExigeMotivoYSolicitudCerrada() {
        familia.setEstado(EstadoFamilia.CERRADA);
        familiaEncontrada();
        DictamenRechazado sinMotivo = assertThrows(DictamenRechazado.class,
                () -> servicio.reabrir(NUMERO, Map.of("motivo", "Porque si."), admin));
        assertEquals(Motivo.DATOS_INVALIDOS, sinMotivo.getMotivo());
        assertEquals(EstadoFamilia.CERRADA, familia.getEstado());

        familia.setEstado(EstadoFamilia.IDONEA);
        DictamenRechazado abierta = assertThrows(DictamenRechazado.class,
                () -> servicio.reabrir(NUMERO, Map.of("motivo", MOTIVO), admin));
        assertEquals(Motivo.ESTADO_NO_PERMITIDO, abierta.getMotivo());
    }
}
