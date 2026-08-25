-- La tienda mostraba 11 temas: las 4 identidades actuales más 7 de
-- generaciones anteriores. El inicializador de catálogo solo crea productos
-- nuevos, nunca retira los que desaparecen del código, así que los antiguos
-- seguían activos en base de datos.
--
-- Se desactivan en vez de borrarse: usuario_producto tiene FK a producto y
-- borrar destruiría el historial de compras.
UPDATE producto
SET activo = false
WHERE categoria = 'Tema'
  AND codigo NOT IN ('TEMA_PROFUNDIDAD', 'TEMA_NEOTOKYO_PLUS', 'TEMA_ALBA', 'TEMA_DULCE');

-- Los usuarios existentes solo poseían los dos temas básicos obsoletos, con
-- TEMA_BASICO_OSCURO equipado. Se les retira: no se les otorga ninguna
-- identidad a cambio, porque la pantalla de onboarding será quien las
-- reparta y todos deben pasar por ella.
DELETE FROM usuario_producto
WHERE producto_id IN (
    SELECT producto_id FROM producto
    WHERE categoria = 'Tema' AND activo = false
);
