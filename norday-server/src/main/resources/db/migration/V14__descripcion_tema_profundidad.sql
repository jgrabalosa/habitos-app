-- La descripción de TEMA_PROFUNDIDAD cambió en el core y en el inicializador,
-- pero el inicializador no actualiza los productos que ya existen, así que la
-- base de datos seguía guardando la antigua. La app no enseña este texto: usa
-- la traducción del core y sólo cae al del backend si falta. Se alinea para
-- que la base de datos no guarde una descripción que ya no es la del producto.
UPDATE producto
SET descripcion = 'Un viaje espacial entre constelaciones, estrellas fugaces y cohetes'
WHERE codigo = 'TEMA_PROFUNDIDAD';
