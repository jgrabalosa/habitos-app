-- Instantánea del estado previo a completar un hábito, para poder revertirlo
-- con exactitud. Los campos _previa/_previo son nullable a propósito: si el
-- hábito no tiene racha o el usuario no tiene mascota, no hay nada que
-- restaurar y null lo dice mejor que un cero.
CREATE TABLE reversion_registro (
    reversion_id                  SERIAL PRIMARY KEY,
    registro_ref                  INTEGER NOT NULL UNIQUE,
    racha_actual_previa           INTEGER,
    racha_maxima_previa           INTEGER,
    periodo_meta_alcanzada_previo DATE,
    ultima_fecha_previa           DATE,
    mascota_experiencia_previa    INTEGER,
    mascota_dia_completo_previo   DATE,
    monedas_otorgadas             INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT FK_reversion_registro
        FOREIGN KEY (registro_ref) REFERENCES registro(registro_id)
);

-- Qué logros desbloqueó ese completado. Cero o pocas filas por registro.
CREATE TABLE reversion_logro (
    id            SERIAL PRIMARY KEY,
    reversion_ref INTEGER NOT NULL,
    logro_ref     INTEGER NOT NULL,
    CONSTRAINT FK_reversion_logro
        FOREIGN KEY (reversion_ref) REFERENCES reversion_registro(reversion_id)
);

CREATE INDEX idx_reversion_registro_ref ON reversion_registro(registro_ref);
CREATE INDEX idx_reversion_logro_ref ON reversion_logro(reversion_ref);
