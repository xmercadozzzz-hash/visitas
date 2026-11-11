// Comunicación con la API en Java. Todas las peticiones pasan por aquí.

export class ErrorApi extends Error {
  constructor(estado, mensaje, campos = {}) {
    super(mensaje)
    this.estado = estado
    this.campos = campos || {}
  }
}

let alVencer = null

/** Función que se llama cuando el servidor dice que la sesión terminó (401). */
export function alVencerSesion(funcion) {
  alVencer = funcion
}

async function pedir(metodo, ruta, datos) {
  const opciones = { method: metodo, credentials: 'same-origin', headers: { Accept: 'application/json' } }
  if (metodo !== 'GET') {
    // El servidor rechaza los cambios que no traen esta cabecera (protección CSRF)
    opciones.headers['X-Requested-With'] = 'VisitaRegistro'
    if (datos !== undefined) {
      opciones.headers['Content-Type'] = 'application/json'
      opciones.body = JSON.stringify(datos)
    }
  }

  let respuesta
  try {
    respuesta = await fetch(ruta, opciones)
  } catch {
    throw new ErrorApi(0, 'No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.')
  }

  let cuerpo = null
  try {
    cuerpo = await respuesta.json()
  } catch {
    cuerpo = null
  }

  if (!respuesta.ok) {
    const error = new ErrorApi(respuesta.status, cuerpo?.error || 'Ocurrió un error inesperado.', cuerpo?.campos)
    const esInicioDeSesion = ruta === 'api/sesion' && metodo === 'POST'
    if (respuesta.status === 401 && !esInicioDeSesion && alVencer) {
      alVencer(error.message)
    }
    throw error
  }
  return cuerpo
}

function conParametros(ruta, parametros) {
  const consulta = new URLSearchParams()
  Object.entries(parametros).forEach(([clave, valor]) => {
    if (valor !== undefined && valor !== null && String(valor).trim() !== '') {
      consulta.set(clave, String(valor).trim())
    }
  })
  const texto = consulta.toString()
  return texto ? `${ruta}?${texto}` : ruta
}

export const api = {
  /** Datos del usuario conectado, o null si no hay sesión. */
  sesion: () => pedir('GET', 'api/sesion'),
  /** Hora actual de la oficina según el servidor. */
  ahora: async () => {
    const sesion = await pedir('GET', 'api/sesion')
    if (!sesion) {
      const mensaje = 'Tu sesión terminó. Vuelve a iniciar sesión.'
      alVencer?.(mensaje)
      throw new ErrorApi(401, mensaje)
    }
    return sesion.ahora
  },
  iniciarSesion: (correo, contrasena) => pedir('POST', 'api/sesion', { correo, contrasena }),
  cerrarSesion: () => pedir('POST', 'api/sesion/cerrar', {}),
  cuentasDemo: () => pedir('GET', 'api/demo'),
  cambiarContrasena: (datos) => pedir('POST', 'api/perfil/contrasena', datos),

  catalogos: () => pedir('GET', 'api/catalogos'),
  panel: () => pedir('GET', 'api/panel'),
  activas: (texto) => pedir('GET', conParametros('api/visitas/activas', { texto })),
  historial: (filtros) => pedir('GET', conParametros('api/visitas', filtros)),
  detalle: (id) => pedir('GET', `api/visitas/${id}`),
  registrarVisita: (datos) => pedir('POST', 'api/visitas', datos),
  registrarSalida: (id) => pedir('POST', `api/visitas/${id}/salida`, {}),
  reporte: (rango, areaId) => pedir('GET', conParametros('api/reportes', { rango, areaId })),

  usuarios: () => pedir('GET', 'api/usuarios'),
  crearUsuario: (datos) => pedir('POST', 'api/usuarios', datos),
  actualizarUsuario: (id, datos) => pedir('PUT', `api/usuarios/${id}`, datos),
}
