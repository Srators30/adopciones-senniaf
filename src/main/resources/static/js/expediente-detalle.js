/* =====================================================================
   SENNIAF · expediente-detalle.js  (HU-03)
   Ficha del expediente e historial de cambios.
   El número del expediente llega en la URL: ?n=EXP-2026-0001
   ===================================================================== */
(() => {
  const sesion = Sesion.exigir();          // los tres roles pueden consultar
  if (!sesion) return;
  Sesion.pintarBarra();

  const numero = new URLSearchParams(location.search).get('n');
  const contenido = document.getElementById('contenido');
  const bannerOk = document.getElementById('banner-ok');
  const textoOk = document.getElementById('banner-ok-text');
  const bannerError = document.getElementById('banner-error');
  const textoError = document.getElementById('banner-error-text');

  const ETIQUETA_ESTADO = { REGISTRADO: 'Registrado', EN_REVISION: 'En revisión', CERRADO: 'Cerrado' };
  const ETIQUETA_TIPO = {
    CREACION: 'Creación', MODIFICACION: 'Modificación', CONSULTA: 'Consulta',
    CAMBIO_ESTADO: 'Estado', DOCUMENTO: 'Documento',
    ESTADO_JURIDICO: 'Etapa jurídica'          // Sprint 8 · HU-07.2
  };
  const ETIQUETA_ROL = {
    ADMINISTRADOR: 'Administrador', TECNICO: 'Técnico/Trabajador', JUEZ: 'Juez',
    SISTEMA: 'automático'                      // Sprint 8: cierre automático
  };

  /* Traducciones de los valores que el formulario guarda en clave */
  const DICCIONARIO = {
    F: 'Femenino', M: 'Masculino',
    si: 'Sí', no: 'No', desconocido: 'Se desconoce', no_evaluado: 'Sin evaluar',
    certificado_nacimiento: 'Certificado de nacimiento', cedula_juvenil: 'Cédula juvenil',
    pasaporte: 'Pasaporte', sin_documento: 'Sin documento (inscripción pendiente)',
    centro_acogimiento: 'Centro de acogimiento', familia_acogida: 'Familia de acogida',
    familiar: 'Acogimiento con un familiar', hospital: 'Hospital o centro de salud', otra: 'Otra',
    juzgado_ninez: 'Juzgado de niñez y adolescencia', ministerio_publico: 'Ministerio Público',
    policia: 'Policía Nacional', mides: 'Ministerio de Desarrollo Social',
    abandono: 'Abandono', negligencia: 'Negligencia o descuido', maltrato: 'Maltrato o violencia',
    orfandad: 'Orfandad', entrega_voluntaria: 'Entrega voluntaria', otro: 'Otro',
    no_determinado: 'No determinado', ngabe: 'Ngäbe', bugle: 'Buglé', guna: 'Guna',
    embera: 'Emberá', wounaan: 'Wounaan', naso: 'Naso Tjër Di', bribri: 'Bribri'
  };
  const leer = v => (v === null || v === undefined || v === '') ? '' : (DICCIONARIO[v] || String(v));

  function fallar(msg) {
    textoError.textContent = msg;
    bannerError.hidden = false;
    document.getElementById('subtitulo').textContent = '';
  }

  /* dt + dd como texto, nunca innerHTML: los datos vienen de la base */
  function dato(lista, etiqueta, valor) {
    const div = document.createElement('div');
    const dt = document.createElement('dt');
    dt.textContent = etiqueta;
    const dd = document.createElement('dd');
    const v = leer(valor);
    dd.textContent = v || 'No registrado';
    if (!v) dd.className = 'vacia';
    div.append(dt, dd);
    lista.appendChild(div);
  }

  function pintarFicha(e) {
    document.getElementById('titulo').textContent = e.nombreCompleto;
    document.getElementById('subtitulo').textContent =
      'Expediente ' + e.numeroExpediente + ' · ' + (leer(e.sexo) || 'sexo no registrado') +
      (e.edad ? ' · ' + e.edad : '');

    document.getElementById('c-numero').textContent = e.numeroExpediente;
    document.getElementById('c-creacion').textContent = e.fechaCreacion;
    document.getElementById('c-responsable').textContent = e.creadoPor;

    const estado = document.getElementById('c-estado');
    estado.replaceChildren();
    const pill = document.createElement('span');
    pill.className = 'pill ' + String(e.estado).toLowerCase();
    pill.textContent = ETIQUETA_ESTADO[e.estado] || e.estado;
    estado.appendChild(pill);

    // Sprint 8 · HU-07.1: etapa jurídica en la cabecera.
    const etapa = document.getElementById('c-etapa');
    etapa.replaceChildren();
    const pillEtapa = document.createElement('span');
    pillEtapa.className = 'pill etapa ' + String(e.estadoJuridico || '').toLowerCase();
    pillEtapa.textContent = e.estadoJuridicoEtiqueta || e.estadoJuridico;
    etapa.appendChild(pillEtapa);

    // Sprint 8: aviso del cierre automático.
    const nota = document.getElementById('nota-cierre');
    const cierre = e.cierreAutomatico || {};
    nota.hidden = true;
    if (e.fechaCierreAutomatico) {
      nota.textContent = 'Cerrado automáticamente el ' + e.fechaCierreAutomatico.slice(0, 16) +
        ' tras ' + cierre.plazoDias + ' días sin actividad en etapa final. Si no debía cerrarse, el Administrador puede reabrirlo.';
      nota.className = 'nota-cierre cerrado';
      nota.hidden = false;
    } else if (cierre.fechaLimite) {
      const d = cierre.diasRestantes;
      nota.textContent = 'Este expediente está en etapa final: se cerrará automáticamente ' +
        (d === 0 ? 'hoy' : 'en ' + d + (d === 1 ? ' día' : ' días')) + ' (' + cierre.fechaLimite +
        ') si nadie lo trabaja. Cualquier cambio, documento o actualización reinicia el plazo.';
      nota.className = 'nota-cierre' + (d <= 2 ? ' urgente' : '');
      nota.hidden = false;
    }

    const menor = document.getElementById('d-menor');
    menor.replaceChildren();
    dato(menor, 'Nombres', e.nombreCompleto);
    dato(menor, 'Sexo', e.sexo);
    dato(menor, 'Fecha de nacimiento', e.fechaNacimiento);
    dato(menor, 'Edad', e.edad);
    dato(menor, 'Documento', e.tipoDocumento);
    dato(menor, 'N.º del documento', e.numeroDocumento);
    dato(menor, 'Provincia', e.provinciaNacimiento);
    dato(menor, 'Distrito', e.distritoNacimiento);
    dato(menor, 'Corregimiento', e.corregimientoNacimiento);

    const ingreso = document.getElementById('d-ingreso');
    ingreso.replaceChildren();
    dato(ingreso, 'Fecha de ingreso', e.fechaIngreso);
    dato(ingreso, 'Modalidad de acogida', e.modalidadAcogida);
    dato(ingreso, 'Centro o lugar', e.nombreCentro);
    dato(ingreso, 'Autoridad que remite', e.autoridadRemitente);
    dato(ingreso, 'N.º de oficio', e.numeroOficio);
    dato(ingreso, 'Motivo de ingreso', e.motivoIngreso);
    dato(ingreso, 'Observaciones', e.observaciones);

    const familia = document.getElementById('d-familia');
    familia.replaceChildren();
    dato(familia, '¿Tiene hermanos?', e.hermanos);
    dato(familia, 'Cantidad de hermanos', e.hermanosCantidad);
    dato(familia, 'Expedientes de hermanos', e.hermanosReferencia);
    dato(familia, 'Familiar de referencia', e.familiarNombre);
    dato(familia, 'Parentesco', e.familiarParentesco);
    dato(familia, 'Teléfono', e.familiarTelefono);

    const salud = document.getElementById('d-salud');
    salud.replaceChildren();
    dato(salud, 'Requiere atención especial', e.necesidadesEspeciales);
    dato(salud, 'Atención requerida', e.detalleNecesidades);
    dato(salud, 'Pueblo indígena', e.puebloIndigena);

    /* Cambiar estado: solo quienes pueden escribir y si no está cerrado */
    if (['ADMINISTRADOR', 'TECNICO'].indexOf(sesion.rol) !== -1 && e.estado !== 'CERRADO') {
      const caja = document.getElementById('acciones-estado');
      document.getElementById('nuevo-estado').value = e.estado;
      caja.hidden = false;
    }
    /* Sprint 8: un expediente cerrado solo lo reabre el Administrador */
    document.getElementById('btn-abrir-reapertura').hidden =
      !(sesion.rol === 'ADMINISTRADOR' && e.estado === 'CERRADO');

    contenido.hidden = false;
    // etapa-juridica.js arma el bloque del Juez con estos mismos datos.
    document.dispatchEvent(new CustomEvent('expediente:cargado', { detail: e }));
  }

  function pintarHistorial(eventos) {
    const lista = document.getElementById('historial');
    lista.replaceChildren();

    if (!eventos.length) {
      const li = document.createElement('li');
      li.textContent = 'Sin movimientos registrados.';
      lista.appendChild(li);
      return;
    }

    eventos.forEach(ev => {
      const li = document.createElement('li');

      const tipo = document.createElement('span');
      tipo.className = 'tipo ' + ev.tipo.toLowerCase();
      tipo.textContent = ETIQUETA_TIPO[ev.tipo] || ev.tipo;

      const detalle = document.createElement('div');
      detalle.className = 'detalle';
      const p = document.createElement('p');
      p.textContent = ev.descripcion;
      const firma = document.createElement('p');
      firma.className = 'firma';
      firma.textContent = ev.usuario + ' (' + (ETIQUETA_ROL[ev.rol] || ev.rol) + ') · ' + ev.fechaHora;
      detalle.append(p, firma);

      li.append(tipo, detalle);
      lista.appendChild(li);
    });
  }

  async function cargar() {
    if (!numero) { fallar('No se indicó qué expediente abrir.'); return; }
    bannerError.hidden = true;

    try {
      const r = await Sesion.pedir('/api/expedientes/' + encodeURIComponent(numero));
      if (r.status === 404) { fallar('No existe un expediente con el número ' + numero + '.'); return; }
      if (r.status === 403) { fallar('Tu rol no tiene permiso para ver este expediente.'); return; }
      if (!r.ok) { fallar('El servidor respondió con un error (' + r.status + ').'); return; }
      pintarFicha(await r.json());

      const h = await Sesion.pedir('/api/expedientes/' + encodeURIComponent(numero) + '/historial');
      if (h.ok) pintarHistorial(await h.json());

    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    }
  }

  document.getElementById('btn-estado').addEventListener('click', async e => {
    const boton = e.target;
    const nuevo = document.getElementById('nuevo-estado').value;
    boton.disabled = true;
    bannerOk.hidden = true;
    bannerError.hidden = true;

    try {
      const r = await Sesion.pedir('/api/tecnico/expedientes/' + encodeURIComponent(numero) + '/estado', {
        method: 'PATCH',
        body: JSON.stringify({ estado: nuevo })
      });

      if (r.status === 403) { fallar('Tu rol no puede cambiar el estado del expediente.'); return; }
      if (!r.ok) {
        const detalle = await r.json().catch(() => ({}));
        fallar(detalle.error || 'No se pudo cambiar el estado (' + r.status + ').');
        return;
      }

      textoOk.textContent = 'Estado actualizado a ' + (ETIQUETA_ESTADO[nuevo] || nuevo) + '.';
      bannerOk.hidden = false;
      await cargar();

    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    } finally {
      boton.disabled = false;
    }
  });

  cargar();
})();
