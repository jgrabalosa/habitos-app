# Norday — Contexto del proyecto (Backend)

Este backend (Spring Boot + PostgreSQL) es el primero de un ecosistema de
apps Norday. Todas las apps futuras compartirán este mismo backend, la
misma base de datos y la misma cuenta de usuario/monedas. Cada decisión
de arquitectura se toma pensando en esa reutilización.

## Regla de arquitectura obligatoria: Motor vs Disparadores

- **Motor** = genérico, reutilizable por cualquier app futura del
  ecosistema: auth/JWT, usuarios, sistema de puntos (UsuarioMoneda),
  logros, tienda (Producto/UsuarioProducto), mascota, email, FCM.
- **Disparadores** = específico del dominio "hábitos": Habito, Registro,
  Frecuencia, Categoria, sus schedulers y controllers.

**Ningún servicio genérico puede conocer conceptos de dominio como
"hábito", "registro" o "categoría".** El motor recibe códigos/eventos
genéricos (ej. `ganarExperiencia(usuarioId, cantidad)`, no
`ganarExperienciaPorHabito(...)`). Si una tarea toca código del motor,
comprueba primero si de verdad pertenece ahí o si en realidad es lógica
específica de hábitos que se ha colado.

## Modularización futura (no ejecutar todavía, solo respetar la disciplina)

El código se reorganizará más adelante en paquetes por dominio:
`com.norday.core`, `com.norday.gamificacion`, `com.norday.habitos`.
Regla de dependencias: `habitos` puede importar de `core` y
`gamificacion`, nunca al revés. Escribe el código nuevo ya respetando
mentalmente esos límites, aunque los paquetes aún no existan, para no
generar deuda nueva.

## Catálogos compartidos

Los catálogos de logros y productos deben poder distinguir a qué app
pertenecen (campo `app`) cuando se construya/amplíe la tienda. El saldo
de puntos (UsuarioMoneda) es único y compartido entre todas las apps del
ecosistema — no crear muros entre apps.

## Estilo de trabajo con el usuario

- Un paso a la vez, confirmar que compila antes de seguir.
- Si algo admite varios diseños o no está claro, preguntar antes de
  decidir — no asumir.
- Nunca hacer push ni tocar sistemas externos (Railway/Oracle/DB de
  producción) sin confirmación explícita.
