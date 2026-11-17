-- Enfoque, fase 1: la fila de Norday Focus en version_app (ver V20).
-- Empieza en 1, como habitos: el versionCode de cualquier build es al menos 1,
-- así que no bloquea a nadie hasta que se suba a mano.
INSERT INTO version_app (app_id, version_minima) VALUES ('enfoque', 1)
ON CONFLICT (app_id) DO NOTHING;
