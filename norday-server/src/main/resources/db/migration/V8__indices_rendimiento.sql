-- Tres índices que faltaban en el camino más caliente de la app.
--
-- registro(habito_ref, fecha): findByHabitoAndRango y findByHabitoAndFecha
-- filtran exactamente por este par en cada carga del dashboard. Sin índice,
-- es sequential scan sobre la tabla que más crece de todo el esquema.
--
-- usuario_pildora y usuario_categoria_preferencia (usuario_id, estado): el
-- catálogo de conocimiento filtra por ambos campos juntos en cada consulta
-- de píldoras/preferencias de un usuario.
CREATE INDEX idx_registro_habito_fecha ON registro (habito_ref, fecha);
CREATE INDEX idx_usuario_pildora_usuario_estado ON usuario_pildora (usuario_id, estado);
CREATE INDEX idx_usuario_categoria_pref_usuario_estado ON usuario_categoria_preferencia (usuario_id, estado);
