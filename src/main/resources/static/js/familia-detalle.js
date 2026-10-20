/* =====================================================================
   SENNIAF · familia-detalle.js  (HU-05)
   Ficha de la familia y su historial.

   Igual que en el expediente: todo el DOM se arma con textContent,
   nunca con innerHTML. Lo que se muestra lo escribió una persona en un
   formulario y no es HTML de confianza.
   ===================================================================== */
(() => {
  const sesion = Sesion.exigir();
  if (!sesion) return;
  Sesion.pintarBarra();

  const numero = new URLSearchParams(location.search).get('n');
  const banner = document.getElementById('banner-error');
  const textoBanner = document.getElementById('banner-error-text');
  const contenido = document.getElementById('contenido');

  function fallar(msg) {
    textoBanner.textContent = msg;
    banner.hidden = false;
  }

  if (!numero) {
    fallar('No se indicó qué solicitud abrir. Vuelve al listado y elige una.');
    return;
  }

  const ESTADO = { REGISTRADA: 'Registrada', EN_EVALUACION: 'En evaluación', CERRADA: 'Cerrada' };
  const MODALIDAD = { adopcion: 'Adopción', acogimiento_familiar: 'Acogimiento familiar' };
  const DOCUMENTO = { cedula: 'Cédula', pasaporte: 'Pasaporte', carne_residente: 'Carné de residente' };
  const CIVIL = {
    soltero: 'Soltero(a)', casado: 'Casado(a)', union_de_hecho: 'Unión de hecho',
    divorciado: 'Divorciado(a)', viudo: 'Viudo(a)'
  };
  const VIVIENDA = {
    propia: 'Propia', alquilada: 'Alquilada',
    familiar: 'Cedida por un familiar', otra: 'Otra'
  };
  const INGRESO = {
    hasta_800: 'Hasta B/. 800', '801_1500': 'B/. 801 a 1,500', '1501_2500': 'B/. 1,501 a 2,500',
    '2501_4000': 'B/. 2,501 a 4,000', mas_4000: 'Más de B/. 4,000'
  };
  const FUENTE = {
    empleo_formal: 'Empleo formal', independiente: 'Trabajo independiente',
    negocio_propio: 'Negocio propio', jubilacion: 'Jubilación o pensión', otra: 'Otra'
  };
  const SEXO = { indistinto: 'Indistinto', F: 'Femenino', M: 'Masculino' };
  const SI_NO = { si: 'Sí', no: 'No' };
  const TIPO_EVENTO = {
    CREACION: 'Creación', MODIFICACION: 'Modificación', CONSULTA: 'Consulta',
    CAMBIO_ESTADO: 'Estado', DOCUMENTO: 'Documento'
  };

  const et = (mapa, v) => mapa[v] || v || '';

  /** Pinta una lista de pares etiqueta/valor dentro de un <dl>. */
  function pintarDatos(dl, pares) {
    dl.replaceChildren();
    pares.forEach(([etiqueta, valor]) => {
      const div = document.createElement('div');
      const dt = document.createElement('dt');
      dt.textContent = etiqueta;
      const dd = document.createElement('dd');
      if (valor === '' || valor === null || valor === undefined) {
        dd.textContent = 'Sin registrar';
        dd.className = 'vacia';
      } else {
        dd.textContent = valor;
      }
      div.append(dt, dd);
      dl.appendChild(div);
    });
  }

  function pintarCabecera(f) {
    document.getElementById('titulo').textContent = f.nombreFamilia;
    document.getElementById('numero').textContent = f.numeroSolicitud;
    document.title = f.numeroSolicitud + ' | SENNIAF';

    const estado = document.getElementById('c-estado');
    estado.replaceChildren();
    const pill = document.createElement('span');
    pill.className = 'pill ' + String(f.estado || '').toLowerCase();
    pill.textContent = et(ESTADO, f.estado);
    estado.appendChild(pill);

    document.getElementById('c-modalidad').textContent = et(MODALIDAD, f.modalidadSolicitada) || '—';
    document.getElementById('c-rango').textContent = f.rangoEdad || '—';
    document.getElementById('c-creador').textContent =
      (f.creadoPor || '—') + (f.fechaCreacion ? ' · ' + f.fechaCreacion : '');
  }

  function pintarFicha(f) {
    pintarDatos(document.getElementById('d-principal'), [
      ['Nombres', f.nombres],
      ['Apellidos', f.apellidos],
      ['Edad', f.edad],
      ['Fecha de nacimiento', f.fechaNacimiento],
      ['Documento', et(DOCUMENTO, f.tipoDocumento)],
      ['N.º de documento', f.numeroDocumento],
      ['Ocupación', f.ocupacion],
      ['Teléfono', f.telefono],
      ['Correo', f.correo]
    ]);

    if (f.tieneSegundo === 'si') {
      document.getElementById('bloque-segundo').hidden = false;
      pintarDatos(document.getElementById('d-segundo'), [
        ['Nombres', f.segundoNombres],
        ['Apellidos', f.segundoApellidos],
        ['Edad', f.segundoEdad],
        ['Fecha de nacimiento', f.segundoFechaNacimiento],
        ['Documento', et(DOCUMENTO, f.segundoTipoDocumento)],
        ['N.º de documento', f.segundoNumeroDocumento],
        ['Ocupación', f.segundoOcupacion]
      ]);
    }

    pintarDatos(document.getElementById('d-hogar'), [
      ['Estado civil', et(CIVIL, f.estadoCivil)],
      ['Años de convivencia', f.aniosConvivencia],
      ['Provincia', f.provinciaResidencia],
      ['Distrito', f.distritoResidencia],
      ['Corregimiento', f.corregimientoResidencia],
      ['Dirección', f.direccion],
      ['Tipo de vivienda', et(VIVIENDA, f.tipoVivienda)],
      ['Habitaciones', f.habitaciones],
      ['Personas en el hogar', f.personasHogar],
      ['Hijos', et(SI_NO, f.tieneHijos)],
      ['Cantidad de hijos', f.hijosCantidad]
    ]);

    pintarDatos(document.getElementById('d-sensible'), [
      ['Rango de ingreso', et(INGRESO, f.ingresoRango)],
      ['Fuente principal', et(FUENTE, f.fuenteIngreso)],
      ['Edad que puede acoger', f.rangoEdad],
      ['Sexo del menor', et(SEXO, f.sexoPreferencia)],
      ['Acepta grupo de hermanos', et(SI_NO, f.aceptaHermanos)],
      ['Acepta necesidades especiales', et(SI_NO, f.aceptaNecesidades)],
      ['Detalle de lo que puede atender', f.detalleAceptacion],
      ['Motivación', f.motivacion],
      ['Última modificación', f.fechaActualizacion
        ? f.fechaActualizacion + ' · ' + f.actualizadoPor : '']
    ]);
  }

  function pintarHistorial(eventos) {
    const lista = document.getElementById('historial');
    lista.replaceChildren();
    document.getElementById('sin-historial').hidden = eventos.length > 0;

    eventos.forEach(ev => {
      const li = document.createElement('li');

      const tipo = document.createElement('span');
      tipo.className = 'tipo ' + String(ev.tipo || '').toLowerCase();
      tipo.textContent = et(TIPO_EVENTO, ev.tipo);

      const detalle = document.createElement('div');
      detalle.className = 'detalle';
      const p = document.createElement('p');
      p.textContent = ev.descripcion;
      const firma = document.createElement('span');
      firma.className = 'firma';
      firma.textContent = ev.usuario + ' · ' + ev.rol + ' · ' + ev.fechaHora;
      detalle.append(p, firma);

      li.append(tipo, detalle);
      lista.appendChild(li);
    });
  }

  // ------------------------------------------------------------------
  //  Cambio de estado (solo para quien puede escribir)
  // ------------------------------------------------------------------
  function prepararEstado(f) {
    if (['ADMINISTRADOR', 'TECNICO'].indexOf(sesion.rol) === -1) return;
    if (f.estado === 'CERRADA') return;   // cerrada no se reabre desde aquí

    const caja = document.getElementById('control-estado');
    const select = document.getElementById('nuevo-estado');
    const boton = document.getElementById('btn-estado');
    select.value = f.estado;
    caja.hidden = false;

    boton.addEventListener('click', async () => {
      if (select.value === f.estado) return;
      boton.disabled = true;
      boton.textContent = 'Aplicando…';
      banner.hidden = true;

      try {
        const r = await Sesion.pedir(
          '/api/tecnico/familias/' + encodeURIComponent(numero) + '/estado',
          { method: 'PATCH', body: JSON.stringify({ estado: select.value }) });

        if (r.status === 403) { fallar('Tu rol no puede cambiar el estado de la solicitud.'); return; }
        if (!r.ok) { fallar('No se pudo cambiar el estado (' + r.status + ').'); return; }

        location.reload();   // la forma más simple de reflejar ficha e historial
      } catch (err) {
        if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
      } finally {
        boton.disabled = false;
        boton.textContent = 'Aplicar';
      }
    });
  }

  // ------------------------------------------------------------------
  //  Carga
  // ------------------------------------------------------------------
  (async () => {
    try {
      const r = await Sesion.pedir('/api/familias/' + encodeURIComponent(numero));
      if (r.status === 404) { fallar('No existe una solicitud con el número ' + numero + '.'); return; }
      if (!r.ok) { fallar('No se pudo cargar la solicitud (' + r.status + ').'); return; }

      const familia = await r.json();
      pintarCabecera(familia);
      pintarFicha(familia);
      prepararEstado(familia);
      contenido.hidden = false;

      const rh = await Sesion.pedir('/api/familias/' + encodeURIComponent(numero) + '/historial');
      if (rh.ok) pintarHistorial(await rh.json());

    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    }
  })();
})();
