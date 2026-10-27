/* =====================================================================
   SENNIAF · dictamen.js  (Sprint 6 · HU-06.1 y HU-06.2)
   Bloque del dictamen de idoneidad en la ficha de la familia.

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

  function mostrarVigente(d) {
    const pill = $('dv-resultado');
    pill.textContent = d.resultadoEtiqueta;
    pill.className = 'pill ' + (d.resultado === 'IDONEA' ? 'idonea' : 'no-idonea');
    $('dv-fecha-idoneidad').textContent = d.fechaIdoneidad ? 'desde el ' + d.fechaIdoneidad : '';
    $('dv-emisor').textContent = d.emitidoPor + ' · ' + (ROL[d.rolEmisor] || d.rolEmisor);
    $('dv-fecha').textContent = d.fechaDictamen;
    $('dv-observacion').textContent = d.observacion;
    vigente.hidden = false;
  }

  async function iniciar(familia) {
    const sesion = Sesion.datos();
    if (!sesion) return;
    const puedeEscribir = ['ADMINISTRADOR', 'TECNICO'].indexOf(sesion.rol) !== -1;
    const numero = familia.numeroSolicitud;

    try {
      const r = await Sesion.pedir('/api/familias/' + encodeURIComponent(numero) + '/dictamen');
      if (!r.ok) { bloque.hidden = false; fallar('No se pudo cargar el dictamen (' + r.status + ').'); return; }
      const { dictamen } = await r.json();
      bloque.hidden = false;

      if (dictamen) { mostrarVigente(dictamen); return; }

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
