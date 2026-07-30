# HábitosApp — Modelo Entidad-Relación

*Última actualización: julio 2026*

> Este diagrama refleja el modelo de datos actual. La entidad `Habito` incluye el campo `frecuencia`, que en la Fase Crítica de rediseño de rachas perderá los valores `MENSUAL` y `PERSONALIZADO` (quedarán solo `DIARIO` y `SEMANAL` en V1) — el diagrama no refleja aún ese cambio, ya que el código todavía no se ha modificado.

```mermaid
erDiagram
    USUARIO ||--o{ HABITO : "propietario"
    USUARIO ||--o{ CATEGORIA : "creador (personalizadas)"
    USUARIO ||--o{ USUARIO_LOGRO : "consigue"
    USUARIO ||--o{ USUARIO_MONEDA : "genera movimientos"
    USUARIO ||--o{ USUARIO_PRODUCTO : "posee"
    HABITO ||--|| RACHA : "tiene"
    HABITO ||--o{ REGISTRO : "genera"
    CATEGORIA ||--o{ HABITO : "tipo (opcional)"
    LOGRO ||--o{ USUARIO_LOGRO : "otorgado en"
    PRODUCTO ||--o{ USUARIO_PRODUCTO : "comprado en"

    USUARIO {
        int usuarioId PK
        string nombre
        string username
        string email
        string contrasena
        string proveedorAuth "LOCAL o GOOGLE"
        datetime fechaRegistro
        string fcmToken "nullable"
    }

    CATEGORIA {
        int categoriaId PK
        string nombre
        string descripcion
        string color
        string icono
        boolean esGlobal
        int orden
        int creador_id FK "nullable, null si es global"
    }

    HABITO {
        int habitoId PK
        string nombre
        string descripcion
        string frecuencia "enum: DIARIO/SEMANAL/MENSUAL/PERSONALIZADO"
        int meta
        date fechaInicio
        boolean activo
        int propietario_id FK
        int tipo_id FK "nullable"
    }

    RACHA {
        int rachaId PK
        int rachaActual
        int rachaMaxima
        date ultimaFecha
        int habito_id FK "unique, relacion 1 a 1"
    }

    REGISTRO {
        int registroId PK
        date fecha
        boolean completado
        string nota "nullable"
        int habito_id FK
    }

    LOGRO {
        int logroId PK
        string codigo "unique, identificador tecnico estable"
        string nombre
        string descripcion
        string categoria "Inicio/Constancia/Volumen/Variedad/Exploracion"
        string nivel "Facil/Medio/Dificil"
        int puntos
        string icono "nullable"
        boolean activo
    }

    USUARIO_LOGRO {
        int usuarioLogroId PK
        int usuario_id FK
        int logro_id FK
        datetime fechaConseguido
    }

    USUARIO_MONEDA {
        int movimientoId PK
        int usuario_id FK
        int cantidad "positivo=entrada, negativo=salida"
        string origen "HABITO_COMPLETADO/HITO_RACHA/LOGRO/COMPRA"
        int referenciaId "nullable, id generico segun origen"
        string descripcion
        datetime fecha
    }

    PRODUCTO {
        int productoId PK
        string nombre
        string descripcion
        string categoria
        string tipo "CONSUMIBLE/EQUIPABLE/COLECCIONABLE"
        int precio "en puntos"
        string icono "nullable"
        boolean activo
    }

    USUARIO_PRODUCTO {
        int usuarioProductoId PK
        int usuario_id FK
        int producto_id FK
        datetime fechaAdquirido
        int cantidad "stock, relevante para CONSUMIBLE"
        boolean equipado "solo aplica a EQUIPABLE"
    }
```

## Notas sobre las relaciones

- **Usuario → Categoria (creador):** solo las categorías personalizadas por un usuario tienen `creador_id` relleno. Las 10 categorías globales (sembradas por `DataInitializer`) tienen `creador_id = null`.
- **Habito → Racha:** relación 1 a 1 real — cada hábito tiene exactamente una fila de racha asociada, creada automáticamente al crear el hábito.
- **UsuarioMoneda → referenciaId:** es un campo genérico sin FK estricta a nivel de base de datos. Según el valor de `origen`, apunta a un `habito_id`, `logro_id` o `producto_id` distinto. Decisión de diseño para evitar tres columnas FK casi siempre en null.
- **PerfilGamificacion (eliminada):** existió como tabla casi vacía (solo la relación con Usuario), reservada para futuros campos de V2 (niveles, XP). Nunca llegó a usarse — ningún DAO, servicio ni controller la leía — y se eliminó como código muerto. Su función quedó cubierta por las cuatro tablas que ya cuelgan de Usuario y que sí se usan: `USUARIO_MONEDA` (saldo y movimientos de puntos), `USUARIO_PRODUCTO` (productos y avatares poseídos), `USUARIO_LOGRO` (logros conseguidos) y `MASCOTA` (nivel y XP, que era justo el hueco que PerfilGamificacion pretendía llenar). Si en el futuro hace falta un campo de gamificación que no encaje en ninguna de ellas, se añade donde corresponda en lugar de resucitar una tabla intermedia vacía.
