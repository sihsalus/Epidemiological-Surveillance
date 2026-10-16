# Contrato REST — Vigilancia Epidemiológica

Versión: 1.0-draft, 2026-09-30. Base: `/openmrs/ws/rest/v1/sihsalusepidemiologicalsurveillance`.
Autenticación: sesión OpenMRS. Todas las referencias se expresan como UUID de OpenMRS,
excepto `infectionAddressUuid`, que es el UUID de `address_hierarchy_entry`.

## Casos de vigilancia

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
  "vaccinationStatus": "SI | NO | IGN opcional",
  "investigationDate": "YYYY-MM-DD opcional",
  "notificationDate": "YYYY-MM-DD opcional",
  "deathDate": "YYYY-MM-DD opcional",
  "surveillanceType": "PASIVA | BUSQUEDA_ACTIVA opcional"
}
```

El servidor deriva evento notificable y gravedad desde `diagnosisUuid` y los
conceptos configurados; no acepta esos valores como campos del caso. Nombres, edad, sexo,
residencia y etnia no se aceptan ni se copian: se leen desde `patient`. Gestación se
registra como `Obs` del encuentro mediante el formulario clínico.

### Respuesta `SurveillanceCase`

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
  "deathDate": null,
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
