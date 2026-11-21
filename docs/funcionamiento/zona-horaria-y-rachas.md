# Zona horaria, locale y rachas

Movido desde `CLAUDE.md` (T-005a).

Ningún cálculo de "hoy" de cara al usuario puede usar `LocalDate.now()` sin
zona. La zona sale siempre del usuario, vía `ZonaUsuarioService`. Los sellos
de auditoría (`fechaRegistro`, `UsuarioLogro`, `UsuarioMoneda`,
`UsuarioProducto`) van en UTC explícito: no son días de usuario. Las
caducidades absolutas (código de recuperación) son instantes, no días, y no
se tocan.

La JVM **no** fija zona por defecto — se eliminó el `TimeZone.setDefault`.
Toma la del sistema operativo: en el VPS es `Europe/Madrid`. Nada del
código debe depender de ese valor. Lo que necesite UTC lo pide explícito,
como hacen los sellos de auditoría del párrafo anterior.

La racha no depende de ningún cron: `Racha` guarda `periodoMetaAlcanzada`
(el inicio del periodo en que se cumplió la meta) y el sello se autocaduca
al cambiar de periodo. La rotura es perezosa y se normaliza al leer, en
`RachaService.rachaActualVigente`. Ningún barrido decide sobre rachas; el
scheduler solo avisa.
