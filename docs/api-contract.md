# Contrato REST — Vigilancia Epidemiológica

Versión: 1.0-draft, 2026-09-30. Base: `/openmrs/ws/rest/v1/sihsalusepidemiologicalsurveillance`.
Autenticación: sesión OpenMRS. Todas las referencias se expresan como UUID de OpenMRS,
excepto `infectionAddressUuid`, que es el UUID de `address_hierarchy_entry`.

## Casos de vigilancia

### Registro semanal (RF-13)

`GET /weekly-records?year=2026&week=1` requiere **Ver Casos** y **Ver Indicadores**.
Devuelve `{year, week, from, to, rows}`; `rows` contiene todos los casos de la
semana de **notificación**, sin paginación del servidor. El intervalo incluye el
inicio de semana y excluye el inicio de la siguiente, según la zona y reglas del
catálogo. Semanas inexistentes devuelven 422 `INVALID_PERIOD`.

Cada fila incluye `id`, `year`, `week`, `paternalSurname`,
`maternalSurname`, `givenNames`, `age` (años), `sex`, `dni`, `province`, `district`,
`locality` (lugar de infección), `residence`, `ethnicity` (nombre),
`habitualResidence`, `icd10`, `diagnosisType`, `vaccinationStatus`,
`onsetDate`, `deathDate`, `investigationDate`, `notificationDate` (fechas ISO),
`investigationForm` (booleano) y `surveillanceType`. Los ausentes son null.
`province`, `district`, `locality` y `residence` contienen códigos territoriales
`user_generated_id` como texto. Residencia se resuelve por la jerarquía completa
de la copia histórica del caso; ausencia o ambigüedad devuelve null. El frontend
presenta y exporta ficha como S/N y vigilancia como P/A; `icd10` solo lleva el código.
Incluye C/P/D y cualquier periodicidad; excluye casos y fuentes clínicas anulados.
La exportación Excel/CSV la genera el frontend con estas filas autorizadas;
no persiste archivos ni crea entidades `IndividualRecord`.

Las respuestas de casos agregan `diagnosisIcd10`; las de eventos agregan `icd10`.
Provienen del mapeo único a la fuente CIE-10 configurada. Ausencia o ambigüedad
produce null, sin inferir códigos del texto del diagnóstico. Cuando no existen
mapeos, se usa un nombre `SHORT` no anulado con formato CIE-10, siempre que sea
único, según el contrato del catálogo MINSA/OCL.

| Operación | Ruta | Privilegio | Resultado |
|---|---|---|---|
| Crear borrador | `POST /cases` | `Vigilancia Epidemiologica: Registrar Casos` | 201 `SurveillanceCase` |
| Editar borrador | `PUT /cases/{uuid}` | `Vigilancia Epidemiologica: Registrar Casos` | 200 `SurveillanceCase` |
| Consultar caso | `GET /cases/{uuid}` | `Vigilancia Epidemiologica: Ver Casos` | 200 `SurveillanceCase` |
| Cerrar/notificar caso | `POST /cases/{uuid}/close` | `Vigilancia Epidemiologica: Registrar Casos` | 200 `SurveillanceCase` |

`POST` y `PUT` permiten campos epidemiológicos incompletos para conservar un borrador.
La operación `close` no persiste una columna de estado: solo valida RF-07 y deja el caso
listo para el flujo de notificación de iteración 2. Mientras no exista dicho flujo,
`close` no asigna `individualRecordUuid`.

### Cuerpo de creación o edición

```json
{
  "patientUuid": "uuid",
  "encounterUuid": "uuid",
  "providerUuid": "uuid",
  "locationUuid": "uuid",
  "diagnosisUuid": "uuid",
  "testOrderUuid": "uuid opcional",
  "laboratoryObservationUuid": "uuid temporal de Obs; el servidor usa Obs.order si existe",
  "origin": "AUTOCTONO | IMPORTADO_NACIONAL | IMPORTADO_INTERNACIONAL | INDUCIDO | INTRODUCIDO | RECAIDA | RECRUDESCENCIA",
  "onsetDate": "YYYY-MM-DD opcional",
  "infectionAddressUuid": "uuid de centro poblado opcional",
  "diagnosisType": "CONFIRMADO | PROBABLE | DESCARTADO",
  "habitualResidence": "1 | 2 | 3 obligatorio",
  "vaccinationStatus": "SI | NO | IGN opcional",
  "notificationDate": "YYYY-MM-DDTHH:mm:ss-05:00 opcional",
  "deathAttribution": "SI | NO | POR_DETERMINAR | null",
  "surveillanceType": "PASIVA | BUSQUEDA_ACTIVA opcional"
}
```

El servidor deriva evento notificable y gravedad desde `diagnosisUuid` y los
conceptos configurados; no acepta esos valores como campos del caso. Nombres, edad, sexo,
residencia y etnia no se aceptan ni se copian: se leen desde `patient`. Gestación se
registra como `Obs` del encuentro mediante el formulario clínico.

### Atribución de defunción (20/10/2026)

`deathAttribution` sustituye la fecha editable del caso. Solo admite `SI`, `NO`
y `POR_DETERMINAR` si `Person.deathDate` existe; omitirla en un fallecido usa
`POR_DETERMINAR`. Sin esa fecha el servidor guarda `null`, incluso si recibe una
selección antigua. `patientDeathDate` es de solo lectura y procede de Person.
El campo anterior `deathDate` se ignora en solicitudes por compatibilidad; su
columna histórica no se convierte en una afirmación de causalidad.

El SaveHandler de Person/Patient limpia las atribuciones de todos sus casos al
volver a vivo o retirar la fecha, con auditoría en la misma operación de guardado.
Los casos de fallecidos siguen siendo editables. El filtro de fallecidos consulta
Person, y la validación de cierre toma también esa fuente.
Requiere actualizar ESM y OMOD juntos y aplicar la migración
`surveillance-death-attribution-20261020` (columna nullable `varchar(20)`).

### Respuesta `SurveillanceCase`

`notificationDate` conserva su nombre de API pero ahora contiene un instante con
fecha, hora y offset. Se devuelve normalizado a UTC−5. Las solicitudes con solo
fecha se rechazan; omitir el valor sigue representando un caso sin notificación.
No puede ser anterior al instante de la atención de origen ni futuro.
`investigationDate` se deriva de la atención y ya no se captura ni se envía desde
el formulario. Se conserva internamente para validaciones y datos históricos.

La migración `notification-time-20261020` cambia `notification_date` de DATE a
DATETIME y reemplaza cada fecha existente por `encounter_datetime + 1 hora`,
incluidos cambios de día/año. Conserva null en casos sin notificación. Usa la
misma convención de almacenamiento JDBC que la atención de OpenMRS; los instantes
se presentan y transmiten en UTC−5. Los filtros de fechas abarcan días completos
en UTC−5, con el límite superior excluyente en la medianoche siguiente.
Actualizar OMOD y frontend juntos antes de admitir nuevas escrituras.

`encounterDate` se serializa como instante ISO 8601 en UTC con sufijo `Z`.
La vista del caso lo convierte a UTC−5; no depende de la zona del navegador.

`habitualResidence` identifica Residencia Habitual: `"1"` Urbana, `"2"` Urbana
marginal y `"3"` Rural/Campesina. Es obligatorio en POST/PUT, sin selección
predeterminada. Los históricos pueden devolver null y deben completarlo al editar.
Se persiste en `habitual_residence varchar(1)` mediante la migración
`surveillance-habitual-residence-20261020`; no modifica la dirección de Person.

La respuesta añade nombres para presentación: `encounterDisplay`, `encounterDate`,
`providerDisplay`, `locationDisplay`, `diagnosisDisplay`, `infectionAddressDisplay`
(provincia → distrito → centro poblado), `testOrderDisplay` y
`laboratoryObservationDisplay`. `laboratoryObservationUuid` conserva el resultado
seleccionado desde la migración `16-preserve-case-laboratory-observation`.
Se comprueba su pertenencia al paciente y atención; si se envía también una orden,
debe coincidir con `Obs.order`. Los registros históricos sin resultado persistido
devuelven null: no se elige automáticamente una observación entre las de una orden.

```json
{
  "uuid": "uuid",
  "patientUuid": "uuid",
  "encounterUuid": "uuid",
  "providerUuid": "uuid",
  "locationUuid": "uuid",
  "diagnosisUuid": "uuid",
  "testOrderUuid": "uuid o null",
  "origin": "AUTOCTONO",
  "onsetDate": "2026-09-30",
  "infectionAddressUuid": "uuid o null",
  "diagnosisType": "PROBABLE",
  "vaccinationStatus": "IGN",
  "investigationDate": null,
  "notificationDate": null,
  "deathAttribution": null,
  "patientDeathDate": null,
  "surveillanceType": "PASIVA",
  "individualRecordUuid": null
}
```

Errores: 400 JSON malformado; 401 sesión ausente; 403 privilegio insuficiente; 404 una
referencia no existe; 409 ya existe un caso para `diagnosisUuid`; 422 campos,
enumeraciones, fechas, residencia o centro poblado inválidos. Las respuestas 422 devuelven
`{ "code": "...", "fields": ["..."] }` con mensajes accionables en el cliente.

## Catálogos geográficos

`GET /reports/geography` requiere el privilegio de indicadores y acepta `event`,
`from`, `to`, `zoneLevel` y `address`. El rango debe pertenecer al mismo año.
El parámetro opcional `diagnosisType` admite `CONFIRMADO` (predeterminado),
`PROBABLE` y `TODOS`. `TODOS` incluye confirmados y probables, nunca descartados.
Se aplica al mapa, riesgo, tabla por origen, pastel e historial de clasificación
del foco. La clasificación consulta el historial hasta el año de referencia,
incluso fuera del intervalo seleccionado. Otros valores producen HTTP 422 con
`INVALID_DIAGNOSIS_TYPE`.

## Indicadores

Información temporal admite únicamente fechas del año calendario actual según
la zona horaria del catálogo; otros años devuelven 422 `OUTSIDE_CURRENT_YEAR`.
`GET /configuration/endemic-channel` (acceso al módulo) devuelve
`[historicalYears, minimumHistoricalYears]`. `PUT` en la misma ruta requiere
administración y guarda ese arreglo de dos enteros positivos: mínimo ≤ histórico
y histórico < año actual. Valores iniciales `[5,3]`; errores de configuración:
422 `INVALID_ENDEMIC_SETTINGS`. La migración `liquibase-endemic-config.xml`
persiste estos parámetros, usados por el reporte temporal.

`GET /reports` añade `historicalYears` (años anteriores al año calendario actual,
en orden descendente) y `channel[].historicalCases` (año → casos). Se aplican los
mismos filtros que al reporte. Un periodo histórico válido sin casos registrados
se presenta como cero en `historicalCases`, sin aumentar la muestra estadística
para los cuartiles. Un periodo inexistente en ese año no tiene entrada.
Seleccionar años en el cliente cambia las series
visibles pero no los cuartiles ni el mínimo de historia suficiente.

`GET /reports` requiere `Vigilancia Epidemiologica: Ver Indicadores` y los parámetros
`event` (UUID), `from` y `to` (fechas inclusivas `YYYY-MM-DD`).

| Parámetro opcional | Valores | Predeterminado |
|---|---|---|
| `period` | `dia`, `semana`, `mes`, `trimestre`, `semestre` | `semana` |
| `zoneLevel` | `DEPARTAMENTO`, `PROVINCIA`, `DISTRITO`, `CENTRO_POBLADO` | `CENTRO_POBLADO` |
| `address` | UUID de Address Hierarchy del nivel seleccionado | Todas las zonas de ese nivel |
| `diagnosisType` | `CONFIRMADO`, `PROBABLE`, `TODOS` | `CONFIRMADO` |

`TODOS` suma confirmados y probables, nunca descartados. Los reportes leen únicamente
`period_case_count`: curva diaria por inicio de síntomas y canal por período. Nunca se
suman distritos y centros poblados juntos. El histórico suma primero las zonas y tipos
seleccionados de cada año y luego calcula cuartiles; un año sin filas históricas no se
inventa como cero. Los períodos parcialmente seleccionados se marcan `PARTIAL_PERIOD`.

La respuesta conserva `generatedAt`, `eventUuid`, `from`, `to`, `period`, `total`,
`curve`, `channel` y `warnings`; agrega `zoneLevel`, `address` y `diagnosisType`.
`population` expresa el diagnóstico seleccionado y `demographics` queda vacío (RF-22
fuera de esta iteración). Nivel, diagnóstico o dirección incompatible producen HTTP
422 con `INVALID_ZONE_LEVEL`, `INVALID_DIAGNOSIS_TYPE` o `INVALID_REPORT_ADDRESS`.

## Eventos notificables

`POST /events` crea un evento con `conceptUuid`, `periodicity`
(`SEMANAL` o `INMEDIATA`), `referenceRegulation` y `active` (booleano obligatorio).
Solo se permite un evento por concepto, incluso si está inactivo; duplicados devuelven
409 `EVENT_ALREADY_EXISTS`. El nombre se deriva de `concept_name` y el plazo de la
periodicidad. `PUT /events/{uuid}` edita esos atributos con el mismo privilegio de
administración, o recibe únicamente `{ "active": false }` para cambiar el estado.
Las respuestas incluyen `active` y no exponen fechas de vigencia. El catálogo y
`GET /events` excluyen inactivos; `includeRetired=true` permite administrarlos.
Un nuevo caso con diagnóstico de un evento inactivo devuelve 409 `EVENT_RETIRED`.
Los casos existentes se conservan. Las fechas del contrato anterior ya no se aceptan
para controlar el estado.
Se conserva el UUID y la auditoría de creación, y se registra quién y cuándo editó.

| Operación | Ruta | Resultado |
|---|---|---|
| Departamentos | `GET /addresses/departments` | entradas `ADDRESS_1` de Address Hierarchy |
| Provincias | `GET /addresses/provinces?parent={uuid}` | hijos del departamento |
| Distritos | `GET /addresses/districts?parent={uuid}` | hijos de la provincia |
| Centros poblados | `GET /addresses/populated-centers?parent={uuid}` | hijos del distrito |

Solo el último endpoint entrega referencias que el backend acepta como
`infectionAddressUuid`.

## Indicadores y cierre semanal (20/10/2026)

GET `/reports/indicators`: parámetros `event`, `from`, `to`, `indicator`
(`completeness`, `fatality`, `timeliness`), `diagnosisType` (`CONFIRMADO`, `PROBABLE`,
`TODOS`), `zoneLevel` y `address` opcional. Requiere REPORT. El rango inclusivo se
aplica al instante de atención convertido a fecha en la zona del calendario.
Devuelve `indicator`, `numerator`, `denominator`, `percentage`; percentage es null
si no hay casos. Fatality rechaza filtros distintos de CONFIRMADO.

GET/PUT `/configuration/notification`: arreglo `[día ISO, hora, minuto]`.
GET requiere ACCESS; PUT requiere MANAGE. Valores iniciales `[1,12,0]`.
Se persiste en `surveillance_notification_config` mediante la migración
`liquibase-notification-config.xml`. El día configurado cae en la semana posterior
a la semana epidemiológica investigada. Notificación oportuna exige un instante
estrictamente anterior al cierre semanal o anterior a atención + 24 h si es
inmediata, y nunca anterior a la atención. Ausencia de notificación no es oportuna.

`vaccinationStatus` y `surveillanceType` son obligatorios en POST/PUT de casos.
Los valores históricos nulos se conservan hasta editar. `deathAttribution` se
normaliza a null salvo caso CONFIRMADO con Person.deathDate; se mantienen las
restricciones y opciones anteriores. Los reportes no deducen causalidad.
