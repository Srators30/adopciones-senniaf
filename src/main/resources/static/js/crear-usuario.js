/* =====================================================================
   SENNIAF · crear-usuario.js
   Validación de ayuda para quien administra. El servidor (PHP) SIEMPRE
   debe volver a validar todo, incluida la unicidad del usuario.
   ===================================================================== */
(() => {
  const form = document.getElementById('form-usuario');
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];

  const USERNAME = /^[a-z0-9._]{4,40}$/i;
  const rules = {
    username: el => USERNAME.test(el.value.trim())
      ? null
      : 'Usa 4 a 40 caracteres: letras, números, puntos o guion bajo, sin espacios.',
    password(el) {
      const v = el.value;
      if (v.length < 8) return 'Usa al menos 8 caracteres.';
      if (!/[A-Z]/.test(v)) return 'Incluye al menos una mayúscula.';
      if (!/[0-9]/.test(v)) return 'Incluye al menos un número.';
      if (!/[^A-Za-z0-9]/.test(v)) return 'Incluye al menos un símbolo.';
      return null;
    },
    password_confirmar: el => el.value === form.password.value ? null : 'Las contraseñas no coinciden.'
  };

  function check(el) {
    if (el.disabled) return '';
    if (el.type === 'checkbox') return el.required && !el.checked ? 'Debes confirmar esta declaración.' : '';
    if (el.type === 'radio') {
      return el.required && !$('[name="' + el.name + '"]:checked', form) ? 'Selecciona una opción.' : '';
    }
    if (el.required && !el.value.trim()) return 'Este campo es obligatorio.';
    if (!el.value.trim()) return '';
    if (rules[el.name]) { const m = rules[el.name](el); if (m) return m; }
    return '';
  }

  function setError(el, msg) {
    const box = el.closest('.field');
    if (!box) return;
    const aviso = $('.error', box);
    if (aviso) aviso.textContent = msg;
    box.classList.toggle('invalid', !!msg);
    $$('[name="' + el.name + '"]', form).forEach(c => c.setAttribute('aria-invalid', msg ? 'true' : 'false'));
  }

  const controls = () => $$('input, select', form).filter(el =>
    !el.disabled && el.type !== 'hidden' &&
    !(el.type === 'radio' && $$('[name="' + el.name + '"]', form).indexOf(el) > 0));

  /* Medidor de fortaleza */
  const bar = $('#strength-bar');
  function pintarFortaleza() {
    const v = form.password.value;
    let level = 0;
    if (v.length >= 8) level++;
    if (/[A-Z]/.test(v) && /[a-z]/.test(v)) level++;
    if (/[0-9]/.test(v)) level++;
    if (/[^A-Za-z0-9]/.test(v)) level++;
    bar.dataset.level = v ? level : 0;
  }

  form.addEventListener('input', e => {
    if (e.target.name === 'password') pintarFortaleza();
    if (e.target.closest('.invalid')) setError(e.target, check(e.target));
    if (e.target.name === 'password' && form.password_confirmar.value) {
      setError(form.password_confirmar, check(form.password_confirmar));
    }
  });
  form.addEventListener('change', e => setError(e.target, check(e.target)));

  form.addEventListener('submit', e => {
    const resumen = $('#resumen-errores'), lista = $('ul', resumen);
    lista.innerHTML = '';
    let primero = null;

    controls().forEach(el => {
      const msg = check(el);
      setError(el, msg);
      if (msg) {
        primero = primero || el;
        const box = el.closest('.field');
        const nombre = $('label, legend', box).textContent.replace('*', '').trim();
        const li = document.createElement('li'), a = document.createElement('a');
        a.href = '#' + el.id; a.textContent = nombre + ': ' + msg;
        a.addEventListener('click', ev => { ev.preventDefault(); el.focus(); el.scrollIntoView({ block: 'center' }); });
        li.appendChild(a); lista.appendChild(li);
      }
    });

    if (primero) {
      e.preventDefault();
      resumen.hidden = false;
      resumen.focus();
      resumen.scrollIntoView({ block: 'start' });
      return;
    }
    resumen.hidden = true;
    const b = $('#btn-guardar'); b.disabled = true; b.textContent = 'Guardando…';
  });

  /* Mostrar / ocultar contraseña en ambos campos */
  $$('.password-toggle').forEach(btn => {
    const input = document.getElementById(btn.dataset.target);
    btn.addEventListener('click', () => {
      const shown = input.type === 'text';
      input.type = shown ? 'password' : 'text';
      btn.setAttribute('aria-pressed', String(!shown));
      btn.setAttribute('aria-label', shown ? 'Mostrar contraseña' : 'Ocultar contraseña');
    });
  });

  pintarFortaleza();
})();
