# descargar-backups.ps1
# Trae a Windows los dumps de /root/backups del VPS, verifica su SHA-256
# y comprueba con pg_restore que cada dump nuevo se puede leer entero.
# Sin acentos a proposito: PowerShell 5.1 lee los .ps1 sin BOM como ANSI.
# Cada ssh/scp/pg_restore corre con la entrada vacia y un limite de tiempo:
# si lo supera, se mata con sus hijos y cuenta como error
# (el 11-sep un ssh lanzado desde la tarea se quedo colgado).
# Si hay errores, o si el ultimo dump del VPS es demasiado antiguo, muestra una
# notificacion de Windows que se queda en pantalla hasta cerrarla.

param(
    [int]$LimiteSegundos = 120,
    [double]$MaxHorasSinDump = 30
)

$ErrorActionPreference = 'Continue'

$Destino       = 'C:\Dev\Norday\backups-bd'
$Clave         = Join-Path $env:USERPROFILE '.ssh\norday-contabo'
$Servidor      = $env:NORDAY_VPS   # p.ej. 'usuario@1.2.3.4'; fuera del repo
if (-not $Servidor) { throw "Falta la variable de entorno NORDAY_VPS (formato usuario@host)" }
$DiasRetencion = 90
$MinimoGuardar = 7    # por tipo (produccion / staging), aunque sean antiguos
$Log           = Join-Path $Destino 'descarga.log'
$Utf8SinBom    = New-Object System.Text.UTF8Encoding $false
$OpcionesSsh   = "-i `"$Clave`" -o BatchMode=yes -o ConnectTimeout=20"
$PgRestore     = 'C:\Program Files\PostgreSQL\18\bin\pg_restore.exe'
$TiposVigilados = @('habitos_db', 'habitos_db_staging')
$AppIdAviso    = '{1AC14E77-02E7-4E5D-B744-2EB1AE5198B7}\WindowsPowerShell\v1.0\powershell.exe'
$MensajesError = New-Object System.Collections.Generic.List[string]

function Registrar([string]$Nivel, [string]$Mensaje) {
    $linea = '{0} {1,-5} {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Nivel, $Mensaje
    [System.IO.File]::AppendAllText($Log, $linea + "`r`n", $Utf8SinBom)
    Write-Host $linea
    if ($Nivel -eq 'ERROR') { $MensajesError.Add($Mensaje) }
}

# Notificacion de Windows con escenario 'reminder': no se oculta sola, hay que cerrarla.
function Notificar([string]$Titulo, [string]$Texto) {
    try {
        $null = [Windows.UI.Notifications.ToastNotificationManager, Windows.UI.Notifications, ContentType = WindowsRuntime]
        $null = [Windows.Data.Xml.Dom.XmlDocument, Windows.Data.Xml.Dom.XmlDocument, ContentType = WindowsRuntime]
        $t = [System.Security.SecurityElement]::Escape($Titulo)
        $x = [System.Security.SecurityElement]::Escape($Texto)
        $xml = New-Object Windows.Data.Xml.Dom.XmlDocument
        $xml.LoadXml("<toast scenario='reminder'><visual><binding template='ToastGeneric'><text>$t</text><text>$x</text></binding></visual><actions><action content='Cerrar' arguments='dismiss' activationType='system'/></actions></toast>")
        $aviso = New-Object Windows.UI.Notifications.ToastNotification $xml
        [Windows.UI.Notifications.ToastNotificationManager]::CreateToastNotifier($AppIdAviso).Show($aviso)
        return $true
    } catch {
        Registrar 'AVISO' ('No se pudo mostrar la notificacion: ' + $_.Exception.Message)
        return $false
    }
}

# Lanza un exe con entrada vacia y limite de tiempo. Nunca se queda esperando.
function Ejecutar([string]$Exe, [string]$Argumentos) {
    $tmpOut = [System.IO.Path]::GetTempFileName()
    $tmpErr = [System.IO.Path]::GetTempFileName()
    $tmpIn  = [System.IO.Path]::GetTempFileName()
    try {
        $p = Start-Process -FilePath $Exe -ArgumentList $Argumentos -WorkingDirectory $Destino `
            -NoNewWindow -PassThru `
            -RedirectStandardInput $tmpIn -RedirectStandardOutput $tmpOut -RedirectStandardError $tmpErr
        $null = $p.Handle    # sin esto, ExitCode sale vacio en PowerShell 5.1

        $colgado = -not $p.WaitForExit($LimiteSegundos * 1000)
        if ($colgado) {
            & taskkill.exe /PID $p.Id /T /F 2>&1 | Out-Null
            $null = $p.WaitForExit(10000)
        } else {
            $p.WaitForExit()
        }

        [pscustomobject]@{
            Colgado = $colgado
            Codigo  = $(if ($colgado) { -1 } else { $p.ExitCode })
            Salida  = @(Get-Content -LiteralPath $tmpOut)
            Errores = ((Get-Content -LiteralPath $tmpErr) -join ' ').Trim()
        }
    } finally {
        Remove-Item -LiteralPath $tmpOut, $tmpErr, $tmpIn -Force -ErrorAction SilentlyContinue
    }
}

$errores     = 0
$descargados = 0
$borrados    = 0

Get-ChildItem -LiteralPath $Destino -Filter '*.part' | Remove-Item -Force
Registrar 'INFO' "Inicio (limite $LimiteSegundos s por conexion)"

# 1. Lista remota con hash. -mmin +10 ignora dumps que se puedan estar escribiendo.
$comando = "find /root/backups -maxdepth 1 -name '*.dump' -mmin +10 -exec sha256sum {} +"
$r = Ejecutar 'ssh.exe' "-n $OpcionesSsh $Servidor `"$comando`""

$remotos = @()
if ($r.Colgado) {
    Registrar 'ERROR' "ssh sin respuesta en $LimiteSegundos s, cortado"
    $errores++
} elseif ($r.Codigo -ne 0) {
    Registrar 'ERROR' "ssh salio con codigo $($r.Codigo): $($r.Errores)"
    $errores++
} else {
    if ($r.Errores) { Registrar 'AVISO' "ssh: $($r.Errores)" }
    foreach ($l in $r.Salida) {
        if ("$l" -match '^([0-9a-f]{64})\s+/root/backups/([A-Za-z0-9_.-]+\.dump)$') {
            $remotos += [pscustomobject]@{ Hash = $Matches[1]; Nombre = $Matches[2] }
        } elseif ("$l".Trim() -ne '') {
            Registrar 'AVISO' ('Linea no reconocida: ' + $l)
        }
    }
    if ($remotos.Count -eq 0) {
        Registrar 'ERROR' 'El VPS no ha devuelto ningun dump'
        $errores++
    }
}

# 2. Frescura: si el cron del VPS deja de generar dumps, no habria nada que descargar
#    y sin esta comprobacion todo pareceria correcto.
if ($remotos.Count -gt 0) {
    $ahora = Get-Date
    foreach ($tipo in $TiposVigilados) {
        $fechas = foreach ($d in $remotos) {
            if ($d.Nombre -match '^(?<tipo>.+?)_(?<fecha>\d{4}-\d{2}-\d{2})[_-](?<hora>\d{4})\.dump$' -and $Matches['tipo'] -eq $tipo) {
                [datetime]::ParseExact("$($Matches['fecha']) $($Matches['hora'])", 'yyyy-MM-dd HHmm', [Globalization.CultureInfo]::InvariantCulture)
            }
        }
        if (-not $fechas) {
            Registrar 'ERROR' "No hay ningun dump de $tipo en el VPS"
            $errores++
            continue
        }
        $ultimo = ($fechas | Sort-Object -Descending | Select-Object -First 1)
        $horas  = ($ahora - $ultimo).TotalHours
        if ($horas -gt $MaxHorasSinDump) {
            Registrar 'ERROR' ("El ultimo dump de {0} en el VPS tiene {1:N1} h (maximo {2} h)" -f $tipo, $horas, $MaxHorasSinDump)
            $errores++
        }
    }
}

# 3. Descarga de los que faltan, a .part. Solo se renombran a .dump si el hash
#    coincide y pg_restore puede leerlos enteros.
foreach ($d in $remotos) {
    $final = Join-Path $Destino $d.Nombre
    $parte = "$final.part"

    if (Test-Path -LiteralPath $final) {
        $local = (Get-FileHash -LiteralPath $final -Algorithm SHA256).Hash.ToLower()
        if ($local -ne $d.Hash) {
            Registrar 'ERROR' "$($d.Nombre) ya existe y su hash no coincide con el del VPS"
            $errores++
        }
        continue
    }

    # Destino relativo: WorkingDirectory es $Destino y asi scp no confunde C: con un host
    $s = Ejecutar 'scp.exe' "-q $OpcionesSsh `"${Servidor}:/root/backups/$($d.Nombre)`" `"$($d.Nombre).part`""

    if ($s.Colgado -or $s.Codigo -ne 0 -or -not (Test-Path -LiteralPath $parte)) {
        $motivo = if ($s.Colgado) { "sin respuesta en $LimiteSegundos s, cortado" } else { "codigo $($s.Codigo) $($s.Errores)" }
        Registrar 'ERROR' "$($d.Nombre): scp fallo, $motivo"
        if (Test-Path -LiteralPath $parte) { Remove-Item -LiteralPath $parte -Force -ErrorAction SilentlyContinue }
        $errores++
        continue
    }

    $hash = (Get-FileHash -LiteralPath $parte -Algorithm SHA256).Hash.ToLower()
    if ($hash -ne $d.Hash) {
        Remove-Item -LiteralPath $parte -Force
        Registrar 'ERROR' "$($d.Nombre): el hash descargado no coincide, copia borrada"
        $errores++
        continue
    }

    # El hash solo prueba que es igual al del VPS; pg_restore prueba que se puede leer entero.
    # Sin pg_restore.exe el dump se guarda igualmente: mejor una copia sin comprobar que ninguna.
    if (-not (Test-Path -LiteralPath $PgRestore)) {
        Move-Item -LiteralPath $parte -Destination $final
        Registrar 'ERROR' "$($d.Nombre): descargado con hash verificado, pero no se encuentra $PgRestore para comprobarlo"
        $errores++
        $descargados++
        continue
    }

    $v = Ejecutar $PgRestore "-f NUL `"$($d.Nombre).part`""
    if ($v.Colgado -or $v.Codigo -ne 0) {
        Remove-Item -LiteralPath $parte -Force -ErrorAction SilentlyContinue
        $motivo = if ($v.Colgado) { "sin respuesta en $LimiteSegundos s" } else { "codigo $($v.Codigo) $($v.Errores)" }
        Registrar 'ERROR' "$($d.Nombre): pg_restore no puede leerlo ($motivo), copia borrada"
        $errores++
        continue
    }
    if ($v.Errores) { Registrar 'AVISO' "$($d.Nombre): pg_restore: $($v.Errores)" }

    Move-Item -LiteralPath $parte -Destination $final
    Registrar 'OK' "$($d.Nombre) descargado, hash verificado y legible"
    $descargados++
}

# 4. Retencion: borra los de mas de 90 dias segun la fecha del nombre,
#    pero conserva siempre los 7 mas recientes de cada tipo.
$limite = (Get-Date).Date.AddDays(-$DiasRetencion)
$locales = Get-ChildItem -LiteralPath $Destino -Filter '*.dump' | ForEach-Object {
    if ($_.Name -match '^(?<tipo>.+?)_(?<fecha>\d{4}-\d{2}-\d{2})') {
        [pscustomobject]@{
            Fichero = $_
            Tipo    = $Matches['tipo']
            Fecha   = [datetime]::ParseExact($Matches['fecha'], 'yyyy-MM-dd', [Globalization.CultureInfo]::InvariantCulture)
        }
    }
}

foreach ($grupo in ($locales | Group-Object Tipo)) {
    $grupo.Group | Sort-Object Fecha -Descending | Select-Object -Skip $MinimoGuardar |
        Where-Object { $_.Fecha -lt $limite } | ForEach-Object {
            Remove-Item -LiteralPath $_.Fichero.FullName -Force
            Registrar 'INFO' "$($_.Fichero.Name) borrado por antiguedad"
            $borrados++
        }
}

Registrar 'INFO' "Fin: $descargados descargados, $borrados borrados, $errores errores"

if ($errores -gt 0) {
    $texto = $MensajesError[0]
    if ($MensajesError.Count -gt 1) { $texto += " (y $($MensajesError.Count - 1) mas)" }
    $texto += " - Ver $Log"
    $null = Notificar "Backups Norday: $errores errores" $texto
    exit 1
}
exit 0