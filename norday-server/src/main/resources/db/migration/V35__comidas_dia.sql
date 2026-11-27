-- T-077a · Como mucho 3 comidas al día. La mascota cuenta las comidas del día
-- de fecha_ultima_comida; de otro día, ya no cuentan. Las filas que ya
-- existen quedan a 0: la primera comida de hoy pone 1.
ALTER TABLE mascota ADD COLUMN comidas_dia INTEGER NOT NULL DEFAULT 0;
