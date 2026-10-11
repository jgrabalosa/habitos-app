-- Rediseño de rachas, paso B3. Los logros de constancia dejan de medir rachas
-- y pasan a contar días hechos de un mismo hábito: cambian 8 nombres y las 22
-- descripciones. Los códigos (RACHA_3, RACHA_7...) no cambian, y quien ya
-- tiene un logro lo conserva: solo se tocan nombre y descripción.
--
-- Hace falta migración porque LogrosHabitosInitializer no pisa las filas que
-- ya existen. En una base de datos nueva estas filas todavía no existen y los
-- UPDATE no hacen nada: ahí las crea el initializer, ya con estos textos.
--
-- El nombre y la descripción de la base de datos son la caída en español: la
-- app traduce por código.

UPDATE logro SET nombre = 'A la tercera', descripcion = 'Completa un mismo hábito 3 veces' WHERE codigo = 'RACHA_3';
UPDATE logro SET nombre = 'Ya van siete', descripcion = 'Completa un mismo hábito 7 veces' WHERE codigo = 'RACHA_7';
UPDATE logro SET descripcion = 'Vuelve a un hábito después de unos días sin hacerlo' WHERE codigo = 'RACHA_RECUPERADA';
UPDATE logro SET descripcion = 'Completa un mismo hábito 10 veces' WHERE codigo = 'RACHA_10';
UPDATE logro SET descripcion = 'Completa un mismo hábito 15 veces' WHERE codigo = 'RACHA_15';
UPDATE logro SET nombre = 'Veinte hechas', descripcion = 'Completa un mismo hábito 20 veces' WHERE codigo = 'RACHA_20';
UPDATE logro SET descripcion = 'Completa un mismo hábito 25 veces' WHERE codigo = 'RACHA_25';
UPDATE logro SET nombre = 'Coge forma', descripcion = 'Completa un mismo hábito 30 veces' WHERE codigo = 'RACHA_30';
UPDATE logro SET nombre = 'Paso a paso', descripcion = 'Completa un mismo hábito 35 veces' WHERE codigo = 'RACHA_35';
UPDATE logro SET descripcion = 'Completa un mismo hábito 40 veces' WHERE codigo = 'RACHA_40';
UPDATE logro SET descripcion = 'Completa un mismo hábito 45 veces' WHERE codigo = 'RACHA_45';
UPDATE logro SET descripcion = 'Completa un mismo hábito 50 veces' WHERE codigo = 'RACHA_50';
UPDATE logro SET descripcion = 'Completa un mismo hábito 55 veces' WHERE codigo = 'RACHA_55';
UPDATE logro SET nombre = 'Sesenta', descripcion = 'Completa un mismo hábito 60 veces' WHERE codigo = 'RACHA_60';
UPDATE logro SET descripcion = 'Completa un mismo hábito 65 veces' WHERE codigo = 'RACHA_65';
UPDATE logro SET nombre = 'Setenta', descripcion = 'Completa un mismo hábito 70 veces' WHERE codigo = 'RACHA_70';
UPDATE logro SET descripcion = 'Completa un mismo hábito 75 veces' WHERE codigo = 'RACHA_75';
UPDATE logro SET descripcion = 'Completa un mismo hábito 80 veces' WHERE codigo = 'RACHA_80';
UPDATE logro SET descripcion = 'Completa un mismo hábito 85 veces' WHERE codigo = 'RACHA_85';
UPDATE logro SET nombre = 'Casi cien', descripcion = 'Completa un mismo hábito 90 veces' WHERE codigo = 'RACHA_90';
UPDATE logro SET descripcion = 'Completa un mismo hábito 100 veces' WHERE codigo = 'RACHA_100';
UPDATE logro SET descripcion = 'Completa un mismo hábito 365 veces' WHERE codigo = 'RACHA_365';
