# HábitosApp — Diagramas UML de Clases

*Última actualización: julio 2026*

Se presentan dos diagramas separados por dominio, para mantener la legibilidad: **Núcleo de hábitos** y **Gamificación**. `Usuario` aparece en ambos como punto de conexión entre dominios.

---

## Dominio: Núcleo de Hábitos

```mermaid
classDiagram
    class Usuario {
        -int usuarioId
        -String nombre
        -String username
        -String email
        -String contrasena
        -String proveedorAuth
        -LocalDateTime fechaRegistro
        -String fcmToken
    }

    class Categoria {
        -int categoriaId
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
        -LocalDate fechaInicio
        -boolean activo
        -Usuario propietario
        -Categoria tipo
    }

    class Racha {
        -int rachaId
        -int rachaActual
        -int rachaMaxima
        -LocalDate ultimaFecha
        -Habito habito
    }

    class Registro {
        -int registroId
        -LocalDate fecha
        -boolean completado
        -String nota
        -Habito habito
    }

    class Frecuencia {
        <<enumeration>>
        DIARIO
        SEMANAL
        MENSUAL
        PERSONALIZADO
    }

    Usuario "1" --> "0..*" Habito : propietario
    Usuario "1" --> "0..*" Categoria : creador
    Categoria "0..1" --> "0..*" Habito : tipo
    Habito "1" --> "1" Racha
    Habito "1" --> "0..*" Registro
    Habito ..> Frecuencia
```

> ⚠️ **Pendiente (Fase Crítica):** el enum `Frecuencia` perderá `MENSUAL` y `PERSONALIZADO` en V1 — quedarán solo `DIARIO` y `SEMANAL`. Este diagrama refleja el estado actual del código, aún sin modificar.

---

## Dominio: Gamificación

```mermaid
classDiagram
    class Usuario {
        -int usuarioId
    }

    class PerfilGamificacion {
        -int perfilGamificacionId
        -Usuario usuario
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
        -String nombre
        -String descripcion
        -String categoria
        -String tipo
        -int precio
        -String icono
        -boolean activo
    }

    class UsuarioProducto {
        -int usuarioProductoId
        -Usuario usuario
        -Producto producto
        -LocalDateTime fechaAdquirido
        -int cantidad
        -boolean equipado
    }

    Usuario "1" --> "0..1" PerfilGamificacion
    Usuario "1" --> "0..*" UsuarioLogro
    Logro "1" --> "0..*" UsuarioLogro
    Usuario "1" --> "0..*" UsuarioMoneda
    Usuario "1" --> "0..*" UsuarioProducto
    Producto "1" --> "0..*" UsuarioProducto
```

---

## Arquitectura de capas (backend)

No es UML de clases estricto, pero ayuda a visualizar cómo se conectan las piezas de la Fase 9:

```mermaid
flowchart TD
    Controller[GamificacionController] --> LogroService
    Controller --> ProductoService
    Controller --> UsuarioMonedaService

    RegistroService --> MotorLogrosService
    RegistroService --> UsuarioMonedaService
    HabitoService --> MotorLogrosService
    CategoriaService --> MotorLogrosService
    UsuarioService --> MotorLogrosService

    MotorLogrosService --> LogroService
    LogroService --> UsuarioMonedaService

    LogroService --> ILogroDAO
    LogroService --> IUsuarioLogroDAO
    ProductoService --> IProductoDAO
    ProductoService --> IUsuarioProductoDAO
    UsuarioMonedaService --> IUsuarioMonedaDAO
    MotorLogrosService --> IHabitoDAO
    MotorLogrosService --> IRegistroDAO
    MotorLogrosService --> IRachaDAO
```

**Lectura del diagrama:** `MotorLogrosService` es el punto central de evaluación — se llama desde los 4 servicios de negocio (`RegistroService`, `HabitoService`, `CategoriaService`, `UsuarioService`) cada vez que ocurre una acción relevante, y a su vez usa `LogroService` para otorgar logros, que internamente ya dispara el registro de puntos correspondiente en `UsuarioMonedaService`.
