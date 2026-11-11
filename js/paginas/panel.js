// Panel principal: resumen de la recepción de hoy con datos de la base.

import { api } from '../api.js'
import { boton, esc, icono, modalDetalle, tablaVisitas, tituloPagina, vacio } from '../ui.js'
import { duracion, fechaTitulo, hora, primerNombre, saludo } from '../formato.js'
import { pedirSalida } from './activas.js'

export async function paginaPanel(main, ctx) {
  const datos = await api.panel()
  const anfitrion = !ctx.permisos.registrar

  const variacion = (hoy, ayer) => {
    if (!ayer) return 'Sin datos de ayer'
    const cambio = Math.round(((hoy - ayer) / ayer) * 100)
    return `${cambio > 0 ? '+' : cambio < 0 ? '−' : ''}${Math.abs(cambio)}% vs. ayer`
  }
  const variacionDuracion = () => {
    if (datos.duracionPromedioHoy === null || datos.duracionPromedioAyer === null) return 'Sin datos de ayer'
    const diferencia = datos.duracionPromedioHoy - datos.duracionPromedioAyer
    return `${diferencia > 0 ? '+' : diferencia < 0 ? '−' : ''}${Math.abs(diferencia)} min vs. ayer`
  }
  const porcentaje = datos.entradasHoy ? Math.round((datos.finalizadasHoy / datos.entradasHoy) * 100) : 0

  const metricas = [
    ['Visitas activas', datos.activas, 'En las instalaciones', 'green', 'users', `+${datos.ingresosUltimaHora} en la última hora`],
    ['Entradas hoy', datos.entradasHoy, 'Total registrado', 'blue', 'user-plus', variacion(datos.entradasHoy, datos.entradasAyer)],
    ['Finalizadas hoy', datos.finalizadasHoy, 'Salidas registradas', 'violet', 'check', `${porcentaje}% del total`],
    ['Duración promedio', datos.duracionPromedioHoy === null ? '—' : duracion(datos.duracionPromedioHoy), 'Datos de hoy', 'orange', 'clock', variacionDuracion()],
  ]

  main.innerHTML = tituloPagina({
    kicker: fechaTitulo(datos.ahora),
    titulo: `${saludo(datos.ahora)}, ${primerNombre(ctx.usuario.nombre)}`,
    texto: anfitrion ? 'Aquí tienes el resumen de tus visitas de hoy.' : 'Aquí tienes el resumen de la recepción de hoy.',
    acciones: ctx.permisos.registrar ? boton('Registrar visitante', { icon: 'user-plus', atributos: 'data-registrar' }) : '',
  })
    + `<div class="demo-note">${icono('eye')}<span>Datos registrados en la base de datos · actualizado a las ${hora(datos.ahora)}</span></div>`
    + `<section class="metrics">${metricas.map(([etiqueta, valor, sub, tono, ic, delta]) =>
      `<article class="metric"><div class="metric-icon ${tono}">${icono(ic)}</div><span>${etiqueta}</span><strong>${esc(valor)}</strong><p>${sub}</p><small>${esc(delta)}</small></article>`).join('')}</section>`
    + `<section class="panel"><div class="panel-head"><div><h2>Últimas visitas</h2><p>${anfitrion ? 'Visitas recientes en las que eres el anfitrión.' : 'Movimientos recientes registrados en recepción.'}</p></div><button type="button" class="text-link" data-historial>Ver historial ${icono('arrow')}</button></div>`
    + (datos.ultimas.length
      ? tablaVisitas(datos.ultimas, { compacta: true })
      : vacio({ titulo: 'Todavía no hay visitas registradas', texto: 'Cuando registres un ingreso, aparecerá aquí.' }))
    + `</section>`

  main.querySelector('[data-registrar]')?.addEventListener('click', () => ctx.ir('registrar'))
  main.querySelector('[data-historial]').addEventListener('click', () => ctx.ir('historial'))
  main.querySelectorAll('[data-detalle]').forEach((b) => b.addEventListener('click', () => {
    const visita = datos.ultimas.find((v) => v.id === Number(b.dataset.detalle))
    modalDetalle(visita, {
      puedeRegistrarSalida: ctx.permisos.registrar,
      alRegistrarSalida: (v) => pedirSalida(v, ctx),
    })
  }))
}
