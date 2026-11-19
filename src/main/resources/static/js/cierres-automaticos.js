/* =====================================================================
   SENNIAF · cierres-automaticos.js  (Sprint 8, decisión del PO)
   Dos listas: expedientes por cerrarse (del más urgente al menos) y los
   que ya cerró el sistema. Solo Administrador y Técnico: el servidor
   responde 403 al Juez. Todo con textContent.
   ===================================================================== */
(() => {
  const sesion = Sesion.exigir();
  if (!sesion) return;
  Sesion.pintarBarra();

  const $ = id => document.getElementById(id);

  function fallar(msg) {
    $('banner-error-text').textContent = msg;
    $('banner-error').hidden = false;
    $('resumen-por').textContent = '';
    $('resumen-cerrados').textContent = '';
  }

  function celda(texto, clase) {
    const td = document.createElement('td');
    td.textContent = texto == null || texto === '' ? '—' : texto;
    if (clase) td.className = clase;
    return td;
  }

  function enlace(numero) {
    const td = document.createElement('td');
    td.className = 'num';
    const a = document.createElement('a');
    a.href = 'expediente_detalle.html?n=' + encodeURIComponent(numero);
    a.textContent = numero;
    td.appendChild(a);
    return td;
  }

  function etapa(f) {
    const td = document.createElement('td');
    const pill = document.createElement('span');
    pill.className = 'pill etapa ' + String(f.estadoJuridico).toLowerCase();
    pill.textContent = f.estadoJuridicoEtiqueta;
    td.appendChild(pill);
    return td;
  }

  function cuando(d) {
    return d === 0 ? 'Hoy' : (d === 1 ? 'En 1 día' : 'En ' + d + ' días');
  }

  async function cargar() {
    try {
      const r = await Sesion.pedir('/api/tecnico/expedientes/cierres-automaticos');
      if (r.status === 403) { fallar('Esta sección es para el Administrador y el Técnico.'); return; }
      if (!r.ok) { fallar('No se pudo cargar la información (' + r.status + ').'); return; }
      const datos = await r.json();

      $('regla').textContent = 'Un expediente se cierra solo cuando el menor está en una etapa final (Adoptado o ' +
        'Reintegrado a su familia) y nadie lo ha trabajado en ' + datos.plazoDias + ' días. Cualquier cambio, ' +
        'documento o actualización reinicia el plazo; abrir la ficha para mirarla no.';

      const por = $('filas-por');
      por.replaceChildren();
      datos.porCerrarse.forEach(f => {
        const tr = document.createElement('tr');
        const plazo = celda(cuando(f.diasRestantes) + ' · ' + f.fechaLimite, f.diasRestantes <= 2 ? 'urgente' : '');
        tr.append(enlace(f.numeroExpediente), celda(f.nombreCompleto), etapa(f), celda(f.ultimaActividad), plazo);
        por.appendChild(tr);
      });
      $('vacio-por').hidden = datos.porCerrarse.length > 0;
      $('resumen-por').textContent = datos.porCerrarse.length === 1
        ? '1 expediente por cerrarse' : datos.porCerrarse.length + ' expedientes por cerrarse';

      const cer = $('filas-cerrados');
      cer.replaceChildren();
      datos.cerrados.forEach(f => {
        const tr = document.createElement('tr');
        tr.append(enlace(f.numeroExpediente), celda(f.nombreCompleto), etapa(f), celda(f.fechaCierre));
        cer.appendChild(tr);
      });
      $('vacio-cerrados').hidden = datos.cerrados.length > 0;
      $('resumen-cerrados').textContent = datos.cerrados.length === 1
        ? '1 expediente cerrado por el sistema' : datos.cerrados.length + ' expedientes cerrados por el sistema';
    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    }
  }

  cargar();
})();
