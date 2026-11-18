-- Nori vestida, N-4 (18-nov-2026): las prendas se venden por temporadas.
--
-- oferta: un periodo (desde y hasta, días de la zona del usuario, los dos
-- incluidos) en el que un producto se vende a un precio. Un producto con
-- ofertas solo se compra dentro de una vigente y a su precio; sin ofertas,
-- como siempre. Precio 0 es un regalo. Si se solapan, manda la de desde más
-- reciente. Equipar no mira fechas.
--
-- producto.coleccion: la colección de temporada a la que pertenece
-- (INVIERNO_2026, NAVIDAD_2026...); NULL en todo lo de antes.
--
-- No inserta datos: las prendas y sus ofertas se dan de alta aparte.
CREATE TABLE oferta (
    oferta_id   SERIAL  PRIMARY KEY,
    producto_id INTEGER NOT NULL,
    desde       DATE    NOT NULL,
    hasta       DATE    NOT NULL,
    precio      INTEGER NOT NULL,
    CONSTRAINT fk_oferta_producto FOREIGN KEY (producto_id) REFERENCES producto (producto_id),
    CONSTRAINT ck_oferta_precio CHECK (precio >= 0),
    CONSTRAINT ck_oferta_fechas CHECK (desde <= hasta)
);

CREATE INDEX idx_oferta_producto ON oferta (producto_id);

ALTER TABLE producto ADD COLUMN coleccion VARCHAR(50);
