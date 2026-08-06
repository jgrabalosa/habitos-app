-- Afinidad acumulada del usuario con cada categoría. Solo sube: las señales
-- positivas (match, guardar, valorar alto) la empujan, el descarte no la baja.
--
-- Va con DEFAULT 0 para que las preferencias ya existentes arranquen neutras
-- sin necesidad de un UPDATE aparte.
--
-- DOUBLE PRECISION y no NUMERIC(6,2) porque el campo de la entidad es un
-- `double`: con NUMERIC, `ddl-auto=update` reescribe la columna por su cuenta
-- al arrancar, y `ddl-auto=validate` —el perfil local— directamente no arranca.
-- Aquí no se cuenta dinero, así que la precisión decimal exacta no aporta nada.
ALTER TABLE usuario_categoria_preferencia
    ADD COLUMN puntuacion_afinidad DOUBLE PRECISION NOT NULL DEFAULT 0;
