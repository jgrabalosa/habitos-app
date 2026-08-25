-- Los logros de hábitos se crearon con origen_app NULL, indistinguibles de
-- los genéricos del motor. Eso hacía que el catálogo del motor los diera por
-- retirados al no encontrarlos en su propia lista, y de paso que la app de
-- conocimiento los viera en su catálogo, donde no pintan nada.
UPDATE logro
SET origen_app = 'habitos'
WHERE codigo IN (
    'PRIMER_HABITO', 'PRIMERA_CATEGORIA', 'PRIMEROS_PASOS',
    'RACHA_3', 'RACHA_7', 'RACHA_RECUPERADA', 'RACHA_30', 'RACHA_100', 'RACHA_365',
    'HABITOS_ACTIVOS_3', 'HABITOS_ACTIVOS_5',
    'REGISTROS_100', 'REGISTROS_500', 'REGISTROS_1000',
    'CATEGORIAS_3', 'CATEGORIAS_5', 'PRIMERA_NOTA'
);
