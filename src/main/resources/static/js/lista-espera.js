/* =====================================================================
   SENNIAF · lista-espera.js  (Sprint 7 · HU-06.3)
   Muestra la lista de espera tal como la ordena el servidor. Esta
   página no reordena nada: si el orden se calculara aquí, dos personas
   podrían ver listas distintas.

   Todo con textContent: los nombres los escribió una persona.
   ===================================================================== */
(() => {
  const sesion = Sesion.exigir();
  if (!sesion) return;
  Sesion.pintarBarra();

  const cuerpo = document.getElementById('filas');
  const vacio = document.getElementById('vacio');
  const resumen = document.getElementById('resumen');
  const banner = document.getElementById('banner-error');
  const textoBanner = document.getElementById('banner-error-text');

  const MODALIDAD = {
    adopcion: 'Adopción',
    acogimiento_familiar: 'Acogimiento familiar'
  };

  function celda(texto, clase) {
    const td = document.createElement('td');
    td.textContent = texto == null || texto === '' ? '—' : texto;
    if (clase) td.className = clase;
    return td;
  }

  function dias(n) {
    return n === 1 ? '1 día' : n + ' días';
  }

  function fila(f) {
    const tr = document.createElement('tr');

    const tdPos = document.createElement('td');
    tdPos.className = 'col-posicion';
    const pos = document.createElement('span');
    pos.className = 'posicion';
    pos.textContent = f.posicion;
    tdPos.appendChild(pos);

    const tdNum = document.createElement('td');
    tdNum.className = 'num';
    const enlace = document.createElement('a');
    enlace.href = 'familia_detalle.html?n=' + encodeURIComponent(f.numeroSolicitud);
    enlace.textContent = f.numeroSolicitud;
    tdNum.appendChild(enlace);

    tr.append(
      tdPos,
      tdNum,
      celda(f.familia),
      celda(MODALIDAD[f.modalidad] || f.modalidad),
      celda(f.fechaIdoneidad),
      celda(dias(f.diasEnEspera), 'col-dias')
    );
    return tr;
  }

  async function cargar() {
    try {
      const r = await Sesion.pedir('/api/familias/lista-espera');
      if (!r.ok) {
        resumen.textContent = '';
        textoBanner.textContent = 'No se pudo cargar la lista de espera (' + r.status + ').';
        banner.hidden = false;
        return;
      }
      const datos = await r.json();
      cuerpo.replaceChildren();
      datos.familias.forEach(f => cuerpo.appendChild(fila(f)));
      vacio.hidden = datos.total > 0;
      resumen.textContent = (datos.total === 1 ? '1 familia en espera' : datos.total + ' familias en espera')
        + ' · consultado el ' + datos.fechaConsulta;
    } catch (err) {
      if (err.message !== 'sesion') {
        resumen.textContent = '';
        textoBanner.textContent = 'No se pudo conectar con el servidor.';
        banner.hidden = false;
      }
    }
  }

  cargar();
})();
