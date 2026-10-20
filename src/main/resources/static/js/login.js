/* =====================================================================
   SENNIAF · login.js
   Validación de ayuda para el usuario. El servidor (PHP) SIEMPRE debe
   volver a validar usuario/contraseña; esto no reemplaza esa validación.
   ===================================================================== */
(() => {
  const form = document.getElementById('form-login');
  const $ = (s, r = document) => r.querySelector(s);

  /* El banner de error (login fallido / cuenta inhabilitada) ya lo
     renderiza login.php del lado del servidor según ?error=... */

  function check(el) {
    if (el.validity.valueMissing || !el.value.trim()) return 'Este campo es obligatorio.';
    return '';
  }

  function setError(el, msg) {
    const box = el.closest('.field');
    $('.error', box).textContent = msg;
    box.classList.toggle('invalid', !!msg);
    el.setAttribute('aria-invalid', msg ? 'true' : 'false');
  }

  form.addEventListener('input', e => {
    if (e.target.closest('.invalid')) setError(e.target, check(e.target));
  });

  form.addEventListener('submit', e => {
    const resumen = $('#resumen-errores'), lista = $('ul', resumen);
    lista.innerHTML = '';
    let primero = null;

    [form.username, form.password].forEach(el => {
      const msg = check(el);
      setError(el, msg);
      if (msg) {
        primero = primero || el;
        const li = document.createElement('li'), a = document.createElement('a');
        const nombre = $('label', el.closest('.field')).textContent.replace('*', '').trim();
        a.href = '#' + el.id; a.textContent = nombre + ': ' + msg;
        a.addEventListener('click', ev => { ev.preventDefault(); el.focus(); });
        li.appendChild(a); lista.appendChild(li);
      }
    });

    if (primero) {
      e.preventDefault();
      resumen.hidden = false;
      resumen.focus();
      primero.focus();
      return;
    }
    resumen.hidden = true;
    const b = $('#btn-entrar'); b.disabled = true; b.textContent = 'Entrando…';
  });

  /* Mostrar / ocultar contraseña */
  const toggle = $('#toggle-password'), pass = $('#password');
  toggle.addEventListener('click', () => {
    const shown = pass.type === 'text';
    pass.type = shown ? 'password' : 'text';
    toggle.setAttribute('aria-pressed', String(!shown));
    toggle.setAttribute('aria-label', shown ? 'Mostrar contraseña' : 'Ocultar contraseña');
  });
})();
