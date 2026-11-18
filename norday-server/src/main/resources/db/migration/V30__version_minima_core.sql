-- Nori vestida, N-6 (18-nov-2026): el catálogo según la versión del core.
--
-- Desde qué versión del core de Flutter (cabecera X-Norday-Core, «0.56.0»)
-- se ve y se vende un producto. Una app con un core más viejo, o sin la
-- cabecera (las instaladas con el core 0.55.0), no lo ve en el catálogo y
-- su compra se rechaza con «Actualiza la app». NULL = todos, como siempre.
ALTER TABLE producto ADD COLUMN version_minima_core VARCHAR(20);
