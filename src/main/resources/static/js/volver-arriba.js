/* =====================================================================
   SENNIAF · volver-arriba.js
   Dos formas de volver al inicio, para formularios largos:

     1. Un botón flotante que aparece al bajar y devuelve al principio
        de la página.
     2. Un enlace "Volver al inicio" en el encabezado, que lleva a la
        pantalla de bienvenida.

   Se instala solo: basta agregar esta línea a cualquier página, antes
   del cierre de <body>, y no hace falta tocar el CSS ni el HTML.

       <script src="js/volver-arriba.js"></script>

   El botón respeta "reducir movimiento" del sistema operativo: si la
   persona lo tiene activado, el salto es instantáneo en vez de animado.
   ===================================================================== */
(() => {
  const APARECE_DESDE = 320;   // píxeles de scroll antes de mostrarlo

  /* ---------- 1. Botón flotante ---------- */

  const estilo = document.createElement('style');
  estilo.textContent = `
    .volver-arriba {
      position: fixed; right: 20px; bottom: 20px; z-index: 60;
      display: inline-flex; align-items: center; gap: 8px;
      min-height: 44px; padding: 10px 16px;
      font: 700 .875rem/1 inherit; font-family: inherit;
      color: #fff; background: #1B4B9B;
      border: none; border-radius: 24px; cursor: pointer;
      box-shadow: 0 4px 14px rgba(21, 34, 56, .28);
      opacity: 0; visibility: hidden; transform: translateY(12px);
      transition: opacity .18s ease, transform .18s ease, visibility .18s;
    }
    .volver-arriba.visible { opacity: 1; visibility: visible; transform: none; }
    .volver-arriba:hover { background: #12356F; }
    .volver-arriba:focus-visible { outline: 3px solid #12356F; outline-offset: 3px; }
    .volver-arriba svg { flex: none; }
    @media (max-width: 480px) {
      .volver-arriba { right: 12px; bottom: 12px; padding: 10px 14px; }
      .volver-arriba span { display: none; }   /* en móvil solo la flecha */
    }
    @media (prefers-reduced-motion: reduce) {
      .volver-arriba { transition: none; }
    }
    .volver-inicio {
      display: block; margin-top: 4px;
      font-size: .8125rem; color: #1B4B9B; text-decoration: underline;
    }
  `;
  document.head.appendChild(estilo);

  const boton = document.createElement('button');
  boton.type = 'button';
  boton.className = 'volver-arriba';
  boton.setAttribute('aria-label', 'Volver al inicio de la página');
  boton.innerHTML =
    '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" ' +
    'stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
    '<path d="M12 19V5M5 12l7-7 7 7"/></svg><span>Volver arriba</span>';

  boton.addEventListener('click', () => {
    const sinMovimiento = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    window.scrollTo({ top: 0, behavior: sinMovimiento ? 'auto' : 'smooth' });

    /* Devolver el foco al principio además de la vista: si solo se
       mueve el scroll, quien navega con teclado sigue donde estaba. */
    const primero = document.querySelector('h1');
    if (primero) {
      primero.setAttribute('tabindex', '-1');
      primero.focus({ preventScroll: true });
    }
  });

  document.body.appendChild(boton);

  let pendiente = false;
  function revisar() {
    pendiente = false;
    boton.classList.toggle('visible', window.scrollY > APARECE_DESDE);
  }
  window.addEventListener('scroll', () => {
    /* requestAnimationFrame evita recalcular en cada píxel de scroll. */
    if (!pendiente) { pendiente = true; requestAnimationFrame(revisar); }
  }, { passive: true });
  revisar();

  /* ---------- 2. Enlace a la pantalla de bienvenida ---------- */

  const caja = document.querySelector('.topbar .user');
  if (caja && !/bienvenida\.html$/.test(location.pathname)) {
    const enlace = document.createElement('a');
    enlace.className = 'volver-inicio';
    enlace.href = 'bienvenida.html';
    enlace.textContent = '← Volver al inicio';
    caja.appendChild(enlace);
  }
})();
