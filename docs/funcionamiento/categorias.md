# Categorías propias: contrato de `/api/categorias`

(T-031a.) Antes de este cambio el contrato no estaba descrito en ningún
documento; es el código de `CategoriaController` y `CategoriaService`, módulo
`habitos`. Aquí queda por escrito.

Las categorías son de dos clases: **globales** (catálogo sembrado por
`CategoriasInitializer`, con `codigo`, sin creador) y **propias** (las crea el
usuario, sin `codigo`, con creador). Un hábito usa como mucho una
(`habito.tipo_id`, FK `FK_habito_categoria`) o ninguna.

## Qué sale: `CategoriaDTO`

`categoriaId`, `codigo`, `nombre`, `descripcion`, `color`, `icono`, `esGlobal`
y `orden`. Son las claves que salían cuando el endpoint devolvía la entidad,
menos `creador` (el `Usuario` entero): la app ya instalada no nota el cambio.
**Los endpoints devuelven DTO, nunca la entidad con sus relaciones.**

## Qué entra: `CategoriaEntradaDTO`

Solo dos campos; lo demás que llegue en el cuerpo (código, color, creador,
`esGlobal`, orden...) se ignora.

- `nombre`: obligatorio; se recortan los espacios; de 2 a 30 caracteres.
- `icono`: opcional; `null`, o de 1 a 16 caracteres (un emoji puede ocupar
  varios; se cuentan caracteres Unicode, no unidades UTF-16).

Si no cumple: 400, `{"mensaje": ...}`, texto en el idioma del usuario
(`categoria.*` en los bundles de `habitos`).

## Endpoints

| Método y ruta | Resultado |
|---|---|
| `GET /usuario/{usuarioId}` | 200, lista de `CategoriaDTO`: las globales y las del usuario. 403 con el id de otro. |
| `POST /` | 201 con la `CategoriaDTO` creada. El creador sale del token; `codigo` (null) y `esGlobal` (false) los pone el servidor. |
| `PUT /{id}` | 200 con la `CategoriaDTO`. Cambia solo `nombre` e `icono`; un `icono` null lo quita. |
| `DELETE /{id}` | 200. Ver «Borrar». |

`PUT` y `DELETE`: 404 si no existe; 403 si no es del usuario del token. Las
globales (creador null) no se editan ni se borran: 403.

## Conflictos (409, `ConflictoException`)

- El nombre, recortado y sin distinguir mayúsculas, coincide con otra
  categoría propia del usuario o con una global (su `nombre` en la base de
  datos, no su traducción). Al editar no cuenta la propia categoría.
- El usuario ya tiene **20** categorías propias (solo al crear).

Dos nombres iguales de usuarios distintos sí valen.

## Borrar

En una sola transacción: **todos los hábitos que usan la categoría pasan a «sin
categoría»** (`tipo = null`), activos o no, pausados o no; después se borra la
categoría. Ningún hábito se borra ni cambia nada más (registros y objetivos
intactos). Sin ese paso la FK `FK_habito_categoria` impide el borrado y salía
un 500.

El paso es un `UPDATE` en lote (`IHabitoDAO.quitarCategoria`), llamado desde
`CategoriaService.eliminar`: sigue dentro del módulo `habitos` y no recorre los
hábitos uno a uno. Decisión y alternativas:
`docs/decisiones/2026-11-22-borrar-categoria-deja-habitos-sin-categoria.md`.

## Bloqueos y logros

Crear, editar y borrar bloquean primero al usuario (`HabitoService.bloquearUsuario`):
el tope de 20 y la unicidad de nombre se comprueban contando, y dos peticiones
a la vez se colarían sin ese bloqueo. Crear la primera categoría concede
`PRIMERA_CATEGORIA` (`LogrosHabitosService.evaluarTrasCrearCategoria`).

## Tests

`CategoriasApiTest` (`norday-server`, `@SpringBootTest` contra `habitos_db_test`)
cubre todo lo anterior, incluido el borrado con dos hábitos. Tiene que ser de
integración: el fallo original es de la clave ajena y un unitario con DAOs
mockeados no lo ve. `AutorizacionPorUsuarioTest` ya cubre el 403 del `GET`
con el id de otro usuario.
