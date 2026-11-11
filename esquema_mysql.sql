-- =====================================================================
-- VisitaRegistro · Base de datos relacional (MySQL 8 o superior)
-- =====================================================================
-- No hace falta ejecutar este archivo: el servidor Java crea las tablas
-- solo la primera vez que arranca (clase bd/Esquema.java). Sirve para
-- revisar el modelo o para crear las tablas a mano en phpMyAdmin.
--
-- Cinco entidades, como en el documento de requerimientos:
--   areas               Áreas de la oficina
--   anfitriones         Empleados que reciben visitantes (pertenecen a un área)
--   usuarios_recepcion  Cuentas de acceso y su rol
--   visitantes          Datos mínimos de cada visitante
--   visitas             Ingreso, salida, duración y estado de cada visita
--
-- Las restricciones CHECK hacen que la base rechace datos incoherentes:
-- una visita ACTIVA no tiene salida; una FINALIZADA tiene salida posterior
-- al ingreso y una duración en minutos.
-- =====================================================================

CREATE TABLE areas (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(80) NOT NULL,
  activa BOOLEAN DEFAULT TRUE NOT NULL,
  CONSTRAINT uq_areas_nombre UNIQUE (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE anfitriones (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  correo VARCHAR(150),
  area_id INTEGER NOT NULL,
  activo BOOLEAN DEFAULT TRUE NOT NULL,
  CONSTRAINT fk_anfitriones_area FOREIGN KEY (area_id) REFERENCES areas (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE usuarios_recepcion (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  correo VARCHAR(150) NOT NULL,
  contrasena_hash VARCHAR(255) NOT NULL,
  rol VARCHAR(20) NOT NULL,
  activo BOOLEAN DEFAULT TRUE NOT NULL,
  anfitrion_id INTEGER,
  creado_en DATETIME NOT NULL,
  ultimo_acceso DATETIME,
  CONSTRAINT uq_usuarios_correo UNIQUE (correo),
  CONSTRAINT ck_usuarios_rol CHECK (rol IN ('RECEPCIONISTA', 'ANFITRION', 'ADMINISTRADOR')),
  CONSTRAINT fk_usuarios_anfitrion FOREIGN KEY (anfitrion_id) REFERENCES anfitriones (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE visitantes (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(100) NOT NULL,
  documento VARCHAR(20),
  empresa VARCHAR(100),
  creado_en DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_visitantes_documento ON visitantes (documento);

CREATE TABLE visitas (
  id INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  visitante_id INTEGER NOT NULL,
  anfitrion_id INTEGER NOT NULL,
  area_id INTEGER NOT NULL,
  motivo VARCHAR(60) NOT NULL,
  ingreso DATETIME NOT NULL,
  salida DATETIME,
  duracion_minutos INTEGER,
  estado VARCHAR(12) NOT NULL,
  registrado_por INTEGER NOT NULL,
  salida_registrada_por INTEGER,
  CONSTRAINT fk_visitas_visitante FOREIGN KEY (visitante_id) REFERENCES visitantes (id),
  CONSTRAINT fk_visitas_anfitrion FOREIGN KEY (anfitrion_id) REFERENCES anfitriones (id),
  CONSTRAINT fk_visitas_area FOREIGN KEY (area_id) REFERENCES areas (id),
  CONSTRAINT fk_visitas_registro FOREIGN KEY (registrado_por) REFERENCES usuarios_recepcion (id),
  CONSTRAINT fk_visitas_salida FOREIGN KEY (salida_registrada_por) REFERENCES usuarios_recepcion (id),
  CONSTRAINT ck_visitas_estado CHECK (
    (estado = 'ACTIVA' AND salida IS NULL AND duracion_minutos IS NULL)
    OR (estado = 'FINALIZADA' AND salida IS NOT NULL AND salida >= ingreso AND duracion_minutos >= 0)
  )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_visitas_ingreso ON visitas (ingreso);
CREATE INDEX idx_visitas_estado ON visitas (estado);

-- =====================================================================
-- Consultas de ejemplo
-- =====================================================================

-- Visitas activas (sin salida registrada), de la más antigua a la más reciente
SELECT vi.nombre AS visitante, a.nombre AS anfitrion, ar.nombre AS area, v.ingreso
FROM visitas v
JOIN visitantes vi ON vi.id = v.visitante_id
JOIN anfitriones a ON a.id = v.anfitrion_id
JOIN areas ar ON ar.id = v.area_id
WHERE v.estado = 'ACTIVA'
ORDER BY v.ingreso;

-- Visitas y duración promedio por área en los últimos 7 días
SELECT ar.nombre AS area, COUNT(*) AS visitas, ROUND(AVG(v.duracion_minutos)) AS minutos_promedio
FROM visitas v
JOIN areas ar ON ar.id = v.area_id
WHERE v.ingreso >= CURDATE() - INTERVAL 6 DAY
GROUP BY ar.nombre
ORDER BY visitas DESC;

-- Visitas por día
SELECT DATE(v.ingreso) AS dia, COUNT(*) AS visitas
FROM visitas v
GROUP BY DATE(v.ingreso)
ORDER BY dia DESC;
