// CU-01 y CU-02: formulario rápido para registrar el ingreso de un visitante.

import { api } from '../api.js'
import { alerta, aviso, avisoError, boton, campo, conEspera, insignia, icono, limpiarError, marcarErrores, opciones, tituloPagina } from '../ui.js'
import { hora, sumarMinutos } from '../formato.js'

const NOMBRE = /^[\p{L}][\p{L} .'-]*$/u
const DOCUMENTO = /^[A-Za-z0-9-]+$/

/** Las mismas reglas que valida el servidor (RNF-03). */
function validar(d) {
  const e = {}
  if (!d.nombre) e.nombre = 'Este campo es obligatorio.'
  else if (d.nombre.length < 3 || d.nombre.length > 100) e.nombre = 'Escribe entre 3 y 100 caracteres.'
  else if (!NOMBRE.test(d.nombre)) e.nombre = 'Usa solo letras y espacios.'
  if (d.documento && (d.documento.length < 5 || d.documento.length > 20 || !DOCUMENTO.test(d.documento))) {
    e.documento = 'Escribe entre 5 y 20 letras o números, sin espacios.'
  }
  if (d.empresa && d.empresa.length > 100) e.empresa = 'Escribe máximo 100 caracteres.'
  if (!d.motivo) e.motivo = 'Este campo es obligatorio.'
  if (!d.anfitrionId) e.anfitrionId = 'Este campo es obligatorio.'
  if (!d.areaId) e.areaId = 'Este campo es obligatorio.'
  return e
}

export async function paginaRegistrar(main, ctx) {
  const [catalogos, horaServidor] = await Promise.all([ctx.catalogos(), api.ahora()])
  const inicio = Date.now()
  const horaActual = () => hora(sumarMinutos(horaServidor, Math.floor((Date.now() - inicio) / 60000)))

  main.innerHTML = tituloPagina({
    kicker: 'RF-01 · RF-02 · RF-03',
    titulo: 'Registrar visitante',
    texto: 'Registra únicamente la información necesaria para gestionar el ingreso.',
  }) + `<form class="form-panel" novalidate>
      <div class="form-head"><div class="step">1</div><div><h2>Información de la visita</h2><p>Los campos marcados con * son obligatorios.</p></div>${insignia(`Ingreso: hoy, ${horaActual()}`, 'blue')}</div>
      <div class="form-grid">
        ${campo({ etiqueta: 'Nombre completo', nombre: 'nombre', requerido: true,
          control: '<input name="nombre" placeholder="Ej. Andrea Martínez" maxlength="100" autocomplete="off">' })}
        ${campo({ etiqueta: 'Documento de identificación', nombre: 'documento', ayuda: 'Opcional · Solicitar solo si la política lo requiere',
          control: '<input name="documento" placeholder="Número de identificación" maxlength="20" autocomplete="off">' })}
        ${campo({ etiqueta: 'Empresa o entidad', nombre: 'empresa',
          control: '<input name="empresa" placeholder="Opcional" maxlength="100" autocomplete="off">' })}
        ${campo({ etiqueta: 'Motivo de la visita', nombre: 'motivo', requerido: true,
          control: `<select name="motivo">${opciones(catalogos.motivos.map((m) => ({ valor: m, texto: m })), '', 'Seleccionar motivo')}</select>` })}
        ${campo({ etiqueta: 'Anfitrión', nombre: 'anfitrionId', requerido: true,
          control: `<select name="anfitrionId">${opciones(catalogos.anfitriones.map((a) => ({ valor: a.id, texto: a.nombre })), '', 'Buscar o seleccionar')}</select>` })}
        ${campo({ etiqueta: 'Área a visitar', nombre: 'areaId', requerido: true,
          control: `<select name="areaId">${opciones(catalogos.areas.map((a) => ({ valor: a.id, texto: a.nombre })), '', 'Seleccionar área')}</select>` })}
      </div>
      <div class="privacy">${icono('archive')}<div><strong>Privacidad de la información</strong><span>Los datos se utilizarán exclusivamente para gestionar esta visita y se conservarán ${ctx.sesion.retencionDias} días, según la política de retención.</span></div></div>
      <div class="form-actions">${boton('Cancelar', { variante: 'secondary', atributos: 'data-cancelar' })}${boton('Registrar ingreso', { tipo: 'submit', icon: 'check' })}</div>
    </form>`

  // La hora de ingreso que muestra la insignia avanza sola
  const actualizarReloj = setInterval(() => {
    const badge = main.querySelector('.form-head > .badge')
    if (badge) badge.lastChild.textContent = `Ingreso: hoy, ${horaActual()}`
  }, 15000)
  ctx.alDejarPagina(() => clearInterval(actualizarReloj))

  const form = main.querySelector('form')
  const anfitriones = new Map(catalogos.anfitriones.map((a) => [String(a.id), a]))

  // Al elegir el anfitrión se propone su área (se puede cambiar)
  form.elements.anfitrionId.addEventListener('change', () => {
    const anfitrion = anfitriones.get(form.elements.anfitrionId.value)
    if (anfitrion) {
      form.elements.areaId.value = String(anfitrion.areaId)
      limpiarError(form.elements.areaId.closest('label.field'))
    }
  })
  form.addEventListener('input', (e) => limpiarError(e.target.closest('label.field')))
  form.addEventListener('change', (e) => limpiarError(e.target.closest('label.field')))
  main.querySelector('[data-cancelar]').addEventListener('click', () => ctx.ir('panel'))

  form.addEventListener('submit', async (e) => {
    e.preventDefault()
    form.querySelector(':scope > .alert')?.remove()
    const datos = {
      nombre: form.elements.nombre.value.trim().replace(/\s+/g, ' '),
      documento: form.elements.documento.value.trim().toUpperCase() || null,
      empresa: form.elements.empresa.value.trim() || null,
      motivo: form.elements.motivo.value,
      anfitrionId: Number(form.elements.anfitrionId.value) || null,
      areaId: Number(form.elements.areaId.value) || null,
    }
    const errores = validar(datos)
    marcarErrores(form, errores)
    if (Object.keys(errores).length) return

    try {
      await conEspera(form.querySelector('[type=submit]'), () => api.registrarVisita(datos))
      aviso('Ingreso registrado correctamente')
      ctx.actualizarContadores()
      ctx.ir('activas')
    } catch (error) {
      if (error.estado === 400 && Object.keys(error.campos).length) {
        marcarErrores(form, error.campos)
      } else if (error.estado === 409) {
        form.querySelector('.form-head').insertAdjacentHTML('afterend', alerta('error', 'No se registró el ingreso', error.message))
      } else if (error.estado !== 401) {
        avisoError(error)
      }
    }
  })

  form.elements.nombre.focus()
}
