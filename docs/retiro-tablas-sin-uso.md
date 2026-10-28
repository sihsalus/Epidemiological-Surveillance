# Retiro de tablas sin uso
Fecha: 28/10/2026.

## Estado
El usuario confirmó que revisó la base real y que no hay impedimentos para el retiro.
La migración liquibase-retire-unused-tables.xml está incluida al final del changelog.
Se ejecutará al instalar o actualizar el OMOD; esta sesión no la aplicó a la base real.

RF-15 conserva la generación y descarga del archivo individual. No registra su
descarga, huella, fecha ni historial. Descargar no modifica los casos.

## Tablas y dependencias resueltas
| Tabla retirada | Dependencia resuelta |
|---|---|
| epidemiologysurveillance_item | Remanente del esqueleto inicial; sin consumidores encontrados. |
| outbreak_alert | Retirados OutbreakAlert y su mapeo. Las alertas activas usan surveillance_alert. |
| outbreak_alert_rule | Retirados OutbreakAlertRule, mapeo y consulta DAO. La evaluación antigua lee las reglas vigentes de surveillance_outbreak_config. |
| epidemiological_focus | Retirados entidad, mapeo y método DAO que devolvía null. El mapa calcula los focos desde los casos. |
| individual_record | Retirados entidad, mapeo y asociación/accesores en SurveillanceCase; se elimina también individual_record_id. |

Se quita primero sc_record_fk y su columna en surveillance_case. outbreak_alert se
elimina antes de outbreak_alert_rule por oa_rule_fk. Las FK salientes se eliminan con
sus tablas; no se eliminan las tablas padre del núcleo. No se usa CASCADE ni se
deshabilitan las claves foráneas.

## Compatibilidad del servicio
Se mantienen registerCase() y getCase() del servicio público. Su evaluación lee
OutbreakRuleConfiguration y usa percentil, número de semanas, origen, clasificación
de foco y mínimo de casos configurados, en lugar de consultar outbreak_alert_rule.
CONSECUTIVE exige superar el percentil durante las semanas configuradas, igual que
la configuración vigente; no introduce una condición adicional de crecimiento.
La ventana de consulta se ajusta al número de semanas configurado.

La proyección antigua de encuentros no identifica el centro poblado. Por ello conserva
FOCUS_UNKNOWN y no sustituye una sede asistencial por un foco geográfico. FocusStatus
es un valor transitorio del evaluador, sin tabla ni mapeo Hibernate. El flujo actual
de casos y alertas conserva su cálculo geográfico desde surveillance_case.

## Protección de la migración
VerifyUnusedSurveillanceTables comprueba, antes del primer DDL:
- Existencia de las cinco tablas y de las dos FK entrantes conocidas.
- Ausencia de filas en las tablas retiradas y de enlaces de descarga en los casos.
- Ausencia de FK entrantes inesperadas, incluso desde otro esquema.

Si la situación cambió desde la revisión de la base, la migración se detiene.
No descarta datos automáticamente ni oculta un esquema parcial con MARK_RAN.
El SQL de consulta [preflight-retired-tables.sql](migrations/preflight-retired-tables.sql)
se conserva como herramienta de diagnóstico de vistas, triggers, rutinas y consumidores.

Las migraciones antiguas y RecoverEmptySurveillanceSchema permanecen intactos:
describen la etapa anterior del esquema y la reparación histórica de los changesets
13/14. Sus pruebas se conservan. La migración nueva es incremental.

MariaDB puede confirmar cada DDL por separado. No hay rollback transaccional ni
recuperación de datos por esta migración; volver al binario anterior requiere restaurar
el esquema previo desde un respaldo compatible.

## Validación
- 195 pruebas del API exitosas, sin fallos ni errores.
- LiquibaseMigrationTest comprueba la secuencia completa desde base limpia y desde
  el esquema parcial histórico, y confirma la ausencia final de las tablas retiradas.
- RetireUnusedTablesTest ejecuta el changelog real sobre H2: retiro y segunda ejecución,
  preservación de casos/alertas/configuración/conteos, bloqueo por datos en cada tabla,
  enlaces de descarga y FK inesperadas locales o desde otro esquema.
- NativePersistenceTest verifica persistencia Hibernate con los mapeos vigentes;
  las pruebas de descarga semanal, reglas y servicios continúan pasando.
- Empaquetado completo del OMOD exitoso. Las pruebas de migración automatizadas usan
  H2; la comprobación de la base real fue confirmada por el usuario, no ejecutada aquí.

Migración activa: [liquibase-retire-unused-tables.xml](../api/src/main/resources/liquibase-retire-unused-tables.xml).
