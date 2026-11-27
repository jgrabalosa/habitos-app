-- Rebalanceo de la tienda (24-nov-2026, D-71, docs/decisiones/2026-11-24-precios-tienda.md):
-- sobraban puntos y un usuario normal compraba toda la tienda en menos de un
-- mes. Lo que se gana no cambia; lo que se compra cuesta más.
--
-- Cada UPDATE lleva el precio viejo en el WHERE: si alguien lo cambió a mano,
-- no se pisa. Los initializers no tocan filas que ya existen, por eso hace
-- falta esta migración (y llevan los mismos precios para una base nueva).
-- Las prendas de pago se venden al precio de su oferta, así que suben las dos.
--
-- No toca TEMA_ALBA (retirado), el gorro de Papá Noel (sigue a 0), lo ya
-- comprado (usuario_producto) ni los saldos y movimientos (usuario_moneda).
UPDATE producto SET precio = 1250 WHERE codigo = 'TEMA_PROFUNDIDAD' AND precio = 1000;
UPDATE producto SET precio = 1250 WHERE codigo = 'TEMA_NEOTOKYO_PLUS' AND precio = 1000;
UPDATE producto SET precio = 1250 WHERE codigo = 'TEMA_DULCE' AND precio = 1000;
UPDATE producto SET precio = 400 WHERE codigo = 'PRENDA_GORRO_LANA' AND precio = 200;
UPDATE producto SET precio = 400 WHERE codigo = 'PRENDA_BUFANDA' AND precio = 250;
UPDATE producto SET precio = 500 WHERE codigo = 'PRENDA_TRAJE_NOEL' AND precio = 400;
UPDATE producto SET precio = 350 WHERE codigo = 'PAUSA_HABITO' AND precio = 300;
UPDATE producto SET precio = 100 WHERE codigo = 'COMIDA_BASICA' AND precio = 50;
UPDATE oferta SET precio = 400 WHERE precio = 200 AND producto_id = (SELECT producto_id FROM producto WHERE codigo = 'PRENDA_GORRO_LANA');
UPDATE oferta SET precio = 400 WHERE precio = 250 AND producto_id = (SELECT producto_id FROM producto WHERE codigo = 'PRENDA_BUFANDA');
UPDATE oferta SET precio = 500 WHERE precio = 400 AND producto_id = (SELECT producto_id FROM producto WHERE codigo = 'PRENDA_TRAJE_NOEL');
