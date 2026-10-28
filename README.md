# Vigilancia epidemiológica — SIH Salus

OMOD `io.github.proyecto-santaclotilde:sihsalusepidemiologicalsurveillance`, para OpenMRS 2.4.2 y MariaDB. El frontend asociado es el único microfrontend `@sihsalus/esm-epidemiological-surveillance-app`.

## Diseño vigente

- Una sola base de datos OpenMRS; no hay réplica ni segundo datasource.
- El changelog activo incluye el [retiro de cinco tablas sin uso](docs/retiro-tablas-sin-uso.md) y de `surveillance_case.individual_record_id`. Las alertas se conservan en `surveillance_alert` y sus reglas en `surveillance_outbreak_config`. RF-15 mantiene la descarga del registro individual sin registrar la descarga ni persistir sus metadatos.
- Los casos se guardan en `surveillance_case`; los datos del paciente se leen de `patient` y la gestación permanece como `Obs` del encuentro.
- Los conteos se recalculan al registrar o actualizar casos y mediante una acción manual idempotente de configuración.
- El componente no crea una tabla de auditoría ni se integra con NOTI-Web.

## Registro de casos

El contrato está congelado en [docs/api-contract.md](docs/api-contract.md). `POST /cases` y `PUT /cases/{uuid}` permiten borradores. `POST /cases/{uuid}/close` aplica las validaciones RF-07 sin añadir una columna de estado. La restricción única por diagnóstico impide dos filas para el mismo diagnóstico.

El servidor conserva `laboratoryObservationUuid` en `laboratory_observation_id` y
deriva `test_order_id` desde `Obs.order` cuando exista. Verifica que la observación
pertenezca al paciente y atención y que la orden enviada coincida. No crea órdenes
ni resultados. La migración aditiva `16-preserve-case-laboratory-observation` conserva
las filas existentes: resultados históricos cuya referencia se perdió quedan vacíos,
sin asociar resultados por suposición. Actualizar frontend y OMOD juntos.

Las respuestas de casos incluyen `encounterDisplay`, `encounterDate`,
`providerDisplay`, `locationDisplay`, `infectionAddressDisplay` (provincia → distrito
→ centro poblado), `testOrderDisplay` y `laboratoryObservationDisplay`; los UUID
permanecen en el contrato técnico, pero no se muestran en la pantalla.

## Eventos y conteos

Al editar se recalculan los conteos incluso si el caso pasa a descartado o deja
de tener datos suficientes para contabilizarse. El reemplazo conserva los grupos
vigentes y elimina únicamente los acumulados derivados obsoletos del evento;
no elimina casos. Repetir el guardado no añade otra contribución. Para reparar
acumulados anteriores, ejecutar el recálculo manual después de actualizar el OMOD.

Las columnas SQL `DATE` se interpretan como fechas de calendario sin conversión
de zona horaria. Los instantes con hora mantienen la conversión a la zona del
catálogo. Tras actualizar desde una versión con el desfase de fechas, ejecutar
`POST /ws/rest/v1/sihsalusepidemiologicalsurveillance/counts/refresh` con el
privilegio de configuración para reconstruir los acumulados existentes desde
las fechas de inicio originales. No modifica los casos ni sus fechas.

Cada enfermedad tiene un único evento notificable. `POST /events` recibe concepto, periodicidad, norma de referencia y `active` (booleano). `PUT /events/{uuid}` modifica esos atributos o únicamente el estado. Los eventos inactivos se excluyen del catálogo de selección y no admiten nuevos casos; siguen disponibles para edición y reactivación. El nombre se deriva de `concept_name` y el plazo de la periodicidad.

La migración `liquibase-event-state.xml` inicializa el estado según la vigencia anterior y agrega unicidad por concepto. Si existen varias versiones de una enfermedad, se detiene antes de modificar datos: sus referencias deben conciliarse antes de desplegar. Las fechas anteriores se conservan internamente como datos históricos y dejan de formar parte del formulario y de la respuesta de eventos.

Los casos `CONFIRMADO` y `PROBABLE` desencadenan el recálculo de `period_case_count`. Si se requiere reconciliar datos históricos, un usuario con privilegio de configuración puede ejecutar `POST /counts/refresh`. Solo se cuentan casos no anulados, con fecha de inicio y centro poblado, en su centro poblado y distrito.

## Columnas del consolidado geográfico

`GET /reports/geography` admite `column=origin|age|sex|ageGroup|ethnicity|pregnancy|period`
y `period=dia|semana|mes|trimestre|semestre|anio`, con valores iniciales `origin`
y `mes`. La respuesta agrega `table`: filas identificadas por centro poblado,
columnas, matriz, totales y grupos etarios. Reutiliza los dominios y cálculos de
demografía sin habilitar Enfermedad en este cruce. La selección territorial,
enfermedad, clasificación y fechas se aplica a la misma población que el mapa.
Solo la tabla usa la dimensión seleccionada; riesgo e historial de focos conservan
su cálculo. El servidor devuelve agregados, nunca fichas de pacientes.

Actualizar ESM y OMOD juntos; no requiere migración. Se mantiene el privilegio
de indicadores. Validación local: `mvn -B clean package` PASSED, incluidas columnas,
totales, valores desconocidos y enlace de parámetros REST. DEV/QLTY integrado
NOT RUN; no se realizó despliegue.

## Privilegios

| Caso de uso | Privilegio definido en `config.xml` |
| --- | --- |
| Abrir la aplicación y catálogo | `app:home.epidemiologicalSurveillance` |
| Consultar casos | `Vigilancia Epidemiologica: Ver Casos` |
| Crear, editar o cerrar casos | `Vigilancia Epidemiologica: Registrar Casos` |
| Consultar indicadores | `Vigilancia Epidemiologica: Ver Indicadores` |
| Configurar eventos y ejecutar recálculo | `Vigilancia Epidemiologica: Configurar` |

Además se requieren los privilegios clínicos nativos de OpenMRS para consultar las referencias involucradas. Los nombres no se asignan automáticamente a roles.

## Auditoría

RF-28 utiliza auditoría básica en las entidades, sin OMOD de auditoría ni retención
obligatoria de 20 años. El contrato anterior de `docs/auditoria-integracion.md` queda
obsoleto por el cambio de alcance del 28/10/2026.

`OutbreakRuleConfiguration` y `AgeGroupConfiguration` registran `creator`,
`date_created`, `changed_by` y `date_changed`. Los servicios toman el usuario de la
autorización `MANAGE` y la fecha del servidor, conservan los datos de creación y
actualizan los de última modificación en la misma transacción que la configuración.
La migración incremental `liquibase-configuration-audit.xml` agrega las columnas y
sus claves foráneas a `users`. Las filas anteriores conservan los metadatos de creación
en NULL porque su autor y fecha originales se desconocen; las nuevas ediciones sí
quedan atribuidas. Las configuraciones nuevas reciben autor y fecha al crearse.

## Desarrollo

Ejecutar desde la raíz de `epidemiologysurveillance` para compilar el API antes
del OMOD y reconstruir las clases y pruebas desde cero:

```sh
mvn --batch-mode clean verify
```

Para generar el OMOD con sus pruebas sin ejecutar la fase adicional de verificación,
usar `mvn clean package`. Si las pruebas indican `cannot find symbol` para clases
del propio API, reconstruir con `clean` antes de modificar los imports: una caché
de `target/maven-status` sin sus correspondientes `.class` puede dejar un estado
inconsistente. No reutilizar directorios `target` procedentes de otra compilación
o checkout. `compiler:testCompile` por sí solo no compila primero el código principal.

El artefacto es `omod/target/sihsalusepidemiologicalsurveillance-1.0.0-SNAPSHOT.omod`. Los changesets 1–12 ya fueron aplicados y no se modifican. Los changesets 13 y 14 retiran las tablas históricas tras una copia SQL externa autorizada; su rollback no recupera esos datos.
