-- 4.4 · El usuario puede elegir qué fase de Nori ve, entre las que ya ha
-- desbloqueado. Sólo afecta a la imagen: la fase real se deriva del nivel.
-- NULL significa «la que toque», que es como nace toda mascota y como queda
-- tras cada evolución.
ALTER TABLE mascota ADD COLUMN fase_elegida VARCHAR(16);
