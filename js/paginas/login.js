// Inicio de sesión. Valida en el navegador y luego el servidor verifica
// las credenciales contra la base de datos.

import { api } from '../api.js'
import { alerta, boton, campo, esc, icono, marcarErrores, limpiarError, conEspera } from '../ui.js'

export async function mostrarLogin(raiz, { mensaje, alEntrar }) {
  raiz.innerHTML = `<div class="login-shell">
    <div class="login-brand"><div class="brand-mark">VR</div><div><strong>VisitaRegistro</strong><span>Control de recepción</span></div></div>
    <main class="login-card">
      <div class="login-copy"><span class="eyebrow">ACCESO AL SISTEMA</span><h1>Bienvenido de nuevo</h1><p>Ingresa tus credenciales para continuar.</p></div>
      <form novalidate>
        ${campo({ etiqueta: 'Usuario o correo electrónico', nombre: 'correo', requerido: true,
          control: '<input name="correo" type="email" autocomplete="username" placeholder="nombre@empresa.com" maxlength="150">' })}
        ${campo({ etiqueta: 'Contraseña', nombre: 'contrasena', requerido: true,
          control: '<input name="contrasena" type="password" autocomplete="current-password" placeholder="Ingresa tu contraseña" maxlength="72">' })}
        ${boton(`Iniciar sesión ${icono('arrow')}`, { tipo: 'submit' })}
      </form>
      <div class="demo-box" hidden>
        <div><strong>Acceso de demostración</strong><span>Selecciona un rol para explorar sus permisos.</span></div>
        <select aria-label="Cuenta de demostración"></select>
        <small>Cada rol llena el correo y la contraseña de su cuenta de prueba.</small>
      </div>
    </main>
    <footer>VisitaRegistro · Diseño y experiencia de usuario · Universidad Cooperativa de Colombia</footer>
  </div>`

  const form = raiz.querySelector('form')
  const correo = form.elements.correo
  const contrasena = form.elements.contrasena

  const mostrarAlerta = (titulo, texto) => {
    raiz.querySelector('.login-card > .alert')?.remove()
    if (titulo) form.insertAdjacentHTML('beforebegin', alerta('error', titulo, texto))
  }

  if (mensaje) mostrarAlerta('Inicia sesión de nuevo', mensaje)

  form.addEventListener('input', (e) => limpiarError(e.target.closest('label.field')))

  form.addEventListener('submit', async (e) => {
    e.preventDefault()
    const errores = {}
    if (!correo.value.trim()) errores.correo = 'Este campo es obligatorio.'
    if (!contrasena.value) errores.contrasena = 'Este campo es obligatorio.'
    if (Object.keys(errores).length) {
      mostrarAlerta('Completa los campos requeridos', 'Ingresa tu correo y contraseña para continuar.')
      marcarErrores(form, errores)
      return
    }
    marcarErrores(form, {})
    mostrarAlerta(null)
    try {
      const sesion = await conEspera(form.querySelector('[type=submit]'),
        () => api.iniciarSesion(correo.value.trim(), contrasena.value))
      alEntrar(sesion)
    } catch (error) {
      if (error.estado === 400 && Object.keys(error.campos).length) {
        mostrarAlerta('Completa los campos requeridos', 'Ingresa tu correo y contraseña para continuar.')
        marcarErrores(form, error.campos)
      } else {
        mostrarAlerta('No pudimos iniciar sesión', error.message)
        contrasena.select()
      }
    }
  })

  // Cuentas de prueba (solo si el servidor tiene activado DATOS_DEMO)
  try {
    const cuentas = await api.cuentasDemo()
    if (cuentas.length) {
      const caja = raiz.querySelector('.demo-box')
      const selector = caja.querySelector('select')
      selector.innerHTML = cuentas.map((c, i) => `<option value="${i}">${esc(c.rol)}</option>`).join('')
      const llenar = () => {
        const cuenta = cuentas[Number(selector.value)]
        correo.value = cuenta.correo
        contrasena.value = cuenta.contrasena
        marcarErrores(form, {})
        mostrarAlerta(null)
      }
      selector.addEventListener('change', llenar)
      caja.hidden = false
      if (!mensaje) llenar()
    }
  } catch {
    // sin cuentas de prueba, el formulario funciona igual
  }
}
