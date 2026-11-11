# VisitaRegistro

Sistema de registro de visitantes y control de recepción.
Proyecto de **Diseño y experiencia de usuario** – Universidad Cooperativa de Colombia, 2026.

La interfaz es la del prototipo aprobado en Figma, ahora conectada a una base
de datos real: cada ingreso, salida y usuario queda guardado.

| Capa | Tecnología |
|---|---|
| Frontend | HTML5, CSS3 y JavaScript (carpeta `web/`) |
| Servidor / API | Java 21, sin frameworks: servidor HTTP del JDK y JDBC (carpeta `src/`) |
| Base de datos | MySQL en producción; HSQLDB embebida para ejecutarlo en el computador |

## Ejecutarlo en el computador

1. Instala **Java 21 (JDK)**. En Windows, desde la terminal:
   ```
   winget install EclipseAdoptium.Temurin.21.JDK
   ```
   Cierra y vuelve a abrir la terminal después de instalarlo.
2. Doble clic en **`ejecutar.cmd`** (Windows) o ejecuta `./ejecutar.sh` (Linux/macOS).
   La primera vez descarga tres librerías de internet (necesita conexión).
3. Abre **http://localhost:8080**.

Sin configurar nada usa una base de datos local en la carpeta `datos/`, que se crea sola.

### Cuentas de prueba

| Rol | Correo | Contraseña |
|---|---|---|
| Recepcionista | laura.vargas@empresa.com | Recepcion2026 |
| Empleado anfitrión | ana.torres@empresa.com | Anfitrion2026 |
| Administrador de recepción | miguel.santos@empresa.com | Admin2026 |

En la pantalla de inicio, el selector **Acceso de demostración** llena estos datos.
Existen mientras `DATOS_DEMO` esté activo (valor por defecto).

## Pruebas automáticas

`probar.cmd` (Windows) o `./probar.sh` ejecutan 24 pruebas JUnit que verifican el
registro de ingresos y salidas, el cálculo de la duración, las visitas activas,
la búsqueda, los reportes, la validación en el servidor y los permisos de cada rol.

## Estructura

```
web/                         Frontend (HTML5, CSS3, JavaScript)
  index.html
  css/base.css               Reinicio de estilos del prototipo
  css/estilos.css            Estilos del prototipo de Figma
  js/app.js                  Arranque, menú y navegación
  js/api.js                  Peticiones a la API
  js/ui.js                   Componentes (botones, campos, tablas, ventanas)
  js/formato.js              Fechas y duraciones en español
  js/paginas/*.js            Una pantalla por archivo
src/main/java/co/edu/ucc/visitaregistro/
  Aplicacion.java            Punto de entrada
  Sistema.java               Arma y arranca todas las piezas
  config/                    Variables de entorno y datos iniciales
  bd/                        Conexión, esquema de tablas y ayudas JDBC
  modelo/                    Entidades
  repositorio/               Consultas SQL (siempre con parámetros ?)
  servicio/                  Reglas de negocio, permisos y validaciones
  seguridad/                 Contraseñas, sesiones y bloqueo de intentos
  web/                       Servidor HTTP, rutas y controladores
src/test/java/               Pruebas JUnit
base_de_datos/esquema_mysql.sql   Modelo relacional y consultas de ejemplo
herramientas/                Descarga de librerías (no necesita Maven)
Dockerfile                   Imagen para publicarlo en internet
```

## Base de datos

Las tablas se crean solas al arrancar. El script equivalente está en
`base_de_datos/esquema_mysql.sql`.

```mermaid
erDiagram
    areas ||--o{ anfitriones : "tiene"
    areas ||--o{ visitas : "recibe"
    anfitriones ||--o{ visitas : "atiende"
    anfitriones |o--o{ usuarios_recepcion : "vinculado a"
    visitantes ||--o{ visitas : "realiza"
    usuarios_recepcion ||--o{ visitas : "registra"
```

## Cómo cumple el documento de requerimientos

| Requerimiento | Dónde se cumple |
|---|---|
| RF-01 Registrar visitante con datos mínimos, empresa y motivo | Pantalla *Registrar visitante*; `VisitaServicio.registrar` |
| RF-02 Anfitrión o área visitada | Campos obligatorios; al elegir el anfitrión se propone su área |
| RF-03 Fecha y hora de ingreso | La pone el servidor al registrar (no la escribe el usuario) |
| RF-04 Salida y duración | *Registrar salida*; la duración se calcula y se guarda en `duracion_minutos` |
| RF-05 Búsqueda por fecha, visitante o anfitrión | *Historial de visitas* con filtros; `GET /api/visitas` |
| RF-06 Visitantes sin salida | *Visitas activas*, contador en el menú y aviso de visitas de más de 4 h |
| RF-07 Reportes por día o área | *Reportes*: hoy, 7 días o mes, filtrable por área |
| RF-08 Acceso al historial por rol y retención | `servicio/Permisos.java` y `RetencionServicio` (ver abajo) |
| RNF-01 Usabilidad | Diseño del prototipo aprobado, mensajes claros y estados vacíos |
| RNF-02 Seguridad | Sesiones en el servidor, contraseñas cifradas, permisos verificados en cada petición |
| RNF-03 Validación en navegador y servidor | Mismas reglas en `js/paginas/registrar.js` y `servicio/Validacion.java` |
| RNF-04 Integridad | Fechas guardadas por el servidor; restricciones CHECK y llaves foráneas en la base |
| RNF-05 Escritorio y móvil | Diseño adaptable del prototipo (menú lateral desplegable en móvil) |
| RNF-06 Mantenibilidad | Código separado por capas y pantallas; este README |

**Permisos por rol**

- *Recepcionista*: registra ingresos y salidas, ve las visitas activas, los reportes
  y el historial de los últimos 30 días.
- *Empleado anfitrión*: solo ve las visitas en las que él es el anfitrión, sin el
  documento de identidad del visitante. No registra ingresos ni ve reportes.
- *Administrador de recepción*: todo lo anterior sin límite de días y la gestión de
  usuarios (crear, cambiar rol, activar o desactivar).
- *Retención*: las visitas finalizadas con más de 365 días se eliminan cada día,
  junto con los visitantes que ya no tienen visitas.

**Seguridad**

- Contraseñas guardadas con PBKDF2-SHA256 y sal aleatoria, nunca en texto plano.
- Sesión en el servidor con cookie `HttpOnly` y `SameSite=Lax`; expira tras 30 minutos sin uso.
- Bloqueo de 5 minutos después de 5 intentos fallidos de inicio de sesión.
- Todas las consultas SQL usan parámetros (sin inyección SQL) y el frontend escapa
  los textos que muestra (sin XSS).
- Las peticiones que modifican datos exigen la cabecera `X-Requested-With` (protección CSRF).

## Publicarlo en internet (Render + MySQL)

1. Sube esta carpeta a un repositorio de GitHub (todo lo que hay aquí; no hace falta `lib/` ni `build/`).
2. En **Render**: *New* → *Web Service* → elige el repositorio. Render detecta el
   `Dockerfile`. Elige el plan *Free*.
3. En *Environment* agrega las variables de la base de datos MySQL:

| Variable | Valor |
|---|---|
| `DB_HOST` | Host del servidor MySQL |
| `DB_PORT` | Puerto (en Clever Cloud no siempre es 3306) |
| `DB_NAME` | Nombre de la base de datos |
| `DB_USER` | Usuario |
| `DB_PASSWORD` | Contraseña |

En vez de esas cinco puedes poner solo `DATABASE_URL` con la URL
`mysql://usuario:clave@host:puerto/base` (en Clever Cloud es `MYSQL_ADDON_URI`).

Variables opcionales:

| Variable | Para qué | Por defecto |
|---|---|---|
| `DATOS_DEMO` | Crear cuentas y visitas de ejemplo | `true` |
| `ADMIN_CORREO` / `ADMIN_CONTRASENA` | Crear un administrador propio | — |
| `RETENCION_DIAS` | Días que se conservan las visitas finalizadas | `365` |
| `HISTORIAL_DIAS_RECEPCION` | Días de historial que ve la recepcionista | `30` |
| `SESION_MINUTOS` | Minutos sin uso para cerrar la sesión | `30` |
| `ZONA_HORARIA` | Hora de la oficina | `America/Bogota` |
| `NOMBRE_OFICINA` | Texto de la cabecera | `Oficina Central` |
| `DB_POOL` | Conexiones simultáneas a la base | `3` |

Al arrancar, los registros (*Logs*) muestran `Tablas creadas` la primera vez y
`VisitaRegistro listo`. Para un uso real, pon `DATOS_DEMO=false` y define
`ADMIN_CORREO` y `ADMIN_CONTRASENA`.

## API

| Método | Ruta | Uso |
|---|---|---|
| POST | `/api/sesion` | Iniciar sesión |
| POST | `/api/sesion/cerrar` | Cerrar sesión |
| GET | `/api/sesion` | Usuario conectado y permisos |
| GET | `/api/panel` | Resumen del día |
| GET | `/api/catalogos` | Áreas, anfitriones y motivos |
| POST | `/api/visitas` | Registrar ingreso |
| GET | `/api/visitas/activas` | Visitas sin salida |
| GET | `/api/visitas?desde&hasta&texto&anfitrionId&areaId&estado` | Historial |
| GET | `/api/visitas/{id}` | Detalle |
| POST | `/api/visitas/{id}/salida` | Registrar salida |
| GET | `/api/reportes?rango=hoy\|7d\|mes&areaId` | Reportes |
| GET, POST | `/api/usuarios` | Listar y crear usuarios (administrador) |
| PUT | `/api/usuarios/{id}` | Editar usuario (administrador) |
| POST | `/api/perfil/contrasena` | Cambiar la contraseña propia |
| GET | `/api/salud` | Estado del servidor y de la base de datos |
