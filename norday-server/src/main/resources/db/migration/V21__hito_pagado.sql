-- Hábitos en minutos, fase 1: el libro de hitos. Una fila por cada hito que
-- un hábito ya ha cobrado, para que no se pague dos veces. Los días hechos se
-- recalculan con la meta actual y pueden bajar (al subir la meta, días
-- pasados dejan de contar); sin el libro, al volver a cruzar un umbral el
-- hito HITO_DIAS_HECHOS se pagaba otra vez.
--
-- registro_ref es el completado que lo pagó: deshacerlo borra la fila. El
-- relleno de abajo también lo rellena. La columna admite NULL de todos modos.
-- pagado_en va en UTC, como los demás sellos de auditoría.
CREATE TABLE hito_pagado (
    id           SERIAL PRIMARY KEY,
    habito_ref   INTEGER NOT NULL,
    umbral       INTEGER NOT NULL,
    registro_ref INTEGER,
    pagado_en    TIMESTAMP NOT NULL,
    CONSTRAINT UK_hito_pagado_habito_umbral UNIQUE (habito_ref, umbral),
    CONSTRAINT FK_hito_pagado_habito
        FOREIGN KEY (habito_ref) REFERENCES habito(habito_id),
    CONSTRAINT FK_hito_pagado_registro
        FOREIGN KEY (registro_ref) REFERENCES registro(registro_id)
);

-- Deshacer busca por registro. La búsqueda por hábito ya la cubre el índice
-- de la restricción única, que empieza por habito_ref.
CREATE INDEX idx_hito_pagado_registro_ref ON hito_pagado(registro_ref);

-- Relleno: los umbrales que cada hábito ya ha cruzado según sus días hechos
-- actuales se dan por pagados, cada uno ligado al registro que lo cruzó, para
-- que deshacer ese registro borre su fila igual que en vivo. Los días hechos
-- se cuentan igual que CalculoPremio.diasHechos, con la meta actual:
--   DIARIO  = días con al menos GREATEST(meta, 1) registros completados;
--   SEMANAL = días con algún registro completado.
-- Los umbrales son los de ParametrosPremio.HITOS: 3, 7, 30, 100 y 365.
--
-- Cada día hecho tiene un único registro que lo cierra: el meta-ésimo de ese
-- día en un diario, el primero en un semanal. Los cierres se numeran por
-- registro_id, no por fecha: el id es el orden real en que se marcaron, y es
-- el orden en que el contador subió en vivo. Con un completado retroactivo
-- (el martes marcado después del miércoles) el umbral lo cruzó el del martes,
-- que es además el último registro del hábito y el único que se puede
-- deshacer; numerando por fecha, la fila quedaría ligada al del miércoles y
-- deshacer el martes no la borraría. El conjunto de (hábito, umbral) es el
-- mismo con cualquier orden: el número de cierres es el de días hechos.
WITH por_dia AS (
    SELECT r.habito_ref, r.registro_id,
           ROW_NUMBER() OVER (PARTITION BY r.habito_ref, r.fecha ORDER BY r.registro_id) AS n
    FROM registro r
    WHERE r.completado = TRUE
),
cierres AS (
    SELECT p.habito_ref, p.registro_id,
           ROW_NUMBER() OVER (PARTITION BY p.habito_ref ORDER BY p.registro_id) AS dia_hecho
    FROM por_dia p
    JOIN habito h ON h.habito_id = p.habito_ref
    WHERE p.n = CASE WHEN h.frecuencia = 'SEMANAL' THEN 1
                     ELSE GREATEST(COALESCE(h.meta, 1), 1) END
)
INSERT INTO hito_pagado (habito_ref, umbral, registro_ref, pagado_en)
SELECT c.habito_ref, u.umbral, c.registro_id, (NOW() AT TIME ZONE 'UTC')
FROM cierres c
JOIN (VALUES (3), (7), (30), (100), (365)) AS u(umbral) ON u.umbral = c.dia_hecho;
