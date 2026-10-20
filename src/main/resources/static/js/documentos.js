/* =====================================================================
   SENNIAF · documentos.js  (HU-04)
   Adjuntar, listar y descargar documentos del expediente.

   Se carga DESPUÉS de expediente-detalle.js y trabaja sobre el mismo
   número de expediente que viene en la URL (?n=EXP-2026-0001).
   ===================================================================== */
(() => {
  const sesion = Sesion.datos();
  if (!sesion) return;                       // expediente-detalle.js ya mandó al login

  const numero = new URLSearchParams(location.search).get('n');
  if (!numero) return;

  const seccion = document.getElementById('bloque-documentos');
  const lista = document.getElementById('lista-documentos');
  const sinDocs = document.getElementById('sin-documentos');
  const form = document.getElementById('form-documento');
  const banner = document.getElementById('doc-error');
  const textoBanner = document.getElementById('doc-error-text');
  const bannerOk = document.getElementById('doc-ok');
  const textoOk = document.getElementById('doc-ok-text');

  /* Adjuntar es escribir: solo los roles que pueden hacerlo ven el formulario.
     Quien bloquea de verdad es el backend, que responde 403. */
  const puedeSubir = ['ADMINISTRADOR', 'TECNICO'].indexOf(sesion.rol) !== -1;
  if (!puedeSubir) form.hidden = true;

  const ICONO = {
    'application/pdf': 'PDF',
    'image/jpeg': 'JPG',
    'image/png': 'PNG',
    'application/vnd.openxmlformats-officedocument.wordprocessingml.document': 'DOCX'
  };

  function fallar(msg) {
    textoBanner.textContent = msg;
    banner.hidden = false;
    bannerOk.hidden = true;
  }

  function avisar(msg) {
    textoOk.textContent = msg;
    bannerOk.hidden = false;
    banner.hidden = true;
  }

  function fila(doc) {
    const li = document.createElement('li');

    const icono = document.createElement('div');
    icono.className = 'doc-icono';
    icono.textContent = ICONO[doc.tipoMime] || 'DOC';

    const datos = document.createElement('div');
    datos.className = 'doc-datos';
    const nombre = document.createElement('strong');
    nombre.textContent = doc.nombre;               // texto, nunca innerHTML
    const meta = document.createElement('span');
    meta.textContent = doc.categoriaEtiqueta + ' · ' + doc.tamano +
                       ' · ' + doc.subidoPor + ' · ' + doc.fechaSubida;
    const hash = document.createElement('span');
    hash.className = 'doc-hash';
    hash.textContent = 'SHA-256: ' + doc.hashSha256;
    datos.append(nombre, meta, hash);

    const boton = document.createElement('button');
    boton.type = 'button';
    boton.className = 'btn btn-secondary';
    boton.textContent = 'Descargar';
    boton.addEventListener('click', () => descargar(doc, boton));

    li.append(icono, datos, boton);
    return li;
  }

  async function cargar() {
    try {
      const r = await Sesion.pedir('/api/expedientes/' + encodeURIComponent(numero) + '/documentos');
      if (!r.ok) { fallar('No se pudo cargar la lista de documentos (' + r.status + ').'); return; }

      const docs = await r.json();
      lista.replaceChildren();
      docs.forEach(d => lista.appendChild(fila(d)));
      sinDocs.hidden = docs.length > 0;
      seccion.hidden = false;

    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    }
  }

  /* El navegador no manda el encabezado Authorization en un enlace
     normal, así que se pide con fetch y la descarga se arma desde el
     blob que devuelve el servidor. */
  async function descargar(doc, boton) {
    boton.disabled = true;
    boton.textContent = 'Descargando…';
    banner.hidden = true;

    try {
      const r = await Sesion.pedir('/api/expedientes/' + encodeURIComponent(numero) +
                                   '/documentos/' + doc.id);
      if (r.status === 403) { fallar('Tu rol no tiene permiso para descargar este documento.'); return; }
      if (!r.ok) { fallar('No se pudo descargar el documento (' + r.status + ').'); return; }

      const blob = await r.blob();
      const url = URL.createObjectURL(blob);
      const enlace = document.createElement('a');
      enlace.href = url;
      enlace.download = doc.nombre;
      document.body.appendChild(enlace);
      enlace.click();
      enlace.remove();
      URL.revokeObjectURL(url);

      avisar('Descarga iniciada. La operación quedó registrada en el historial.');

    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    } finally {
      boton.disabled = false;
      boton.textContent = 'Descargar';
    }
  }

  if (puedeSubir) {
    form.addEventListener('submit', async e => {
      e.preventDefault();
      const archivo = form.archivo.files[0];
      const categoria = form.categoria.value;

      if (!archivo) { fallar('Elige un archivo antes de adjuntar.'); return; }
      if (!categoria) { fallar('Indica de qué tipo de documento se trata.'); return; }

      /* Aviso temprano para no subir 30 MB y recibir el rechazo después.
         El límite real lo aplica el servidor. */
      if (archivo.size > 10 * 1024 * 1024) {
        fallar('El archivo pesa más de 10 MB. Reduce su tamaño antes de adjuntarlo.');
        return;
      }

      const boton = document.getElementById('btn-adjuntar');
      boton.disabled = true;
      boton.textContent = 'Adjuntando…';
      banner.hidden = true;
      bannerOk.hidden = true;

      const cuerpo = new FormData();
      cuerpo.append('archivo', archivo);
      cuerpo.append('categoria', categoria);

      try {
        const r = await Sesion.pedir('/api/tecnico/expedientes/' + encodeURIComponent(numero) + '/documentos', {
          method: 'POST',
          body: cuerpo
        });

        if (r.status === 403) { fallar('Tu rol no puede adjuntar documentos a este expediente.'); return; }
        if (r.status === 413) { fallar('El archivo supera el tamaño máximo permitido.'); return; }
        if (r.status === 400) {
          const detalle = await r.json().catch(() => ({}));
          fallar(detalle.error || 'El archivo no cumple las reglas del sistema.');
          return;
        }
        if (!r.ok) { fallar('No se pudo adjuntar el documento (' + r.status + ').'); return; }

        const doc = await r.json();
        avisar('Documento «' + doc.nombre + '» adjuntado y cifrado correctamente.');
        form.reset();
        await cargar();

      } catch (err) {
        if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
      } finally {
        boton.disabled = false;
        boton.textContent = 'Adjuntar documento';
      }
    });
  }

  cargar();
})();
