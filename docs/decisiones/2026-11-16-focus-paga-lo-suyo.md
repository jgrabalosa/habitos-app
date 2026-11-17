# Norday Focus paga lo suyo

_16-nov-2026. Decidido por Quim sobre el diseño «Norday Focus con premio,
tienda, equipamiento y logros propios»._

## Contexto

Hasta ahora una sesión de Norday Focus solo pagaba si llevaba sus minutos a un
hábito de minutos, y lo que pagaba era lo del hábito. Una sesión sin hábito no
daba nada, Focus no tenía logros y el ánimo de Nori solo miraba los hábitos:
quien usaba solo Focus no ganaba nada y veía a Nori triste siempre. Focus no
podía vivir sin Norday Habits.

## Decisión

1. **Cada sesión que cuenta paga su premio**, tenga hábito o no: 1 moneda por
   minuto, 5 más si se completa, y 1 XP de Nori por cada 5 minutos. Una
   perdida no paga.
2. **Con hábito, se suman** el premio de la sesión y lo que paga el hábito.
3. **Tope diario de 120 monedas y 24 XP** por lo que pagan las sesiones. Con
   el tope de monedas alcanzado, la sesión no paga nada más ese día, pero
   cuenta para todo lo demás.
4. **21 logros propios de Focus** (`origen_app = enfoque`), invisibles para
   Habits.
5. **El ánimo de Nori junta las apps que el usuario usa**: feliz si alguna ha
   empezado, exultante solo con todas cumplidas, triste solo si todas están
   ausentes. Con una sola app, el ánimo es el suyo, sin cambios.

## Alternativas descartadas

- **Que la sesión con hábito pague solo lo del hábito** (premio propio solo
  sin hábito): mandar minutos a un hábito ya cumplido hoy pagaría menos que no
  mandarlos. Incentivo al revés.
- **Sin tope**: Focus sería una fábrica de monedas, y la economía es una sola
  para todo el ecosistema.
- **Dos topes sueltos** (monedas y XP por separado): con sesiones completadas
  el de monedas llega antes, y una quinta sesión daría XP con el aviso de «ya
  has ganado todo». Se para todo a la vez.
- **Que Focus escriba el día completo de la mascota** (`registrarDiaCompleto`):
  esa fecha la guarda y la restaura la reversión de Habits al deshacer, y
  pisaría la de Focus. El ánimo ya pregunta en el momento.
- **Un ánimo «el mejor de las dos»** (exultante con una sola cumplida):
  exultante es «todo lo de hoy hecho».

## Consecuencias

- **Sesiones falsas.** Antes, falsificar una sesión por la API no daba más que
  apuntar minutos a mano en Habits (revisión del 15-nov-2026). Ahora da como
  mucho el tope de cada día. Lo acotan el tope, las reglas de la sesión (sin
  solapes, 10–120 minutos, ni futura ni de hace más de 7 días) y que las
  monedas no se venden. Aceptado.
- Quien usa las dos apps gana más que quien usa una. Es intencionado y el tope
  lo acota.
- El contrato de `POST /api/enfoque/sesiones` gana `premio`. Norday Focus aún
  no está en Play, así que no hace falta versión mínima.
- Las cifras viven en `ParametrosPremioEnfoque` y el detalle en
  `docs/Logicas/03-calculos-y-logica.md`.
