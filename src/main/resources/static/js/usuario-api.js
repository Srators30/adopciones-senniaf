/* =====================================================================
   SENNIAF · usuario-api.js
   Sustituye a crear_usuario_guardar.php. Crea la cuenta en el backend
   (POST /api/admin/usuarios), que solo acepta tokens de Administrador.
   ===================================================================== */
(() => {
  const sesion = Sesion.exigir(['ADMINISTRADOR']);
  if (!sesion) return;
  Sesion.pintarBarra();

  const form = document.getElementById('form-usuario');
  const bannerOk = document.getElementById('banner-creado');
  const bannerError = document.getElementById('banner-error');
  const textoError = document.getElementById('banner-error-text');

  if (new URLSearchParams(location.search).get('creado')) bannerOk.hidden = false;

  function fallar(msg) {
    textoError.textContent = msg;
    bannerError.hidden = false;
    bannerError.scrollIntoView({ block: 'start' });
    const b = document.getElementById('btn-guardar');
    b.disabled = false;
    b.textContent = 'Crear usuario';
  }

  form.addEventListener('submit', async e => {
    if (e.defaultPrevented) return;   // crear-usuario.js halló errores
    e.preventDefault();
    bannerError.hidden = true;
    bannerOk.hidden = true;

    const d = new FormData(form);
    try {
      const r = await Sesion.pedir('/api/admin/usuarios', {
        method: 'POST',
        body: JSON.stringify({
          username: (d.get('username') || '').trim(),
          password: d.get('password') || '',
          rol: d.get('rol') || '',
          nombreCompleto: (d.get('nombre_completo') || '').trim(),
          habilitado: (d.get('estado') || 'activo') === 'activo'
        })
      });

      if (r.status === 409) { fallar('Ya existe una cuenta con ese usuario. Elige otro.'); return; }
      if (r.status === 403) { fallar('Solo un Administrador puede crear cuentas. La cuenta no se creó.'); return; }
      if (r.status === 400) { fallar('El servidor rechazó los datos: revisa el formato del usuario y la contraseña.'); return; }
      if (!r.ok) { fallar('El servidor respondió con un error (' + r.status + '). La cuenta no se creó.'); return; }

      location.href = 'crear-usuario.html?creado=1';

    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor. La cuenta no se creó.');
    }
  });
})();
