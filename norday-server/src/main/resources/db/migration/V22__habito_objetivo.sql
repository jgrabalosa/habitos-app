-- Hábitos en minutos, fase 2: el historial de objetivos. Una fila por cada
-- versión del objetivo de un hábito, vigente desde `desde` hasta la víspera
-- de la siguiente. Cada día se juzga con la versión vigente en esa fecha, así
-- que cambiar la meta o la frecuencia ya no reinterpreta lo que se hizo.
-- habito sigue guardando una copia del objetivo vigente hoy.
--
-- Puede haber como mucho una versión con `desde` futura: el cambio
-- programado, que se aplica al llegar su fecha.
--
-- unidad y minutos_objetivo son de la fase 3 (hábitos en minutos): van ya
-- aquí para no necesitar otra migración. creado_en va en UTC, como los demás
-- sellos de auditoría.
CREATE TABLE habito_objetivo (
    id               SERIAL PRIMARY KEY,
    habito_ref       INTEGER NOT NULL,
    desde            DATE NOT NULL,
    unidad           VARCHAR(10) NOT NULL DEFAULT 'VECES',
    frecuencia       VARCHAR(20) NOT NULL,
    meta             INTEGER NOT NULL,
    minutos_objetivo INTEGER,
    dias_semana      VARCHAR(20),
    creado_en        TIMESTAMP NOT NULL,
    CONSTRAINT UK_habito_objetivo_habito_desde UNIQUE (habito_ref, desde),
    CONSTRAINT FK_habito_objetivo_habito
        FOREIGN KEY (habito_ref) REFERENCES habito(habito_id)
);

-- La búsqueda por hábito ya la cubre el índice de la restricción única, que
-- empieza por habito_ref.

-- Relleno: cada hábito existente empieza con una versión desde su fecha de
-- inicio y su objetivo actual. La meta nula se lee como 1, igual que en el
-- relleno de V21 (y CalculoPremio trata un objetivo menor que 1 como 1).
INSERT INTO habito_objetivo (habito_ref, desde, unidad, frecuencia, meta,
                             minutos_objetivo, dias_semana, creado_en)
SELECT habito_id, fecha_inicio, 'VECES', frecuencia, COALESCE(meta, 1),
       NULL, dias_semana, (NOW() AT TIME ZONE 'UTC')
FROM habito;
