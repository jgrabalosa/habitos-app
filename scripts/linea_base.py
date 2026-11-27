#!/usr/bin/env python3
"""Línea base de contrato de la salida 1.3.0 (docs/despliegue.md, «Línea base de contrato»).

Uso, como root en el VPS:
  python3 -I /root/linea_base.py antes   <usuarioId>   (con el backend VIEJO arrancado)
  python3 -I /root/linea_base.py despues <usuarioId>   (con el backend NUEVO arrancado; el mismo día)
  python3 -I /root/linea_base.py comparar

Firma un token con el jwt.secret del servicio (lo lee del config del servicio o
del jar, sin copiarlo a disco), pide los endpoints de hábitos, registros,
mascota y tienda de ese usuario por https://api.norday.app/api, y guarda cada
respuesta normalizada en /root/linea-base/<fase>/. «comparar» dice qué claves
desaparecen o cambian de tipo (eso rompería la app instalada) y qué claves
son nuevas. Los valores que cambian solo se cuentan.
"""
import base64, glob, hashlib, hmac, json, os, subprocess, sys, time, urllib.error, urllib.request, zipfile

BASE = os.environ.get("LB_BASE", "/opt/norday-backend")
API = os.environ.get("LB_API", "https://api.norday.app/api")
BD = os.environ.get("LB_BD", "habitos_db")
SALIDA = os.environ.get("LB_SALIDA", "/root/linea-base")


def leer_secreto():
    for ruta in (f"{BASE}/config/application.properties", f"{BASE}/application.properties"):
        s = _prop(open(ruta, encoding="utf-8").read()) if os.path.exists(ruta) else None
        if s:
            return s, ruta
    jars = [j for j in glob.glob(f"{BASE}/norday-server/target/*.jar") if not j.endswith("-plain.jar")]
    if len(jars) != 1:
        sys.exit(f"ERROR: esperaba un jar en {BASE}/norday-server/target y hay {len(jars)}")
    with zipfile.ZipFile(jars[0]) as z:
        s = _prop(z.read("BOOT-INF/classes/application.properties").decode("utf-8"))
    if not s:
        sys.exit("ERROR: no encuentro jwt.secret")
    return s, jars[0]


def _prop(texto):
    for l in texto.splitlines():
        l = l.strip()
        if l.startswith("jwt.secret") and "=" in l:
            return l.split("=", 1)[1].strip()
    return None


def b64(b):
    return base64.urlsafe_b64encode(b).rstrip(b"=").decode()


def token(secreto, email, usuario_id):
    clave = secreto.encode()
    bits = len(clave) * 8
    # Como Keys.hmacShaKeyFor de jjwt: el algoritmo sale del tamaño de la clave.
    alg, h = ("HS512", hashlib.sha512) if bits >= 512 else ("HS384", hashlib.sha384) if bits >= 384 else ("HS256", hashlib.sha256)
    ahora = int(time.time())
    cab = b64(json.dumps({"alg": alg}, separators=(",", ":")).encode())
    cuerpo = b64(json.dumps({"sub": email, "usuarioId": usuario_id, "iat": ahora, "exp": ahora + 900}, separators=(",", ":")).encode())
    firma = b64(hmac.new(clave, f"{cab}.{cuerpo}".encode(), h).digest())
    return f"{cab}.{cuerpo}.{firma}", alg


def psql(sql):
    r = subprocess.run(["sudo", "-u", "postgres", "psql", "-d", BD, "-Atc", sql], capture_output=True, text=True)
    if r.returncode:
        sys.exit(f"ERROR en psql: {r.stderr.strip()}")
    return [l for l in r.stdout.splitlines() if l]


def pedir(ruta, tok):
    req = urllib.request.Request(API + ruta, headers={"Authorization": f"Bearer {tok}", "Accept": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            return r.status, json.loads(r.read().decode() or "null")
    except urllib.error.HTTPError as e:
        return e.code, None


def capturar(fase, usuario_id):
    email = psql(f"SELECT email FROM usuario WHERE usuario_id = {int(usuario_id)}")
    if not email:
        sys.exit("ERROR: ese usuario no existe")
    habitos = psql(f"SELECT habito_id FROM habito WHERE propietario_id = {int(usuario_id)} ORDER BY habito_id LIMIT 3")
    secreto, origen = leer_secreto()
    tok, alg = token(secreto, email[0], int(usuario_id))
    print(f"secreto de {origen}; firma {alg}; {len(habitos)} hábitos")
    u = int(usuario_id)
    rutas = {
        "resumen": f"/habitos/usuario/{u}/resumen",
        "mascota": f"/mascota/{u}",
        "inventario": f"/gamificacion/productos/usuario/{u}",
        "catalogo": "/gamificacion/productos/catalogo",
    }
    for h in habitos:
        rutas[f"habito-{h}"] = f"/habitos/{h}"
        rutas[f"detalle-{h}"] = f"/habitos/{h}/detalle"
        rutas[f"registros-{h}"] = f"/registros/habito/{h}"
    carpeta = os.path.join(SALIDA, fase)
    os.makedirs(carpeta, mode=0o700, exist_ok=True)
    malos = 0
    for nombre, ruta in rutas.items():
        estado, datos = pedir(ruta, tok)
        malos += estado != 200
        with open(os.path.join(carpeta, nombre + ".json"), "w", encoding="utf-8") as f:
            json.dump({"estado": estado, "datos": datos}, f, sort_keys=True, ensure_ascii=False, indent=1)
        print(f"  {estado} {ruta}")
    print(f"{fase}: {len(rutas)} respuestas en {carpeta}; distintas de 200: {malos}")


def forma(v, ruta, sal):
    if isinstance(v, dict):
        sal[ruta] = "objeto"
        for k, x in v.items():
            forma(x, f"{ruta}.{k}", sal)
    elif isinstance(v, list):
        sal[ruta] = "lista"
        for x in v[:1]:
            forma(x, f"{ruta}[]", sal)
    else:
        sal[ruta] = "nulo" if v is None else type(v).__name__


def comparar():
    a_dir, d_dir = os.path.join(SALIDA, "antes"), os.path.join(SALIDA, "despues")
    rompe = 0
    for f in sorted(os.listdir(a_dir)):
        a = json.load(open(os.path.join(a_dir, f)))
        try:
            d = json.load(open(os.path.join(d_dir, f)))
        except FileNotFoundError:
            print(f"ROMPE {f}: no está después"); rompe += 1; continue
        if a["estado"] != d["estado"]:
            print(f"ROMPE {f}: {a['estado']} -> {d['estado']}"); rompe += 1
        fa, fd = {}, {}
        forma(a["datos"], "", fa); forma(d["datos"], "", fd)
        quitadas = [k for k in fa if k not in fd]
        tipo = [k for k in fa if k in fd and fa[k] != fd[k] and "nulo" not in (fa[k], fd[k])]
        nuevas = [k for k in fd if k not in fa]
        for k in quitadas:
            print(f"ROMPE {f}: desaparece {k or '(raíz)'}"); rompe += 1
        for k in tipo:
            print(f"ROMPE {f}: {k} pasa de {fa[k]} a {fd[k]}"); rompe += 1
        if nuevas:
            print(f"nuevo {f}: {', '.join(nuevas[:12])}{' …' if len(nuevas) > 12 else ''}")
        if a["datos"] != d["datos"] and not (quitadas or tipo or nuevas):
            print(f"valores {f}: cambian valores, no la forma")
    print("RESULTADO: " + ("SOLO AÑADE" if rompe == 0 else f"ROMPE ({rompe})"))


if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] in ("antes", "despues"):
        capturar(sys.argv[1], sys.argv[2])
    elif len(sys.argv) == 2 and sys.argv[1] == "comparar":
        comparar()
    else:
        sys.exit(__doc__)
