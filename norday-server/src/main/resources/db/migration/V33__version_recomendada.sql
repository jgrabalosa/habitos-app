-- Versión recomendada (versionCode de Android) de cada app del ecosistema.
-- No obliga a nada: por debajo de ella la app solo enseña una tarjeta que
-- invita a actualizar (obligar sigue siendo la versión mínima). Nula = sin
-- aviso, que es lo que tienen todas las filas existentes y una app sin fila.
-- Se sube a mano al publicar una versión, por ejemplo:
--   UPDATE version_app SET version_recomendada = 12 WHERE app_id = 'habitos';
-- Para quitar el aviso:
--   UPDATE version_app SET version_recomendada = NULL WHERE app_id = 'habitos';
ALTER TABLE version_app ADD COLUMN version_recomendada INTEGER NULL;
