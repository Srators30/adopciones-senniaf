/* =====================================================================
   SENNIAF · login-api.js
   Sustituye a login_verificar.php. Se registra DESPUÉS de login.js, así
   que corre después de su validación: si ese archivo ya canceló el
   envío por campos vacíos, aquí no se hace nada.
   ===================================================================== */
(() => {
  const form = document.getElementById('form-login');
  const banner = document.getElementById('banner-error');
  const texto = document.getElementById('banner-error-text');
  const boton = document.getElementById('btn-entrar');

  /* Si ya hay sesión válida, no tiene sentido mostrar el login */
  if (!Sesion.vencida()) { location.replace('bienvenida.html'); return; }

  function avisar(msg) {
    texto.textContent = msg;
    banner.hidden = false;
    boton.disabled = false;
    boton.textContent = 'Entrar';
  }

  /* Mensajes que antes venían por ?error= desde PHP */
  const motivo = new URLSearchParams(location.search).get('error');
  if (motivo === 'sesion') avisar('Tu sesión terminó por seguridad. Vuelve a iniciar sesión.');

  form.addEventListener('submit', async e => {
    if (e.defaultPrevented) return;   // login.js encontró campos vacíos
    e.preventDefault();
    banner.hidden = true;

    try {
      const r = await fetch('/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          username: form.username.value.trim(),
          password: form.password.value
        })
      });

      /* 401 = usuario inexistente, contraseña incorrecta o cuenta
         deshabilitada. El backend responde igual en los tres casos. */
      if (r.status === 401) {
        avisar('Usuario o contraseña incorrectos.');
        form.password.value = '';
        form.password.focus();
        return;
      }
      if (r.status === 400) { avisar('Faltan datos por completar.'); return; }
      if (!r.ok) { avisar('El servidor respondió con un error (' + r.status + ').'); return; }

      const datos = await r.json();
      Sesion.guardar(datos.token, datos.nombreCompleto);
      location.href = 'bienvenida.html';

    } catch (err) {
      avisar('No se pudo conectar con el servidor. Revisa que la aplicación esté corriendo.');
    }
  });
})();
