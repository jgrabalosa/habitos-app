-- Conocimiento: su fila en version_app (ver V20), como V25 hizo con Focus.
-- Empieza en 1: el versionCode de cualquier build es al menos 1, así que no
-- bloquea a nadie hasta que se suba a mano.
INSERT INTO version_app (app_id, version_minima) VALUES ('conocimiento', 1)
ON CONFLICT (app_id) DO NOTHING;
