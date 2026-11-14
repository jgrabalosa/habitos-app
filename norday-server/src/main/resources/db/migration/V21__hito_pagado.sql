-- Hábitos en minutos, fase 1: el libro de hitos. Una fila por cada hito que
-- un hábito ya ha cobrado, para que no se pague dos veces. Los días hechos se
-- recalculan con la meta actual y pueden bajar (al subir la meta, días
-- pasados dejan de contar); sin el libro, al volver a cruzar un umbral el
-- hito HITO_DIAS_HECHOS se pagaba otra vez.
--
-- registro_ref es el completado que lo pagó: deshacerlo borra la fila. Admite
-- NULL porque las filas del relleno de abajo no tienen registro.
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
-- actuales se dan por pagados. Los días hechos se cuentan igual que
-- CalculoPremio.diasHechos, con la meta actual:
--   DIARIO  = días con al menos GREATEST(meta, 1) registros completados;
--   SEMANAL = días con algún registro completado.
-- Los umbrales son los de ParametrosPremio.HITOS: 3, 7, 30, 100 y 365.
WITH veces_por_dia AS (
    SELECT r.habito_ref, r.fecha, COUNT(*) AS veces
    FROM registro r
    WHERE r.completado = TRUE
    GROUP BY r.habito_ref, r.fecha
),
dias_hechos AS (
    SELECT h.habito_id,
           COUNT(v.fecha) FILTER (
               WHERE h.frecuencia = 'SEMANAL'
                  OR v.veces >= GREATEST(COALESCE(h.meta, 1), 1)
           ) AS dias
    FROM habito h
    JOIN veces_por_dia v ON v.habito_ref = h.habito_id
    GROUP BY h.habito_id
)
INSERT INTO hito_pagado (habito_ref, umbral, registro_ref, pagado_en)
SELECT d.habito_id, u.umbral, NULL, (NOW() AT TIME ZONE 'UTC')
FROM dias_hechos d
JOIN (VALUES (3), (7), (30), (100), (365)) AS u(umbral) ON u.umbral <= d.dias;
