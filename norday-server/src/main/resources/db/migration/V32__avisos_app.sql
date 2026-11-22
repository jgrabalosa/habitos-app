-- Avisos de la app, fase 1: los datos. Todavía no se manda ninguno.
--
-- usuario.avisos_app: la preferencia «avisos de la app» (la vuelta y el
-- momento débil), aparte de los recordatorios que el usuario pone a cada cosa
-- suya. Encendida por defecto para todos, también para los que ya existen.
ALTER TABLE usuario ADD COLUMN avisos_app BOOLEAN NOT NULL DEFAULT TRUE;

-- aviso_app: una fila por aviso enviado, para respetar el tope semanal y no
-- repetir texto. tipo es VUELTA_1, VUELTA_2 o MOMENTO_DEBIL; variante es cuál de
-- los textos del tipo se mandó. enviado_en va en UTC, como los demás sellos de
-- auditoría. Sin borrado en cascada: la baja de cuenta la limpia
-- LimpiadorHabitos antes de borrar al usuario.
-- El índice (usuario_id, enviado_en) sirve a «los avisos de este usuario desde
-- tal fecha» y también a buscar por usuario.
CREATE TABLE aviso_app (
    id         SERIAL PRIMARY KEY,
    usuario_id INTEGER     NOT NULL,
    tipo       VARCHAR(20) NOT NULL,
    variante   INTEGER     NOT NULL,
    enviado_en TIMESTAMP   NOT NULL,
    CONSTRAINT FK_aviso_app_usuario
        FOREIGN KEY (usuario_id) REFERENCES usuario(usuario_id)
);

CREATE INDEX IX_aviso_app_usuario_enviado ON aviso_app (usuario_id, enviado_en);
