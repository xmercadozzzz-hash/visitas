// CU-03 y CU-04: visitas que todavía no registran salida, y registro de salida.

import { api } from '../api.js'
import { abrirModal, aviso, avisoError, boton, conEspera, esc, icono, modalDetalle, tablaVisitas, tituloPagina, vacio } from '../ui.js'
import { duracion, fechaHora, hora, iniciales, sumarMinutos } from '../formato.js'

/** Ventana de confirmación de salida, con la duración calculada. */
export async function pedirSalida(visita, ctx) {
  // Hora del servidor para calcular la duración que se va a registrar
  const { actualizado } = await api.activas()
  const minutos = Math.max(0, visita.minutosTranscurridos ?? 0)
  const salida = actualizado || sumarMinutos(visita.ingreso, minutos)
  const estimado = Math.max(0, Math.round((Date.parse(salida) - Date.parse(visita.ingreso)) / 60000))

  const modal = abrirModal('Confirmar registro de salida',
    `<div class="exit-summary"><div class="avatar large">${esc(iniciales(visita.visitante))}</div><div><strong>${esc(visita.visitante)}</strong><span>${esc(visita.empresa || 'No especificada')}</span></div></div>`
    + `<div class="exit-data"><div><span>Ingreso</span><strong>${fechaHora(visita.ingreso)}</strong></div><div><span>Salida a registrar</span><strong>${fechaHora(salida)}</strong></div><div><span>Duración calculada</span><strong>${duracion(estimado)}</strong></div></div>`
    + `<p class="modal-note">La visita cambiará a “Finalizada” y se trasladará al historial.</p>`
    + `<div class="modal-actions">${boton('Cancelar', { variante: 'secondary', atributos: 'data-cerrar' })}${boton('Confirmar salida', { atributos: 'data-confirmar' })}</div>`)

  modal.elemento.querySelector('[data-confirmar]').addEventListener('click', async (e) => {
    try {
      await conEspera(e.currentTarget, () => api.registrarSalida(visita.id))
      modal.cerrar()
      aviso('Salida registrada correctamente')
      ctx.actualizarContadores()
      ctx.ir('historial')
    } catch (error) {
      modal.cerrar()
      if (error.estado !== 401) avisoError(error)
      if (error.estado === 409) ctx.ir('activas')
    }
  })
}

export async function paginaActivas(main, ctx) {
  const puedeRegistrar = ctx.permisos.registrar
  let datos = await api.activas()
  let filtro = ''

  main.innerHTML = tituloPagina({
    kicker: 'RF-06',
    titulo: 'Visitas activas',
    texto: puedeRegistrar
      ? 'Personas que se encuentran actualmente en las instalaciones.'
      : 'Personas que te están visitando en este momento.',
    acciones: puedeRegistrar ? boton('Registrar visitante', { icon: 'user-plus', atributos: 'data-registrar' }) : '',
  })
    + `<div class="count-strip"><div>${icono('users', 24)}</div><strong data-total></strong><span>visitas activas ahora</span><i data-hora></i></div>`
    + `<section class="panel"><div class="panel-head"><div><h2>En las instalaciones</h2><p>${puedeRegistrar ? 'Registra la salida cuando la visita haya terminado.' : 'La recepción registra la salida cuando la visita termina.'}</p></div><div class="search-box">${icono('search')}<input type="search" placeholder="Buscar visitante..." aria-label="Buscar visitante"></div></div><div data-tabla></div></section>`

  const dibujar = () => {
    const texto = filtro.trim().toLowerCase()
    const lista = datos.visitas.filter((v) => !texto
      || [v.visitante, v.empresa, v.anfitrion, v.area, v.documento].some((x) => String(x || '').toLowerCase().includes(texto)))
    main.querySelector('[data-total]').textContent = datos.visitas.length
    main.querySelector('[data-hora]').textContent = `Actualizado a las ${hora(datos.actualizado)}`

    const tabla = main.querySelector('[data-tabla]')
    if (lista.length) {
      tabla.innerHTML = tablaVisitas(lista, { conSalida: puedeRegistrar })
    } else if (datos.visitas.length) {
      tabla.innerHTML = vacio({ titulo: 'No encontramos resultados', texto: 'Prueba con otro nombre, empresa o anfitrión.' })
    } else {
      tabla.innerHTML = vacio({
        titulo: 'No hay visitas activas en este momento',
        texto: 'Cuando registres un ingreso, aparecerá aquí.',
        accion: puedeRegistrar ? boton('Registrar un visitante', { atributos: 'data-registrar' }) : '',
      })
    }
    tabla.querySelector('[data-registrar]')?.addEventListener('click', () => ctx.ir('registrar'))
    tabla.querySelectorAll('[data-detalle]').forEach((b) => b.addEventListener('click', () => {
      const visita = datos.visitas.find((v) => v.id === Number(b.dataset.detalle))
      modalDetalle(visita, { puedeRegistrarSalida: puedeRegistrar, alRegistrarSalida: (v) => pedirSalida(v, ctx) })
    }))
    tabla.querySelectorAll('[data-salida]').forEach((b) => b.addEventListener('click', () => {
      pedirSalida(datos.visitas.find((v) => v.id === Number(b.dataset.salida)), ctx).catch(avisoError)
    }))
  }

  main.querySelector('.page-title [data-registrar]')?.addEventListener('click', () => ctx.ir('registrar'))
  main.querySelector('.search-box input').addEventListener('input', (e) => {
    filtro = e.target.value
    dibujar()
  })
  dibujar()

  // Se actualiza sola cada minuto mientras la página está abierta
  const intervalo = setInterval(async () => {
    try {
      datos = await api.activas()
      dibujar()
      ctx.actualizarContadores()
    } catch {
      // se reintenta en el siguiente minuto
    }
  }, 60000)
  ctx.alDejarPagina(() => clearInterval(intervalo))
}
