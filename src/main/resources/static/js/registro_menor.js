/* =====================================================================
   SENNIAF · registro_menor.js
   Validación de ayuda para HU-02. El servidor (PHP) SIEMPRE debe volver
   a validar todo antes de guardar.
   ===================================================================== */
(() => {
  const form = document.getElementById('form-menor');
  const $ = (s, r = document) => r.querySelector(s);
  const $$ = (s, r = document) => [...r.querySelectorAll(s)];
  const val = n => { const e = form.elements[n]; return e ? e.value : ''; };

  /* Fechas */
  const today = new Date(); today.setHours(0, 0, 0, 0);
  const iso = d => d.toISOString().slice(0, 10);
  const parse = s => new Date(s + 'T00:00:00');
  form.fecha_nacimiento.max = iso(today);
  form.fecha_ingreso.max = iso(today);
  $('#fecha-hoy').textContent = today.toLocaleDateString('es-PA', { day: 'numeric', month: 'long', year: 'numeric' });

  function edad(d) {
    let y = today.getFullYear() - d.getFullYear();
    let m = today.getMonth() - d.getMonth();
    if (today.getDate() < d.getDate()) m--;
    if (m < 0) { y--; m += 12; }
    return { y, m };
  }

  /* Reglas específicas (mensaje de error o null) */
  const NOMBRE = /^[\p{L}][\p{L}\s'’.\-]*$/u;      // ajustar si el equipo define otro formato
  const DOC = /^[A-Za-z0-9\-]{5,20}$/;              // ajustar a los formatos oficiales
  const TEL = /^[0-9+\s\-()]{7,20}$/;
  const rules = {
    nombres: el => NOMBRE.test(el.value.trim()) ? null : 'Usa solo letras, espacios, apóstrofes o guiones.',
    apellidos: el => NOMBRE.test(el.value.trim()) ? null : 'Usa solo letras, espacios, apóstrofes o guiones.',
    fecha_nacimiento(el) {
      if (!el.value) return null;
      const d = parse(el.value);
      if (d > today) return 'La fecha de nacimiento no puede ser futura.';
      if (edad(d).y >= 18) return 'La persona registrada debe ser menor de 18 años.';
      return null;
    },
    fecha_ingreso(el) {
      if (!el.value) return null;
      const d = parse(el.value);
      if (d > today) return 'La fecha de ingreso no puede ser futura.';
      if (form.fecha_nacimiento.value && d < parse(form.fecha_nacimiento.value)) return 'El ingreso no puede ser anterior al nacimiento.';
      return null;
    },
    numero_documento: el => DOC.test(el.value.trim()) ? null : 'Usa 5 a 20 letras, números o guiones.',
    familiar_telefono: el => !el.value.trim() || TEL.test(el.value.trim()) ? null : 'Escribe un teléfono válido (7 a 20 dígitos).'
  };

  function check(el) {
    if (el.disabled) return '';
    const isText = /^(text|tel|number|date)$/.test(el.type) || el.tagName === 'TEXTAREA';
    const empty = isText ? !el.value.trim() : false;
    if (el.required && (el.validity.valueMissing || empty)) {
      if (el.type === 'checkbox') return 'Debes confirmar esta declaración.';
      if (el.type === 'radio' || el.tagName === 'SELECT') return 'Selecciona una opción.';
      return 'Este campo es obligatorio.';
    }
    if (empty) return '';
    if (rules[el.name]) { const m = rules[el.name](el); if (m) return m; }
    if (el.validity.rangeUnderflow || el.validity.rangeOverflow) return 'Escribe un número entre ' + el.min + ' y ' + el.max + '.';
    return '';
  }

  function setError(el, msg) {
    const box = el.closest('.field');
    if (!box) return;
    /* Corregido: #g-corregimiento y #g-hermanos-ref no tienen <p class="error">,
       asi que esta linea reventaba al ocultar esos grupos al cargar la pagina. */
    const aviso = $('.error', box);
    if (aviso) aviso.textContent = msg;
    box.classList.toggle('invalid', !!msg);
    $$('[name="' + el.name + '"]', form).forEach(c => c.setAttribute('aria-invalid', msg ? 'true' : 'false'));
  }

  const controls = () => $$('input, select, textarea', form).filter(el =>
    !el.disabled && el.type !== 'hidden' && !(el.type === 'radio' && $$('[name="' + el.name + '"]', form).indexOf(el) > 0));

  /* Campos condicionales: al ocultarlos se deshabilitan y no viajan en el POST */
  const conditions = [
    ['#g-documento', () => val('tipo_documento') !== 'sin_documento'],
    ['#g-nacionalidad-otra', () => val('nacionalidad') === 'otra'],
    ['#g-distrito', () => !['', 'EXTERIOR', 'DESCONOCIDO'].includes(val('provincia_nacimiento'))],
    ['#g-corregimiento', () => !['', 'EXTERIOR', 'DESCONOCIDO'].includes(val('provincia_nacimiento'))],
    ['#g-pais', () => val('provincia_nacimiento') === 'EXTERIOR'],
    ['#g-hermanos-n', () => val('hermanos') === 'si'],
    ['#g-hermanos-ref', () => val('hermanos') === 'si'],
    ['#g-necesidades', () => val('necesidades_especiales') === 'si']
  ];
  function applyConditions() {
    conditions.forEach(([sel, show]) => {
      const g = $(sel);
      /* Corregido: #g-nacionalidad-otra y #g-pais no existen en el HTML
         actual. Sin esta guarda, applyConditions lanzaba un error y el
         resto de la inicializacion (progreso, campos condicionales) no
         llegaba a ejecutarse. */
      if (!g) return;
      const on = show();
      g.hidden = !on;
      $$('input, select, textarea', g).forEach(el => {
        el.disabled = !on;
        if (!on) { el.value = ''; setError(el, ''); }
      });
    });
  }

  /* Edad calculada */
  function pintarEdad() {
    const out = $('#edad-out'), v = form.fecha_nacimiento.value;
    if (!v || rules.fecha_nacimiento(form.fecha_nacimiento)) { out.textContent = ''; return; }
    const { y, m } = edad(parse(v));
    out.textContent = 'Edad: ' + y + (y === 1 ? ' año' : ' años') + (m ? ', ' + m + (m === 1 ? ' mes' : ' meses') : '');
  }

  /* Contadores de caracteres */
  $$('.count').forEach(c => {
    const t = form.elements[c.dataset.for];
    const upd = () => { c.textContent = t.value.length + ' / ' + t.maxLength; };
    t.addEventListener('input', upd); upd();
  });

  /* Progreso por sección */
  function progreso() {
    $$('.sec').forEach(sec => {
      const ok = $$('input, select, textarea', sec).filter(e => !e.disabled)
        .every(e => !check(e) && (!e.required || (e.type === 'radio' ? !!$('[name="' + e.name + '"]:checked', form) : e.type === 'checkbox' ? e.checked : e.value.trim())));
      $('.rail a[href="#' + sec.id + '"]').classList.toggle('done', ok);
    });
  }
  const links = $$('.rail a');
  const io = new IntersectionObserver(es => es.forEach(e => {
    if (e.isIntersecting) links.forEach(a => a.classList.toggle('current', a.getAttribute('href') === '#' + e.target.id));
  }), { rootMargin: '-20% 0px -60% 0px' });
  $$('.sec').forEach(s => io.observe(s));

  /* Eventos */
  let dirty = false;
  form.addEventListener('input', e => {
    dirty = true;
    if (e.target.closest('.invalid')) setError(e.target, check(e.target));
    if (e.target.name === 'fecha_nacimiento') pintarEdad();
    progreso();
  });
  form.addEventListener('change', e => {
    applyConditions();
    if (e.target.name === 'fecha_nacimiento') { pintarEdad(); if (form.fecha_ingreso.value) setError(form.fecha_ingreso, check(form.fecha_ingreso)); }
    setError(e.target, check(e.target));
    progreso();
  });
  form.addEventListener('focusout', e => {
    if (e.target.matches('input, select, textarea') && e.target.type !== 'radio' && e.target.type !== 'checkbox') {
      if (e.target.value.trim() || e.target.closest('.invalid')) setError(e.target, check(e.target));
    }
  });

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
    dirty = false;
    const b = $('#btn-guardar'); b.disabled = true; b.textContent = 'Guardando…';
  });

  window.addEventListener('beforeunload', e => { if (dirty) { e.preventDefault(); e.returnValue = ''; } });

  applyConditions();
  progreso();
})();
