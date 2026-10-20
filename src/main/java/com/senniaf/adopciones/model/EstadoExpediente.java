package com.senniaf.adopciones.model;

/**
 * Estado administrativo del expediente dentro del sistema.
 *
 * No confundir con el estado JURIDICO del menor (adoptabilidad), que es
 * trabajo de HU-07 y se modelara aparte: ese lo decide un juez, este
 * solo refleja en que punto del tramite interno esta la carpeta.
 */
public enum EstadoExpediente {
    REGISTRADO,     // recien creado
    EN_REVISION,    // alguien lo esta trabajando
    CERRADO         // ya no admite cambios
}
