-- El Escudo de Racha se vendía en el catálogo por 300 puntos ("Protege tu
-- racha durante 1 día si olvidas completar tu hábito") pero nunca se llegó
-- a implementar el consumo: RachaService.rachaActualVigente no lo consulta
-- en ningún punto. Se desactiva en vez de borrarse: si algún tester ya lo
-- compró, la fila de compra (usuario_producto) sigue existiendo intacta, y
-- el día que se implemente basta con volver activo=true.
UPDATE producto SET activo = false WHERE codigo = 'ESCUDO_RACHA';
