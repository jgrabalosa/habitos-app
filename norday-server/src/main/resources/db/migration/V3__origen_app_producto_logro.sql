-- Alcance del catálogo: de qué app es cada producto y cada logro.
--
-- NULL significa "compartido por todo el ecosistema", que es justo lo que son
-- hoy las filas existentes. Por eso la columna va nullable y sin default: así
-- todo el catálogo actual sigue visible en todas las apps sin tocar un dato.
ALTER TABLE producto ADD COLUMN origen_app VARCHAR(30);
ALTER TABLE logro ADD COLUMN origen_app VARCHAR(30);
