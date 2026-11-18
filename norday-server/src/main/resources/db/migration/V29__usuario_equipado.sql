-- Nori vestida, N-5 (18-nov-2026): una cosa puesta por sitio, garantizado
-- por la base de datos.
--
-- Hasta ahora lo puesto era usuario_producto.equipado, y que solo hubiera uno
-- por categoría lo cuidaba la lógica. Con dos móviles a la vez se podían
-- quedar dos equipados en la misma categoría, y entonces equipar en ella
-- fallaba para siempre (getSingleResult). Desde aquí, lo puesto vive en
-- usuario_equipado: una fila por usuario y sitio (la categoría del producto),
-- con clave primaria (usuario_id, categoria). usuario_producto.equipado se
-- sigue escribiendo, coherente, porque lo leen los clientes.
--
-- Una tabla y no un índice único parcial: al cambiar de prenda, un índice se
-- incumpliría un instante a mitad del cambio.

-- 1. Quien tenga dos o más equipados en la misma categoría se queda con el de
--    usuario_producto_id más alto (la misma regla que sigue el core al leer
--    el inventario).
UPDATE usuario_producto up
SET equipado = FALSE
FROM producto p
WHERE p.producto_id = up.producto_id
  AND up.equipado
  AND EXISTS (
      SELECT 1
      FROM usuario_producto otro
      JOIN producto po ON po.producto_id = otro.producto_id
      WHERE otro.usuario_id = up.usuario_id
        AND po.categoria = p.categoria
        AND otro.equipado
        AND otro.usuario_producto_id > up.usuario_producto_id
  );

-- 2. La tabla.
CREATE TABLE usuario_equipado (
    usuario_id          INTEGER     NOT NULL,
    categoria           VARCHAR(50) NOT NULL,
    usuario_producto_id INTEGER     NOT NULL,
    CONSTRAINT pk_usuario_equipado PRIMARY KEY (usuario_id, categoria),
    CONSTRAINT uk_usuario_equipado_usuario_producto UNIQUE (usuario_producto_id),
    CONSTRAINT fk_usuario_equipado_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (usuario_id),
    CONSTRAINT fk_usuario_equipado_usuario_producto FOREIGN KEY (usuario_producto_id)
        REFERENCES usuario_producto (usuario_producto_id)
);

-- 3. Lo que queda equipado, en una sola sentencia.
INSERT INTO usuario_equipado (usuario_id, categoria, usuario_producto_id)
SELECT up.usuario_id, p.categoria, up.usuario_producto_id
FROM usuario_producto up
JOIN producto p ON p.producto_id = up.producto_id
WHERE up.equipado;
