// Mi perfil: datos de la cuenta y cambio de contraseña.

import { api } from '../api.js'
import { aviso, avisoError, boton, campo, conEspera, esc, insignia, limpiarError, marcarErrores, tituloPagina } from '../ui.js'
import { fechaHora, iniciales } from '../formato.js'

export async function paginaPerfil(main, ctx) {
  const u = ctx.usuario
  const detalle = [u.correo, u.anfitrion ? `Anfitrión: ${u.anfitrion}` : null,
    u.ultimoAcceso ? `Último acceso: ${fechaHora(u.ultimoAcceso)}` : null].filter(Boolean).join(' · ')

  main.innerHTML = tituloPagina({ titulo: 'Mi perfil', texto: 'Información de tu cuenta y permisos asignados.' })
    + `<section class="form-panel profile-card"><div class="avatar large">${esc(iniciales(u.nombre))}</div><div><h2>${esc(u.nombre)}</h2><p>${esc(u.rolEtiqueta)}</p>${insignia('Cuenta activa', 'green')}<p>${esc(detalle)}</p></div></section>`
    + `<form class="form-panel" novalidate>
        <div class="form-head sin-paso"><div><h2>Cambiar contraseña</h2><p>Usa al menos 8 caracteres, con letras y números.</p></div></div>
        <div class="form-grid">
          ${campo({ etiqueta: 'Contraseña actual', nombre: 'actual', requerido: true, control: '<input type="password" name="actual" autocomplete="current-password" maxlength="72">' })}
          <div></div>
          ${campo({ etiqueta: 'Nueva contraseña', nombre: 'nueva', requerido: true, control: '<input type="password" name="nueva" autocomplete="new-password" maxlength="72">' })}
          ${campo({ etiqueta: 'Confirmar nueva contraseña', nombre: 'confirmacion', requerido: true, control: '<input type="password" name="confirmacion" autocomplete="new-password" maxlength="72">' })}
        </div>
        <div class="form-actions">${boton('Guardar contraseña', { tipo: 'submit', icon: 'check' })}</div>
      </form>`

  const form = main.querySelector('form')
  form.addEventListener('input', (e) => limpiarError(e.target.closest('label.field')))
  form.addEventListener('submit', async (e) => {
    e.preventDefault()
    const f = form.elements
    const errores = {}
    if (!f.actual.value) errores.actual = 'Este campo es obligatorio.'
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(f.nueva.value)) {
      errores.nueva = f.nueva.value ? 'Usa al menos 8 caracteres, con letras y números.' : 'Este campo es obligatorio.'
    }
    if (f.nueva.value && f.nueva.value !== f.confirmacion.value) errores.confirmacion = 'Las contraseñas no coinciden.'
    marcarErrores(form, errores)
    if (Object.keys(errores).length) return
    try {
      await conEspera(form.querySelector('[type=submit]'), () => api.cambiarContrasena({
        actual: f.actual.value, nueva: f.nueva.value, confirmacion: f.confirmacion.value,
      }))
      form.reset()
      aviso('Contraseña actualizada correctamente', 'Úsala la próxima vez que inicies sesión.')
    } catch (error) {
      if (error.estado === 400 && Object.keys(error.campos).length) marcarErrores(form, error.campos)
      else if (error.estado !== 401) avisoError(error)
    }
  })
}
