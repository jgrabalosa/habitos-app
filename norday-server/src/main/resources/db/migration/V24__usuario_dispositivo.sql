-- Enfoque, fase 1: un token de notificaciones por usuario y app.
--
-- FCM da un token por instancia de app, así que con Norday Habits y Norday
-- Focus en el mismo móvil hay dos tokens. Con el único usuario.fcm_token, la
-- última app en arrancar pisaba a la otra y los avisos de una llegaban a la
-- otra. Aquí cada app guarda el suyo: una fila por (usuario, app).
--
-- app_id es el que cada app manda en la cabecera X-Norday-App (habitos,
-- conocimiento, enfoque). fcm_token admite 4096: FCM no publica una longitud
-- máxima. actualizado_en va en UTC, como los demás sellos de auditoría.
-- La restricción única empieza por usuario_ref: sirve también de índice para
-- buscar por usuario, así que no hace falta otro.
CREATE TABLE usuario_dispositivo (
    id             SERIAL PRIMARY KEY,
    usuario_ref    INTEGER       NOT NULL,
    app_id         VARCHAR(30)   NOT NULL,
    fcm_token      VARCHAR(4096) NOT NULL,
    actualizado_en TIMESTAMP     NOT NULL,
    CONSTRAINT UK_usuario_dispositivo_usuario_app UNIQUE (usuario_ref, app_id),
    CONSTRAINT FK_usuario_dispositivo_usuario
        FOREIGN KEY (usuario_ref) REFERENCES usuario(usuario_id)
);

-- Relleno: hasta hoy solo había una app que registrara token, Norday Habits,
-- así que cada usuario.fcm_token es el suyo. Los vacíos no se copian: nunca
-- se les ha podido enviar nada. usuario.fcm_token no se toca: el backend lo
-- sigue escribiendo para habitos durante la transición.
INSERT INTO usuario_dispositivo (usuario_ref, app_id, fcm_token, actualizado_en)
SELECT usuario_id, 'habitos', fcm_token, (NOW() AT TIME ZONE 'UTC')
FROM usuario
WHERE fcm_token IS NOT NULL AND BTRIM(fcm_token) <> '';
