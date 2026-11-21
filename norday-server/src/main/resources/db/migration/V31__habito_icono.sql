-- T-011a (21-nov-2026): el icono de cada hábito.
--
-- Es el código del icono que dibuja la app (el catálogo vive en la app; el
-- backend solo guarda el código). NULL = sin icono, como todos los hábitos
-- que ya existen. No es parte del objetivo: no hay columna en
-- habito_objetivo.
ALTER TABLE habito ADD COLUMN icono VARCHAR(40);
