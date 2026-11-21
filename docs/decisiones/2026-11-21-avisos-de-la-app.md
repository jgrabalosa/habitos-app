# Avisos de la app: la vuelta y el momento débil, con tope y retirada

_21-nov-2026. T-014 (backend: T-014a y T-014b). Diseño decidido el 10-oct
(`RACHAS_DECISIONES.md` §15 y §16), revisado el 15-nov sobre tokens por app y
hábitos de minutos._

## Contexto

Además de los recordatorios que el usuario pone a cada hábito, la app puede
avisar por su cuenta. Hay poco margen: entre 1 y 3 avisos a la semana retienen;
a partir de 6, un tercio de los usuarios desactiva las notificaciones o deja la
app. Un aviso mal elegido cuesta más que no mandarlo.

## Decisión

1. **Dos avisos, y solo dos.**
   - **La vuelta** (19:00 locales): a los 3 días sin completar nada, y una
     última a los 7. Después, silencio hasta que complete algo.
   - **El momento débil** (viernes 18:00 locales): un solo aviso que nombra los
     semanales sin recordatorio a los que aún les cabe lo que falta.
2. **Límites comunes:** como mucho dos por semana ISO local y nunca dos el mismo
   día. Los recordatorios del usuario no cuentan.
3. **Se retira solo.** Si los 3 últimos avisos no fueron seguidos de ningún
   registro completado en 48 h, calla 30 días (contados desde que se cierra la
   ventana del último) y luego manda uno de prueba. «No sirven» es que no haya
   registro; no se mide si se abrió la notificación, para no exigir tocar la
   app.
4. **Apagables aparte** de los recordatorios (`avisosApp`), encendidos por
   defecto.
5. **Solo al token de Norday Habits.** Son avisos de Habits; el de Focus no se
   usa.
6. **La hora es la del usuario**, no la del servidor. El barrido corre cada
   hora en punto y cada usuario solo se evalúa cuando en su reloj son las 19:00
   (o el viernes a las 18:00): nunca de noche.
7. **Textos de 2 o 3 variantes, nunca la misma dos veces seguidas.** Se guarda la
   `variante` en `aviso_app`; la siguiente es la que sigue a la última del mismo
   tipo, por turno. Redacción: lo hecho al principio y lo que falta al final, el
   fallo no se nombra, se dice la causa, se invita y no se ordena.
8. **Sin deep link:** tocar el aviso abre la app como hoy.

Las reglas y los números exactos están en `docs/Logicas/03`, «Avisos de la app».

## Alternativas descartadas

- **Un aviso por hábito.** Con cinco hábitos son cinco avisos y se pasa del tope
  de seis a la semana a la primera. Es además lo que ya hacen los recordatorios,
  para quien los quiere. Por eso la vuelta es una sola, de toda la app, y el
  momento débil agrupa.
- **«Está en juego» (tu racha, tu semana).** Funciona a corto plazo y cuesta
  confianza: convierte el hábito en una amenaza, nombra el fallo y manda lo
  contrario de lo que Nori dice en la app. Por eso `NotificadorRachaEnPeligro`
  sigue sin programarse y ningún texto habla de perder algo.
- **Medir si se abrió la notificación** para decidir la retirada. Exige un
  evento del cliente y deja fuera a quien actúa sin tocar el aviso (abre la app
  por su cuenta). Un registro completado en 48 h es una señal que el servidor ya
  tiene.
- **Retirada definitiva** (hasta que el usuario lo reactive). Un usuario que
  vuelve a los dos meses se quedaría sin ayuda para siempre. Un aviso de prueba
  cada 30 días es barato y se calla en cuanto no sirve.
- **Elegir la variante al azar con la restricción «distinta de la anterior».**
  Depende de un generador de números y los tests tendrían que fijarlo. El turno
  da el mismo resultado sin él y reparte todas las variantes por igual.
- **Un reloj de servidor para decidir la hora de envío** (todos a las 19:00 del
  servidor): a quien está en otra zona le llegaría de madrugada o a mitad de la
  tarde.

## Consecuencias

- Una tabla (`aviso_app`, T-014a) con un aviso por fila: sirve para el tope, para
  no repetir variante y para la retirada. Se borra con la cuenta y sale en la
  exportación.
- El barrido hace una consulta por tipo de dato (hábitos activos, tokens, avisos,
  último completado) para todos los usuarios que están en su hora, y solo
  consulta los registros de las 48 h de quien ya tiene tres avisos y aún podría
  estar retirado.
- Un aviso al que FCM da el token por dado de baja no se apunta: no gasta tope.
- Cambiar una hora, un umbral o un texto es tocar `CalculoAvisos` y el bundle;
  `BundlesMensajesTest` vigila que cada variante exista en los tres idiomas.
