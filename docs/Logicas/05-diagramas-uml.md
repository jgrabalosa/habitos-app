# Norday — Diagramas UML de clases y flujos (backend)

*Última actualización: noviembre de 2026*

Los diagramas se separan por responsabilidad para mantener la legibilidad:
**Núcleo de hábitos**, **Gamificación**, **Cuenta y dispositivos**,
**Norday Focus y los puertos del motor**, la **arquitectura de módulos** y los
**flujos** de un completado, de una sesión de Focus y de deshacer.

> Los diagramas representan clases que existen en el código de `wip-enfoque`.
> No incluyen estructuras futuras ni clases retiradas. Los atributos son los
> campos de cada entidad; se omiten algunos que no aportan a la lectura
> (UTM, contraseña, recordatorio).

---

## Dominio: Núcleo de hábitos (`habitos`)

```mermaid
classDiagram
    class Usuario {
        -int usuarioId
        -String nombre
        -String zonaHoraria
        -String idioma
    }

    class Categoria {
        -int categoriaId
        -String codigo
        -String nombre
        -String descripcion
        -String color
        -String icono
        -boolean esGlobal
        -int orden
        -Usuario creador
    }

    class Habito {
        -int habitoId
        -String nombre
        -String descripcion
        -Frecuencia frecuencia
        -int meta
        -String diasSemana
        -Unidad unidad
        -Integer minutosObjetivo
        -LocalDate fechaInicio
        -LocalDateTime fechaCreacion
        -boolean activo
        -Usuario propietario
        -Categoria tipo
    }

    class HabitoObjetivo {
        -int id
        -Habito habito
        -LocalDate desde
        -Unidad unidad
        -Frecuencia frecuencia
        -int meta
        -Integer minutosObjetivo
        -String diasSemana
        -LocalDateTime creadoEn
    }

    class Registro {
        -int registroId
        -LocalDate fecha
        -LocalDateTime creadoEn
        -boolean completado
        -String nota
        -Integer valoracion
        -int cantidad
        -OrigenRegistro origen
        -UUID claveCliente
        -Habito habito
    }

    class Racha {
        -int rachaId
        -int rachaActual
        -int rachaMaxima
        -LocalDate ultimaFecha
        -LocalDate periodoMetaAlcanzada
        -Habito habito
    }

    class Ritmo {
        -int ritmoId
        -double valor
        -LocalDate desde
        -Double valorPrevio
        -LocalDate desdePrevio
        -LocalDate cambio
        -Habito habito
    }

    class HitoPagado {
        -int id
        -Habito habito
        -int umbral
        -Registro registro
        -LocalDateTime pagadoEn
    }

    class ReversionRegistro {
        -int reversionId
        -Registro registro
        -Integer rachaActualPrevia
        -Integer rachaMaximaPrevia
        -LocalDate periodoMetaAlcanzadaPrevio
        -LocalDate ultimaFechaPrevia
        -Integer mascotaExperienciaOtorgada
        -LocalDate mascotaDiaCompletoPrevio
        -int monedasOtorgadas
    }

    class ReversionLogro {
        -int id
        -ReversionRegistro reversion
        -int logroRef
    }

    class Frecuencia {
        <<enumeration>>
        DIARIO
        SEMANAL
    }

    class Unidad {
        <<enumeration>>
        VECES
        MINUTOS
    }

    class OrigenRegistro {
        <<enumeration>>
        MANUAL
        ENFOQUE
    }

    Usuario "1" --> "0..*" Habito : propietario
    Usuario "1" --> "0..*" Categoria : creador
    Categoria "0..1" --> "0..*" Habito : tipo
    Habito "1" --> "0..*" HabitoObjetivo : historial
    Habito "1" --> "0..*" Registro
    Habito "1" --> "0..1" Racha
    Habito "1" --> "0..1" Ritmo
    Habito "1" --> "0..*" HitoPagado
    Registro "0..1" <-- "0..*" HitoPagado : lo pagó
    Registro "1" --> "0..1" ReversionRegistro
    ReversionRegistro "1" --> "0..*" ReversionLogro
    Habito ..> Frecuencia
    Habito ..> Unidad
    Registro ..> OrigenRegistro
```

- **`Habito` guarda una copia del objetivo vigente hoy** (`frecuencia`,
  `meta`, `diasSemana`, `unidad`, `minutosObjetivo`); el historial completo
  está en `HabitoObjetivo`, una fila por versión, vigente desde `desde`
  hasta la víspera de la siguiente. Puede haber como mucho una versión
  futura: el cambio programado. Cada día se juzga con la versión vigente ese
  día. Un hábito sin versiones se juzga con la copia de `Habito`.
- **`Registro.cantidad`** es lo que aporta al día: los minutos en un hábito
  de minutos, 1 en uno de veces. `origen` dice si vino del panel (`MANUAL`)
  o de una sesión de Norday Focus (`ENFOQUE`). `claveCliente` es la clave del
  reintento: única en la tabla, nunca sale en el JSON. `creadoEn` es el
  instante en que se marcó (UTC); `fecha`, el día al que corresponde.
- **`HitoPagado`** es el libro de hitos (3, 7, 30, 100 y 365 días hechos):
  una fila por hábito y umbral, para que un hito no se pague dos veces
  aunque los días hechos bajen y vuelvan a subir. Apunta al registro que lo
  pagó, y deshacer ese registro borra la fila.
- **`Ritmo`** no se guarda al día: es un ancla (`valor` desde `desde`, más el
  ancla anterior y la fecha del último cambio) que `RitmoService` pone al
  día repasando los registros al leer. Completar y deshacer no lo escriben;
  el ancla se mueve al cambiar el objetivo y con las pausas.
- **`Racha`** sigue en el modelo, pero el completado ya no la actualiza
  (apagada el 21-oct-2026): solo se guarda su estado en la reversión para
  que deshacer lo restaure.

---

## Dominio: Gamificación (`norday-motor`, `com.norday.gamificacion`)

```mermaid
classDiagram
    class Usuario {
        -int usuarioId
    }

    class Logro {
        -int logroId
        -String codigo
        -String nombre
        -String descripcion
        -String categoria
        -String nivel
        -int puntos
        -String icono
        -boolean activo
        -String origenApp
    }

    class UsuarioLogro {
        -int usuarioLogroId
        -Usuario usuario
        -Logro logro
        -LocalDateTime fechaConseguido
    }

    class UsuarioMoneda {
        -int movimientoId
        -Usuario usuario
        -int cantidad
        -String origen
        -Integer referenciaId
        -String descripcion
        -LocalDateTime fecha
    }

    class Producto {
        -int productoId
        -String codigo
        -String nombre
        -String descripcion
        -String categoria
        -String tipo
        -int precio
        -String icono
        -boolean activo
        -String origenApp
    }

    class UsuarioProducto {
        -int usuarioProductoId
        -Usuario usuario
        -Producto producto
        -LocalDateTime fechaAdquirido
        -int cantidad
        -boolean equipado
    }

    class Mascota {
        -int mascotaId
        -Usuario usuario
        -int experiencia
        -String nombre
        -LocalDate fechaUltimaComida
        -LocalDate fechaUltimoDiaCompleto
        -String faseElegida
    }

    Usuario "1" --> "0..*" UsuarioLogro
    Logro "1" --> "0..*" UsuarioLogro
    Usuario "1" --> "0..*" UsuarioMoneda
    Usuario "1" --> "0..*" UsuarioProducto
    Producto "1" --> "0..*" UsuarioProducto
    Usuario "1" --> "0..1" Mascota
```

- `origenApp` distingue el catálogo de cada app: `null` es del motor, y los
  logros y productos de hábitos (incluida la `PAUSA_HABITO`) llevan
  `habitos`.
- `UsuarioMoneda` es un libro de sólo añadir: el saldo es la suma de los
  movimientos, y deshacer compensa con un movimiento de signo contrario.
- La mascota **no guarda nivel ni fase**: el nivel sale de `experiencia` y la
  fase del nivel (`MascotaService`); `faseElegida` es solo la que el usuario
  ha elegido ver. El modelo no incluye `PerfilGamificacion`, retirado.

---

## Cuenta y dispositivos (`norday-motor`, `com.norday.core`)

```mermaid
classDiagram
    class Usuario {
        -int usuarioId
        -String nombre
        -String username
        -String email
        -String proveedorAuth
        -String fcmToken
        -LocalDateTime fechaRegistro
        -String idioma
        -String zonaHoraria
        -LocalDateTime ultimoAcceso
    }

    class UsuarioDispositivo {
        -int id
        -Usuario usuario
        -String appId
        -String fcmToken
        -LocalDateTime actualizadoEn
    }

    class VersionApp {
        -String appId
        -int versionMinima
        -Integer versionRecomendada
    }

    class AppNorday {
        <<constantes>>
        HABITOS
        CONOCIMIENTO
        ENFOQUE
    }

    Usuario "1" --> "0..*" UsuarioDispositivo : un token por app
    UsuarioDispositivo ..> AppNorday : appId
    VersionApp ..> AppNorday : appId
```

- **`usuario_dispositivo`** guarda un token de FCM por usuario y app
  (restricción única `usuario_ref, app_id`). Lo escribe
  `DispositivoService` con la app de la cabecera `X-Norday-App` (sin
  cabecera, `habitos`), y todo envío push pide el token de su app.
- **`Usuario.fcmToken`** es columna de transición: se sigue escribiendo con el
  token de `habitos` para poder volver al jar anterior a V24.
- **`VersionApp`** es la versión mínima (versionCode) de cada app y, desde
  V33, la recomendada (nula = sin aviso, no obliga a nada); las lee
  `GET /api/version/{appId}`, público, que devuelve
  `{"versionMinima": n, "versionRecomendada": n | null}`.

---

## Norday Focus (`enfoque`) y los puertos del motor

```mermaid
classDiagram
    class DestinoMinutos {
        <<interface>>
        +tipo() String
        +disponibles(Long usuarioId) List~DestinoDisponible~
        +aportar(Long usuarioId, Long destinoId, int minutos, UUID clave, LocalDate dia, String origen) ResultadoAporte
        +progreso(Long usuarioId, Long destinoId, LocalDate dia) ProgresoDestino
    }

    class DestinoDisponible {
        <<record>>
        String tipo
        Long id
        String nombre
        int minutosDia
        int minutosObjetivo
    }

    class ResultadoAporte {
        <<record>>
        Estado estado
        String motivo
        int monedas
        int xp
        Integer minutosDia
        Integer minutosObjetivo
        Boolean diaHecho
        List~String~ logros
    }

    class ProgresoDestino {
        <<record>>
        Integer minutosDia
        Integer minutosObjetivo
        Boolean diaHecho
    }

    class CumplimientoDiarioPort {
        <<interface>>
        +hoyCumplido(int usuarioId) boolean
        +comoVaHoy(int usuarioId) AvanceDeHoy
    }

    class DestinoMinutosHabitos

    class CumplimientoDiarioHabitos

    class SesionEnfoqueService {
        -List~DestinoMinutos~ destinos
        +destinos(Usuario) List~DestinoDisponible~
        +registrar(...) Resultado
    }

    class EnfoqueController

    class SesionEnfoque {
        -int id
        -Usuario usuario
        -UUID clave
        -LocalDateTime inicioUtc
        -int duracionSeg
        -int duracionObjetivoMin
        -EstadoSesion estado
        -LocalDate dia
        -String destinoTipo
        -Long destinoRef
        -EstadoAporte aporteEstado
        -String aporteMotivo
        -int minutosAportados
        -int monedasAportadas
        -int xpAportada
        -String logrosAportados
        -LocalDateTime creadoEn
    }

    class EstadoSesion {
        <<enumeration>>
        COMPLETADA
        TERMINADA
        PERDIDA
    }

    class EstadoAporte {
        <<enumeration>>
        SIN_DESTINO
        PENDIENTE
        ACEPTADO
        REPETIDO
        RECHAZADO
    }

    class RegistroService
    class MascotaService

    DestinoMinutos <|.. DestinoMinutosHabitos
    CumplimientoDiarioPort <|.. CumplimientoDiarioHabitos
    DestinoMinutos ..> DestinoDisponible
    DestinoMinutos ..> ResultadoAporte
    DestinoMinutos ..> ProgresoDestino
    EnfoqueController --> SesionEnfoqueService
    SesionEnfoqueService --> DestinoMinutos : elige por tipo()
    SesionEnfoqueService --> SesionEnfoque
    SesionEnfoque ..> EstadoSesion
    SesionEnfoque ..> EstadoAporte
    DestinoMinutosHabitos --> RegistroService : completar(origen ENFOQUE)
    MascotaService --> CumplimientoDiarioPort
```

- **Los puertos viven en el motor** (`gamificacion.service`, y los records en
  `gamificacion.model`) y los implementa el dominio que sabe responder:
  `DestinoMinutosHabitos` y `CumplimientoDiarioHabitos`, en `habitos`.
  `DestinoMinutosHabitos` es el destino de tipo `habito`.
  `enfoque` no conoce los hábitos ni `habitos` conoce las sesiones: Spring
  le da a `SesionEnfoqueService` todos los destinos como
  `List<DestinoMinutos>`, y elige por `tipo()`.
- **`destinoRef` no tiene FK**: es opaco para `enfoque`, y borrar el hábito no
  borra la sesión.
- **La sesión guarda lo que pagó el destino** (`monedasAportadas`,
  `xpAportada`, `logrosAportados`) para que un reintento devuelva el
  resultado original sin volver a llamar a `aportar`.
- `RegistroService` y `MascotaService` aparecen aquí solo como extremos de
  las dependencias; sus detalles, en los flujos de abajo y en 03.

---

## Arquitectura de módulos del backend

```mermaid
flowchart TD
    Server[norday-server] --> Habitos[habitos]
    Server --> Conocimiento[conocimiento]
    Server --> Enfoque[enfoque]
    Habitos --> Motor[norday-motor]
    Conocimiento --> Motor
    Enfoque --> Motor

    Motor --> Core[core: cuenta, auth, email, dispositivos, versión mínima]
    Motor --> Gamificacion[gamificacion: monedas, logros, tienda, mascota]
    Gamificacion --> Puertos[puertos: DestinoMinutos, CumplimientoDiarioPort]

    Habitos --> HabitosCore[Hábitos, objetivos y categorías]
    Habitos --> Registros[Registros, hitos y ritmo]
    Habitos --> Adaptadores[DestinoMinutosHabitos, CumplimientoDiarioHabitos]
    Adaptadores -. implementan .-> Puertos

    Conocimiento --> Pildoras[Píldoras, preferencias y valoraciones]

    Enfoque --> Sesiones[Sesiones, resumen e historial]
    Sesiones -. usan .-> Puertos
```

`norday-server` es el único ejecutable y cuelga de los tres dominios; cada
dominio cuelga del motor y ninguno conoce a los otros (Maven lo impone: no se
declaran entre sí). Cuando un dominio necesita algo de otro, el motor declara
un puerto y el otro lo implementa.

---

## Flujo de un completado (`RegistroService.completar`)

Lo usan el panel de la app (`POST /api/registros/completar/{habitoId}`) y
Norday Focus, a través de `DestinoMinutosHabitos.aportar`. Todo va en una
transacción.

```mermaid
flowchart TD
    Entrada[POST completar o DestinoMinutosHabitos.aportar] --> Clave{¿La clave ya está en registro?}
    Clave -- sí --> Repetido[Respuesta del registro original, premios a 0]
    Clave -- no --> Bloqueo[HabitoService.bloquear: usuario y después hábito, releído]
    Bloqueo --> Clave2{¿La clave ya está? se vuelve a mirar}
    Clave2 -- sí --> Repetido
    Clave2 -- no --> Reglas[Reglas: no en pausa, no futuro, semana en curso, no antes del alta]
    Reglas --> Objetivo[ObjetivoService.ponerAlDia y objetivo vigente ese día]
    Objetivo --> Unidad{¿El día es de minutos?}
    Unidad -- sí --> Minutos[minutos de 1 a 600, sin minutos 409 «actualiza la app»]
    Unidad -- no --> Veces[veces: no pasar de la meta del día]
    Minutos --> Foto
    Veces --> Foto
    Foto[Instantánea: saldo, racha previa sin actualizarla, mascota, logros] --> Guardar[Guardar Registro: cantidad, origen, claveCliente]
    Guardar --> Premio[CalculoPremio: monedas del paso y extra de semana en UsuarioMoneda]
    Premio --> XP[XP a la mascota, o revisar logros de fase]
    XP --> Hitos[Hitos cruzados que no estén en hito_pagado: se apuntan y se pagan]
    Hitos --> Dia[Día completo de la mascota, si es hoy]
    Dia --> Logros[LogrosHabitosService: constancia y vuelta]
    Logros --> Reversion[ReversionRegistro y ReversionLogro con lo que pasó]
    Reversion --> Respuesta[201 con puntos, xp, logros, minutosDia, minutosObjetivo, diaHecho]
```

- **La racha no se toca ni se guarda**: la reversión lleva sus cuatro campos
  a `null`.
- **El ritmo no se escribe**: `RitmoService` lo calcula al leer (Hoy,
  detalle) desde los registros.
- **El libro de hitos** decide qué hitos se pagan: los umbrales cruzados en
  este paso que no tengan ya fila en `hito_pagado`.
- Todo lo que el completado escribe en el saldo o en los logros se mide como
  «después − antes» con el usuario bloqueado, y queda en la reversión.

---

## Flujo de una sesión de Norday Focus (`POST /api/enfoque/sesiones`)

`SesionEnfoqueService.registrar` no es `@Transactional`: son tres
transacciones, para que un fallo al llevar los minutos no se lleve por
delante la sesión.

```mermaid
flowchart TD
    App[Norday Focus: sesión acabada en la cola] --> Ctrl[EnfoqueController]
    Ctrl --> Leer[Leer el cuerpo: estado, duración, objetivo, destino]
    Leer -- no vale --> E400[400 definitivo]
    Leer --> T1[Transacción 1: bloquear al usuario]
    T1 --> Clave{¿La clave ya está guardada?}
    Clave -- sí, con otro cuerpo --> E409[409 definitivo]
    Clave -- sí, la misma petición --> Estado
    Clave -- no --> Tiempo[Reglas de tiempo: no acaba en el futuro, no empezó hace más de 7 días]
    Tiempo -- no vale --> E400
    Tiempo --> Solape{¿Se solapa con otra sesión?}
    Solape -- sí --> E409
    Solape -- no --> Alta[Guardar SesionEnfoque: SIN_DESTINO, RECHAZADO si PERDIDA, o PENDIENTE]
    Alta --> Estado{¿Aporte PENDIENTE?}
    Estado -- no --> Guardado[Responder con el aporte guardado]
    Estado -- sí --> T2[Transacción 2, REQUIRES_NEW: DestinoMinutos.aportar del tipo]
    T2 --> Habitos[DestinoMinutosHabitos: valida y llama a RegistroService.completar]
    Habitos --> Res{Resultado}
    Res -- ACEPTADO, REPETIDO o RECHAZADO --> T3[Transacción 3: apuntar el resultado con precedencia]
    Res -- fallo técnico o espera de bloqueo agotada --> E503[503: la sesión queda PENDIENTE y la app reintenta con la misma clave]
    T3 --> Respuesta[201 si es nueva, 200 si es un reintento, con el aporte y el progreso del día]
    Guardado --> Respuesta
```

- **Enfoque no paga nada propio**: monedas, XP y logros los paga el hábito,
  con sus reglas, igual que el panel de minutos.
- **La clave de la sesión es la clave del registro**: un reintento no suma ni
  paga dos veces, ni en `sesion_enfoque` ni en `registro`.
- **El contrato de reintento**: 5xx o sin respuesta, reintentar con el mismo
  cuerpo; 400 y 409, definitivos. La cola de Focus deja también en la cola
  otros 4xx (401, 404) y descarta las sesiones de más de 7 días.

---

## Deshacer un completado

```mermaid
flowchart TD
    Deshacer[DELETE /api/registros/id] --> Bloqueo[HabitoService.bloquear: usuario y hábito]
    Bloqueo --> Reglas[Solo el último completado, de la semana en curso, hábito no en pausa]
    Reglas --> Reversion[Leer ReversionRegistro]
    Reversion --> Logros[Retirar los logros de ReversionLogro]
    Reversion --> Monedas[Movimiento de signo contrario en UsuarioMoneda]
    Reversion --> XP[Restar la XP que dio el paso]
    Reversion --> Mascota[Restaurar el día completo previo de la mascota]
    Reversion --> Hito[Borrar las filas de hito_pagado de ese registro]
    Hito --> Borrar[Borrar la reversión y el registro]
```

Todo efecto nuevo de un completado tiene que quedar en la reversión. Deshacer
un registro que vino de una sesión de Focus no toca la sesión: sus
`minutosAportados` se quedan (limitación conocida).

---

## Notas

- `RachaService` aplica una estrategia de **rotura perezosa**: la racha se
  normaliza al leerla, no con un proceso que resetee rachas a una hora fija.
  Y desde el 21-oct-2026 el completado ya no la actualiza.
- **`NotificadorRachaEnPeligro` está desactivado**: su `@Scheduled` está
  comentado desde el rediseño de rachas (B5) y el barrido no corre. El único
  barrido programado de `habitos` es `NotificacionScheduler` (recordatorios,
  cada 5 minutos).
- El modelo de gamificación es compartido por el ecosistema; los eventos que
  generan logros de hábitos pertenecen al módulo `habitos`.
- No se representan aquí clases retiradas ni estructuras marcadas únicamente
  como futuras.
