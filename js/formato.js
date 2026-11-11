// Formatos de fecha, hora y duración en español.
// El servidor envía las fechas con la hora de la oficina ("2026-06-18T08:42:00"),
// así que se leen tal cual, sin convertir de zona horaria.

const MESES = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'septiembre', 'octubre', 'noviembre', 'diciembre']
const MESES_CORTOS = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'sep', 'oct', 'nov', 'dic']
const DIAS = ['domingo', 'lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado']

export function partes(iso) {
  const [fecha, hora = '00:00'] = String(iso).split('T')
  const [anio, mes, dia] = fecha.split('-').map(Number)
  const [h, m] = hora.split(':').map(Number)
  return { anio, mes, dia, hora: h, minuto: m }
}

const dos = (n) => String(n).padStart(2, '0')

/** "18 jun 2025" */
export function fechaCorta(iso) {
  const p = partes(iso)
  return `${p.dia} ${MESES_CORTOS[p.mes - 1]} ${p.anio}`
}

/** "08:42" */
export function hora(iso) {
  const p = partes(iso)
  return `${dos(p.hora)}:${dos(p.minuto)}`
}

/** "18 jun 2025, 08:42" */
export function fechaHora(iso) {
  return `${fechaCorta(iso)}, ${hora(iso)}`
}

/** "MIÉRCOLES, 18 DE JUNIO" */
export function fechaTitulo(iso) {
  const p = partes(iso)
  const dia = new Date(Date.UTC(p.anio, p.mes - 1, p.dia)).getUTCDay()
  return `${DIAS[dia]}, ${p.dia} de ${MESES[p.mes - 1]}`.toUpperCase()
}

/** "Del 12 al 18 de junio", "Del 28 de mayo al 3 de junio" o "18 de junio" */
export function rango(desde, hasta) {
  const a = partes(desde)
  const b = partes(hasta)
  if (desde === hasta) return `${b.dia} de ${MESES[b.mes - 1]}`
  if (a.mes === b.mes && a.anio === b.anio) return `Del ${a.dia} al ${b.dia} de ${MESES[b.mes - 1]}`
  return `Del ${a.dia} de ${MESES[a.mes - 1]} al ${b.dia} de ${MESES[b.mes - 1]}`
}

/** 86 → "1 h 26 min", 52 → "52 min" */
export function duracion(minutos) {
  if (minutos === null || minutos === undefined) return '—'
  const h = Math.floor(minutos / 60)
  const m = minutos % 60
  if (h === 0) return `${m} min`
  return m === 0 ? `${h} h` : `${h} h ${m} min`
}

/** "Laura Vargas" → "LV" */
export function iniciales(nombre) {
  return String(nombre || '')
    .split(/\s+/)
    .filter(Boolean)
    .map((p) => p[0])
    .join('')
    .slice(0, 2)
    .toUpperCase()
}

export function primerNombre(nombre) {
  return String(nombre || '').split(/\s+/)[0]
}

export function saludo(iso) {
  const h = partes(iso).hora
  if (h < 12) return 'Buenos días'
  if (h < 19) return 'Buenas tardes'
  return 'Buenas noches'
}

/** Fecha para un campo <input type="date">: "2026-06-18" */
export function fechaCampo(iso) {
  return String(iso).slice(0, 10)
}

/** Suma minutos a una fecha ISO local y devuelve otra fecha ISO local. */
export function sumarMinutos(iso, minutos) {
  const p = partes(iso)
  const d = new Date(Date.UTC(p.anio, p.mes - 1, p.dia, p.hora, p.minuto) + minutos * 60000)
  return `${d.getUTCFullYear()}-${dos(d.getUTCMonth() + 1)}-${dos(d.getUTCDate())}T${dos(d.getUTCHours())}:${dos(d.getUTCMinutes())}:00`
}
