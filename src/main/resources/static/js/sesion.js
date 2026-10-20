/* =====================================================================
   SENNIAF · sesion.js
   Reemplaza lo que antes hacía la sesión de PHP: saber quién entró,
   pintarlo en pantalla y cerrar sesión.

   El token se guarda en sessionStorage: vive mientras la pestaña esté
   abierta y se borra al cerrarla.

   Todo lo de aquí es comodidad para el usuario. La seguridad real la
   aplica el backend, que valida la firma del token y el rol en cada
   petición: una pantalla escondida no protege nada por sí sola.
   ===================================================================== */
(() => {
  const CLAVE = 'senniaf_token';
  const LOGIN = 'login.html';

  function leer() {
    try { return sessionStorage.getItem(CLAVE); } catch (e) { return null; }
  }

  /* Lee el contenido del token (no lo valida: eso ya lo firmó el servidor). */
  function contenido() {
    const t = leer();
    if (!t) return null;
    try {
      const b = t.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const bin = atob(b.padEnd(b.length + (4 - b.length % 4) % 4, '='));
      return JSON.parse(new TextDecoder().decode(Uint8Array.from(bin, c => c.charCodeAt(0))));
    } catch (e) { return null; }
  }

  const ETIQUETA = {
    ADMINISTRADOR: 'Administrador',
    TECNICO: 'Técnico/Trabajador',
    JUEZ: 'Juez'
  };

  const Sesion = {
    guardar(token, nombre) {
      try {
        sessionStorage.setItem(CLAVE, token);
        if (nombre) sessionStorage.setItem('senniaf_nombre', nombre);
      } catch (e) {}
    },

    token: leer,
    datos: contenido,
    usuario() { const d = contenido(); return d ? d.sub : null; },
    rol() { const d = contenido(); return d ? d.rol : null; },
    nombre() {
      try { return sessionStorage.getItem('senniaf_nombre') || this.usuario(); }
      catch (e) { return this.usuario(); }
    },

    vencida() {
      const d = contenido();
      return !d || !d.exp || d.exp * 1000 <= Date.now();
    },

    cerrar(motivo) {
      try { sessionStorage.clear(); } catch (e) {}
      location.href = motivo ? LOGIN + '?error=' + motivo : LOGIN;
    },

    /* Guarda de página: sin sesión válida, al login. Con roles, exige uno. */
    exigir(roles) {
      if (this.vencida()) { this.cerrar('sesion'); return null; }
      const d = contenido();
      if (roles && roles.indexOf(d.rol) === -1) {
        document.body.innerHTML =
          '<main style="max-width:640px;margin:0 auto;padding:60px 24px;font-family:system-ui">' +
          '<h1 style="font-size:1.5rem">Sin permiso</h1>' +
          '<p>Tu rol (' + (ETIQUETA[d.rol] || d.rol) + ') no tiene acceso a esta pantalla.</p>' +
          '<p><a href="bienvenida.html">Volver al inicio</a></p></main>';
        return null;
      }
      return d;
    },

    /* fetch con el token puesto. 401 = sesión vencida -> al login. */
    async pedir(url, opciones) {
      const op = Object.assign({}, opciones);
      op.headers = Object.assign({}, op.headers);
      const t = leer();
      if (t) op.headers['Authorization'] = 'Bearer ' + t;
      /* Con FormData (subida de archivos) NO se pone Content-Type: el
         navegador tiene que generarlo con el boundary del multipart. */
      if (op.body && !op.headers['Content-Type'] && !(op.body instanceof FormData)) {
        op.headers['Content-Type'] = 'application/json';
      }

      const r = await fetch(url, op);
      if (r.status === 401) { Sesion.cerrar('sesion'); throw new Error('sesion'); }
      return r;
    },

    pintarBarra() {
      const d = contenido();
      if (!d) return;
      const nom = document.getElementById('sesion-nombre');
      const rol = document.getElementById('sesion-rol');
      const salir = document.getElementById('btn-salir');
      const saludo = document.getElementById('saludo-nombre');
      const resp = document.getElementById('responsable-out');
      if (nom) nom.textContent = this.nombre();
      if (saludo) saludo.textContent = this.nombre();
      if (resp) resp.textContent = this.nombre();
      if (rol) rol.textContent = ETIQUETA[d.rol] || d.rol;
      if (salir) salir.addEventListener('click', e => { e.preventDefault(); Sesion.cerrar(); });
    },

    /* Muestra los elementos cuyo data-roles incluya el rol del token. */
    mostrarPorRol() {
      const r = this.rol();
      document.querySelectorAll('[data-roles]').forEach(el => {
        el.hidden = el.dataset.roles.split(',').indexOf(r) === -1;
      });
    }
  };

  window.Sesion = Sesion;
})();
