package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.EventoExpediente;
import com.senniaf.adopciones.model.TipoEvento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventoExpedienteRepository extends JpaRepository<EventoExpediente, Long> {

    /** Historial de un expediente, del evento mas reciente al mas antiguo. */
    List<EventoExpediente> findByExpedienteIdOrderByFechaHoraDescIdDesc(Long expedienteId);

    /**
     * Sprint 8: ultima vez que alguien trabajo el expediente. Cuenta todo
     * evento menos las consultas: abrir la ficha para mirarla no es trabajar.
     */
    @Query("SELECT MAX(ev.fechaHora) FROM EventoExpediente ev "
            + "WHERE ev.expediente.id = :id AND ev.tipo <> :excluido")
    LocalDateTime ultimaActividad(@Param("id") Long expedienteId, @Param("excluido") TipoEvento excluido);
}
