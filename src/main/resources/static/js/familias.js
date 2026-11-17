/* =====================================================================
   SENNIAF · familias.js  (HU-05)
   Listado de familias solicitantes con filtros y paginación.

   Todo el DOM se arma con textContent, nunca con innerHTML: los datos
   vienen de lo que escribió una persona en un formulario y no se pueden
   tratar como HTML de confianza.
   ===================================================================== */
(() => {
  const sesion = Sesion.exigir();
  if (!sesion) return;
  Sesion.pintarBarra();

  /* Registrar es escribir: el botón solo aparece para quien puede.
     Quien bloquea de verdad es el backend, que responde 403. */
  if (['ADMINISTRADOR', 'TECNICO'].indexOf(sesion.rol) !== -1) {
    document.getElementById('btn-nueva').hidden = false;
  }

  const form = document.getElementById('form-filtros');
  const cuerpo = document.getElementById('filas');
  const vacio = document.getElementById('vacio');
  const resumen = document.getElementById('resumen');
  const banner = document.getElementById('banner-error');
  const textoBanner = document.getElementById('banner-error-text');
  const btnAnterior = document.getElementById('btn-anterior');
  const btnSiguiente = document.getElementById('btn-siguiente');
  const indicador = document.getElementById('indicador-pagina');

  const TAMANO = 10;
  let pagina = 0;
  let totalPaginas = 1;

  const ESTADO = {
    REGISTRADA: 'Registrada',
    EN_EVALUACION: 'En evaluación',
    IDONEA: 'Idónea',          // Sprint 7: los asigna el dictamen
    NO_IDONEA: 'No idónea',
    CERRADA: 'Cerrada'
  };

  const MODALIDAD = {
    adopcion: 'Adopción',
    acogimiento_familiar: 'Acogimiento familiar'
  };

  function fallar(msg) {
    textoBanner.textContent = msg;
    banner.hidden = false;
  }

  function celda(texto, clase) {
    const td = document.createElement('td');
    if (clase) td.className = clase;
    td.textContent = texto || '—';
    return td;
  }

  function fila(f) {
    const tr = document.createElement('tr');

    const tdNum = document.createElement('td');
    tdNum.className = 'num';
    const enlace = document.createElement('a');
    enlace.href = 'familia_detalle.html?n=' + encodeURIComponent(f.numeroSolicitud);
    enlace.textContent = f.numeroSolicitud;
    tdNum.appendChild(enlace);

    const tdFamilia = document.createElement('td');
    tdFamilia.textContent = f.nombreFamilia;
    if (f.integrantes === 2) {
      const marca = document.createElement('span');
      marca.className = 'pill';
      marca.style.marginLeft = '8px';
      marca.style.background = 'var(--paper)';
      marca.style.color = 'var(--muted)';
      marca.textContent = '2 solicitantes';
      tdFamilia.appendChild(marca);
    }

    const residencia = [f.distritoResidencia, f.provinciaResidencia]
      .filter(Boolean).join(', ');

    const tdEstado = document.createElement('td');
    const pill = document.createElement('span');
    pill.className = 'pill ' + String(f.estado || '').toLowerCase();
    pill.textContent = ESTADO[f.estado] || f.estado;
    tdEstado.appendChild(pill);

    tr.append(
      tdNum,
      tdFamilia,
      celda(MODALIDAD[f.modalidadSolicitada] || f.modalidadSolicitada),
      celda(residencia),
      celda(f.rangoEdad),
      tdEstado,
      celda(f.fechaCreacion)
    );
    return tr;
  }

  async function cargar() {
    banner.hidden = true;
    resumen.textContent = 'Cargando…';

    const p = new URLSearchParams();
    const q = form.q.value.trim();
    if (q) p.set('q', q);
    if (form.estado.value) p.set('estado', form.estado.value);
    if (form.modalidad.value) p.set('modalidad', form.modalidad.value);
    if (form.provincia.value) p.set('provincia', form.provincia.value);
    p.set('pagina', pagina);
    p.set('tamano', TAMANO);

    try {
      const r = await Sesion.pedir('/api/familias?' + p.toString());
      if (!r.ok) {
        resumen.textContent = '';
        fallar('No se pudo cargar el listado (' + r.status + ').');
        return;
      }

      const datos = await r.json();
      cuerpo.replaceChildren();
      datos.contenido.forEach(f => cuerpo.appendChild(fila(f)));

      vacio.hidden = datos.contenido.length > 0;
      totalPaginas = Math.max(datos.totalPaginas, 1);

      resumen.textContent = datos.total === 1
        ? '1 familia encontrada'
        : datos.total + ' familias encontradas';

      indicador.textContent = 'Página ' + (datos.pagina + 1) + ' de ' + totalPaginas;
      btnAnterior.disabled = datos.pagina <= 0;
      btnSiguiente.disabled = datos.pagina + 1 >= totalPaginas;

    } catch (err) {
      if (err.message !== 'sesion') {
        resumen.textContent = '';
        fallar('No se pudo conectar con el servidor.');
      }
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
