-- Solo lectura. Ejecutar en MariaDB con la base OpenMRS seleccionada.
-- No muestra datos clinicos; devuelve esquema, dependencias y conteos.
SELECT DATABASE() AS esquema;

SELECT TABLE_NAME, TABLE_TYPE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('epidemiologysurveillance_item', 'outbreak_alert', 'outbreak_alert_rule', 'epidemiological_focus', 'individual_record');

-- FK entrantes y salientes, incluidas referencias desde otros esquemas.
SELECT CONSTRAINT_SCHEMA, TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME,
       REFERENCED_TABLE_SCHEMA, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE REFERENCED_TABLE_NAME IS NOT NULL AND (
 (REFERENCED_TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IN ('epidemiologysurveillance_item', 'outbreak_alert', 'outbreak_alert_rule', 'epidemiological_focus', 'individual_record'))
 OR (TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('epidemiologysurveillance_item', 'outbreak_alert', 'outbreak_alert_rule', 'epidemiological_focus', 'individual_record')))
ORDER BY REFERENCED_TABLE_NAME, TABLE_NAME, CONSTRAINT_NAME;

-- Ejecutar los siguientes conteos solo tras confirmar que existen las cinco tablas.
SELECT 'epidemiologysurveillance_item' AS tabla, COUNT(*) AS filas FROM epidemiologysurveillance_item
UNION ALL SELECT 'outbreak_alert', COUNT(*) FROM outbreak_alert
UNION ALL SELECT 'outbreak_alert_rule', COUNT(*) FROM outbreak_alert_rule
UNION ALL SELECT 'epidemiological_focus', COUNT(*) FROM epidemiological_focus
UNION ALL SELECT 'individual_record', COUNT(*) FROM individual_record;
SELECT COUNT(*) AS casos_con_registro_descarga
FROM surveillance_case WHERE individual_record_id IS NOT NULL;

-- Revisar definiciones; no basta con que una tabla este vacia.
SELECT TABLE_SCHEMA, TABLE_NAME, VIEW_DEFINITION FROM information_schema.VIEWS
WHERE LOWER(VIEW_DEFINITION) REGEXP 'epidemiologysurveillance_item|outbreak_alert|epidemiological_focus|individual_record';
SELECT TRIGGER_SCHEMA, TRIGGER_NAME, EVENT_OBJECT_TABLE, ACTION_STATEMENT
FROM information_schema.TRIGGERS
WHERE (EVENT_OBJECT_SCHEMA = DATABASE() AND EVENT_OBJECT_TABLE IN ('epidemiologysurveillance_item', 'outbreak_alert', 'outbreak_alert_rule', 'epidemiological_focus', 'individual_record'))
 OR LOWER(ACTION_STATEMENT) REGEXP 'epidemiologysurveillance_item|outbreak_alert|epidemiological_focus|individual_record';
SELECT ROUTINE_SCHEMA, ROUTINE_NAME, ROUTINE_TYPE, ROUTINE_DEFINITION
FROM information_schema.ROUTINES
WHERE LOWER(ROUTINE_DEFINITION) REGEXP 'epidemiologysurveillance_item|outbreak_alert|epidemiological_focus|individual_record';
SELECT EVENT_SCHEMA, EVENT_NAME, EVENT_DEFINITION FROM information_schema.EVENTS
WHERE LOWER(EVENT_DEFINITION) REGEXP 'epidemiologysurveillance_item|outbreak_alert|epidemiological_focus|individual_record';

-- El resultado solo es completo si el usuario puede ver los metadatos involucrados.
-- Revisar tambien consumidores externos y SQL dinamico; no aparecen necesariamente aqui.
