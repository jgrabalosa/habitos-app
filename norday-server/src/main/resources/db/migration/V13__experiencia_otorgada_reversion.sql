-- 3.5 · Deshacer un completado resta la XP que dio, en vez de fijar la XP al
-- valor previo: fijarla borraba la ganada después por otros hábitos o por la
-- comida. Igual que monedas_otorgadas. NULL = reversión anterior a esta
-- columna: no hay nada fiable que restaurar en la mascota.
ALTER TABLE reversion_registro ADD COLUMN mascota_experiencia_otorgada INTEGER;
ALTER TABLE reversion_registro DROP COLUMN mascota_experiencia_previa;
