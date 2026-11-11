// Arranque del frontend: revisa la sesión, dibuja el menú lateral y la
// cabecera, y muestra la página que indica la dirección (#panel, #registrar...).

import { api, alVencerSesion } from './api.js'
import { esc, icono, avisoError } from './ui.js'
import { iniciales } from './formato.js'
import { mostrarLogin } from './paginas/login.js'
import { paginaPanel } from './paginas/panel.js'
import { paginaRegistrar } from './paginas/registrar.js'
import { paginaActivas } from './paginas/activas.js'
import { paginaHistorial } from './paginas/historial.js'
import { paginaReportes } from './paginas/reportes.js'
import { paginaUsuarios } from './paginas/usuarios.js'
import { paginaPerfil } from './paginas/perfil.js'

const raiz = document.getElementById('root')

// Páginas del menú lateral, con el permiso que exige cada una
const PAGINAS = [
  { id: 'panel', etiqueta: 'Panel principal', icono: 'grid', mostrar: paginaPanel },
  { id: 'registrar', etiqueta: 'Registrar visitante', icono: 'user-plus', mostrar: paginaRegistrar, permiso: 'registrar' },
  { id: 'activas', etiqueta: 'Visitas activas', icono: 'clock', mostrar: paginaActivas },
  { id: 'historial', etiqueta: 'Historial de visitas', icono: 'archive', mostrar: paginaHistorial },
  { id: 'reportes', etiqueta: 'Reportes', icono: 'chart', mostrar: paginaReportes, permiso: 'reportes' },
  { id: 'usuarios', etiqueta: 'Usuarios y permisos', icono: 'users', mostrar: paginaUsuarios, permiso: 'usuarios' },
  { id: 'perfil', etiqueta: 'Mi perfil', icono: 'user', mostrar: paginaPerfil },
]

const estado = { sesion: null, catalogos: null, limpiarPagina: null }

/** Datos y funciones que reciben todas las páginas. */
const contexto = {
  get sesion() {
    return estado.sesion
  },
  get usuario() {
    return estado.sesion.usuario
  },
  get permisos() {
    return estado.sesion.permisos
  },
  ir: (pagina) => {
    if (location.hash === `#${pagina}`) mostrarPagina()
    else location.hash = pagina
  },
  catalogos: async () => {
    estado.catalogos = estado.catalogos || (await api.catalogos())
    return estado.catalogos
  },
  actualizarContadores,
  alDejarPagina: (funcion) => {
    estado.limpiarPagina = funcion
  },
}

async function iniciar() {
  alVencerSesion((mensaje) => entrar(null, mensaje))
  try {
    entrar(await api.sesion())
  } catch (error) {
    entrar(null, error.message)
  }
}

/** Con sesión muestra la aplicación; sin sesión, el inicio de sesión. */
function entrar(sesion, mensaje) {
  estado.limpiarPagina?.()
  estado.limpiarPagina = null
  estado.sesion = sesion
  estado.catalogos = null
  if (!sesion) {
    mostrarLogin(raiz, { mensaje, alEntrar: (nueva) => entrar(nueva) })
    return
  }
  dibujarEstructura()
  mostrarPagina()
  actualizarContadores()
}

function paginasPermitidas() {
  return PAGINAS.filter((p) => !p.permiso || estado.sesion.permisos[p.permiso])
}

function dibujarEstructura() {
  const u = estado.sesion.usuario
  const navegacion = paginasPermitidas().map((p) =>
    `<button type="button" data-ir="${p.id}">${icono(p.icono)}<span>${esc(p.etiqueta)}</span>${p.id === 'activas' ? '<i data-contador hidden>0</i>' : ''}</button>`).join('')

  raiz.innerHTML = `<div class="app-shell">
    <aside class="sidebar">
      <div class="side-brand"><div class="brand-mark">VR</div><div><strong>VisitaRegistro</strong><span>Control de recepción</span></div><button type="button" class="mobile-close" aria-label="Cerrar menú" data-menu-cerrar>${icono('close')}</button></div>
      <nav aria-label="Menú principal">${navegacion}</nav>
      <div class="side-user"><div class="avatar">${esc(iniciales(u.nombre))}</div><div><strong>${esc(u.nombre)}</strong><span>${esc(u.rolEtiqueta)}</span></div><button type="button" title="Cerrar sesión" aria-label="Cerrar sesión" data-salir>${icono('logout')}</button></div>
    </aside>
    <div class="app-main">
      <header><button type="button" class="menu-btn" aria-label="Abrir menú" data-menu-abrir>${icono('menu')}</button><div class="header-system"><strong>Sistema de visitantes</strong><span>${esc(estado.sesion.oficina)}</span></div><div class="header-right"><button type="button" class="notification" data-avisos title="Visitas activas con más de 4 horas" aria-label="Visitas activas con más de 4 horas">${icono('bell')}<i data-prolongadas hidden>0</i></button><div class="header-user"><div class="avatar">${esc(iniciales(u.nombre))}</div><div><strong>${esc(u.nombre)}</strong><span>${esc(u.rolEtiqueta)}</span></div></div></div></header>
      <main id="contenido"></main>
    </div>
  </div>`

  const shell = raiz.querySelector('.app-shell')
  const barra = shell.querySelector('.sidebar')
  const abrirMenu = () => {
    barra.classList.add('open')
    if (!shell.querySelector('.scrim')) {
      barra.insertAdjacentHTML('afterend', '<div class="scrim"></div>')
      shell.querySelector('.scrim').addEventListener('click', cerrarMenu)
    }
  }
  const cerrarMenu = () => {
    barra.classList.remove('open')
    shell.querySelector('.scrim')?.remove()
  }
  shell.querySelector('[data-menu-abrir]').addEventListener('click', abrirMenu)
  shell.querySelector('[data-menu-cerrar]').addEventListener('click', cerrarMenu)
  shell.querySelectorAll('[data-ir]').forEach((b) => b.addEventListener('click', () => {
    cerrarMenu()
    contexto.ir(b.dataset.ir)
  }))
  shell.querySelector('[data-avisos]').addEventListener('click', () => contexto.ir('activas'))
  shell.querySelector('[data-salir]').addEventListener('click', async () => {
    try {
      await api.cerrarSesion()
    } catch {
      // aunque falle la red, se sale de la aplicación
    }
    history.replaceState(null, '', location.pathname)
    entrar(null)
  })
}

function mostrarPagina() {
  if (!estado.sesion) return
  const id = location.hash.replace('#', '') || 'panel'
  const pagina = paginasPermitidas().find((p) => p.id === id) || paginasPermitidas()[0]

  estado.limpiarPagina?.()
  estado.limpiarPagina = null
  raiz.querySelectorAll('[data-ir]').forEach((b) => b.classList.toggle('active', b.dataset.ir === pagina.id))
  raiz.querySelector('.modal-backdrop')?.remove()

  const main = raiz.querySelector('#contenido')
  main.innerHTML = ''
  window.scrollTo(0, 0)
  pagina.mostrar(main, contexto).catch((error) => {
    if (error.estado !== 401) avisoError(error)
  })
}

/** Número de visitas activas (menú) y de visitas prolongadas (campana). */
async function actualizarContadores() {
  try {
    const { visitas } = await api.activas()
    const contador = raiz.querySelector('[data-contador]')
    if (contador) {
      contador.textContent = visitas.length
      contador.hidden = false
    }
    const prolongadas = visitas.filter((v) => v.minutosTranscurridos >= 240).length
    const campana = raiz.querySelector('[data-prolongadas]')
    if (campana) {
      campana.textContent = prolongadas
      campana.hidden = prolongadas === 0
    }
  } catch {
    // el contador no es crítico
  }
}

window.addEventListener('hashchange', mostrarPagina)
iniciar()
