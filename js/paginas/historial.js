// CU-05 y CU-08: búsqueda protegida del historial. El servidor aplica los
// límites del rol (días que se pueden consultar, visitas propias, datos ocultos).

import { api } from '../api.js'
import { alerta, boton, campo, marcarErrores, modalDetalle, opciones, tablaVisitas, tituloPagina, vacio } from '../ui.js'
import { fechaCampo, fechaCorta } from '../formato.js'
import { pedirSalida } from './activas.js'

export async function paginaHistorial(main, ctx) {
  const anfitrion = !ctx.permisos.verDocumento
  const recepcionista = ctx.usuario.rol === 'RECEPCIONISTA'
  const catalogos = await ctx.catalogos()
  const hoy = fechaCampo(ctx.sesion.ahora)

  const avisoPermisos = anfitrion
    ? alerta('warning', 'Consulta limitada por permisos', 'Solo ves las visitas en las que eres el anfitrión. El documento de los visitantes permanece oculto.')
    : recepcionista
      ? alerta('warning', `Consulta limitada a los últimos ${ctx.sesion.diasHistorial} días`, `Tu rol consulta el historial reciente. Las visitas finalizadas se conservan ${ctx.sesion.retencionDias} días según la política de retención.`)
      : ''

  const campos = [
    campo({ etiqueta: 'Desde', nombre: 'desde', control: `<input type="date" name="desde" max="${hoy}">` }),
    campo({ etiqueta: 'Hasta', nombre: 'hasta', control: `<input type="date" name="hasta" max="${hoy}">` }),
    campo({ etiqueta: 'Visitante', nombre: 'texto', control: '<input name="texto" placeholder="Nombre o identificación" maxlength="100">' }),
    anfitrion ? '' : campo({ etiqueta: 'Anfitrión', nombre: 'anfitrionId',
      control: `<select name="anfitrionId">${opciones(catalogos.anfitriones.map((a) => ({ valor: a.id, texto: a.nombre })), '', 'Todos los anfitriones')}</select>` }),
    campo({ etiqueta: 'Área', nombre: 'areaId',
      control: `<select name="areaId">${opciones(catalogos.areas.map((a) => ({ valor: a.id, texto: a.nombre })), '', 'Todas las áreas')}</select>` }),
    campo({ etiqueta: 'Estado', nombre: 'estado',
      control: `<select name="estado">${opciones([{ valor: 'FINALIZADA', texto: 'Finalizada' }, { valor: 'ACTIVA', texto: 'Activa' }], '', 'Todos')}</select>` }),
  ].join('')

  main.innerHTML = tituloPagina({
    kicker: 'RF-05 · RF-08',
    titulo: anfitrion ? 'Mis visitas autorizadas' : 'Historial de visitas',
    texto: anfitrion ? 'Solo puedes consultar visitas asociadas a tu perfil.' : 'Consulta protegida de ingresos y salidas anteriores.',
  }) + avisoPermisos
    + `<form class="filter-panel" novalidate><div class="filter-grid${anfitrion ? '' : ' seis'}">${campos}</div><div class="filter-actions">${boton('Limpiar filtros', { variante: 'ghost', atributos: 'data-limpiar' })}${boton('Buscar', { tipo: 'submit', icon: 'search' })}</div></form>`
    + `<section class="panel"><div class="panel-head"><div><h2>Resultados</h2><p data-resumen>Aplica filtros para acotar la consulta.</p></div></div><div data-resultados></div></section>`

  const form = main.querySelector('form')
  let visitas = []

  const buscar = async (porUsuario) => {
    const f = form.elements
    const filtros = {
      desde: f.desde.value,
      hasta: f.hasta.value,
      texto: f.texto.value,
      anfitrionId: f.anfitrionId?.value,
      areaId: f.areaId.value,
      estado: f.estado.value,
    }
    if (filtros.desde && filtros.hasta && filtros.hasta < filtros.desde) {
      marcarErrores(form, { hasta: 'La fecha final debe ser igual o posterior a la inicial.' })
      return
    }
    marcarErrores(form, {})
    try {
      const r = await api.historial(filtros)
      visitas = r.visitas
      const recortado = r.truncado ? ` (se muestran los ${r.limite} más recientes)` : ''
      let resumen = porUsuario
        ? `${visitas.length} ${visitas.length === 1 ? 'registro encontrado' : 'registros encontrados'}${recortado}`
        : `Visitas desde el ${fechaCorta(r.desdeAplicado)}${recortado}. Aplica filtros para acotar la consulta.`
      if (r.desdeAjustado) resumen += ` · La búsqueda empieza el ${fechaCorta(r.desdeAplicado)} por tu nivel de acceso.`
      main.querySelector('[data-resumen]').textContent = resumen
      if (!porUsuario) {
        // la primera fecha que el rol puede consultar
        f.desde.min = r.desdeAplicado
        f.hasta.min = r.desdeAplicado
      }

      const contenedor = main.querySelector('[data-resultados]')
      contenedor.innerHTML = visitas.length
        ? tablaVisitas(visitas)
        : vacio({ titulo: 'No encontramos resultados', texto: 'Prueba cambiando o limpiando los filtros de búsqueda.' })
      contenedor.querySelectorAll('[data-detalle]').forEach((b) => b.addEventListener('click', () => {
        modalDetalle(visitas.find((v) => v.id === Number(b.dataset.detalle)), {
          puedeRegistrarSalida: ctx.permisos.registrar,
          alRegistrarSalida: (v) => pedirSalida(v, ctx),
        })
      }))
    } catch (error) {
      if (error.estado === 400) marcarErrores(form, error.campos)
      else throw error
    }
  }

  form.addEventListener('submit', (e) => {
    e.preventDefault()
    buscar(true)
  })
  main.querySelector('[data-limpiar]').addEventListener('click', () => {
    form.reset()
    buscar(false)
  })

  await buscar(false)
}
