package com.senniaf.adopciones.model;

/** Tipo de operacion que queda registrada en el historial del expediente. */
public enum TipoEvento {
    CREACION,
    MODIFICACION,
    CONSULTA,
    CAMBIO_ESTADO,
    DOCUMENTO      // lo usa HU-04 al adjuntar o descargar un documento
}
