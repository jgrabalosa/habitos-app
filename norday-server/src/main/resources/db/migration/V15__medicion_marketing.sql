-- Medición de marketing (DISENO_R2 §8). Fechas en UTC, como fecha_registro.
-- Solo añade columnas: ningún dato existente cambia de significado.

-- Origen de la instalación (Install Referrer de Google Play) y último acceso.
-- Todo nullable: los usuarios anteriores no tienen origen y no hay dato mejor.
ALTER TABLE usuario ADD COLUMN utm_source     VARCHAR(100);
ALTER TABLE usuario ADD COLUMN utm_medium     VARCHAR(100);
ALTER TABLE usuario ADD COLUMN utm_campaign   VARCHAR(100);
ALTER TABLE usuario ADD COLUMN referrer_crudo VARCHAR(500);
ALTER TABLE usuario ADD COLUMN referrer_fecha TIMESTAMP;
ALTER TABLE usuario ADD COLUMN ultimo_acceso  TIMESTAMP;

-- Cuándo se creó cada hábito. Las filas anteriores se aproximan a las 00:00
-- de su fecha_inicio: es lo más cercano que hay, pero NO es la hora real.
ALTER TABLE habito ADD COLUMN fecha_creacion TIMESTAMP;
UPDATE habito SET fecha_creacion = fecha_inicio::timestamp;
ALTER TABLE habito ALTER COLUMN fecha_creacion SET NOT NULL;

-- Cuándo se marcó cada registro (registro.fecha es el día al que corresponde,
-- no el momento en que se marcó). Las filas anteriores se aproximan a las
-- 00:00 de su fecha.
ALTER TABLE registro ADD COLUMN creado_en TIMESTAMP;
UPDATE registro SET creado_en = fecha::timestamp;
ALTER TABLE registro ALTER COLUMN creado_en SET NOT NULL;
