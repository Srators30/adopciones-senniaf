package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.EventoExpediente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoExpedienteRepository extends JpaRepository<EventoExpediente, Long> {

    /** Historial de un expediente, del evento mas reciente al mas antiguo. */
    List<EventoExpediente> findByExpedienteIdOrderByFechaHoraDescIdDesc(Long expedienteId);
}
