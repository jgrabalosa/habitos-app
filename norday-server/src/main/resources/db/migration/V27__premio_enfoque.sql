-- Norday Focus paga lo suyo (16-nov-2026): cada sesión que cuenta da monedas y
-- XP de Nori, tenga hábito destino o no, y desbloquea los logros de Focus.
--
-- premio_monedas y premio_xp: lo que pagó la sesión por sí misma, ya recortado
-- por el tope diario. Es aparte de monedas_aportadas y xp_aportada, que son lo
-- que pagó el hábito destino al recibir los minutos.
-- premio_logros: los logros que desbloqueó la sesión (los de Focus y los de
-- fase de Nori por su XP), códigos separados por comas; NULL si ninguno.
-- premio_tope: si con esta sesión el día llegó al tope de monedas.
--
-- Se guardan para que un reintento con la misma clave devuelva el premio
-- original sin volver a pagarlo. Las sesiones de antes de esta migración se
-- quedan con 0: el premio no es retroactivo.
ALTER TABLE sesion_enfoque
    ADD COLUMN premio_monedas INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN premio_xp      INTEGER       NOT NULL DEFAULT 0,
    ADD COLUMN premio_logros  VARCHAR(1000),
    ADD COLUMN premio_tope    BOOLEAN       NOT NULL DEFAULT FALSE;
