// CU-07: el administrador crea usuarios, cambia su rol y los activa o desactiva.

import { api } from '../api.js'
import { abrirModal, alerta, aviso, avisoError, boton, campo, conEspera, esc, insignia, limpiarError, marcarErrores, opciones, tituloPagina } from '../ui.js'

const ROLES = [
  { valor: 'RECEPCIONISTA', texto: 'Recepcionista' },
  { valor: 'ANFITRION', texto: 'Empleado anfitrión' },
  { valor: 'ADMINISTRADOR', texto: 'Administrador de recepción' },
]

export async function paginaUsuarios(main, ctx) {
  const [usuarios, catalogos] = await Promise.all([api.usuarios(), ctx.catalogos()])

  main.innerHTML = tituloPagina({
    titulo: 'Usuarios y permisos',
    texto: 'Administra el acceso y los roles del equipo de recepción.',
    acciones: boton('Agregar usuario', { icon: 'user-plus', atributos: 'data-agregar' }),
  }) + `<section class="panel"><div class="panel-head"><div><h2>Usuarios del sistema</h2><p>${usuarios.length} usuarios registrados</p></div></div>`
    + `<div class="table-wrap"><table><thead><tr><th>Nombre</th><th>Usuario o correo</th><th>Rol</th><th>Estado</th><th></th></tr></thead><tbody>${usuarios.map((u) =>
      `<tr><td><strong>${esc(u.nombre)}</strong></td><td>${esc(u.correo)}</td><td>${esc(u.rolEtiqueta)}${u.anfitrion ? `<span class="cell-sub">Anfitrión: ${esc(u.anfitrion)}</span>` : ''}</td><td>${insignia(u.activo ? 'Activo' : 'Inactivo', u.activo ? 'green' : 'gray')}</td><td><button type="button" class="text-link" data-editar="${u.id}">Editar</button></td></tr>`).join('')}</tbody></table></div></section>`

  main.querySelector('[data-agregar]').addEventListener('click', () => abrirFormulario(null, catalogos, ctx))
  main.querySelectorAll('[data-editar]').forEach((b) => b.addEventListener('click', () =>
    abrirFormulario(usuarios.find((u) => u.id === Number(b.dataset.editar)), catalogos, ctx)))
}

function abrirFormulario(usuario, catalogos, ctx) {
  const editando = Boolean(usuario)
  const esUnoMismo = editando && usuario.id === ctx.usuario.id
  const anfitriones = catalogos.anfitriones.map((a) => ({ valor: a.id, texto: `${a.nombre} · ${a.area}` }))

  const modal = abrirModal(editando ? 'Editar usuario' : 'Agregar usuario', `<form novalidate>
      <div class="modal-form">
        ${campo({ etiqueta: 'Nombre completo', nombre: 'nombre', requerido: true,
          control: `<input name="nombre" placeholder="Nombre y apellido" maxlength="100" value="${esc(usuario?.nombre)}">` })}
        ${campo({ etiqueta: 'Correo corporativo', nombre: 'correo', requerido: !editando,
          ayuda: editando ? 'El correo no se puede cambiar.' : undefined,
          control: `<input name="correo" type="email" placeholder="nombre@empresa.com" maxlength="150" value="${esc(usuario?.correo)}"${editando ? ' disabled' : ''}>` })}
        ${campo({ etiqueta: 'Rol asignado', nombre: 'rol', requerido: true,
          ayuda: esUnoMismo ? 'No puedes cambiar tu propio rol.' : undefined,
          control: `<select name="rol"${esUnoMismo ? ' disabled' : ''}>${opciones(ROLES, usuario?.rol || 'RECEPCIONISTA')}</select>` })}
        <div data-bloque-anfitrion>${campo({ etiqueta: 'Empleado anfitrión vinculado', nombre: 'anfitrionId', requerido: true,
          ayuda: 'Solo verá las visitas de este anfitrión.',
          control: `<select name="anfitrionId">${opciones(anfitriones, usuario?.anfitrionId, 'Seleccionar anfitrión')}</select>` })}</div>
        ${campo({ etiqueta: editando ? 'Nueva contraseña' : 'Contraseña temporal', nombre: 'contrasena', requerido: !editando,
          ayuda: editando ? 'Déjala vacía para no cambiarla.' : 'Mínimo 8 caracteres, con letras y números.',
          control: `<input name="contrasena" type="password" autocomplete="new-password" maxlength="72" placeholder="${editando ? 'Sin cambios' : 'Contraseña inicial'}">` })}
        ${editando ? campo({ etiqueta: 'Estado', nombre: 'activo',
          control: `<select name="activo"${esUnoMismo ? ' disabled' : ''}>${opciones([{ valor: 'true', texto: 'Activo' }, { valor: 'false', texto: 'Inactivo' }], String(usuario.activo))}</select>` }) : ''}
      </div>
      <div class="modal-actions">${boton('Cancelar', { variante: 'secondary', atributos: 'data-cerrar' })}${boton('Guardar usuario', { tipo: 'submit' })}</div>
    </form>`)

  const form = modal.elemento.querySelector('form')
  modal.elemento.querySelector('.modal-actions [data-cerrar]').addEventListener('click', modal.cerrar)
  const bloqueAnfitrion = form.querySelector('[data-bloque-anfitrion]')
  const mostrarAnfitrion = () => {
    bloqueAnfitrion.hidden = form.elements.rol.value !== 'ANFITRION'
  }
  form.elements.rol.addEventListener('change', mostrarAnfitrion)
  mostrarAnfitrion()
  form.addEventListener('input', (e) => limpiarError(e.target.closest('label.field')))

  form.addEventListener('submit', async (e) => {
    e.preventDefault()
    form.querySelector('.alert')?.remove()
    const f = form.elements
    const datos = {
      nombre: f.nombre.value.trim(),
      correo: editando ? usuario.correo : f.correo.value.trim(),
      rol: f.rol.value,
      anfitrionId: f.rol.value === 'ANFITRION' ? Number(f.anfitrionId.value) || null : null,
      contrasena: f.contrasena.value || null,
      activo: editando ? f.activo.value === 'true' : true,
    }
    const errores = {}
    if (datos.nombre.length < 3) errores.nombre = datos.nombre ? 'Escribe entre 3 y 100 caracteres.' : 'Este campo es obligatorio.'
    if (!editando && !/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(datos.correo)) errores.correo = datos.correo ? 'Escribe un correo válido, por ejemplo nombre@empresa.com.' : 'Este campo es obligatorio.'
    if (datos.rol === 'ANFITRION' && !datos.anfitrionId) errores.anfitrionId = 'Selecciona el empleado anfitrión que corresponde a esta cuenta.'
    if ((!editando || datos.contrasena) && !/^(?=.*[A-Za-z])(?=.*\d).{8,72}$/.test(datos.contrasena || '')) {
      errores.contrasena = datos.contrasena ? 'Usa al menos 8 caracteres, con letras y números.' : 'Este campo es obligatorio.'
    }
    marcarErrores(form, errores)
    if (Object.keys(errores).length) return

    try {
      await conEspera(form.querySelector('[type=submit]'),
        () => (editando ? api.actualizarUsuario(usuario.id, datos) : api.crearUsuario(datos)))
      modal.cerrar()
      aviso(editando ? 'Usuario actualizado correctamente' : 'Usuario creado correctamente')
      ctx.ir('usuarios')
    } catch (error) {
      if (error.estado === 400 && Object.keys(error.campos).length) {
        marcarErrores(form, error.campos)
      } else if (error.estado === 409) {
        form.insertAdjacentHTML('afterbegin', alerta('error', 'No se guardaron los cambios', error.message))
      } else if (error.estado !== 401) {
        modal.cerrar()
        avisoError(error)
      }
    }
  })
}
