/* =====================================================================
   SENNIAF · etapa-juridica.js  (Sprint 8 · HU-07.2 y reapertura)

   Espera a que expediente-detalle.js termine de cargar la ficha (evento
   "expediente:cargado") y arma:
     · el bloque "Etapa jurídica", que solo ve el Juez;
     · el formulario "Reabrir expediente", solo para el Administrador.

   Las reglas las decide el servidor; aquí solo se ofrecen las opciones
   posibles y se avisa antes de enviar. Todo con textContent.
   ===================================================================== */
(() => {
  const MINIMO = 20;   // igual que en el servidor
  const ETAPA = {
    EN_PROTECCION: 'En protección',
    EN_PROCESO_JUDICIAL: 'En proceso judicial',
    DECLARADO_ADOPTABLE: 'Declarado adoptable',
    ADOPTADO: 'Adoptado',
    REINTEGRADO: 'Reintegrado a su familia'
  };

  const $ = id => document.getElementById(id);
  const numero = new URLSearchParams(location.search).get('n');
  const banner = $('banner-error');
  const textoBanner = $('banner-error-text');

  function fallar(msg) {
    textoBanner.textContent = msg;
    banner.hidden = false;
    banner.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  function marcar(campo, msg) {
    const salida = $('e-' + campo);
    if (salida) salida.textContent = msg || '';
    const control = { etapa: 'nueva-etapa', observacion: 'obs-etapa', motivo: 'motivo-reapertura' }[campo];
    const caja = control && $(control).closest('.field');
    if (caja) caja.classList.toggle('invalid', !!msg);
  }

  /* ------------------------ HU-07.2 · Juez ------------------------ */

  function prepararEtapa(e) {
    const sesion = Sesion.datos();
    if (!sesion || sesion.rol !== 'JUEZ') return;

    const bloque = $('bloque-etapa');
    const nota = $('etapa-nota');
    const form = $('form-etapa');
    bloque.hidden = false;
    nota.hidden = true;
    form.hidden = true;

    if (e.estado === 'CERRADO') {
      nota.textContent = 'El expediente está cerrado: el Administrador debe reabrirlo antes de cambiar la etapa jurídica.';
      nota.hidden = false;
      return;
    }
    if (e.etapaFinal) {
      nota.textContent = (ETAPA[e.estadoJuridico] || e.estadoJuridico) + ' es una etapa final: ya no admite cambios.';
      nota.hidden = false;
      return;
    }

    // Solo se ofrecen las etapas a las que se puede pasar desde la actual.
    const select = $('nueva-etapa');
    select.replaceChildren();
    const vacia = document.createElement('option');
    vacia.value = '';
    vacia.textContent = 'Selecciona';
    select.appendChild(vacia);
    (e.siguientesEtapas || []).forEach(v => {
      const o = document.createElement('option');
      o.value = v;
      o.textContent = ETAPA[v] || v;
      select.appendChild(o);
    });
    if ((e.siguientesEtapas || []).length === 1) select.value = e.siguientesEtapas[0];
    form.hidden = false;
  }

  function validarEtapa() {
    let ok = true;
    if (!$('nueva-etapa').value) { marcar('etapa', 'Elige la nueva etapa.'); ok = false; }
    else marcar('etapa', '');
    if ($('obs-etapa').value.trim().length < MINIMO) {
      marcar('observacion', 'Explica el cambio en al menos ' + MINIMO + ' caracteres.'); ok = false;
    } else marcar('observacion', '');
    return ok;
  }

  $('form-etapa').addEventListener('submit', async ev => {
    ev.preventDefault();
    banner.hidden = true;
    if (!validarEtapa()) return;

    const boton = $('btn-etapa');
    boton.disabled = true;
    boton.textContent = 'Guardando…';
    try {
      const r = await Sesion.pedir('/api/juez/expedientes/' + encodeURIComponent(numero) + '/estado-juridico', {
        method: 'PATCH',
        body: JSON.stringify({ etapa: $('nueva-etapa').value, observacion: $('obs-etapa').value.trim() })
      });
      if (r.ok) { location.reload(); return; }
      const detalle = await r.json().catch(() => ({}));
      if (r.status === 403) fallar('Solo el Juez puede cambiar la etapa jurídica.');
      else if (r.status === 400) {
        fallar(detalle.error || 'El cambio no cumple las reglas.');
        (detalle.campos || []).forEach(c => marcar(c, detalle.error));
      } else fallar(detalle.error || 'No se pudo cambiar la etapa (' + r.status + ').');
    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    } finally {
      boton.disabled = false;
      boton.textContent = 'Cambiar etapa';
    }
  });
  ['nueva-etapa', 'obs-etapa'].forEach(id => $(id).addEventListener('input', () => {
    if ($('form-etapa').querySelector('.invalid')) validarEtapa();
  }));

  /* ------------------ Reapertura · Administrador ------------------ */

  const abrir = $('btn-abrir-reapertura');
  const formReap = $('form-reapertura');
  abrir.addEventListener('click', () => {
    abrir.hidden = true;
    formReap.hidden = false;
    $('motivo-reapertura').focus();
  });
  $('btn-cancelar-reapertura').addEventListener('click', () => {
    formReap.hidden = true;
    abrir.hidden = false;
  });
  formReap.addEventListener('submit', async ev => {
    ev.preventDefault();
    banner.hidden = true;
    const motivo = $('motivo-reapertura').value.trim();
    if (motivo.length < MINIMO) {
      marcar('motivo', 'Explica el motivo en al menos ' + MINIMO + ' caracteres.');
      return;
    }
    marcar('motivo', '');
    const boton = $('btn-reabrir');
    boton.disabled = true;
    boton.textContent = 'Reabriendo…';
    try {
      const r = await Sesion.pedir('/api/admin/expedientes/' + encodeURIComponent(numero) + '/reapertura',
        { method: 'POST', body: JSON.stringify({ motivo }) });
      if (r.ok) { location.reload(); return; }
      const detalle = await r.json().catch(() => ({}));
      if (r.status === 403) fallar('Solo el Administrador puede reabrir un expediente.');
      else fallar(detalle.error || 'No se pudo reabrir el expediente (' + r.status + ').');
    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    } finally {
      boton.disabled = false;
      boton.textContent = 'Reabrir expediente';
    }
  });

  document.addEventListener('expediente:cargado', e => prepararEtapa(e.detail));
})();
