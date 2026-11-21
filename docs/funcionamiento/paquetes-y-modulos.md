# Paquetes, dependencias y módulos Maven

Movido desde `CLAUDE.md` (T-005a). Las reglas de aquí son las mismas que
había allí; el `CLAUDE.md` conserva un resumen de lo que vale en cada sesión.

## Estructura de paquetes (Nivel 1, ya aplicada)

El código está reorganizado por dominio bajo `com.norday`. Cada paquete
tiene dentro su propia estructura por capas (`config`, `controller`,
`model`, `model.dto`, `repository`, `repository.impl`, `service`):

- **`com.norday`** — solo `NordayApplication`. Vive en la raíz, fuera de
  los demás paquetes, para que el component scan los alcance a todos.
- **`com.norday.core`** — motor de cuenta: auth/JWT y security, usuarios,
  recuperación de contraseña, email, FCM, Firebase.
- **`com.norday.gamificacion`** — motor de juego: puntos (UsuarioMoneda),
  logros, tienda (Producto/UsuarioProducto), mascota.
- **`com.norday.habitos`** — dominio: Habito, Registro, Racha, Categoria,
  Frecuencia, sus schedulers, controllers y logros de dominio.
- **`com.norday.conocimiento`** — dominio de la segunda app: píldoras de
  microaprendizaje, preferencias de categoría y valoraciones. Hermano de
  `habitos`, no descendiente suyo.
- **`com.norday.enfoque`** — dominio de Norday Focus: sesiones de
  concentración (`sesion_enfoque`), su resumen y su historial. Hermano de
  `habitos` y `conocimiento`. Lleva minutos a los hábitos sin conocerlos, por
  el puerto `DestinoMinutos` del motor (ver `focus-destino-minutos.md`).

### Regla de dependencias (obligatoria)

- `habitos` → puede importar de `core` y `gamificacion`.
- `conocimiento` → puede importar de `core` y `gamificacion`.
- `enfoque` → puede importar de `core` y `gamificacion`.
- `gamificacion` ↔ `core` → permitido en ambos sentidos. Los dos son
  *motor* y viven en el mismo módulo Maven `norday-motor`; por eso entre
  ellos no hay muro.
- `core` → `habitos`, `conocimiento` o `enfoque` : **PROHIBIDO**.
- `gamificacion` → `habitos`, `conocimiento` o `enfoque` : **PROHIBIDO**.
- `habitos` ↔ `conocimiento` : **PROHIBIDO** en los dos sentidos. Son
  hermanos y no se conocen: ninguno declara al otro en su `pom.xml`, así
  que una importación cruzada tampoco compila.
- `enfoque` ↔ `habitos` y `enfoque` ↔ `conocimiento` : **PROHIBIDO** en los
  dos sentidos, por lo mismo. Cuando un dominio necesita algo de otro, el
  motor declara un puerto y el otro lo implementa (`DestinoMinutos`,
  `CumplimientoDiarioPort`).

Desde el Nivel 2 esta regla ya no depende solo de la disciplina: Maven la
impone. `norday-motor` no declara ninguna dependencia hacia `habitos`, así
que una importación prohibida ni siquiera compila.

Ningún archivo de `core` ni de `gamificacion` puede contener las palabras
`Habito`, `Registro`, `Racha` o `Categoria` — ni siquiera en comentarios o
texto de logs. Se verifica con dos greps que deben dar cero resultados:

```bash
grep -rn "com\.norday\.habitos" norday-motor/src/main/java
grep -rnE "\b(Habito|Registro|Racha|Categoria)\w*" norday-motor/src/main/java
```

## Estructura de módulos Maven (Nivel 2, ya aplicada)

El backend es un proyecto multi-módulo. El `pom.xml` de la raíz es un
agregador (`packaging=pom`, sin dependencias propias) que hereda de
`spring-boot-starter-parent` y declara cinco módulos:

- **`norday-motor/`** — el motor: `com.norday.core` y
  `com.norday.gamificacion`. Es una librería (sin
  `spring-boot-maven-plugin`) y concentra **todas** las dependencias
  técnicas: JPA, web, security, JWT, validación, bucket4j, mail, OAuth2,
  Google/Firebase, Flyway, PostgreSQL. Es el módulo que comparten los tres
  dominios.
- **`habitos/`** — el dominio de Norday Habits: `com.norday.habitos`.
  Librería también. Su única dependencia de producción es `norday-motor`,
  y las hereda todas por transitividad.
- **`conocimiento/`** — el dominio de Norday Conocimiento:
  `com.norday.conocimiento`. Igual que `habitos`: librería, y su única
  dependencia de producción es `norday-motor`.
- **`enfoque/`** — el dominio de Norday Focus: `com.norday.enfoque`. Igual:
  librería, y su única dependencia de producción es `norday-motor`.
- **`norday-server/`** — el ensamblado ejecutable: `NordayApplication`
  y `src/main/resources` (properties, `mensajes/`, `static/`,
  `db/migration/`). Depende de `habitos`, `conocimiento` **y** `enfoque`, y lleva
  el `spring-boot-maven-plugin` con
  `mainClass com.norday.NordayApplication`. Es el único módulo que
  produce un jar arrancable.

La cadena **no** es lineal, es un abanico: `norday-server` cuelga de los
tres módulos de dominio, y cada uno de ellos cuelga de `norday-motor`. Una
app futura del ecosistema añadiría su propio módulo de dominio junto a
`habitos`, `conocimiento` y `enfoque`, colgando igualmente de `norday-motor`.
