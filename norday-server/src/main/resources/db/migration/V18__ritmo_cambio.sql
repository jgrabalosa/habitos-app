-- Rediseño de rachas, paso A6. La fecha del último cambio de frecuencia u
-- objetivo de un hábito. Ese día el ancla del ritmo pasa a esa fecha, para
-- que lo ya cerrado no se vuelva a juzgar con las reglas nuevas, y el
-- periodo que la contiene solo puede subir: un cambio nunca perjudica.
--
-- Nullable: un hábito que no ha cambiado nunca no tiene fecha de cambio.
ALTER TABLE ritmo ADD COLUMN cambio DATE;
