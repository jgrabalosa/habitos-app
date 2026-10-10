-- Rediseño de rachas, paso A5b. Para decir si un ritmo sube o baja se
-- compara con el de hace siete días, y para calcular ese hace falta el ancla
-- anterior: cómo estaba el ritmo al empezar el día `desde_previo`.
--
-- Las dos columnas son nullable: un ritmo cuya ancla todavía no ha avanzado
-- no tiene ancla anterior, y se compara con la que tiene.
ALTER TABLE ritmo ADD COLUMN valor_previo DOUBLE PRECISION;
ALTER TABLE ritmo ADD COLUMN desde_previo DATE;
