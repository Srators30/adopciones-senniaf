package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.EventoFamilia;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EventoFamiliaRepository extends JpaRepository<EventoFamilia, Long> {

    /** Lo mas reciente primero; el id desempata dentro del mismo segundo. */
    List<EventoFamilia> findByFamiliaIdOrderByFechaHoraDescIdDesc(Long familiaId);
}
