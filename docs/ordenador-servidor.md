# Ordenador-Servidor

_Actualizado el 20-nov-2026. Estado real del servidor local, de Hermes y del robot automatizador._

## 1. Qué es cada máquina

### Ordenador-Trabajo

Windows de trabajo (`C:\Dev\…`).

Se utiliza principalmente para:

- Desarrollo y revisión.
- Builds de release.
- Pruebas en dispositivos móviles.
- Tareas que requieren validación visual directa.

### Ordenador-Servidor

Linux en casa:

```text
quim@192.168.1.135
```

Funciones actuales:

- **Hermes**, agente local conectado a Telegram (usuario `quim`).
- **El robot automatizador** (usuario `robot`): otra instalación de Hermes,
  con sus propios perfiles, más Claude Code. Ver la sección 15.
- Clones locales de los repositorios de Norday.
- Destino doméstico previsto para determinados backups.
- Montaje de Google Drive mediante `rclone` para acceder a las notas de Obsidian/Conocimiento.

Hermes está actualmente orientado a convertirse en el **agente principal de Marketing, estrategia, investigación, contenido y publicación en RRSS de Norday**.

La arquitectura inicial mantiene **un único Hermes principal**. No se crean subagentes hasta que exista una necesidad real que justifique separarlos.

Un futuro agente independiente de **Mantenimiento** podrá encargarse de la supervisión técnica de los repositorios. Su construcción queda para una fase posterior.

En la máquina hay dos usuarios Linux con trabajo propio:

| Usuario | Qué corre | Servicio |
|---|---|---|
| `quim` | Hermes de marketing | `hermes-gateway.service` (`systemd --user`) |
| `robot` | Robot automatizador | `norday-robot-gateway.service` (de sistema) |

Los dos no comparten nada: cada uno tiene su propia instalación de Hermes,
sus propios clones y sus propias credenciales.

---

## 2. Hermes: estado actual

Hermes funciona como un servicio `systemd --user`.

Servicio:

```text
hermes-gateway.service
```

Estado actual:

```text
Loaded: enabled
Active: active (running)
```

El proceso principal es:

```text
/home/quim/.hermes/hermes-agent/venv/bin/python -m hermes_cli.main gateway run
```

El servicio pertenece al usuario `quim` y está gestionado mediante:

```bash
systemctl --user
```

Comandos principales:

```bash
systemctl --user status hermes-gateway.service --no-pager
systemctl --user is-enabled hermes-gateway.service
systemctl --user is-active hermes-gateway.service
```

El gateway utiliza Telegram como interfaz y dispone de procesos auxiliares relacionados con `browser-use`.

### Arquitectura funcional prevista

```text
                         HERMES
                           │
          ┌────────────────┼────────────────┐
          │                │                │
      Marketing       Estrategia       Investigación
          │                │                │
          └────────────────┼────────────────┘
                           │
                       Contenido
                           │
                         RRSS
                           │
                    Joaquim aprueba
```

La prioridad actual es terminar y validar este Hermes principal antes de construir agentes independientes.

---

## 3. Principio de control

La regla de trabajo actual es:

> **Hermes propone → Joaquim aprueba.**

Inicialmente Hermes no debe asumir como autorizadas acciones irreversibles o de alto impacto.

En particular, cualquier publicación, cambio estratégico relevante, modificación de código, push o cambio de producción debe quedar sujeto al flujo de aprobación definido para cada tarea.

El hecho de que Hermes diga que una acción se ha realizado no constituye por sí mismo evidencia.

Cuando sea necesario, las acciones deben verificarse contra su fuente real:

- GitHub.
- Código.
- Documentación.
- Estado de la aplicación.
- Herramientas utilizadas.
- Resultado observable.

---

## 4. Repositorios locales

| Repo | Ordenador-Trabajo | Ordenador-Servidor |
|---|---|---|
| `habitos-app` | `C:\Dev\Norday\habitos-app` | `/home/quim/habitos-app` |
| `habitos_app_mobile` | `C:\Dev\Norday\habitos_app_mobile` | `/home/quim/habitos_app_mobile` |
| `norday_flutter_core` | `C:\Dev\Norday\norday_flutter_core` | `/home/quim/norday_flutter_core` |
| `enfoque_app_mobile` | `C:\Dev\Norday\enfoque_app_mobile` | — |
| `conocimiento_app_mobile` | `C:\Dev\Conocimiento\conocimiento_app_mobile` | `/home/quim/conocimiento_app_mobile` |

Las rutas Linux anteriores son los clones locales conocidos en el servidor
del usuario `quim`. `enfoque_app_mobile` (Norday Focus) tiene repositorio en
GitHub desde el 15-nov-2026 (`jgrabalosa/enfoque_app_mobile`), pero no tiene
clon conocido en el servidor.

El robot tiene sus propios clones en `/home/robot/repos/` (ver la sección 15);
no usa los de esta tabla.

El repositorio `norday_investigation` ya no forma parte de esta arquitectura.

### GitHub

La autenticación actual de GitHub CLI es:

```text
Cuenta: jgrabalosa
Protocolo de Git del CLI: HTTPS
```

Los remotes actuales son:

```text
habitos-app
  git@github-habitos-app:jgrabalosa/habitos-app.git

habitos_app_mobile
  https://github.com/jgrabalosa/habitos_app_mobile.git

norday_flutter_core
  https://github.com/jgrabalosa/norday_flutter_core.git
```

El remote SSH de `habitos-app` es exclusivamente un mecanismo del repositorio para acceder a GitHub. No debe confundirse con el acceso SSH al ordenador-servidor.

No se documenta actualmente un token de GitHub exclusivo de Hermes ni permisos específicos de Hermes hasta verificar su configuración real.

---

## 5. Flujo Git

Antes de trabajar en un repositorio:

```bash
git status
git fetch origin
git switch wip          # o la rama del tema: wip-<tema>, p. ej. wip-enfoque
git pull
```

Reglas:

- Comprobar siempre el estado del repositorio antes de empezar.
- Evitar trabajar simultáneamente sobre la misma rama desde las dos máquinas.
- No mezclar `main` dentro de una rama de trabajo mediante un `git pull origin main` ejecutado accidentalmente desde esa rama.
- Las ramas de trabajo son `wip` y, para un tema largo, `wip-<tema>` (hoy
  `wip-enfoque`, que es además la línea de publicación de hábitos en minutos
  y Norday Focus).
- Nadie trabaja en `main`. **Quim mergea a `main`** cuando ha revisado el
  diff en el remoto, y es quien pone los tags.

Las reglas concretas de trabajo de Hermes deben prevalecer sobre las antiguas instrucciones específicas de programación que existían en este documento.

---

## 6. Google Drive / rclone

Las notas de Obsidian/Conocimiento se encuentran actualmente en:

```text
/home/quim/GoogleDrive/Conocimiento
```

Comprobación:

```bash
ls -la ~/GoogleDrive
```

Servicio actualmente documentado para el montaje:

```bash
sudo systemctl status rclone-gdrive.service
sudo systemctl restart rclone-gdrive.service
```

Existe una comprobación pendiente sobre el remoto y sobre el alcance real de las credenciales de `rclone`.

No se debe ejecutar ninguna eliminación o modificación del remoto `gdrive` hasta comprobar que corresponde al entorno correcto.

---

## 7. Monitorización básica

```bash
free -h
df -h /
htop
```

Para comprobar Hermes:

```bash
systemctl --user status hermes-gateway.service --no-pager
```

Para consultar sus logs:

```bash
journalctl --user -u hermes-gateway.service
```

---

## 8. Reinicio y apagado

```bash
sudo reboot
sudo shutdown now
```

---

## 9. Acceso al servidor

Acceso actual desde la red local:

```bash
ssh quim@192.168.1.135
```

La configuración de seguridad SSH definitiva queda pendiente de auditoría.

No se considera cerrado todavía:

- Reserva permanente de IP.
- Uso exclusivo de claves SSH.
- Acceso remoto desde fuera de la red local mediante VPN.
- Revisión de usuarios y permisos.

No se debe abrir directamente SSH a Internet como solución de acceso remoto.

El usuario `robot` no es la puerta de entrada a la máquina: con el robot se
trabaja desde Telegram, por su gateway de Hermes, y su servicio se gestiona
desde el usuario `quim` con `sudo` (sección 15). `robot` no está en el grupo
`sudo` ni en el de `quim`.

---

## 10. Backups

El servidor forma parte de la estrategia doméstica de almacenamiento de backups.

La situación exacta de:

- backups locales,
- backups del VPS,
- espacio disponible,
- frecuencia,
- segunda copia,
- credenciales utilizadas,

queda pendiente de comprobación.

No se deben asumir como actuales las configuraciones descritas en documentación histórica hasta verificarlas en el servidor.

Del robot hay una copia de su configuración, sin secretos, en `servidor/` del
repositorio privado `norday-robot` (ver la sección 15): con ella se puede
reconstruir. Sus clones salen de GitHub. Sus secretos no están en ese
repositorio ni en este documento. Si el usuario `robot` entra o no en los
backups del servidor queda pendiente (sección 14).

---

## 11. Seguridad y permisos

Los siguientes puntos requieren auditoría antes de considerarse cerrados:

- [ ] Protección técnica de `main` en los repositorios.
- [ ] Determinar exactamente qué credenciales utiliza Hermes para GitHub.
- [ ] Determinar qué permisos necesita realmente Hermes Marketing.
- [ ] Separar, si es necesario, permisos de lectura y escritura.
- [ ] Revisar usuario Linux utilizado por Hermes.
- [ ] Revisar alcance de las credenciales de `rclone`.
- [ ] Revisar acceso a backups.
- [ ] Revisar configuración SSH.
- [ ] Revisar acceso desde fuera de la red local.

La separación de permisos debe diseñarse en función de las necesidades reales de Hermes y no heredarse automáticamente de la antigua arquitectura orientada a programación.

### El robot

El robot ya está separado del usuario `quim`:

- Corre con su propio usuario Linux, `robot`, que solo está en los grupos
  `robot` y `users`.
- No usa la cuenta de GitHub CLI de la sección 4: se autentica con una
  GitHub App propia, con tokens que caducan en una hora.
- Hace push sin `--force`, a ramas `robot/…`, y abre los PR en borrador.
  El merge a `main` es siempre de Quim.
- Claude Code corre con un hook que bloquea lo peligroso (`robot-guard`).
- Sus secretos viven solo en su `/home/robot`, fuera de los repositorios.

---

## 12. Estado de arquitectura Hermes

### Actual

```text
                    Telegram
                       │
                       ▼
                 Hermes Gateway
                       │
             systemd --user
                       │
              Hermes principal
                       │
       ┌───────────────┼───────────────┐
       │               │               │
   Knowledge         Skills          Tools
```

### Futuro

Cuando exista una necesidad real:

```text
                    Hermes
                      │
          ┌───────────┴───────────┐
          │                       │
      Marketing              Maintenance
```

El agente de Mantenimiento será independiente del agente de Marketing.

Su función prevista será principalmente:

- Comprobar clones locales.
- Ejecutar `git fetch`/`git status`.
- Detectar cambios relevantes.
- Informar del estado de los repositorios.

Inicialmente no deberá:

- modificar código,
- hacer commits,
- hacer push,
- modificar producción.

La construcción de este agente queda pospuesta hasta terminar Hermes Marketing/RRSS.

---

## 13. Principios de trabajo

### Simplicidad

La arquitectura debe mantenerse lo más simple y visual posible.

No se deben crear agentes, servicios, conexiones o automatizaciones adicionales sin una necesidad clara.

### Evidencia

Diferenciar siempre:

- Documentado.
- Implementado.
- Verificado.
- Disponible.
- Planificado.
- Inferido.

### Producto

El código es evidencia técnica, pero:

> **implementado técnicamente ≠ disponible públicamente ≠ promesa de marketing**

Hermes debe comprobar el estado real antes de convertir una característica en una afirmación pública.

### Fuentes

Para decisiones sobre Hermes:

1. Documentación oficial actual de Hermes.
2. Configuración y estado real del servidor.
3. Documentación y Ground Truth de Norday.
4. Código y arquitectura de los repositorios.
5. Otras fuentes, cuando sean necesarias.

Las decisiones propias deben distinguirse de las recomendaciones oficiales.

---

## 14. Pendiente de auditoría

### Servidor

- [ ] Revisar configuración SSH.
- [ ] Revisar reserva de IP.
- [ ] Auditar espacio de disco.
- [ ] Auditar backups.
- [ ] Auditar `rclone`.
- [ ] Auditar permisos y credenciales.

### GitHub

- [ ] Verificar protección real de `main`.
- [ ] Verificar permisos efectivos utilizados por Hermes.
- [ ] Determinar si Hermes necesita escritura o únicamente lectura para Marketing.

### Hermes

- [x] Gateway instalado.
- [x] Servicio `hermes-gateway.service` identificado.
- [x] Servicio gestionado mediante `systemd --user`.
- [x] Servicio habilitado.
- [x] Servicio activo.
- [x] Telegram operativo.
- [ ] Auditar errores actuales del gateway.
- [ ] Completar auditoría de comportamiento.
- [ ] Completar Ground Truth.
- [ ] Construir Hermes Marketing/RRSS.

### Robot

- [x] Usuario `robot` propio, fuera del grupo `sudo`.
- [x] Servicio `norday-robot-gateway.service` instalado como servicio de sistema.
- [x] Configuración copiada, sin secretos, en `norday-robot/servidor/`.
- [ ] Decidir si `/home/robot` entra en los backups del servidor.
- [ ] Compilar Android: el servidor no puede; propuesta W-0 sin instalar.

### Arquitectura futura

- [x] Mantener inicialmente un único Hermes principal.
- [ ] Definir y construir agente de Mantenimiento en una fase posterior.

---

## 15. El robot automatizador

### Qué es

El robot programa las tareas de Norday que se le encargan. Es otra
instalación de Hermes, independiente del Hermes de marketing, con cuatro
perfiles, más Claude Code:

| Perfil | Papel |
|---|---|
| `arquitecto` | Trocea cada tarea en tarjetas y lleva el turno. |
| `desarrollador` | Prepara el worktree de la tarjeta, lanza Claude Code y publica el PR en borrador. |
| `revisor` | Comprueba la tarjeta en un clon limpio contra su criterio de hecho, pasa los tests y aprueba o devuelve. |
| `revisor-n2` | Igual que `revisor`, para las tarjetas de nivel 2: las que fallaron en nivel 1 o llevan «Nivel: 2» en su ficha. |

Claude Code es quien escribe el código; los perfiles de Hermes lo lanzan y
lo revisan.

### Usuario y ficheros

Corre con el usuario Linux `robot`. Todo lo suyo vive en `/home/robot`:

- **Hermes**, con sus cuatro perfiles.
- **`~/repos`**: sus clones de los repositorios de su alcance (los dice
  `ROADMAP_ROBOT.md`). Cada tarjeta trabaja en un worktree propio, en una rama
  `robot/T-xxx-…`.
- **`~/bin`**: los guiones del robot (`robot-noche`, `robot-preparar`,
  `robot-programar`, `robot-publicar`, `robot-revisar`, etc.). Lo que hace cada
  uno está en su cabecera.

El servidor no compila Android. La propuesta para que lo compile GitHub
Actions está en `w0/LEEME.md` de `norday-robot`, sin instalar.

### Servicio y trabajos programados

Un solo servicio, de sistema (no `systemd --user`), que corre como `robot`:

```text
norday-robot-gateway.service
```

Arranca un único gateway de Hermes, el del perfil `default`, que atiende
Telegram y a todos los perfiles del robot. Ese gateway hace correr los dos
trabajos programados, que son del perfil `arquitecto`:

| Trabajo | Cuándo | Qué hace |
|---|---|---|
| `robot-turno` | Cada 30 minutos | Lo mecánico del turno: cierra lo terminado, relanza lo bloqueado y, si toca, despierta al arquitecto para trocear una tarea nueva. |
| `robot-cierre` | A las 05:00 | Aparca lo que espera de la cola nocturna y saca el resumen. |

Los dos los lanza `~/bin/robot-noche` (el cierre, con `--cierre`).

### Pararlo y arrancarlo

Hay dos niveles:

- **Que deje de coger trabajo**, sin parar el servicio: desde Telegram,
  `/parar` y `/arrancar`. `/estado` dice cómo está.
- **Parar el servicio entero**: lo instaló Quim con `sudo` y solo se para o
  arranca así, desde el usuario `quim`:

```bash
sudo systemctl stop norday-robot-gateway.service
sudo systemctl start norday-robot-gateway.service
sudo systemctl status norday-robot-gateway.service --no-pager
```

Con el servicio parado tampoco corren los trabajos programados.

### Su documentación

En el repositorio privado `norday-robot`:

- `ROADMAP_ROBOT.md`: cómo funciona (colas, niveles, regla de documentación)
  y la lista de tareas.
- `servidor/LEEME.md`: qué hay instalado en el servidor y dónde. `servidor/`
  es la copia de esa configuración, sin secretos; la fuente de verdad es el
  servidor.
