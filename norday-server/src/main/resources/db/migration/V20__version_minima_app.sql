-- Versión mínima (versionCode de Android) de cada app del ecosistema.
-- Por debajo de ella, la app se bloquea y pide actualizar. Una app sin fila
-- tiene mínima 0: nunca se bloquea por un dato que falta.
-- Se cambia a mano, por ejemplo:
--   UPDATE version_app SET version_minima = 5 WHERE app_id = 'habitos';
-- Primero la versión nueva publicada y visible en Play; después la mínima.
CREATE TABLE version_app (
    app_id         VARCHAR(30) PRIMARY KEY,
    version_minima INTEGER     NOT NULL
);

-- Empieza en 1: no bloquea a nadie hasta que se suba a mano.
INSERT INTO version_app (app_id, version_minima) VALUES ('habitos', 1);
