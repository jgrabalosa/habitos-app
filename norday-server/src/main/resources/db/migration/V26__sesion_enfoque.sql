-- Enfoque, fase 4: las sesiones de concentración de Norday Focus.
--
-- Una fila por sesión. clave es el UUID que la app genera al empezar la
-- sesión: única, para que un reintento (la cola sin conexión) no cree otra
-- fila; la misma clave viaja al hábito, así que tampoco suma dos veces allí.
-- inicio_utc y creado_en van en UTC. dia es el día de inicio en la zona del
-- usuario: el día al que van los minutos.
--
-- destino_tipo y destino_ref dicen a dónde iban los minutos ('habito' y su
-- id). destino_ref es opaco y sin FK: el módulo enfoque no conoce las tablas
-- del destino, y borrar el hábito no debe borrar la sesión.
--
-- aporte_estado: SIN_DESTINO, PENDIENTE (guardada, aún no llevada al destino),
-- ACEPTADO, REPETIDO o RECHAZADO (con su motivo, una clave de los textos).
-- minutos_aportados: los que están en el destino por esta sesión.
-- monedas_aportadas, xp_aportada y logros_aportados: lo que pagó el destino
-- al llevar los minutos (los logros, códigos separados por comas; NULL si
-- ninguno). Se guardan para que un reintento devuelva el resultado original.
--
-- Índices: (usuario_ref, dia) para el resumen y la racha; (usuario_ref,
-- inicio_utc) para el historial y la comprobación de solape.
CREATE TABLE sesion_enfoque (
    id                    SERIAL PRIMARY KEY,
    usuario_ref           INTEGER     NOT NULL,
    clave                 UUID        NOT NULL,
    inicio_utc            TIMESTAMP   NOT NULL,
    duracion_seg          INTEGER     NOT NULL,
    duracion_objetivo_min INTEGER     NOT NULL,
    estado                VARCHAR(12) NOT NULL,
    dia                   DATE        NOT NULL,
    destino_tipo          VARCHAR(20),
    destino_ref           BIGINT,
    aporte_estado         VARCHAR(12) NOT NULL,
    aporte_motivo         VARCHAR(40),
    minutos_aportados     INTEGER     NOT NULL DEFAULT 0,
    monedas_aportadas     INTEGER     NOT NULL DEFAULT 0,
    xp_aportada           INTEGER     NOT NULL DEFAULT 0,
    logros_aportados      VARCHAR(1000),
    creado_en             TIMESTAMP   NOT NULL,
    CONSTRAINT UK_sesion_enfoque_clave UNIQUE (clave),
    CONSTRAINT FK_sesion_enfoque_usuario
        FOREIGN KEY (usuario_ref) REFERENCES usuario(usuario_id)
);

CREATE INDEX idx_sesion_enfoque_usuario_dia ON sesion_enfoque (usuario_ref, dia);
CREATE INDEX idx_sesion_enfoque_usuario_inicio ON sesion_enfoque (usuario_ref, inicio_utc);
