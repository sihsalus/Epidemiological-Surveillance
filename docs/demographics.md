# Distribución demográfica y grupos etarios

RF-22 se implementa sobre `surveillance_case`, no sobre el reporte legado de
observaciones ni `period_case_count`. La respuesta solo contiene agregados y
etiquetas; no expone nombres, identificadores de pacientes o casos.

## API

- `GET /reports/demographics`: privilegio `Vigilancia Epidemiologica: Ver Indicadores`.
  Parámetros obligatorios: `from`, `to`, `row`, `column`.
- Dimensiones: `age`, `sex`, `disease`, `ageGroup`, `ethnicity`, `pregnancy`, `period`.
  Los ejes deben ser distintos. La respuesta conserva ese orden.
- Sin enfermedad en los ejes: enviar `event=<UUID>`, sin `compareEvents`.
- Con enfermedad en cualquiera de los ejes: enviar `compareEvents=<UUID,UUID,...>`,
  sin `event`. Referencias desconocidas y selecciones vacías se rechazan.
- `period`: `dia`, `semana`, `mes`, `trimestre`, `semestre`, `anio`; por defecto mes.
  El frontend solo lo envía cuando uno de los ejes es periodo.
- `diagnosisType`: `CONFIRMADO` (predeterminado), `PROBABLE`, `TODOS`.
- `zoneLevel`: `DEPARTAMENTO`, `PROVINCIA`, `DISTRITO` o `CENTRO_POBLADO` (predeterminado); `address` es
  opcional y debe ser un UUID de Address Hierarchy del nivel elegido. Filtra el
  lugar probable de infección del caso, también antes de contar casos sin fecha.
  Sin dirección se incluyen todas las zonas. La respuesta devuelve ambos filtros.
  La interfaz no muestra un selector de nivel: utiliza el nivel más específico
  seleccionado en Departamento → Provincia → Distrito → Centro poblado.
  Departamento corresponde a `ADDRESS_1`. Sin seleccionar un hijo, el reporte
  incluye todos los descendientes del padre seleccionado. El reporte temporal
  aplica el mismo filtro a los conteos actuales e históricos de centros poblados
  antes de sumar, sin sumar también los agregados de distrito.
- `GET /configuration/age-groups`: privilegio de acceso al módulo.
- `PUT /configuration/age-groups`: privilegio `Vigilancia Epidemiologica: Configurar`.
  Cuerpo: arreglo no vacío de objetos `{key, name, minAge, maxAge}` en orden; último máximo nulo.

La respuesta demográfica incluye `rows`, `columns` (pares `key/label`), `values`
(matriz), `rowTotals`, `columnTotals`, `total`, filtros de ejes/fechas/periodo,
`ageGroups`, `excludedWithoutOnset` y `generatedAt`. La tabla limita a 100 000 celdas
y solicita acotar la consulta si se supera. Ocultar ceros es una opción de
presentación: elimina solo ejes cuyo total es cero; los totales permanecen iguales.

## Semántica del cálculo

Se cuenta cada caso no anulado, probable o confirmado, de la enfermedad seleccionada
según su diagnóstico codificado (concepto del evento o mapeo del catálogo). Se
excluyen diagnósticos anulados y descartados. Las fechas se filtran por inicio de
síntomas, inclusive y en la zona del catálogo. El rango no puede superar hoy; no tiene límite de duración. Los casos sin inicio de síntomas no se asignan a un periodo;
se devuelve por separado su cantidad para las enfermedades/clasificación elegidas,
sin atribuirlos al intervalo solicitado.

Edad, sexo y gestación proceden de las copias del caso; no se recalculan desde el
paciente. Etnia procede del atributo de persona indicado en el catálogo y resuelve
conceptos a etiquetas. Valores ausentes se agrupan como `UNKNOWN`; sexo distinto
de F/M queda en `OTHER`. Las edades mostradas son las observadas; no se inventan
edades para registros históricos sin copia. Las categorías fijas y enfermedades
seleccionadas sin casos se conservan con cero hasta que se active Ocultar ceros.

Los periodos usan el calendario existente; semanas de domingo a sábado según la
configuración epidemiológica. Su clave es la fecha inicial. Un periodo que cruza
el límite del filtro cuenta únicamente sus casos dentro del rango elegido.
Las etiquetas se muestran como `DD/MM/AAAA`, `AAAA-SXX`, `AAAA-MM`, `AAAA-QX`,
`AAAA-X` o `AAAA`, según el periodo. Las semanas usan el año epidemiológico
configurado, que puede diferir del año de la fecha inicial; las claves siguen ISO.

## Configuración persistida

`liquibase-age-groups.xml` crea `surveillance_age_group_config` e inserta la
configuración compartida. Estos son los grupos iniciales; nombres, cantidad y límites son editables:

| Código | Grupo | Inicial |
| --- | --- | --- |
| INFANTS | Infantes | 0–1 |
| CHILDREN | Niños | 2–11 |
| ADOLESCENTS | Adolescentes | 12–17 |
| YOUNG_ADULTS | Jóvenes | 18–29 |
| ADULTS | Adultos | 30–59 |
| OLDER_ADULTS | Adultos mayores | 60 en adelante |

Frontend y backend rechazan listas vacías, claves duplicadas, nombres vacíos, huecos,
solapamientos, rangos vacíos y máximo finito en el último grupo. La edición calcula
los mínimos a partir del máximo anterior; el servidor vuelve a validar todo el
arreglo. El cambio no modifica casos y afecta los reportes siguientes.

## Navegación y actualización

La antigua pestaña Indicadores se llama Información temporal. Las nuevas pestañas
Indicadores y Geográfico se dejan vacías por instrucción del usuario; sus cálculos
y mapa no forman parte de esta entrega. Casos, Información temporal y Demográfico
tienen un encabezado Filtros. Nuevo caso usa el estilo de Nuevo evento.

Crear/editar casos o eventos y guardar grupos etarios llama a la recarga completa
de la página solo después de una respuesta exitosa. Las operaciones rechazadas
conservan el formulario. Requiere actualizar OMOD y ESM juntos; no se despliega
automáticamente.

## Validación

Pruebas sintéticas: 42 combinaciones de dimensiones, totales, datos ausentes,
límites etarios, huecos/solapamientos, periodos con cero, comparación de enfermedades,
filtros de casos, etnia, permisos, persistencia tras limpiar sesión, migración
repetible, contrato REST, filtros condicionales, ocultar ceros y recarga tras guardar.
El smoke clínico en DEV/QLTY requiere una sesión coordinada y permanece pendiente.

Validación local: `mvn package` completado con 115 pruebas de API y 6 de REST;
frontend con 99 pruebas Vitest y una prueba HMR, comprobación TypeScript, lint y
compilación Rspack correctos. No se realizó despliegue.


`liquibase-age-group-names.xml` amplía `lower_bounds` a LONGTEXT para almacenar
el arreglo JSON completo, incluidos nombres Unicode. No se establece un límite
funcional por nombre ni se trunca texto. Se mantiene la lectura del formato legado
de seis mínimos separados por comas; al guardar se escribe JSON. Un nombre ausente
solo se admite para los códigos originales, cuya traducción mantiene el frontend.
Claves personalizadas: letras ASCII, números, guion o guion bajo; UNKNOWN queda
reservada. Renombrar conserva la clave; quitar un grupo redistribuye sus edades
en la partición restante. Requiere OMOD y ESM actualizados conjuntamente.
