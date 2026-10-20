/* =====================================================================
   SENNIAF · registro-api.js
   Sustituye a guardar_menor.php. Envía el expediente al backend con el
   token y traduce su respuesta. La validación de campos sigue estando
   en registro_menor.js, sin cambios.
   ===================================================================== */
(() => {
  const sesion = Sesion.exigir(['ADMINISTRADOR', 'TECNICO']);
  if (!sesion) return;
  Sesion.pintarBarra();

  const form = document.getElementById('form-menor');
  const bannerOk = document.getElementById('banner-creado');
  const bannerError = document.getElementById('banner-error');
  const textoError = document.getElementById('banner-error-text');

  /* Al volver tras guardar, igual que hacía ?creado= en PHP */
  const creado = new URLSearchParams(location.search).get('creado');
  if (creado) {
    document.getElementById('numero-creado').textContent = creado;
    bannerOk.hidden = false;
  }

  function fallar(msg) {
    textoError.textContent = msg;
    bannerError.hidden = false;
    bannerError.scrollIntoView({ block: 'start' });
    const b = document.getElementById('btn-guardar');
    b.disabled = false;
    b.textContent = 'Guardar expediente';
  }

  form.addEventListener('submit', async e => {
    if (e.defaultPrevented) return;   // registro_menor.js halló errores
    e.preventDefault();
    bannerError.hidden = true;
    bannerOk.hidden = true;

    /* Los campos deshabilitados (los ocultos) no entran en FormData,
       así que no se envían: minimización de datos. */
    const datos = {};
    new FormData(form).forEach((v, k) => { datos[k] = v; });
    form.querySelectorAll('input[type=checkbox]').forEach(c => { datos[c.name] = c.checked; });

    try {
      const r = await Sesion.pedir('/api/tecnico/expedientes', {
        method: 'POST',
        body: JSON.stringify(datos)
      });

      if (r.status === 403) { fallar('Tu rol no tiene permiso para registrar expedientes. El expediente no se guardó.'); return; }
      if (r.status === 400) { fallar('El servidor rechazó los datos: faltan campos obligatorios o alguno no es válido.'); return; }
      if (!r.ok) { fallar('El servidor respondió con un error (' + r.status + '). El expediente no se guardó.'); return; }

      const exp = await r.json();
      /* Se recarga la página con el número, como hacía el PHP: así el
         formulario queda limpio para el siguiente registro. */
      location.href = 'registro_menor.html?creado=' + encodeURIComponent(exp.numeroExpediente);

    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor. El expediente no se guardó.');
    }
  });
})();
