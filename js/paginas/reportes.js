// RF-07 y CU-06: reportes por día y por área calculados en el servidor.

import { api } from '../api.js'
import { alerta, avisoError, boton, campo, esc, opciones, tituloPagina } from '../ui.js'
import { duracion, rango } from '../formato.js'

const INTERVALOS = [
  { valor: '7d', texto: 'Últimos 7 días' },
  { valor: 'hoy', texto: 'Hoy' },
  { valor: 'mes', texto: 'Este mes' },
]

export async function paginaReportes(main, ctx) {
  const catalogos = await ctx.catalogos()

  main.innerHTML = tituloPagina({ kicker: 'RF-07', titulo: 'Reportes', texto: 'Analiza el flujo de visitas por fecha o área.' })
    + `<div data-alerta></div>`
    + `<section class="filter-panel inline-filter">`
    + campo({ etiqueta: 'Intervalo', nombre: 'rango', control: `<select name="rango">${opciones(INTERVALOS, '7d')}</select>` })
    + campo({ etiqueta: 'Área', nombre: 'areaId', control: `<select name="areaId">${opciones(catalogos.areas.map((a) => ({ valor: a.id, texto: a.nombre })), '', 'Todas las áreas')}</select>` })
    + boton('Generar reporte', { icon: 'chart', atributos: 'data-generar' })
    + `</section><div data-reporte></div>`

  const generar = async (conAviso) => {
    const r = await api.reporte(main.querySelector('[name=rango]').value, main.querySelector('[name=areaId]').value)
    const maximo = Math.max(1, ...r.barras.map((b) => b.total))
    const periodo = `${rango(r.desde, r.hasta)}${r.area ? ` · ${r.area}` : ''}`

    main.querySelector('[data-reporte]').innerHTML =
      `<section class="metrics report-metrics"><article><span>Total registradas</span><strong>${r.total}</strong></article><article><span>Visitas finalizadas</span><strong>${r.finalizadas}</strong></article><article><span>Continúan activas</span><strong>${r.activas}</strong></article></section>`
      + `<section class="report-grid"><div class="panel chart-panel"><div class="panel-head"><div><h2>${esc(r.titulo)}</h2><p>${esc(periodo)}${r.duracionPromedio !== null ? ` · Duración promedio ${duracion(r.duracionPromedio)}` : ''}</p></div></div>`
      + `<div class="chart" role="img" aria-label="${esc(r.titulo)}">${r.barras.map((b) =>
        `<div class="bar-col${b.total ? '' : ' sin-datos'}"><span>${b.total}</span><div data-altura="${Math.round((b.total / maximo) * 86)}"></div><small>${esc(b.etiqueta)}</small></div>`).join('')}</div></div>`
      + `<div class="panel areas"><div class="panel-head"><div><h2>Resumen por área</h2><p>Distribución del período</p></div></div>${r.porArea.length
        ? r.porArea.map((a) => `<div class="area-row"><span>${esc(a.area)}</span><strong>${a.total}</strong><i>${a.porcentaje}%</i></div>`).join('')
        : '<div class="area-row"><span>Sin visitas en este período</span><strong>0</strong><i>0%</i></div>'}</div></section>`

    // Altura de cada barra (se asigna desde JavaScript para cumplir la política de seguridad del navegador)
    main.querySelectorAll('[data-altura]').forEach((barra) => {
      barra.style.height = `${barra.dataset.altura}%`
    })
    main.querySelector('[data-alerta]').innerHTML = conAviso
      ? alerta('success', 'Reporte generado correctamente', 'Los resultados se calcularon con las visitas registradas en la base de datos.')
      : ''
  }

  main.querySelector('[data-generar]').addEventListener('click', (e) => {
    const botonGenerar = e.currentTarget
    botonGenerar.disabled = true
    generar(true)
      .catch((error) => {
        if (error.estado !== 401) avisoError(error)
      })
      .finally(() => {
        botonGenerar.disabled = false
      })
  })
  await generar(false)
}
