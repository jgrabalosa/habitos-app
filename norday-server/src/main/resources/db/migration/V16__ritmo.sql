-- Rediseño de rachas, paso A1. El ritmo de cada hábito: la medida que
-- sustituye a la racha de cara al usuario. La tabla `racha` no se toca.
--
-- Se guarda un ancla, no el valor al día: `valor` es el ritmo (de 0 a 1) al
-- empezar el día `desde`. El ritmo de hoy se calcula al leer, repasando los
-- registros desde `desde`. Por eso completar o deshacer un hábito no escribe
-- en esta tabla, y no hay nada suyo que guardar en reversion_registro.
CREATE TABLE ritmo (
    ritmo_id   SERIAL PRIMARY KEY,
    habito_ref INTEGER NOT NULL UNIQUE,
    valor      DOUBLE PRECISION NOT NULL,
    desde      DATE NOT NULL,
    CONSTRAINT FK_ritmo_habito
        FOREIGN KEY (habito_ref) REFERENCES habito(habito_id)
);

-- Todos los hábitos que ya existen empiezan en el valor de partida, sin
-- calcular nada de su historial. 0.5 es ParametrosRitmo.VALOR_INICIAL.
INSERT INTO ritmo (habito_ref, valor, desde)
SELECT habito_id, 0.5, CURRENT_DATE FROM habito;
