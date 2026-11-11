// Componentes visuales reutilizables. Cada función devuelve el HTML con las
// mismas clases del prototipo de Figma, así los estilos son idénticos.
// Todo texto que viene de la base de datos pasa por esc() para evitar XSS.

import { duracion, fechaCorta, hora, fechaHora, iniciales } from './formato.js'

export function esc(valor) {
  return String(valor ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;')
}

const TRAZOS = {
  grid: '<rect x="3" y="3" width="7" height="7" rx="1"></rect><rect x="14" y="3" width="7" height="7" rx="1"></rect><rect x="3" y="14" width="7" height="7" rx="1"></rect><rect x="14" y="14" width="7" height="7" rx="1"></rect>',
  'user-plus': '<path d="M15 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"></path><circle cx="8.5" cy="7" r="4"></circle><path d="M19 8v6M16 11h6"></path>',
  clock: '<circle cx="12" cy="12" r="9"></circle><path d="M12 7v5l3 2"></path>',
  archive: '<path d="M4 7v13h16V7M3 3h18v4H3zM9 11h6"></path>',
  chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"></path>',
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="8.5" cy="7" r="4"></circle><path d="M17 11a4 4 0 0 0 0-8M23 21v-2a4 4 0 0 0-3-3.7"></path>',
  user: '<circle cx="12" cy="8" r="4"></circle><path d="M4 21a8 8 0 0 1 16 0"></path>',
  logout: '<path d="M10 17l5-5-5-5M15 12H3M21 3v18h-6"></path>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9M10 21h4"></path>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16"></path>',
  arrow: '<path d="M5 12h14M13 6l6 6-6 6"></path>',
  search: '<circle cx="11" cy="11" r="7"></circle><path d="m20 20-4-4"></path>',
  eye: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12"></path><circle cx="12" cy="12" r="3"></circle>',
  check: '<path d="m5 12 4 4L19 6"></path>',
  close: '<path d="M6 6l12 12M18 6 6 18"></path>',
}

export function icono(nombre, tam = 18) {
  return `<svg aria-hidden="true" width="${tam}" height="${tam}" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${TRAZOS[nombre] || ''}</svg>`
}

/** Botón del prototipo: variante primary | secondary | ghost | danger. `contenido` es HTML. */
export function boton(contenido, { variante = 'primary', icon, tipo = 'button', atributos = '' } = {}) {
  return `<button type="${tipo}" class="btn btn-${variante}" ${atributos}>${icon ? icono(icon, 17) : ''} ${contenido}</button>`
}

export function insignia(texto, tono = 'green') {
  return `<span class="badge badge-${tono}"><span class="badge-dot"></span>${esc(texto)}</span>`
}

export function insigniaEstado(estado) {
  return estado === 'ACTIVA' ? insignia('Activa', 'green') : insignia('Finalizada', 'gray')
}

/** Campo de formulario. `control` es el HTML del input/select; `nombre` sirve para marcar errores. */
export function campo({ etiqueta, nombre, requerido = false, ayuda, control }) {
  return `<label class="field" data-campo="${nombre || ''}"${ayuda ? ` data-ayuda="${esc(ayuda)}"` : ''}>`
    + `<span>${esc(etiqueta)}${requerido ? '<b> *</b>' : ''}</span>${control}`
    + `${ayuda ? `<small class="hint">${esc(ayuda)}</small>` : ''}</label>`
}

/** Muestra (o quita) los mensajes de error debajo de cada campo. Devuelve el primer campo con error. */
export function marcarErrores(contenedor, errores = {}) {
  let primero = null
  contenedor.querySelectorAll('label.field[data-campo]').forEach((label) => {
    const mensaje = errores[label.dataset.campo]
    label.classList.toggle('has-error', Boolean(mensaje))
    label.querySelectorAll('small').forEach((s) => s.remove())
    if (mensaje) {
      label.insertAdjacentHTML('beforeend', `<small>${esc(mensaje)}</small>`)
      primero = primero || label
    } else if (label.dataset.ayuda) {
      label.insertAdjacentHTML('beforeend', `<small class="hint">${esc(label.dataset.ayuda)}</small>`)
    }
  })
  primero?.querySelector('input, select')?.focus()
  return primero
}

/** Quita el error de un campo en cuanto la persona lo corrige. */
export function limpiarError(label) {
  if (!label || !label.classList.contains('has-error')) return
  label.classList.remove('has-error')
  label.querySelectorAll('small:not(.hint)').forEach((s) => s.remove())
  if (label.dataset.ayuda && !label.querySelector('small.hint')) {
    label.insertAdjacentHTML('beforeend', `<small class="hint">${esc(label.dataset.ayuda)}</small>`)
  }
}

export function opciones(lista, seleccionado, vacio) {
  const inicio = vacio !== undefined ? `<option value="">${esc(vacio)}</option>` : ''
  return inicio + lista.map(({ valor, texto }) =>
    `<option value="${esc(valor)}"${String(valor) === String(seleccionado ?? '') ? ' selected' : ''}>${esc(texto)}</option>`).join('')
}

export function alerta(tipo, titulo, texto, icon) {
  const simbolo = icon || (tipo === 'success' ? 'check' : tipo === 'error' ? 'close' : 'archive')
  return `<div class="alert alert-${tipo}" role="${tipo === 'error' ? 'alert' : 'status'}">${icono(simbolo)}<div><strong>${esc(titulo)}</strong><span>${esc(texto)}</span></div></div>`
}

export function tituloPagina({ kicker, titulo, texto, acciones }) {
  return `<div class="page-title"><div>${kicker ? `<span class="eyebrow">${esc(kicker)}</span>` : ''}<h1>${esc(titulo)}</h1><p>${esc(texto)}</p></div>${acciones ? `<div class="title-actions">${acciones}</div>` : ''}</div>`
}

export function vacio({ titulo, texto, accion = '' }) {
  return `<div class="empty"><div>${icono('search', 28)}</div><h3>${esc(titulo)}</h3><p>${esc(texto)}</p>${accion}</div>`
}

/** Tabla de visitas del prototipo. `compacta` quita la columna Tiempo; `conSalida` muestra "Registrar salida". */
export function tablaVisitas(visitas, { compacta = false, conSalida = false } = {}) {
  const filas = visitas.map((v) => {
    const tiempo = v.estado === 'ACTIVA' ? duracion(v.minutosTranscurridos) : duracion(v.duracionMinutos)
    const horario = hora(v.ingreso) + (v.salida ? ` — ${hora(v.salida)}` : '')
    return `<tr>`
      + `<td><strong>${esc(v.visitante)}</strong></td>`
      + `<td>${esc(v.empresa || 'No especificada')}</td>`
      + `<td><strong class="cell-main">${esc(v.anfitrion)}</strong><span class="cell-sub">${esc(v.area)}</span></td>`
      + `<td><strong class="cell-main">${fechaCorta(v.ingreso)}</strong><span class="cell-sub">${horario}</span></td>`
      + (compacta ? '' : `<td>${tiempo}</td>`)
      + `<td>${insigniaEstado(v.estado)}</td>`
      + `<td class="actions"><button class="icon-btn" title="Ver detalle" aria-label="Ver detalle de ${esc(v.visitante)}" data-detalle="${v.id}">${icono('eye')}</button>`
      + (conSalida && v.estado === 'ACTIVA' ? `<button class="exit-link" data-salida="${v.id}">Registrar salida</button>` : '')
      + `</td></tr>`
  }).join('')
  return `<div class="table-wrap"><table><thead><tr><th>Visitante</th><th>Empresa</th><th>Anfitrión / área</th><th>Ingreso</th>${compacta ? '' : '<th>Tiempo</th>'}<th>Estado</th><th></th></tr></thead><tbody>${filas}</tbody></table></div>`
}

/* ---------- Ventanas modales ---------- */

/** Abre una ventana modal dentro de la aplicación. Devuelve { elemento, cerrar }. */
export function abrirModal(titulo, contenido) {
  const fondo = document.createElement('div')
  fondo.className = 'modal-backdrop'
  fondo.innerHTML = `<div class="modal" role="dialog" aria-modal="true" aria-label="${esc(titulo)}"><div class="modal-head"><h2>${esc(titulo)}</h2><button type="button" aria-label="Cerrar" data-cerrar>${icono('close')}</button></div>${contenido}</div>`
  const contenedor = document.querySelector('.app-shell') || document.body
  contenedor.appendChild(fondo)

  const anterior = document.activeElement
  const cerrar = () => {
    document.removeEventListener('keydown', alPresionar)
    fondo.remove()
    anterior?.focus?.()
  }
  const alPresionar = (e) => {
    if (e.key === 'Escape') cerrar()
  }
  document.addEventListener('keydown', alPresionar)
  fondo.querySelectorAll('[data-cerrar]').forEach((b) => b.addEventListener('click', cerrar))
  fondo.querySelector('input, select, .modal-actions .btn-primary')?.focus()
  return { elemento: fondo, cerrar }
}

/** Ventana con todos los datos de una visita. */
export function modalDetalle(v, { puedeRegistrarSalida = false, alRegistrarSalida } = {}) {
  const filas = [
    ['Documento', v.documentoOculto ? 'Oculto por permisos' : (v.documento || 'No registrado')],
    ['Anfitrión', v.anfitrion],
    ['Área', v.area],
    ['Motivo', v.motivo],
    ['Ingreso', fechaHora(v.ingreso)],
    ['Salida', v.salida ? fechaHora(v.salida) : 'Pendiente'],
    ['Duración', v.estado === 'ACTIVA' ? `En curso · ${duracion(v.minutosTranscurridos)}` : duracion(v.duracionMinutos)],
    ['Registrado por', v.registradoPor],
  ]
  const contenido = `<div class="exit-summary"><div class="avatar large">${esc(iniciales(v.visitante))}</div><div><strong>${esc(v.visitante)}</strong><span>${esc(v.empresa || 'No especificada')}</span></div><div style="margin-left:auto">${insigniaEstado(v.estado)}</div></div>`
    + `<div class="exit-data">${filas.map(([k, val]) => `<div><span>${esc(k)}</span><strong>${esc(val)}</strong></div>`).join('')}</div>`
    + `<p class="modal-note">Solo se guardan los datos necesarios para gestionar la visita.</p>`
    + `<div class="modal-actions">${boton('Cerrar', { variante: 'secondary', atributos: 'data-cerrar' })}`
    + `${puedeRegistrarSalida && v.estado === 'ACTIVA' ? boton('Registrar salida', { atributos: 'data-salida-modal' }) : ''}</div>`
  const modal = abrirModal('Detalle de la visita', contenido)
  modal.elemento.querySelector('[data-salida-modal]')?.addEventListener('click', () => {
    modal.cerrar()
    alRegistrarSalida?.(v)
  })
  return modal
}

/* ---------- Mensajes flotantes ---------- */

let temporizador = null

export function aviso(titulo, texto = 'Los cambios ya se reflejan en el sistema.', tipo = 'exito') {
  document.querySelector('.toast')?.remove()
  clearTimeout(temporizador)
  const caja = document.createElement('div')
  caja.className = `toast${tipo === 'error' ? ' toast-error' : ''}`
  caja.setAttribute('role', tipo === 'error' ? 'alert' : 'status')
  caja.innerHTML = `<div>${icono(tipo === 'error' ? 'close' : 'check')}</div><span><strong>${esc(titulo)}</strong>${esc(texto)}</span><button type="button" aria-label="Cerrar mensaje">${icono('close')}</button>`
  caja.querySelector('button').addEventListener('click', () => caja.remove())
  ;(document.querySelector('.app-shell') || document.body).appendChild(caja)
  temporizador = setTimeout(() => caja.remove(), tipo === 'error' ? 8000 : 5000)
}

export function avisoError(error) {
  aviso('No se pudo completar la acción', error?.message || 'Ocurrió un error inesperado.', 'error')
}

/** Desactiva un botón mientras se espera al servidor. */
export async function conEspera(boton, tarea) {
  if (!boton) return tarea()
  boton.disabled = true
  try {
    return await tarea()
  } finally {
    boton.disabled = false
  }
}
