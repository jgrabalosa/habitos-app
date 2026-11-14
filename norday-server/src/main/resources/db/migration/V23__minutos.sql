-- Hábitos en minutos, fase 3: las columnas de los hábitos de minutos.
--
-- habito.unidad y habito.minutos_objetivo son la copia del objetivo vigente
-- hoy, como frecuencia y meta (el historial ya tenía las dos columnas desde
-- V22). Los hábitos existentes quedan de VECES y sin minutos.
--
-- registro.cantidad es lo que aporta el registro a su día: los minutos en un
-- hábito de minutos, 1 en uno de veces. Los registros existentes quedan con
-- 1, que es lo que valían. origen dice de dónde viene (MANUAL o ENFOQUE).
-- clave_cliente es la clave que manda la app con cada completado para que un
-- reintento no cree otro registro: única en toda la tabla y NULL en los
-- registros de antes y en los de la app ya instalada, que no la manda.
ALTER TABLE habito ADD COLUMN unidad VARCHAR(10) NOT NULL DEFAULT 'VECES';
ALTER TABLE habito ADD COLUMN minutos_objetivo INTEGER;

ALTER TABLE registro ADD COLUMN cantidad INTEGER NOT NULL DEFAULT 1;
ALTER TABLE registro ADD COLUMN origen VARCHAR(20) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE registro ADD COLUMN clave_cliente UUID;
ALTER TABLE registro ADD CONSTRAINT UK_registro_clave_cliente UNIQUE (clave_cliente);
