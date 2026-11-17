/* =====================================================================
   SENNIAF · dictamen.js  (Sprint 6 · HU-06.1 y HU-06.2 · Sprint 7 · HU-06.3 y HU-06.4)
   Bloque del dictamen de idoneidad en la ficha de la familia.

   Sprint 7 agrega:
     · la posición de la familia en la lista de espera;
     · el historial de dictámenes, con el periodo de cada uno;
     · el formulario para cambiar el dictamen, solo para el Administrador.

   Espera a que familia-detalle.js termine de cargar la ficha (evento
   "familia:cargada") y usa esos datos: así sabe el estado del trámite
   sin volver a pedir la ficha al servidor.

   Lo que se muestra depende de tres cosas:
     · ¿ya hay dictamen?            → se muestra, en solo lectura.
     · ¿el rol puede escribir?      → Técnico y Administrador sí, Juez no.
     · ¿la solicitud está En evaluación? → solo entonces aparece el formulario.

   Todo con textContent: la observación la escribió una persona y no es
   HTML de confianza.
   ===================================================================== */
(() => {
  const MINIMO_NO_IDONEA = 30;   // HU-06.2, criterio 2 (igual que en el servidor)
  const MINIMO_JUSTIFICACION = 20;   // HU-06.4, criterio 2 (igual que en el servidor)

  const $ = id => document.getElementById(id);
  const bloque = $('bloque-dictamen');
  const vigente = $('dictamen-vigente');
  const vacio = $('dictamen-vacio');
  const form = $('form-dictamen');
  const banner = $('dictamen-error');
  const textoBanner = $('dictamen-error-text');
  const observacion = $('observacion');
  const contador = $('contador-observacion');

  const ROL = { ADMINISTRADOR: 'Administrador', TECNICO: 'Técnico', JUEZ: 'Juez' };

  function fallar(msg) {
    textoBanner.textContent = msg;
    banner.hidden = false;
  }

  function marcar(campo, msg) {
    const salida = $('e-' + campo);
    if (salida) salida.textContent = msg || '';
    const caja = campo === 'resultado' ? form.querySelector('.resultado') : observacion.closest('.field');
    if (caja) caja.classList.toggle('invalid', !!msg);
  }

  function resultadoElegido() {
    const r = form.querySelector('input[name="resultado"]:checked');
    return r ? r.value : '';
  }

  /* El contador cambia de mensaje según el resultado elegido: con
     No idónea dice cuánto falta para el mínimo. */
  function pintarContador() {
    const n = observacion.value.trim().length;
    if (resultadoElegido() === 'NO_IDONEA' && n < MINIMO_NO_IDONEA) {
      contador.textContent = n + ' de ' + MINIMO_NO_IDONEA + ' caracteres mínimos';
      contador.classList.add('falta');
    } else {
      contador.textContent = n === 1 ? '1 carácter' : n + ' caracteres';
      contador.classList.remove('falta');
    }
  }

  function validar() {
    let ok = true;
    const r = resultadoElegido();
    const n = observacion.value.trim().length;

    if (!r) { marcar('resultado', 'Elige el resultado del dictamen.'); ok = false; }
    else marcar('resultado', '');

    if (n === 0) {
      marcar('observacion', 'Todo dictamen necesita su fundamento.'); ok = false;
    } else if (r === 'NO_IDONEA' && n < MINIMO_NO_IDONEA) {
      marcar('observacion', 'Un dictamen No idónea exige al menos ' + MINIMO_NO_IDONEA +
                            ' caracteres que expliquen el motivo.');
      ok = false;
    } else {
      marcar('observacion', '');
    }
    return ok;
  }

  function claseResultado(r) { return r === 'IDONEA' ? 'idonea' : 'no-idonea'; }
  function soloFecha(fechaHora) { return String(fechaHora || '').slice(0, 10); }

  function mostrarVigente(d, posicion) {
    const pill = $('dv-resultado');
    pill.textContent = d.resultadoEtiqueta;
    pill.className = 'pill ' + claseResultado(d.resultado);
    $('dv-fecha-idoneidad').textContent = d.fechaIdoneidad ? 'desde el ' + d.fechaIdoneidad : '';
    $('dv-emisor').textContent = d.emitidoPor + ' · ' + (ROL[d.rolEmisor] || d.rolEmisor);
    $('dv-fecha').textContent = d.fechaDictamen;
    $('dv-observacion').textContent = d.observacion;
    if (d.justificacionCambio) {
      $('dv-justificacion').textContent = d.justificacionCambio;
      $('dv-justificacion-caja').hidden = false;
    }
    if (posicion) {
      $('dv-posicion-numero').textContent = posicion;
      $('dv-posicion').hidden = false;
    }
    vigente.hidden = false;
  }

  /* HU-06.4, criterio 5. Cada entrada dice cuándo estuvo vigente: así se
     ve, por ejemplo, desde cuándo y hasta cuándo una familia fue No idónea. */
  function mostrarHistorial(lista) {
    if (!lista || lista.length < 2) return;   // con uno solo, ya se ve arriba
    const ol = $('dh-lista');
    ol.replaceChildren();
    lista.forEach(d => {
      const li = document.createElement('li');
      li.className = d.vigente ? 'vigente' : 'sustituido';

      const cab = document.createElement('div');
      cab.className = 'dh-cabecera';
      const pill = document.createElement('span');
      pill.className = 'pill ' + claseResultado(d.resultado);
      pill.textContent = d.resultadoEtiqueta;
      const periodo = document.createElement('span');
      periodo.className = 'dh-periodo';
      periodo.textContent = d.vigente
        ? 'Vigente desde el ' + soloFecha(d.fechaDictamen)
        : 'Del ' + soloFecha(d.fechaDictamen) + ' al ' + soloFecha(d.fechaSustitucion);
      cab.append(pill, periodo);

      const quien = document.createElement('p');
      quien.className = 'dh-quien';
      quien.textContent = 'Emitido por ' + d.emitidoPor + ' (' + (ROL[d.rolEmisor] || d.rolEmisor) + ')'
        + (d.vigente ? '' : ' · sustituido por ' + d.sustituidoPor);

      const obs = document.createElement('p');
      obs.className = 'dh-texto';
      obs.textContent = d.observacion;

      li.append(cab, quien, obs);
      if (d.justificacionCambio) {
        const j = document.createElement('p');
        j.className = 'dh-texto dh-justificacion';
        j.textContent = 'Motivo del cambio: ' + d.justificacionCambio;
        li.appendChild(j);
      }
      ol.appendChild(li);
    });
    $('dictamen-historial').hidden = false;
  }

  /* ------------------ Cambio de dictamen (HU-06.4) ------------------ */

  const formCambio = $('form-cambio');
  const obsCambio = $('cambio-observacion');
  const justCambio = $('cambio-justificacion');
  const contadorCambio = $('contador-cambio');
  let resultadoVigente = '';

  function marcarCambio(campo, msg) {
    const salida = $('ec-' + campo);
    if (salida) salida.textContent = msg || '';
    const caja = campo === 'resultado' ? formCambio.querySelector('.resultado')
      : (campo === 'observacion' ? obsCambio : justCambio).closest('.field');
    if (caja) caja.classList.toggle('invalid', !!msg);
  }

  function resultadoCambio() {
    const r = formCambio.querySelector('input[name="cambio-resultado"]:checked');
    return r ? r.value : '';
  }

  function pintarContadorCambio() {
    const n = obsCambio.value.trim().length;
    if (resultadoCambio() === 'NO_IDONEA' && n < MINIMO_NO_IDONEA) {
      contadorCambio.textContent = n + ' de ' + MINIMO_NO_IDONEA + ' caracteres mínimos';
      contadorCambio.classList.add('falta');
    } else {
      contadorCambio.textContent = n === 1 ? '1 carácter' : n + ' caracteres';
      contadorCambio.classList.remove('falta');
    }
  }

  function validarCambio() {
    let ok = true;
    const r = resultadoCambio();
    const n = obsCambio.value.trim().length;
    const j = justCambio.value.trim().length;

    if (!r) { marcarCambio('resultado', 'Elige el nuevo resultado.'); ok = false; }
    else if (r === resultadoVigente) { marcarCambio('resultado', 'El dictamen ya es ese: un cambio tiene que cambiar el resultado.'); ok = false; }
    else marcarCambio('resultado', '');

    if (n === 0) { marcarCambio('observacion', 'El nuevo dictamen necesita su fundamento.'); ok = false; }
    else if (r === 'NO_IDONEA' && n < MINIMO_NO_IDONEA) {
      marcarCambio('observacion', 'Un dictamen No idónea exige al menos ' + MINIMO_NO_IDONEA + ' caracteres.'); ok = false;
    } else marcarCambio('observacion', '');

    if (j < MINIMO_JUSTIFICACION) {
      marcarCambio('justificacion', 'Explica por qué se cambia, en al menos ' + MINIMO_JUSTIFICACION + ' caracteres.'); ok = false;
    } else marcarCambio('justificacion', '');
    return ok;
  }

  function prepararCambio(familia, dictamen) {
    if (Sesion.datos().rol !== 'ADMINISTRADOR') return;
    if (familia.estado === 'CERRADA') { $('cambio-cerrada').hidden = false; return; }   // se reabre arriba
    resultadoVigente = dictamen.resultado;
    // La opción igual a la vigente no tiene sentido: se deshabilita.
    formCambio.querySelectorAll('input[name="cambio-resultado"]').forEach(i => {
      i.disabled = i.value === resultadoVigente;
      i.closest('.opcion').classList.toggle('deshabilitada', i.disabled);
      if (!i.disabled) i.checked = true;
    });
    $('cambio-acciones').hidden = false;
    pintarContadorCambio();
  }

  $('btn-abrir-cambio').addEventListener('click', () => {
    $('cambio-acciones').hidden = true;
    formCambio.hidden = false;
    obsCambio.focus();
  });
  $('btn-cancelar-cambio').addEventListener('click', () => {
    formCambio.hidden = true;
    $('cambio-acciones').hidden = false;
  });
  formCambio.addEventListener('change', () => { pintarContadorCambio(); if (formCambio.querySelector('.invalid')) validarCambio(); });
  [obsCambio, justCambio].forEach(el => el.addEventListener('input', () => {
    pintarContadorCambio(); if (formCambio.querySelector('.invalid')) validarCambio();
  }));

  formCambio.addEventListener('submit', async e => {
    e.preventDefault();
    banner.hidden = true;
    if (!validarCambio()) return;

    const numero = new URLSearchParams(location.search).get('n');
    const boton = $('btn-cambio');
    boton.disabled = true;
    boton.textContent = 'Guardando…';
    try {
      const r = await Sesion.pedir('/api/admin/familias/' + encodeURIComponent(numero) + '/dictamen/cambio', {
        method: 'POST',
        body: JSON.stringify({
          resultado: resultadoCambio(),
          observacion: obsCambio.value.trim(),
          justificacion: justCambio.value.trim()
        })
      });
      if (r.status === 201) { location.reload(); return; }

      const detalle = await r.json().catch(() => ({}));
      if (r.status === 403) fallar('Solo el Administrador puede cambiar un dictamen.');
      else if (r.status === 400) {
        fallar(detalle.error || 'El cambio no cumple las reglas.');
        (detalle.campos || []).forEach(c => marcarCambio(c, detalle.error));
      }
      else if (r.status === 409) fallar(detalle.error || 'La solicitud no admite cambios de dictamen en este momento.');
      else fallar('No se pudo cambiar el dictamen (' + r.status + ').');
    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    } finally {
      boton.disabled = false;
      boton.textContent = 'Guardar cambio';
    }
  });

  async function iniciar(familia) {
    const sesion = Sesion.datos();
    if (!sesion) return;
    const puedeEscribir = ['ADMINISTRADOR', 'TECNICO'].indexOf(sesion.rol) !== -1;
    const numero = familia.numeroSolicitud;

    try {
      const r = await Sesion.pedir('/api/familias/' + encodeURIComponent(numero) + '/dictamen');
      if (!r.ok) { bloque.hidden = false; fallar('No se pudo cargar el dictamen (' + r.status + ').'); return; }
      const { dictamen, posicionLista, historial } = await r.json();
      bloque.hidden = false;
      mostrarHistorial(historial);

      if (dictamen) {
        mostrarVigente(dictamen, posicionLista);
        prepararCambio(familia, dictamen);
        return;
      }

      // Sin dictamen: ¿quién lo ve y en qué estado está la solicitud?
      if (!puedeEscribir) {
        vacio.textContent = 'Esta solicitud todavía no tiene dictamen.';
      } else if (familia.estado !== 'EN_EVALUACION') {
        vacio.textContent = familia.estado === 'CERRADA'
          ? 'La solicitud está cerrada y no recibió dictamen.'
          : 'Para emitir el dictamen, primero cambia el estado de la solicitud a En evaluación (arriba a la derecha).';
      } else {
        form.hidden = false;
        pintarContador();
        return;
      }
      vacio.hidden = false;

    } catch (err) {
      if (err.message !== 'sesion') { bloque.hidden = false; fallar('No se pudo conectar con el servidor.'); }
    }
  }

  form.addEventListener('change', () => { pintarContador(); if (form.querySelector('.invalid')) validar(); });
  observacion.addEventListener('input', () => { pintarContador(); if (form.querySelector('.invalid')) validar(); });

  form.addEventListener('submit', async e => {
    e.preventDefault();
    banner.hidden = true;
    if (!validar()) return;

    const numero = new URLSearchParams(location.search).get('n');
    const boton = $('btn-dictamen');
    boton.disabled = true;
    boton.textContent = 'Emitiendo…';

    try {
      const r = await Sesion.pedir('/api/tecnico/familias/' + encodeURIComponent(numero) + '/dictamen', {
        method: 'POST',
        body: JSON.stringify({ resultado: resultadoElegido(), observacion: observacion.value.trim() })
      });

      if (r.status === 201) { location.reload(); return; }   // ficha e historial al día

      const detalle = await r.json().catch(() => ({}));
      if (r.status === 403) fallar('Tu rol no puede emitir dictámenes.');
      else if (r.status === 400) {
        fallar(detalle.error || 'El dictamen no cumple las reglas.');
        (detalle.campos || []).forEach(c => marcar(c, detalle.error));
      }
      else if (r.status === 409) fallar(detalle.error || 'La solicitud no admite dictamen en este momento.');
      else fallar('No se pudo emitir el dictamen (' + r.status + ').');

    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    } finally {
      boton.disabled = false;
      boton.textContent = 'Emitir dictamen';
    }
  });

  document.addEventListener('familia:cargada', e => iniciar(e.detail));
})();
