package com.senniaf.adopciones.repository;

import com.senniaf.adopciones.model.DictamenIdoneidad;
import com.senniaf.adopciones.model.EstadoFamilia;
import com.senniaf.adopciones.model.ResultadoIdoneidad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DictamenIdoneidadRepository extends JpaRepository<DictamenIdoneidad, Long> {

    Optional<DictamenIdoneidad> findFirstByFamiliaIdAndVigenteTrue(Long familiaId);

    boolean existsByFamiliaIdAndVigenteTrue(Long familiaId);

    /** Todos los dictamenes vigentes (ajuste de estados al arrancar, Sprint 7). */
    List<DictamenIdoneidad> findByVigenteTrue();

    /** HU-06.4, criterio 5: todos los dictamenes de la familia, del mas reciente al mas antiguo. */
    List<DictamenIdoneidad> findByFamiliaIdOrderByFechaDictamenDescIdDesc(Long familiaId);

    /**
     * HU-06.3, criterios 1 y 2: dictamenes vigentes con el resultado
     * pedido, ordenados por fecha de idoneidad y, a igual fecha, por
     * numero de solicitud. Las solicitudes cerradas no esperan turno.
     *
     * JOIN FETCH trae la familia en la misma consulta: sin el, armar la
     * lista haria una consulta extra por cada fila.
     */
    @Query("SELECT d FROM DictamenIdoneidad d JOIN FETCH d.familia f "
            + "WHERE d.vigente = true AND d.resultado = :resultado AND f.estado <> :excluido "
            + "ORDER BY d.fechaIdoneidad ASC, f.numeroSolicitud ASC")
    List<DictamenIdoneidad> listaDeEspera(@Param("resultado") ResultadoIdoneidad resultado,
                                          @Param("excluido") EstadoFamilia excluido);
}
