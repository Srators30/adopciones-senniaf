/* =====================================================================
   SENNIAF · panama.js
   División política de Panamá: 10 provincias, 4 comarcas y sus 83
   distritos.

   Para qué sirve: antes la provincia y el distrito se escribían a mano
   en dos campos de texto. Eso dejaba entrar "Panama", "panamá",
   "Pma Oeste" y "San Migelito" como si fueran lugares distintos, y
   cualquier reporte agrupado por provincia salía mal. Con listas
   cerradas, el valor guardado es siempre el mismo texto.

   El distrito depende de la provincia: hasta que no se elige una,
   el selector de distrito está deshabilitado, y al cambiar de provincia
   se vuelve a llenar solo con los distritos que le corresponden.

   Esta tabla debería vivir en la base de datos cuando el sistema crezca
   (los distritos cambian por ley). Mientras sea un archivo, actualizarlo
   es cambiar este objeto y nada más.
   ===================================================================== */
(() => {

  const DIVISION = {
    'Bocas del Toro': ['Almirante', 'Bocas del Toro', 'Changuinola', 'Chiriquí Grande'],

    'Coclé': ['Aguadulce', 'Antón', 'La Pintada', 'Natá', 'Olá', 'Penonomé'],

    'Colón': ['Chagres', 'Colón', 'Donoso', 'Omar Torrijos Herrera', 'Portobelo', 'Santa Isabel'],

    'Chiriquí': ['Alanje', 'Barú', 'Boquerón', 'Boquete', 'Bugaba', 'David', 'Dolega',
                 'Gualaca', 'Remedios', 'Renacimiento', 'San Félix', 'San Lorenzo',
                 'Tierras Altas', 'Tolé'],

    'Darién': ['Chepigana', 'Pinogana', 'Santa Fe'],

    'Herrera': ['Chitré', 'Las Minas', 'Los Pozos', 'Ocú', 'Parita', 'Pesé', 'Santa María'],

    'Los Santos': ['Guararé', 'Las Tablas', 'Los Santos', 'Macaracas', 'Pedasí', 'Pocrí', 'Tonosí'],

    'Panamá': ['Balboa', 'Chepo', 'Chimán', 'Panamá', 'San Miguelito', 'Taboga'],

    'Panamá Oeste': ['Arraiján', 'Capira', 'Chame', 'La Chorrera', 'San Carlos'],

    'Veraguas': ['Atalaya', 'Calobre', 'Cañazas', 'La Mesa', 'Las Palmas', 'Mariato',
                 'Montijo', 'Río de Jesús', 'San Francisco', 'Santa Fe', 'Santiago', 'Soná'],

    'Comarca Emberá-Wounaan': ['Cémaco', 'Sambú'],

    'Comarca Guna Yala': ['Guna Yala'],

    'Comarca Naso Tjër Di': ['Naso Tjër Di'],

    'Comarca Ngäbe-Buglé': ['Besikó', 'Jirondai', 'Kankintú', 'Kusapín', 'Mironó', 'Müna',
                            'Nole Duima', 'Ñürüm', 'Santa Catalina (Calovébora)']
  };

  const PROVINCIAS = Object.keys(DIVISION);

  /** Vacía un <select> y le pone su opción inicial. */
  function reiniciar(select, etiqueta) {
    select.replaceChildren();
    const vacia = document.createElement('option');
    vacia.value = '';
    vacia.textContent = etiqueta;
    select.appendChild(vacia);
  }

  function agregar(select, valores) {
    valores.forEach(v => {
      const op = document.createElement('option');
      op.value = v;           // se guarda el nombre tal cual, no un código
      op.textContent = v;
      select.appendChild(op);
    });
  }

  const Panama = {

    provincias() { return PROVINCIAS.slice(); },

    distritos(provincia) { return (DIVISION[provincia] || []).slice(); },

    /**
     * Conecta los dos selectores.
     *
     * @param selProvincia  <select> de provincia
     * @param selDistrito   <select> de distrito
     * @param inicial       {provincia, distrito} para precargar (al editar)
     */
    enlazar(selProvincia, selDistrito, inicial) {
      if (!selProvincia || !selDistrito) return;

      reiniciar(selProvincia, 'Selecciona una provincia');
      agregar(selProvincia, PROVINCIAS);

      function llenarDistritos(provincia, preseleccion) {
        const lista = DIVISION[provincia] || [];

        if (lista.length === 0) {
          reiniciar(selDistrito, 'Elige primero la provincia');
          selDistrito.disabled = true;
          return;
        }

        reiniciar(selDistrito, 'Selecciona un distrito');
        agregar(selDistrito, lista);
        selDistrito.disabled = false;

        if (preseleccion && lista.indexOf(preseleccion) !== -1) {
          selDistrito.value = preseleccion;
        }
      }

      selProvincia.addEventListener('change', () => {
        /* Al cambiar de provincia el distrito anterior deja de ser
           válido, así que se descarta en vez de arrastrarlo. */
        llenarDistritos(selProvincia.value, null);
      });

      if (inicial && inicial.provincia && DIVISION[inicial.provincia]) {
        selProvincia.value = inicial.provincia;
        llenarDistritos(inicial.provincia, inicial.distrito);
      } else {
        llenarDistritos('', null);
      }
    },

    /** Llena un <select> suelto con las provincias (para los filtros). */
    llenarProvincias(select, etiqueta) {
      if (!select) return;
      reiniciar(select, etiqueta || 'Todas las provincias');
      agregar(select, PROVINCIAS);
    }
  };

  window.Panama = Panama;

  /* -------------------------------------------------------------------
     Conexión automática, para no repetir código en cada página.

       <select id="provincia_x" data-provincia="distrito_x"> ... </select>
       <select id="distrito_x"> ... </select>

     y para los filtros del listado:

       <select data-provincias-filtro>
     ------------------------------------------------------------------- */
  function autoconectar() {
    document.querySelectorAll('select[data-provincia]').forEach(sp => {
      const sd = document.getElementById(sp.dataset.provincia);
      if (sd) Panama.enlazar(sp, sd);
    });
    document.querySelectorAll('select[data-provincias-filtro]').forEach(s => {
      Panama.llenarProvincias(s, s.dataset.provinciasFiltro || 'Todas las provincias');
    });
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', autoconectar);
  } else {
    autoconectar();
  }
})();
