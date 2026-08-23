-- El logro INTERACCION_RESENA otorgaba 100 puntos por interactuar con el
-- diálogo nativo de reseña de Google Play (in_app_review). Se retira porque
-- incentivar reseñas —dar cualquier recompensa a cambio de interactuar con
-- el sistema de reseñas, sea cual sea el resultado— está prohibido por la
-- política de Google Play y podía provocar rechazo en revisión. Se
-- desactiva en vez de borrarse: si algún usuario ya lo tiene otorgado, su
-- fila en usuario_logro sigue existiendo intacta.
UPDATE logro SET activo = false WHERE codigo = 'INTERACCION_RESENA';
