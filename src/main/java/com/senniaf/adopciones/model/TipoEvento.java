package com.senniaf.adopciones.model;

/**
 * Tipo de operacion que queda registrada en el historial del expediente
 * o de la solicitud de una familia.
 *
 * Al agregar un valor nuevo, las bases de datos ya creadas tienen una
 * restriccion CHECK que solo admite los valores anteriores. La limpia
 * config/EsquemaEventos al arrancar; ver la explicacion alli.
 */
public enum TipoEvento {
    CREACION,
    MODIFICACION,
    CONSULTA,
    CAMBIO_ESTADO,
    DOCUMENTO,     // HU-04: adjuntar o descargar un documento
    DICTAMEN       // HU-06.1: dictamen de idoneidad de una familia
}
