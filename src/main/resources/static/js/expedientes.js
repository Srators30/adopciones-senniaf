/* =====================================================================
   SENNIAF · expedientes.js  (HU-03)
   Listado con filtros. Pide los datos a GET /api/expedientes con el
   token de la sesión; quien decide qué se puede ver es el backend.
   ===================================================================== */
(() => {
  const sesion = Sesion.exigir();          // los tres roles pueden consultar
  if (!sesion) return;
  Sesion.pintarBarra();

  /* Registrar menor solo lo ven quienes pueden escribir */
  if (['ADMINISTRADOR', 'TECNICO'].indexOf(sesion.rol) !== -1) {
    document.getElementById('btn-nuevo').hidden = false;
  }

  const form = document.getElementById('form-filtros');
  const filas = document.getElementById('filas');
  const vacio = document.getElementById('vacio');
  const resumen = document.getElementById('resumen');
  const bannerError = document.getElementById('banner-error');
  const textoError = document.getElementById('banner-error-text');
  const btnAnterior = document.getElementById('btn-anterior');
  const btnSiguiente = document.getElementById('btn-siguiente');
  const indicador = document.getElementById('indicador-pagina');

  const TAMANO = 10;
  let pagina = 0;
  let totalPaginas = 1;

  const ETIQUETA_ESTADO = {
    REGISTRADO: 'Registrado',
    EN_REVISION: 'En revisión',
    CERRADO: 'Cerrado'
  };
  const ETIQUETA_MODALIDAD = {
    centro_acogimiento: 'Centro de acogimiento',
    familia_acogida: 'Familia de acogida',
    familiar: 'Acogimiento con un familiar',
    hospital: 'Hospital o centro de salud',
    otra: 'Otra'
  };

  function fallar(msg) {
    textoError.textContent = msg;
    bannerError.hidden = false;
    resumen.textContent = '';
  }

  /* Los datos vienen del servidor: se insertan como texto, nunca como
     HTML, para que un nombre con < o > no pueda inyectar marcado. */
  function celda(texto, clase) {
    const td = document.createElement('td');
    if (clase) td.className = clase;
    td.textContent = texto || '—';
    return td;
  }

  function fila(exp) {
    const tr = document.createElement('tr');

    const tdNum = document.createElement('td');
    tdNum.className = 'num';
    const enlace = document.createElement('a');
    enlace.href = 'expediente_detalle.html?n=' + encodeURIComponent(exp.numeroExpediente);
    enlace.textContent = exp.numeroExpediente;
    tdNum.appendChild(enlace);
    tr.appendChild(tdNum);

    tr.appendChild(celda(exp.nombreCompleto));
    tr.appendChild(celda(exp.edad));
    tr.appendChild(celda(ETIQUETA_MODALIDAD[exp.modalidadAcogida] || exp.modalidadAcogida));

    const tdEstado = document.createElement('td');
    const pill = document.createElement('span');
    pill.className = 'pill ' + String(exp.estado || '').toLowerCase();
    pill.textContent = ETIQUETA_ESTADO[exp.estado] || exp.estado;
    tdEstado.appendChild(pill);
    tr.appendChild(tdEstado);

    tr.appendChild(celda((exp.fechaCreacion || '').slice(0, 10) + ' · ' + exp.creadoPor));
    return tr;
  }

  async function cargar() {
    bannerError.hidden = true;
    resumen.textContent = 'Buscando…';

    const parametros = new URLSearchParams({
      q: form.q.value.trim(),
      estado: form.estado.value,
      modalidad: form.modalidad.value,
      pagina: String(pagina),
      tamano: String(TAMANO)
    });

    try {
      const r = await Sesion.pedir('/api/expedientes?' + parametros.toString());
      if (r.status === 403) { fallar('Tu rol no tiene permiso para consultar expedientes.'); return; }
      if (!r.ok) { fallar('El servidor respondió con un error (' + r.status + ').'); return; }

      const datos = await r.json();
      filas.replaceChildren();
      datos.contenido.forEach(exp => filas.appendChild(fila(exp)));

      const hay = datos.contenido.length > 0;
      vacio.hidden = hay;
      totalPaginas = Math.max(datos.totalPaginas, 1);

      resumen.textContent = datos.total === 0
        ? 'Sin resultados'
        : datos.total + (datos.total === 1 ? ' expediente encontrado' : ' expedientes encontrados');

      indicador.textContent = 'Página ' + (datos.pagina + 1) + ' de ' + totalPaginas;
      btnAnterior.disabled = datos.pagina <= 0;
      btnSiguiente.disabled = datos.pagina + 1 >= totalPaginas;

    } catch (err) {
      if (err.message !== 'sesion') fallar('No se pudo conectar con el servidor.');
    }
  }

  form.addEventListener('submit', e => { e.preventDefault(); pagina = 0; cargar(); });

  document.getElementById('btn-limpiar').addEventListener('click', () => {
    form.reset();
    pagina = 0;
    cargar();
  });

  btnAnterior.addEventListener('click', () => { if (pagina > 0) { pagina--; cargar(); } });
  btnSiguiente.addEventListener('click', () => { if (pagina + 1 < totalPaginas) { pagina++; cargar(); } });

  cargar();
})();
