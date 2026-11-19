package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.EstadoExpediente;
import com.senniaf.adopciones.model.EstadoJuridico;
import com.senniaf.adopciones.model.Expediente;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ExpedienteRepository extends JpaRepository<Expediente, Long> {

    Optional<Expediente> findByNumeroExpediente(String numeroExpediente);

    /**
     * Busqueda del listado (HU-03). Los tres filtros son opcionales: si
     * llegan nulos, esa condicion no filtra nada. Se compara en
     * minusculas para que buscar "maria" encuentre "María".
     */
    @Query("""
           SELECT e FROM Expediente e
           WHERE (:texto IS NULL
                  OR LOWER(e.nombres) LIKE :texto
                  OR LOWER(e.apellidos) LIKE :texto
                  OR LOWER(e.numeroExpediente) LIKE :texto
                  OR LOWER(e.numeroDocumento) LIKE :texto)
             AND (:estado IS NULL OR e.estado = :estado)
             AND (:modalidad IS NULL OR e.modalidadAcogida = :modalidad)
             AND (:etapa IS NULL OR e.estadoJuridico = :etapa)
           """)
    Page<Expediente> buscar(@Param("texto") String texto,
                            @Param("estado") EstadoExpediente estado,
                            @Param("modalidad") String modalidad,
                            @Param("etapa") EstadoJuridico etapa,
                            Pageable pageable);

    // ------------------------------- Sprint 8 -------------------------------

    /** Lee el expediente bloqueando la fila: dos cambios a la vez no se pisan. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Expediente e WHERE e.numeroExpediente = :numero")
    Optional<Expediente> buscarParaCambio(@Param("numero") String numero);

    /** HU-07.1, criterio 2: los expedientes anteriores al Sprint 8 arrancan en En proteccion. */
    @Modifying
    @Query("UPDATE Expediente e SET e.estadoJuridico = :etapa, e.fechaEstadoJuridico = e.fechaCreacion "
            + "WHERE e.estadoJuridico IS NULL")
    int asignarEtapaInicial(@Param("etapa") EstadoJuridico etapa);

    /** Candidatos al cierre automatico: etapa final y todavia abiertos. */
    List<Expediente> findByEstadoJuridicoInAndEstadoNot(Collection<EstadoJuridico> etapas, EstadoExpediente estado);

    /** Los que cerro el sistema y siguen cerrados, del mas reciente al mas antiguo. */
    List<Expediente> findByFechaCierreAutomaticoIsNotNullAndEstadoOrderByFechaCierreAutomaticoDesc(EstadoExpediente estado);
}
