package co.edu.ucc.visitaregistro.web.controladores;

import co.edu.ucc.visitaregistro.servicio.UsuarioServicio;
import co.edu.ucc.visitaregistro.servicio.UsuarioServicio.SolicitudUsuario;
import co.edu.ucc.visitaregistro.web.Enrutador;
import co.edu.ucc.visitaregistro.web.Enrutador.Acceso;
import co.edu.ucc.visitaregistro.web.Enrutador.Respuesta;

/** Rutas de gestión de usuarios (solo administrador; el servicio lo verifica). */
public final class UsuarioControlador {

    private final UsuarioServicio usuarios;

    public UsuarioControlador(UsuarioServicio usuarios) {
        this.usuarios = usuarios;
    }

    public void registrar(Enrutador r) {
        r.get("/api/usuarios", Acceso.CON_SESION, (x, s) -> usuarios.listar(s));

        r.post("/api/usuarios", Acceso.CON_SESION,
            (x, s) -> new Respuesta(201, usuarios.crear(s, x.cuerpo(SolicitudUsuario.class))));

        r.put("/api/usuarios/{id}", Acceso.CON_SESION,
            (x, s) -> usuarios.actualizar(s, x.id(), x.cuerpo(SolicitudUsuario.class)));
    }
}
