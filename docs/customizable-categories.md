# Base de categorías personalizables

## Estado y límite de esta entrega

Base preparatoria implementada; **no activada en la base de producción**. Room sigue en v3,
`Movement.category` sigue siendo enum obligatorio y el formulario/validación/reportes actuales
conservan sus contratos. No se ha migrado ningún dato del usuario ni se afirma que el sistema
personalizable ya sea utilizable de extremo a extremo.

La inspección encontró enums en Movement, MovementEntity, validación, JSON v1/v2, DTO y mappers
Firestore, resumen mensual y PDF. Sustituir únicamente dos propiedades de Movement dejaría
respaldos y documentos remotos incapaces de representar una categoría personalizada.

## Contratos implementados

`domain/category` contiene:

- Category: id Long local (0 antes de insertar), uuid estable, name, scope INCOME/EXPENSE/BOTH,
  reportGroup, iconKey, archived y sortOrder.
- Subcategory: id, uuid, categoryId obligatorio, name, iconKey, archived y sortOrder.
- CategorySelection: categoryId Long? y subcategoryId Long?. Impide hijo sin padre; valida
  existencia, pertenencia, tipo y archivado al asignar una selección nueva. No obliga a elegir
  categoría ni hijo. No usar isAssignable para invalidar retrospectivamente movimientos
  históricos de categorías archivadas.
- LegacyCategoryCatalog: semillas y correspondencia explícita desde ambos enums.
- categoryReportBucket: base de clasificación que independiza nombres/iconos de los grupos;
  los ingresos siempre suman como ingresos, también con categorías BOTH. Egresos sin categoría
  tienen grupo UNCATEGORIZED. No suma dinero ni mezcla monedas.

`data/category` contiene entidades Room, mappers, DAO y repositorio transaccional para una base experimental aislada.
Ofrece consulta, inserción, actualización de metadata, archivado y posición. Identidades UUID tienen índices únicos;
Subcategory referencia Category con RESTRICT. No hay borrado físico en el DAO. Los archivos aún
no se registran en MiFlujoDatabase ni se inyectan en el contenedor.

`RoomCategoryRepository` aplica las guardas de dominio antes de escribir y no se conecta a la UI.
La base `CategoryCatalogDatabase` está aislada de `MiFlujoDatabase`; esta última sigue en v3.
El orden empata por UUID, no por ID local. Archivar un padre oculta sus hijos para nuevas
selecciones, pero mantiene el historial legible. Reordenar/renombrar no cambia identidades.

iconKey es una clave de texto estable. La UI futura resolverá claves conocidas a iconos internos
y usará un icono genérico para claves desconocidas; no se persisten R.drawable ni ImageVector.

## Migración preparada y transición pendiente

CATEGORY_CATALOG_MIGRATION_3_4 crea las tablas/índices y siembra:

| Enum de origen | ID local semilla | UUID estable | Grupo |
| --- | --- | --- | --- |
| GENERAL_INCOME | 1 | e2e2de81-2c70-37cb-adda-392be081ad53 | INCOME |
| FIXED_COST | 2 | 299690db-dcc7-380d-850f-535c74bd8827 | FIXED_COST |
| MAINTENANCE | 3 | 9a93cc1f-a2ca-3969-b4d8-3ead840589ec | MAINTENANCE |
| OTHER | 4 | ee7fe63c-f73b-381f-9260-f2cdee3c51c5 | OTHER |

Las subcategorías conservan IDs locales semilla 1/2/3 bajo la categoría 2:

| Enum | UUID portable definitivo |
| --- | --- |
| WATER | 919bb4b6-da9e-3857-b442-d682d5d80c2d |
| ELECTRICITY | 4307fcb5-e7e5-3e01-afd3-3cbefc523e13 |
| INTERNET | f1b7ab2a-33aa-3912-969a-a45c9d4481f1 |

Las siete constantes son UUID v3 RFC determinísticos: `UUID.nameUUIDFromBytes` sobre UTF-8 de
`com.carlos.miflujo/catalog/v1/category/<ENUM>` o
`com.carlos.miflujo/catalog/v1/subcategory/<ENUM>`, sin espacios ni salto de línea.
El algoritmo usa MD5 sobre esos bytes con bits de versión 3 y variante RFC; no es UUID v5 ni
requiere el namespace implícito de otras librerías. Las constantes quedan congeladas aunque se
renombre la etiqueta o se retiren los enums. No usar ordinales ni etiquetas traducidas.
Las claves anteriores `miflujo:…` eran preparatorias, nunca se activaron en Room/backup/cloud;
no son identidades válidas del contrato definitivo. Un catálogo experimental con esas claves no
se debe importar silenciosamente como datos definitivos.

La migración anterior instala exclusivamente el catálogo experimental. La
[migración de movimientos de la iteración 6A](#movimientos-con-catálogo-y-migración-room-iteración-6a)
es una candidata independiente con el mismo tramo 3 → 4. La versión de Room de producción
sigue siendo 3. Los dos objetos de migración corresponden a bases distintas.

## Reportes y semántica histórica

Conservar importes Long y separación C$/US$. El grupo no se obtiene del nombre: categorías
personalizadas de hogar pueden asignarse a FIXED_COST, otras a MAINTENANCE u OTHER.
BOTH puede usar cualquier grupo de egresos; para ingresos manda el tipo de movimiento.
El agregado debe incluir egresos UNCATEGORIZED para reconciliar siempre desglose y total.

Producción conserva `CalculateMonthlyCashFlowReportUseCase` y el PDF con enums. La
[ruta experimental de la iteración 5](#reportes-y-pdf-experimentales-iteración-5)
agrupa por `reportGroup` y desglosa por UUID con nombres actuales, incluido el catálogo archivado.
Las líneas heredadas Agua, Luz e Internet se identifican por los UUID de semillas, de modo
que una subcategoría nueva tiene su propia línea.

Decisión definitiva: reportGroup y scope se eligen al crear y quedan inmutables desde entonces.
Renombrar o archivar no reclasifica el historial; para otra agrupación se crea otra categoría.
El repositorio aislado ya aplica esa guarda; aún no se conecta a la administración ni a movimientos.

## Backup, Firestore y datos reales

El respaldo de producción exporta movimientos con enums en v2. La
[integración experimental 6B](#respaldo-y-restauración-con-catálogo-iteración-6b)
lleva el códec v3 y la restauración atómica desde el sandbox a la base candidata de movimientos.
La activación requiere conectar los consumidores financieros y conservar los bloqueos de
restauración y sincronización.

Firestore requiere catálogo por UID, relaciones por UUID, versión de contrato y resolución
UUID -> Long local. Sincronizar el catálogo antes de movimientos; conservar referencias
archivadas, timestamps de conflicto y tombstones seguros. Impedir que clientes antiguos
sobrescriban datos nuevos con enums. Definir backfill remoto y compatibilidad de reglas antes
de habilitar escritura del formato nuevo. Nada de esto se activa en esta entrega.

## Siguiente iteración

Primero completar persistencia, backup/cloud y reportes anteriores con pruebas de migración y
restauración en entorno aislado. Después conectar repositorio/ViewModel y construir un único
selector integrado categoría/subcategoría: «Sin categoría», icono junto al nombre, navegación
compacta y conservación de valores al editar. Administración CRUD, archivado y orden serán otra
pantalla pequeña, sin exigir que el usuario recorra el catálogo para capturar cada movimiento.

No probar migraciones con datos de producción ni asumir que una rama aísla Room/Firestore.

## Contratos definitivos de dominio (iteración 1)

### Identidad y actualizaciones

Category/Subcategory validan UUID canónico en minúsculas, variante RFC y versión 1–5; esta forma
es compatible con la expresión UUID existente de Firestore. Creación de usuario genera UUID v4
aleatorio por defecto. El nombre no identifica ni deduplica: dos fichas llamadas «Casa» pueden
coexistir. Long=0 significa aún no insertado; un Long positivo solo identifica una fila local.
Ningún Long se usa como referencia portable. Los códecs futuros preservarán UUID exactamente;
esto no vuelve compatibles los payloads actuales, que todavía solo transportan enums.

`edited` solo permite nombre, iconKey, archived y sortOrder. `requireSameStructureAs` rechaza
cambios de id/uuid y scope/reportGroup de categoría, o id/uuid/padre de subcategoría. Debe llamarlo
el repositorio al actualizar desde cualquier origen, incluidas importación y sincronización.
Los modelos siguen siendo data classes: `copy` y deserialización no son una autorización para
modificar la estructura. El repositorio aislado ya aplica esta frontera para actualizaciones locales;
los caminos futuros de importación y sincronización todavía deben adoptarla. Al crear una
fila desde un backup se remapea su ID local; no es una actualización de la identidad existente.

Los nombres e iconKey deben tener contenido; iconos desconocidos se conservan y la UI futura
mostrará un icono genérico. Renombrar, cambiar icono, ordenar y archivar preservan identidad.
No existe borrado físico del catálogo en este contrato. Desarchivar es un cambio explícito
permitido. Archivar un padre bloquea nuevas selecciones de todos sus hijos, sin borrar ni
cambiar las referencias históricas de estos.

### Relaciones y edición histórica

- (null, null): sin categoría, válido en ingresos y egresos.
- (categoría, null): válido si existe y admite el tipo, sin obligación especial para costos fijos.
- (categoría, subcategoría): válido si ambas existen y el hijo pertenece al padre.
- (null, subcategoría): rechazado al construir CategorySelection.
- ID desconocido, entidad resuelta con otro ID, padre ajeno o scope incompatible: error explícito.
  Una entidad extra cuando se indicó null también es error, nunca se ignora silenciosamente.

`validate` devuelve errores distinguibles (UNKNOWN_CATEGORY, UNKNOWN_SUBCATEGORY, WRONG_PARENT,
WRONG_SCOPE, UNEXPECTED_REFERENCE y archivado). Nunca trata una búsqueda fallida como categoría
vacía. `isAssignable` sigue siendo solo el atajo para asignaciones nuevas.

Para editar un movimiento con archivadas, el repositorio entrega la selección y tipo anteriores
**leídos del movimiento almacenado**, no del formulario. Solo la misma selección completa y el
mismo tipo pueden conservar archivadas. Cambiar padre, hijo o tipo exige una selección activa y
válida; quitar toda la categoría siempre es posible. Incluso al conservar archivadas se validan
existencia, pertenencia y scope. Importar/sincronizar referencias históricas archivadas usa
validación de integridad, no el bloqueo de nuevas asignaciones; su integración sigue pendiente.

### Orden portable

Orden ascendente por `(sortOrder, uuid)` dentro del catálogo de categorías y dentro de cada
padre para subcategorías. No depender de ID local, nombre ni colación del idioma. UUID canónico
ASCII permite el mismo desempate lexicográfico en Kotlin y SQLite BINARY. Se admiten posiciones
repetidas y enteros negativos; el reordenamiento puede normalizar a 0..n sin cambiar identidades.
Un lote local de reordenamiento será transaccional; cambios concurrentes pueden producir empates
sin romper la lista. Los comparadores puros y las consultas del DAO implementan este contrato.

### Sincronización del catálogo: contrato elegido, motor pendiente

Cada ficha portable futura contendrá UUID, estructura inmutable, metadata editable completa y
`revision = (counter: Long >= 0, actorUuid: UUID de instalación)`. La subcategoría referirá al
padre mediante UUID, nunca categoryId local. La revisión ya se persiste en las entidades del catálogo aislado (iteración 2).

La instalación genera una identidad v4 persistente: sobrevive reinicios, no se copia al restaurar
un backup en otro dispositivo. Una edición, también offline, incrementa el mayor contador conocido
de esa ficha y usa el actor local. Counter no es la hora del teléfono. Las semillas parten de
`(0, 00000000-0000-4000-8000-000000000000)`. Overflow se rechaza; no se permite volver a cero.
`CatalogRevision` implementa validación, comparación y siguiente revisión como funciones de dominio.

Con estructura coincidente, gana el mayor par `(counter, actorUuid)` lexicográficamente. Igual
revisión debe tener idéntica metadata; si no, es un error de integridad. Estructura diferente para
el mismo UUID siempre es conflicto de integridad, no una actualización válida. La escritura
remota deberá comparar revisiones transaccionalmente, sin reemplazar una revisión mayor por menor.

Se resuelve la ficha completa, no campo por campo: dos renombrados convergen a un ganador; archivo
y desarchivo concurrentes también. Un renombrado concurrente con un archivado puede descartar el
archivado si gana la otra ficha. Es una limitación aceptada del registro de último escritor lógico,
no una garantía de archivo global inmediato. Una edición basada en la revisión ya observada gana
sobre ella; entre dispositivos desconectados el orden real de reloj no determina el ganador.
El usuario puede repetir una edición sobre la revisión ganadora cuando vea la convergencia.

Estados locales persistidos (transporte pendiente): pendiente, sincronizada y error reintentable, asociados a la revisión
exacta. Persistir ficha + pendiente en una transacción. Un ACK de una revisión vieja no limpia
una edición nueva. Tras conflicto, persistir el ganador y reconocer explícitamente si la edición
local perdió; no reenviar indefinidamente la revisión perdedora. No almacenar estados de envío
como contenido remoto. Reordenar/renombrar/archivar deben programar sync aunque ningún movimiento
cambie. El transporte conserva aislamiento por UID y los bloqueos de restauración actuales.

Descargar/resolver categorías y subcategorías antes de movimientos. Una referencia válida que aún
no existe localmente se aplaza y se reintenta sin materializar un movimiento huérfano, cambiarlo
a «Sin categoría», marcarlo sincronizado ni eliminar su versión local. Referencia malformed o
estructura contradictoria es error de integridad; referencia todavía no disponible es dependencia
pendiente. Los lectores históricos incluyen archivadas. En backup, una referencia ausente del
catálogo incluido hace inválido el archivo entero antes de escribir.

Backup deberá conservar identidad, estructura, metadata y revisión de cada ficha. Al restaurar,
remapear UUID→Long local y conservar revisión; no generar nuevas identidades del catálogo ni
continuar el actor de otro dispositivo. Una edición posterior usa el actor local y contador mayor.
La política actual de bloqueo cloud sigue vigente: esto no autoriza a mezclar un backup viejo con
una cuenta remota activa. Compatibilidad v1/v2 se obtiene por mapeo explícito a las semillas.

### Verificación y siguiente hito

Pruebas JVM cubren constantes y algoritmo UUID, formato compatible, creación v4 sin deduplicación
por nombre, relaciones, errores de integridad, conservación de archivadas, guardas de estructura,
orden sin IDs locales, orden total de revisiones y rechazo de overflow. No ejecutan Room ni Firebase.

Persistencia aislada implementa repositorio transaccional, revisiones/estado pendiente, orden UUID
y guardas de escrituras locales. Sigue pendiente la integración con movimientos y la validación
histórica en los caminos de importación/sincronización.
Después completar migración de referencias, formatos backup/cloud y reportes según el plan previo.
La migración candidata sigue sin registrar; Movement, reportes y formatos actuales no cambian.


## Persistencia transaccional aislada (iteración 2)

### Base y repositorio

`CategoryRepository` expone fichas de dominio con revisión y estado local de envío. Su implementación
`RoomCategoryRepository` recibe exclusivamente `CategoryCatalogDatabase`; no utiliza el contenedor,
repositorio de movimientos ni Cloud Sync. `openIsolated` abre únicamente
`category-catalog-experiment.db`, con versión experimental 4. No se abre automáticamente desde
la app. Su esquema no es el esquema financiero v4: la numeración permite probar la candidata
3→4 contra una fuente v3 separada. No existe fallback destructivo.

Creación, edición, lectura padre/hijos y reordenamientos usan `withTransaction` cuando agrupan
varias operaciones. Crear exige id=0, valida el modelo y deja que Room asigne el Long. Crear un
hijo comprueba padre existente y no archivado; la FK protege la pertenencia aun fuera del repositorio.
Actualizar exige la revisión esperada de la lectura anterior: si cambió, se rechaza la escritura
obsoleta. Luego se aplica `requireSameStructureAs`; solo la metadata editable puede cambiar.
Para renombrar/cambiar icono/archivar se usa `value.edited(...)` y `updateCategory`/`updateSubcategory`.
Las consultas por UUID devuelven null si no existe; las operaciones que requieren esa referencia
fallan explícitamente, sin fabricar un elemento ni reemplazar la referencia por ausencia.

Los reordenamientos aceptan una lista sin UUID repetidos, asignan posiciones 0..n-1 a ese subconjunto
y conservan posiciones de los demás. Se permiten empates, resueltos por UUID; no se exige enviar
el catálogo entero ni se promete un orden global contiguo después de editar un subconjunto.
Una referencia inválida, un hijo de otro padre o cualquier error revierte también los cambios
anteriores del lote y sus revisiones. Un lote puede usar nombres archivados sin eliminarlos.

Las listas se filtran por tipo (incluyendo BOTH) y archivado; la consulta histórica incluye
archivadas. Si el padre está archivado, su lista visible es vacía, pero la consulta histórica y
la consulta directa del hijo lo conservan. Archivar/desarchivar el padre no modifica ni incrementa
revisiones de sus hijos: su visibilidad se deriva del padre. No hay relaciones con movimientos
nuevas en esta entrega; el historial financiero actual sigue basado en enums.

### Esquema, inicialización y restricciones

Categorías y subcategorías incorporan `revisionCounter INTEGER NOT NULL`,
`revisionActorUuid TEXT NOT NULL` y `syncState TEXT NOT NULL`. Estados: PENDING, SYNCED, ERROR.
`catalog_installation` contiene una única fila id=1 con actorUuid aleatorio v4, generado una vez.
No debe exportarse/restaurarse como identidad de la instalación en otro dispositivo.

Se mantienen UUID únicos por tabla, FK padre con RESTRICT e índice de categoryId. Triggers
compartidos impiden cambiar estructura o borrar físicamente fichas; validan contador no negativo,
booleano de archivo y estados conocidos. También protegen la identidad singleton de instalación.
Los UUID canónicos y las reglas de scope/reportGroup se validan en el dominio; el repositorio es
la frontera de escritura de la aplicación. El DAO no es una API para que la UI evada las revisiones.
No se implementó un escritor remoto: deberá aplicar comparación de revisiones y todas las guardas.

`CategoryCatalogSchema` concentra DDL, triggers y siembra. La migración candidata crea el catálogo
con ese DDL e inicializa. En instalación limpia, Room crea sus tablas y el callback compartido
instala las mismas protecciones y semillas. Ambas rutas generan actor de instalación propio y
siembran los mismos siete UUID, revisión (0, actor reservado de semillas) y estado PENDING.
Las semillas quedan pendientes porque un futuro remoto puede no tenerlas aún.
No se modifican ni se leen filas financieras durante esta inicialización.

La candidata sigue sin registrarse en MiFlujoDatabase/MiFlujoAppContainer; solo está registrada en
la base experimental. No se generó manualmente un esquema Room ni se afirma equivalencia con su
DDL generado sin ejecutar KSP y los tests instrumentados. `exportSchema` permanece habilitado
para generar el esquema experimental real cuando se compile con las herramientas del proyecto.

### Revisiones y pendientes

Creación de usuario: revisión 1 con actor de instalación y PENDING. Cada cambio efectivo de nombre,
icono, archivo o posición incrementa el contador de la ficha y marca PENDING en la misma transacción.
Un cambio sin efecto conserva revisión y estado. El ordenamiento solo incrementa las fichas cuya
posición cambia; contador agotado falla sin envolverlo y revierte el lote. No se usan relojes físicos.

ACK y error se aplican mediante un UPDATE condicional al UUID y par de revisión exactos. Un ACK
antiguo no limpia una edición nueva. ACK marca SYNCED; error deja ERROR y sigue apareciendo entre
pendientes reintentables. Un error tardío de la misma revisión ya reconocida no revierte SYNCED.
Estas operaciones no incrementan revisión. No ejecutan Firebase, no programan el scheduler actual
y no implementan resolución remota: solo dejan listo el estado local necesario.

### Verificación y límites

- JVM: contratos de dominio y round-trip de entidades/revisión/estados; compilación aislada con
  dependencias locales, incluyendo fuentes de pruebas instrumentadas. No ejecuta KSP ni genera DAOs.
- SQLite del equipo: `python3 scripts/test_catalog_sqlite.py` ejecuta el SQL real extraído del
  código, restricciones, duplicados, orden, ACK condicional, rollback y preservación de una tabla
  movements ficticia creada desde el esquema v3. Comparar estas rutas SQL compartidas no demuestra
  equivalencia con la creación de Room.
- Android/Room: `CategoryCatalogRoomTest` usa in-memory y un archivo de prueba separado;
  cubre CRUD, revisión/no-op/ACK, archivo e historial, estructura, UUID duplicados, orden, rollback,
  persistencia de actor al reabrir, validación Room de la candidata y comparación de tablas/índices/
  FKs/triggers contra instalación limpia. El usuario informó 7/7 en Android físico.

### Respaldos preparados (iteración 3)

El códec y la restauración aislada de v3 ya están implementados; contrato completo en
[catalog-backup-format.md](catalog-backup-format.md). Incluyen lectura v1/v2 por el parser actual,
semillas estables, snapshot transaccional con archivadas/revisiones y relaciones por UUID, y
reemplazo atómico en BackupSandboxDatabase (otro archivo, sin datos financieros reales).

CategoryDao añade una lectura de todos los hijos para el snapshot: no omite huérfanos de una base
corrupta. El restore aislado conserva revisiones, marca catálogo PENDING, mantiene actor de la
instalación y deja staging de movimientos LOCAL_ONLY. La excepción temporal de borrado se limita
a dos triggers dentro de la misma transacción de mantenimiento; no existe en el DAO de edición.

La base financiera, Movement, exportador/importador/parser/serializer actuales, SettingsViewModel,
Cloud Sync y reportes siguen sin cambios. El usuario informó 5/5 pruebas del respaldo sandbox
en Android físico. La activación requiere el corte coordinado.

## Contrato remoto (iteración 4A)

El [contrato Firestore del catálogo](catalog-firestore-contract.md) define las colecciones
por UID, los DTO v1 y sus campos, las reglas locales propuestas, la resolución por revisión
lógica y el corte compatible con clientes de movimientos v1. Incluye pruebas JVM y una
suite local que pasó 5/5 en Firestore Emulator con el proyecto demo.

## Motor aislado (iteración 4B)

El [motor experimental](catalog-firestore-contract.md#motor-experimental-del-catálogo-4b)
usa una base Room por UID y un cliente con acceso exclusivo a los emuladores del proyecto demo.
Los pendientes y el actor de la cuenta persisten al reabrir la base. El archivo experimental
sin dueño y el respaldo sandbox quedan fuera del motor. Aplicar ganadores y confirmar
escrituras conserva los IDs existentes y compara la revisión con la ficha local más reciente.
Las referencias históricas a padres archivados permanecen válidas.

La sincronización aplica categorías antes de hijos y verifica las revisiones dentro de la
transacción Firestore. Una respuesta atrasada deja pendiente la edición local posterior.
La verificación local cubre el motor con fakes y las reglas Firestore con el SDK de pruebas.
El usuario confirmó las dos pruebas Room y la prueba de extremo a extremo contra el proyecto
demo en Android físico. La integración con la app y la adopción de referencias UUID en
movimientos requieren el corte coordinado descrito en el contrato.

## Reportes y PDF experimentales (iteración 5)

| Componente auditado | Dependencia actual |
| --- | --- |
| `MovementDao` y `RoomMovementRepository` | Entregan movimientos vigentes por rango de fechas y conservan los enums al mapear las filas. |
| `CalculateMonthlyCashFlowReportUseCase` | Filtra el mes y suma ingresos y egresos por moneda. `ExpenseBreakdown` fija los tres grupos de egresos y las líneas Agua, Luz e Internet. |
| `HomeViewModel` y `HomeScreen` | Usan los totales heredados para el mes actual. La lista de movimientos recientes obtiene las etiquetas de los enums. |
| `ReportViewModel`, `ReportUiState` y `ReportScreen` | Seleccionan un `YearMonth` y conservan el reporte y los movimientos como entradas de pantalla y exportación. La pantalla usa filas de desglose fijas y lee los enums para etiquetar movimientos. |
| `ReportPdfExporter` y `ReportPdfGenerator` | Exportan ese mismo estado. El generador anterior dibuja filas fijas y obtiene las etiquetas del movimiento de los enums. |

- `CalculateFlexibleMonthlyReport` recibe un conjunto en memoria de movimientos vigentes y
  un catálogo para el mes solicitado. `fromLegacy` resuelve los enums mediante las semillas
  UUID, omite tombstones como el DAO y preserva fecha, detalle e importe. `fromCatalogSnapshot`
  adapta el modelo experimental `CatalogBackup` que el llamador ya tiene en memoria.
- Los totales se calculan en unidades menores `Long` por tipo y moneda. Para cada desglose,
  la suma de los grupos más las referencias sin resolver coincide con el total. El subtotal
  de una categoría contiene sus asignaciones directas y las de sus subcategorías. Las líneas
  de subcategoría desglosan ese subtotal. Los UUID duplicados en el catálogo o de movimientos
  del mes se rechazan. Si un total supera el rango de `Long`, el cálculo falla explícitamente.
- `UNCATEGORIZED` se reserva para movimientos con ambas referencias ausentes. Una referencia
  inválida o no disponible genera un diagnóstico separado con el importe completo del
  movimiento. Esto preserva los totales sin asignar al grupo del padre un movimiento cuya
  subcategoría aún no se pudo validar. Una categoría archivada sigue siendo válida para el
  historial. Para una categoría `BOTH`, los ingresos se agrupan en Ingresos generales y los
  egresos usan su `reportGroup`.
- Las filas dinámicas se comparten entre el componente de lista de Compose y el PDF. Usan
  UUID para distinguir nombres iguales, muestran los nombres actuales y una marca de archivo,
  y ordenan por `sortOrder` y UUID dentro de cada grupo y entre los hijos de un mismo padre.
  Se omiten categorías sin movimientos del período. `iconKey` queda disponible para el
  consumidor de UI.
- `FlexibleReportPdfGenerator` recibe un reporte calculado y un `OutputStream`. Dibuja los
  resúmenes por moneda, las filas dinámicas y los movimientos. Ajusta nombres y detalles al
  ancho de la página, divide filas largas en límites de línea y reserva el ancho del importe
  más largo para mantener todos los importes en una línea. La primera página de cada fila
  contiene el importe. Cada página muestra el período y la numeración.

El corte coordinado deberá leer movimientos y catálogo, incluidos los archivados, desde un
snapshot coherente y resolver las referencias locales a UUID. Quedan pendientes la conexión
de Inicio, Reportes y la exportación mediante el flujo de compartir, la resolución visual de
`iconKey` y el manejo en UI de diagnósticos o fallos del cálculo. Ver
[estado y pruebas de la iteración 5](work-in-progress.md#reportes-y-pdf-experimentales-iteración-5).

## Movimientos con catálogo y migración Room (iteración 6A)

### Auditoría del camino activo

| Componente | Dependencia que condiciona la activación |
| --- | --- |
| `MiFlujoDatabase`, `MovementDao`, `MovementEntity` y mappers | Room v3 almacena los enums como texto. El DAO gestiona IDs, UUID, tombstones, ventanas por fecha, reemplazos y mutaciones condicionales de sincronización. El contenedor registra 1 → 2 → 3. |
| `Movement`, repositorios, casos de uso y validación | El dominio y los formularios esperan categoría obligatoria y Costos fijos exige subcategoría bajo las reglas heredadas. El decorador de escritura decide los pendientes según la activación de Cloud Sync. |
| Respaldo, Firestore y reportes activos | El respaldo v2 y los DTO remotos v1 escriben enums. Los restauradores y mappers validan ese contrato. Inicio, reportes mensuales y PDF consumen el modelo heredado. |
| Catálogos experimentales | `CategoryCatalogMigration` instala solo tablas del catálogo. `CategoryRepository` valida revisiones e identidades, y `ScopedCatalogStore` separa catálogos por cuenta en archivos propios. Su versión 4 no describe movimientos financieros ni sustituye la candidata 6A. |

### Modelo, esquema y validación

- `CatalogMovementDraft` representa tipo, moneda, importe positivo en unidades menores, fecha y detalle. `CatalogMovementSelection` admite categoría y subcategoría opcionales por UUID y exige categoría si hay subcategoría. `CatalogMovement` añade identidad y metadata de almacenamiento para el repositorio candidato. Los formatos externos conservan sus contratos propios.
- `CatalogMovementDatabase` v4 define `movements`, `categories`, `subcategories` y `catalog_installation`. La tabla de movimientos conserva su PK local y UUID único. Sustituye las dos columnas de enums por `category_id` y `subcategory_id` anulables, cada una indexada y con FK `ON DELETE RESTRICT`.
- Las mismas protecciones SQL se instalan al crear y al migrar. Los triggers de movimientos exigen ámbito compatible y validan el par padre/hijo incluso si la FK del padre sería nula. Se eligieron dos FKs y triggers en vez de agregar un índice único al catálogo compartido, lo que habría cambiado también los esquemas experimentales de respaldo y sincronización. Las protecciones existentes del catálogo impiden reparentar subcategorías, cambiar ámbitos y borrar físicamente referencias.
- `CatalogMovementRepository` recibe UUID, resuelve IDs y valida con `CategorySelection` dentro de la transacción de escritura. El estado archivado conserva una asignación histórica si el tipo y el par padre/hijo completo son iguales al original. Puede quitarse la asignación. Una nueva asignación a un elemento archivado, una referencia desconocida o una edición sobre un tombstone fallan. La edición compara la ficha leída con la versión almacenada y conserva la creación y la última sincronización. Un cambio real avanza la marca temporal y deja pendientes de subida los movimientos cuyo estado anterior ya era de sincronización. Esa política local de la candidata debe conectarse a la activación de cuenta en el corte.

### Copia histórica y corte

`CATALOG_MOVEMENT_MIGRATION_3_4` instala y siembra el catálogo con UUID estables y el mismo contrato de revisiones que el catálogo experimental. Traduce por enum y por los IDs de semillas congelados de la tabla anterior. Conserva una fila histórica de Costos fijos sin subcategoría porque su categoría se puede identificar sin ambigüedad y el destino admite esa selección. Rechaza enums desconocidos, incompatibilidades de ámbito y parentesco, UUID con formato inválido, importes no positivos, fechas fuera de rango y tipos de almacenamiento incompatibles. El error identifica el ID local de la fila para diagnóstico. La aplicación del corte deberá tratar el fallo como recuperable desde la base original y conservar evidencia para revisarlo.

Dentro de la transacción de upgrade de Room, se copia mediante `INSERT … SELECT` a una tabla intermedia. Así se conservan de forma exacta IDs, UUID, importes, monedas, fechas, detalles, marcas temporales, estados y tombstones. Se comprueba el conteo antes de reemplazar la tabla y se conserva el máximo histórico de autoincremento de v3, incluso si la última fila se había borrado. Se crean los índices y los triggers del destino y se comprueban las FKs. Las rutas desde v1 y v2 reutilizan `MIGRATION_1_2` y `MIGRATION_2_3`: v1 recibe UUID por la migración vigente, v2 conserva los existentes, y ambas reciben la metadata local por defecto de v3. Si alguna etapa falla, Room debe revertir la ruta completa. La candidata se abre solo desde pruebas y la ruta carece de recreación destructiva.

Antes de activar en el corte coordinado hay que validar en Android las rutas y el esquema realmente generado, medir la copia de bases grandes y preparar el mensaje y la recuperación ante una fila inválida. También debe resolverse el ámbito de cuenta y la promoción de catálogos por UUID junto con la política de clientes antiguos y escritura remota descrita en el [contrato Firestore](catalog-firestore-contract.md). El flujo nuevo necesita lectura y escritura completas, adaptadores y validaciones en formularios, respaldo v3 con restore coordinado, reportes y consumidores de UI antes del cambio de base. La migración de Room no activa ninguno de esos componentes.

## Respaldo y restauración con catálogo (iteración 6B)

En el flujo activo, `BackupExporter` usa el serializador v2 y `BackupImporter` valida v1/v2
antes de que `SettingsViewModel` pida confirmación. La comprobación de disponibilidad se
repite al confirmar. `MovementRepository.replaceAllMovements` reemplaza todos los movimientos
locales dentro de una transacción de `MovementDao`. El decorador de Cloud Sync delega ese
reemplazo sin programar una subida. Es un reemplazo local completo. La política vigente y
sus estados están en [Restauración cloud-safe](cloud-safe-restore.md).

`CatalogBackupJson` conserva la [estructura v3](catalog-backup-format.md#ejemplo-completo-v3).
La raíz trae versión, aplicación y fecha de exportación. El catálogo incluye UUID, nombre,
ámbito, grupo, icono, orden, archivo y revisión lógica. Los hijos se vinculan a su padre por
UUID. Cada movimiento incluye UUID, importe entero positivo en unidades menores, moneda,
tipo, fecha, detalle, marcas de creación y edición y referencias de catálogo anulables.
Los IDs de Room se resuelven dentro de la base. El lector experimental rechaza JSON
malformado, miembros repetidos, UUID duplicados, referencias desconocidas, inconsistencias de
padre o ámbito y campos v3 ausentes o de tipo incorrecto.

| Versión | Transformación para la candidata |
| --- | --- |
| v1 | Delega en el lector histórico, valida los IDs y enums de origen, genera UUID de movimiento y usa las semillas congeladas de `LegacyCategoryCatalog`. |
| v2 | Aplica la misma transformación de catálogo y conserva los UUID de movimiento del archivo. |
| v3 | Restaura los UUID y las revisiones del catálogo completo, incluidas fichas archivadas. Acepta ambos UUID nulos o un padre sin hijo. Un hijo presente exige su padre correcto. |

La ausencia de subcategoría en un ingreso o en un egreso histórico de Mantenimiento u Otros
es válida en v1/v2. Un respaldo histórico con categoría ausente o Costos fijos sin hijo falla
según el lector vigente. Una fila en Room v3 de Costos fijos sin hijo sí puede sobrevivir a
la migración 6A y exportarse en v3, porque la candidata representa esa selección. En los tres
formatos, una referencia inválida causa rechazo y deja intacto el destino.

`CatalogMovementBackupStore.snapshot` lee catálogo y movimientos visibles de
`CatalogMovementDatabase` en una transacción. Traduce el día de época a fecha ISO y los
milisegundos almacenados a marcas locales ISO mediante UTC, la convención de los mappers
vigentes. El códec conserva nanosegundos en el archivo. Al restaurar en la candidata, las
marcas de movimiento se reducen a la precisión de Room en milisegundos y un desbordamiento
falla antes de escribir. Las exportaciones de la candidata pueden volver a restaurarse con
equivalencia exacta de contenido y marcas a dicha precisión.

`restore` recibe el respaldo ya leído, crea una copia validada y prepara todas las filas.
Dentro de `withTransaction` vuelve a leer el catálogo destino, comprueba la evidencia de
sincronización y rechaza cambios de ámbito, grupo, padre o clase para UUID de catálogo ya
conocidos. La rutina compartida con el sandbox retira las dos protecciones de borrado, limpia
movimientos, hijos y padres en ese orden, reinserta el catálogo y resuelve UUID a nuevos IDs
locales antes de insertar los movimientos. Conserva el actor de instalación, las FKs y las
guardas de relación. Reinstala las protecciones antes de confirmar. Si falla una etapa, Room
revierte filas y cambios de triggers. El reemplazo incluye el catálogo completo, por lo que
un respaldo histórico restaura únicamente las semillas y un v3 vacío puede limpiar la candidata
local. Repetir la restauración reemplaza la misma colección y no duplica filas.

### Cuenta, tombstones y condiciones de activación

El archivo portable conserva la revisión causal del catálogo. Los UID, ACK, estados de
envío, actor de instalación y tombstones quedan fuera del JSON. La candidata excluye las
bajas del snapshot, restaura movimientos como `LOCAL_ONLY` y registra las fichas del catálogo
como `PENDING` con la revisión importada, sin encolar envíos.

6B permite reemplazar únicamente la candidata sin ámbito de cuenta. Rechaza un destino ligado
a cualquier cuenta, incluso con la sesión cerrada, y protege un destino con tombstones, estados
de sincronización o marcas de última sincronización. Esta guarda experimental es más restrictiva
que la disponibilidad vigente al apagar Cloud Sync. Un estado `PENDING` del catálogo por sí
solo no demuestra procedencia local. El coordinador futuro debe obtener el ámbito de una fuente
confiable y mantener exclusión durante la confirmación y el reemplazo. El JSON carece de datos
para afirmar que corresponde a un UID.

Antes de activar v3, quedan estas decisiones y comprobaciones para el corte coordinado.

- Acordar el tratamiento de una base local con historial cloud. La opción preparada es
  bloquear el reemplazo directo en ese destino. Hace falta definir cómo se recupera el
  contenido visible sin perder tombstones ni publicar revisiones antiguas tras reactivar sync.
- Integrar la confirmación explícita y los bloqueos existentes con un coordinador que cubra
  cambios de cuenta, trabajos en curso y escritores del catálogo y de movimientos. Cualquier
  promoción futura a una cuenta requiere reconciliar por UID, UUID y revisión contra el
  estado remoto, probar conflictos y tombstones con datos sintéticos y coordinar clientes
  antiguos según el [contrato Firestore](catalog-firestore-contract.md).
- Verificar en Android el DAO generado, las rutas de migración, los respaldos v1/v2/v3,
  el rollback y el flujo completo de archivo. Evaluar snapshots grandes, errores recuperables
  y el reemplazo del catálogo en la confirmación antes de registrar Room v4 y conectar los
  consumidores de la aplicación.

## Cloud Sync con catálogo y movimientos (iteración 6C)

### Auditoría

| Frontera | Implementación vigente | Integración experimental |
| --- | --- | --- |
| Cuenta y Room | `CloudSyncEngine` obtiene el UID autorizado al inicio y serializa corridas con un mutex en la instancia. `RoomCloudSyncLocalDataSource` lee la base financiera común, incluidos tombstones. El DAO aplica ACK y actualizaciones condicionales sobre la fila local. | Una partición Room por UID contiene movimientos, catálogo y la revisión base de cada movimiento. El adaptador de emulador liga la corrida a la sesión capturada, revisa la identidad antes y después de la red y comprueba su vigencia dentro de cada transacción. |
| Movimientos remotos | La ruta es `users/{uid}/movements/{uuid}`. El contrato v1 usa enums. La reconciliación compara `updatedAt` y considera tombstones, estado local y equivalencia. La transacción remota relee el documento, verifica vigencia y preserva `createdAt`. No usa una revisión base compartida entre dispositivos. | La misma ruta usa documentos v2. La transacción valida la revisión base, protege la identidad y conserva marcas temporales como datos. El marcador de cuenta se comprueba en servidor. |
| Catálogo | El motor experimental 4B valida y reconcilia catálogo v1 por `(contador, actorUuid)`, con padres antes que hijos. Sus reglas también protegen la estructura y el incremento de revisión. | Se reutilizan el motor, las formas remotas, el repositorio y la persistencia. Se confirman los UUID utilizables por la fase de movimientos. |
| Migración y restauración | 6A copia filas y estados a una candidata v4 sin cuenta. 6B restaura contenido portable en la candidata local y bloquea destinos con vínculo de cuenta o evidencia de sync. | La partición de cuenta reutiliza las tablas, DAO y guardas de 6A y añade una tabla de revisiones remotas. Se crea en un archivo propio. La conexión de cuentas existentes y la adopción de pendientes quedan para el corte. |

### Contratos y sesión

- `AccountMovementSyncDatabase` usa un archivo de nombre derivado del SHA-256 del UID.
  El cliente del experimento usa una instancia Firebase separada, el proyecto fijo
  `demo-miflujo-catalog`, emuladores locales y caché en memoria. La partición permanece
  ligada a su UID cuando la sesión cambia. Al observar un cambio, la sesión capturada
  queda invalidada. La respuesta de una corrida previa se descarta. Una petición ya
  entregada al SDK puede terminar bajo el UID anterior. Su ruta, credencial exigida en
  servidor y archivo local corresponden a ese UID.
- `AccountMovementSyncStore` reutiliza el modelo, DAOs, triggers y repositorios candidatos.
  Solo el repositorio de esta partición crea movimientos en estado `PENDING_UPLOAD` y
  genera tombstones locales. El respaldo v3 escribe en otra clase de base Room. La
  partición de cuenta carece de un camino de restauración o promoción automática.
- Categorías y subcategorías conservan [el contrato remoto v1 del catálogo](catalog-firestore-contract.md).
  Se mantienen UUID, ámbito y grupo o padre inmutables, icono, orden, archivo y revisión;
  archivar mantiene válidas las referencias del historial. El borrado físico permanece
  bloqueado. Dos cambios concurrentes de una ficha pueden perder el cambio del perdedor
  según el par de revisión. Igual revisión con contenido diferente se trata como conflicto.
- Un movimiento v2 conserva UUID, tipo, importe entero de 64 bits, moneda, fecha ISO,
  detalle, `createdAt`, `updatedAt` y `deletedAt`. Usa `categoryUuid` y
  `subcategoryUuid` con nulos explícitos y una `revision` entera positiva. La
  revisión inicial es 1. Los timestamps deben ser representables en los milisegundos de Room.
  La relación exige ámbito compatible y que el hijo pertenezca al padre. Los IDs locales,
  `lastSyncedAt` y los estados de envío pertenecen a Room. El conversor de corte v1
  conserva la clasificación por semillas, los UUID y los tombstones. Un v1 residual en
  modo v2 se diagnostica como documento incompatible.

### Orden, concurrencia y reintentos

1. Comprobar sesión y protocolo con una lectura de servidor. Leer ambas colecciones de
   catálogo y reutilizar el motor 4B para aplicar o publicar padres e hijos. Ante un fallo
   que invalida toda la lectura del catálogo se detiene la fase de movimientos.
2. Leer los movimientos v2 desde servidor y validar cada documento. Resolver las
   relaciones usando las fichas confirmadas por esa corrida y comprobar las referencias
   de nuevo en Room al aplicar. Un UUID ausente o incompatible aplaza ese movimiento,
   incluidos tombstones. Las filas independientes pueden avanzar.
3. Publicar pendientes en transacciones por UUID. La transacción lee el marcador,
   la revisión actual y las referencias de catálogo. Crear exige ausencia y revisión 1.
   Una edición exige que la revisión actual sea la base registrada en Room, y avanza
   exactamente una revisión. Si el contenido confirmado coincide con el enviado, se
   acepta como reintento idempotente. Un tombstone confirmado queda inmutable. Las
   marcas del reloj se conservan y la revisión controla la concurrencia.
4. Confirmar respuesta y estado de Room por UUID. Si durante la solicitud hubo otra edición
   local, una confirmación exacta avanza la revisión base y deja la nueva edición pendiente
   en la misma transacción. Una revisión remota más nueva que la base con contenido
   divergente conserva ambos estados en sus respectivos orígenes y registra el conflicto.
   Un documento remoto ausente jamás convierte una edición con base conocida en creación.

Las colecciones se consultan por separado. Cada publicación es atómica por documento,
y cada aplicación usa una transacción Room para sus filas y revisión. No hay commit
conjunto entre Room, catálogo y movimientos remotos. Las reglas verifican las
dependencias en el estado de escritura. Sin conexión, una transacción remota falla y
las filas permanecen pendientes. Una lectura posterior puede confirmar un envío cuyo
ACK se perdió. Un conflicto de carrera puede aparecer primero como denegación de reglas,
como ocurrió en el emulador. Releer permite reconocer la revisión ganadora y conservar el
pendiente divergente. Los conflictos requieren que el usuario decida la recuperación en
un flujo futuro. La corrida experimental es manual y sus diagnósticos son en memoria.

### Protección del servidor y secuencia de corte

Las [reglas candidatas aisladas](../scripts/firestore-catalog/6c/firestore.rules) exigen
UID de la ruta igual al del token y autorización explícita. El marcador
`users/{uid}/metadata/movementProtocol` admite solo escritura administrativa.
Su ausencia o la versión 1 mantiene v1. La versión 0 congela las escrituras cliente.
La versión 2 admite exclusivamente movimientos v2 y catálogo para ese UID. Un marcador
malformado cierra la escritura. Las reglas deniegan altas, reemplazos, tombstones y
escrituras de metadatos del cliente v1 después del corte. Tampoco permiten que un cliente
reescriba como v2 un documento v1 residual de forma directa. La revisión exigida en
reglas limita versiones y cambios de contenido. El chequeo de la base que el dispositivo
observó lo hace su transacción. Un cliente autorizado que fabrique el próximo número
puede seguir cumpliendo la forma de escritura, y el SDK de administración opera bajo IAM.

Secuencia propuesta para un UID:

1. Publicar primero reglas compatibles con v1 y verificar que el resto de las cuentas
   continúe operando. Entregar el cliente actualizado con lectura del marcador y estado
   de compatibilidad. Actualizar los dispositivos, sincronizar los pendientes v1 y
   preparar su recuperación por separado cuando no se puedan confirmar.
2. Fijar el marcador en 0. Confirmar el bloqueo en servidor y respaldar los documentos
   existentes, incluidos tombstones. Sembrar el catálogo base y convertir cada movimiento
   con el conversor v1 → v2 en la misma ruta y con el mismo UUID. El backfill administrativo
   será reanudable. Antes de continuar necesita reconciliar conteos, referencias, monedas,
   importes, UUID, marcas temporales y tombstones. La candidata incluye el conversor, pero
   la herramienta administrativa y el plan operativo de recuperación quedan pendientes.
3. Tras verificar todo el conjunto, fijar el marcador en 2. El cliente nuevo hidrata su
   partición por UID antes de admitir escrituras. El antiguo mantiene sus datos locales,
   puede leer documentos autorizados y no entiende los v2. Sus escrituras se rechazan
   incluso si estaban pendientes al volver a conectarse. Mantener el marcador en 0 ante
   fallos durante el corte. Después de aceptar escrituras v2, recuperar en v2 o congelar
   el UID para reparar. Volver a v1 implicaría pérdida de relaciones personalizadas.

### Verificación y riesgos para 6D

- Las pruebas host comprueban el códec, UUID y exactitud monetaria, orden de dependencias,
  sesión y reintentos. La [suite del emulador](../scripts/firestore-catalog/6c/movement.test.mjs)
  usa cuentas ficticias y comprueba el corte, permisos, inmutabilidad y concurrencia real de
  transacciones. El [checkpoint](work-in-progress.md#cloud-sync-coordinado-iteración-6c)
  registra resultados y validación pendiente en Android.
- Para reproducir la suite, copiar `firebase.json`, `firestore.rules` y
  `movement.test.mjs` del directorio `scripts/firestore-catalog/6c/` a un directorio
  temporal, junto con `package.json` y `seed-android.mjs` del directorio padre.
  Instalar las dependencias de prueba con `npm install --ignore-scripts --no-audit --no-fund`
  en la copia. Ejecutar allí el comando siguiente con la CLI local. Para la prueba
  instrumentada, seguir las instrucciones de la sección siguiente con los emuladores activos.
- Antes de activar, validar el DAO generado y la sesión real con Auth Emulator, incluidas
  autenticaciones sucesivas, revocación y fallos de red. Falta decidir y verificar la
  reconciliación de pendientes de Room v3 con las cuentas, el manejo de restauraciones
  previas y el flujo de resolución para un conflicto de movimientos. Debe coordinarse también
  WorkManager, el volumen de lecturas y la visibilidad de errores parciales.

```sh
env -u FIREBASE_TOKEN -u GOOGLE_APPLICATION_CREDENTIALS -u GOOGLE_CLOUD_PROJECT \
  -u GCLOUD_PROJECT -u FIREBASE_CONFIG XDG_CONFIG_HOME="$PWD/firebase-config" CI=1 \
  firebase emulators:exec --project demo-miflujo-catalog --only auth,firestore \
  'node --test movement.test.mjs && node seed-android.mjs --catalog-colors'
```

### Prueba Android con Firebase Emulator en AVD o por USB

1. Desde la copia temporal con `firebase.json` y `firestore.rules` de
   `scripts/firestore-catalog/6c/`, iniciar ambos emuladores y dejarlos activos.

   ```sh
   firebase emulators:start --project demo-miflujo-catalog --only auth,firestore
   ```

2. En otra terminal, desde la raíz del repositorio, preparar la cuenta ficticia y el
   marcador 3 del protocolo experimental con color.

   ```sh
   env FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 \
     node scripts/firestore-catalog/seed-android.mjs --catalog-colors
   ```

3. Para el POCO X7 Pro, conectar el teléfono por USB con la depuración USB activada y
   aceptar la autorización del equipo. Con un solo dispositivo USB conectado, ejecutar
   estos comandos en Fedora.

   ```sh
   adb -d reverse tcp:9099 tcp:9099
   adb -d reverse tcp:8080 tcp:8080
   adb -d reverse --list
   ```

4. En Android Studio, seleccionar la variante `debug` y el POCO o el AVD como destino de
   pruebas. Abrir `AccountMovementSyncTest.kt` y usar el botón del margen del método
   `androidSdkSyncsArchivedReferencesAndTombstoneThroughTheDemoEmulators`. En
   **Run → Edit Configurations**, en la configuración **Android Instrumented Tests** de
   ese método, agregar los argumentos del runner como pares de nombre y valor.

   | Argumento | POCO por USB | AVD |
   | --- | --- | --- |
   | `catalogMovementEmulator` | `true` | `true` |
   | `catalogMovementEmulatorHost` | `127.0.0.1` | `10.0.2.2` o dejarlo sin definir |

5. Ejecutar el método y comprobar que el panel de resultados muestre la prueba por
   nombre con resultado aprobado y cero omitidas. Las otras dos pruebas Room se
   ejecutan directamente y prescinden de argumentos, emuladores Firebase y redirecciones.
6. Al terminar con el teléfono, retirar las dos redirecciones.

   ```sh
   adb -d reverse --remove tcp:9099
   adb -d reverse --remove tcp:8080
   ```

El cliente de prueba acepta únicamente las dos direcciones indicadas. Configura Auth en
9099 y Firestore en 8080 antes de iniciar sesión, con una instancia Firebase separada
para `demo-miflujo-catalog` y credenciales ficticias. Usar una instalación de pruebas
aislada si el teléfono contiene MiFlujo con datos reales.

## Integración funcional experimental (iteración 6D)

La variante `catalogLab` instala `com.carlos.miflujo.cataloglab` y abre una actividad
propia. Usa una base nueva con nombre fijo para trabajo local y las particiones por UID
de 6C para probar sincronización. El manifiesto de esa variante retira el acceso a
`MainActivity` y la inicialización Firebase predeterminada. La configuración Firebase
contiene únicamente datos ficticios. El cliente configura Auth y Firestore Emulator
para `demo-miflujo-catalog` con las dos direcciones locales ya admitidas.

1. En Android Studio, seleccionar `catalogLab` en **Build Variants** y ejecutar la app
   de pruebas. En la base local, **Agregar categoría de prueba** crea una categoría
   y una subcategoría ficticias con iconos. **Nuevo** y **Editar** reutilizan los controles
   de cápsulas y el lector de montos. El selector usa UUID, permite ambas referencias
   vacías y conserva una selección archivada histórica al editarla. Room valida ámbito
   y parentesco al guardar.
2. **Reporte mensual** calcula C$ y US$ por separado a partir de una lectura
   transaccional del catálogo y los movimientos visibles. Reutiliza el desglose flexible
   y el generador de PDF. **Exportar JSON** usa el mismo modelo portable v3 en ambas
   particiones. **Restaurar JSON** valida v1/v2/v3, pide confirmación y repite las
   comprobaciones de sincronización en la transacción de 6B. La partición de cuenta
   ofrece exportación y reportes y carece de acceso al almacén de restauración.
3. Preparar la cuenta ficticia con la [configuración de emuladores de 6C](#prueba-android-con-firebase-emulator-en-avd-o-por-usb).
   Elegir **Emulador por USB** o **Emulador en AVD**. La pantalla abre la partición del
   UID autenticado y deja aparte la base local. **Sincronizar** ejecuta el coordinador de
   6C manualmente y muestra los errores por operación. En la base de cuenta, **Eliminar**
   crea un tombstone. Cerrar sesión vuelve a los datos locales. Los cambios pendientes
   quedan en su partición y pueden consultarse al volver a entrar.

La prueba integral local parte de una Room v3 sintética, abre la candidata v4, crea y edita
movimientos con y sin clasificación, compara el reporte y restaura el JSON en una base
limpia. También comprueba la lectura histórica, la recuperación ante errores y la guarda
si aparece evidencia cloud entre la validación y la confirmación. La suite de migración
compara los esquemas limpio y migrado. La prueba SDK de cuenta recorre la misma sesión
experimental que usa la pantalla, con reporte, exportación, bloqueo de restauración,
tombstone y reintento. Ejecutarla con emuladores recién iniciados y el fixture de 6C.
Las pruebas de coordinación existentes cubren desconexión, cambio de cuenta y conflictos.
El [checkpoint 6D](work-in-progress.md#integración-funcional-y-validación-iteración-6d)
mantiene separados los resultados host y Android.

**Condiciones para activar producción.** Room v3 carece de propietario por fila y de una
revisión remota base. Adoptar pendientes exige comprobar su procedencia por UID y su
equivalencia con el documento remoto. Los casos divergentes requieren una decisión explícita
que conserve ambos estados. El reemplazo de la base de cuenta mediante backup sigue bloqueado.
Antes del corte se necesita una única programación de trabajo por sesión y protocolo que
detenga o drene los trabajos v1 antes de habilitar v2. La candidata usa sincronización manual.
El [corte por UID de 6C](#protección-del-servidor-y-secuencia-de-corte) requiere una herramienta
administrativa de backfill, reanudable y con inventario antes y después, probada primero con
datos ficticios. Un fallo conserva el UID congelado. Ante una migración local fallida,
Room conserva la v3 por rollback. Falta el flujo de recuperación para el usuario y verificar
apertura, restauración y corte en instalaciones aisladas de Android antes de activar.

## Gestión de categorías y subcategorías

En la variante aislada `catalogLab`, abrir **Configuración → Categorías y subcategorías**.
La lista expandible muestra fichas activas y archivadas. Se pueden crear, renombrar, cambiar
el icono, archivar tras confirmación y restaurar. Si el padre está archivado, la interfaz pide
restaurarlo primero para reactivar una subcategoría. Los nombres iguales son válidos y el UUID
distingue cada ficha. El tipo y el grupo de reporte se eligen al crear una categoría y quedan
fijos, igual que la relación padre-hijo.

En **Nuevo** y **Editar movimiento**, el selector ofrece fichas activas compatibles con el tipo,
subcategorías de su padre y la opción sin categoría. **Crear nueva categoría** conserva el importe,
moneda, fecha y detalle del borrador y selecciona la ficha recién guardada. Una clasificación
histórica archivada sigue visible con su nombre e icono. El repositorio decide si se conserva
o se cambia al guardar.

El selector incluye 50 [Material Symbols Rounded](https://developers.google.com/fonts/docs/material_symbols)
como VectorDrawables, con búsqueda en español y nueve grupos. Los nombres de categorías son
independientes de los nombres de los símbolos. Se almacena `iconKey` en Room, JSON v3 y el
protocolo experimental. Un identificador desconocido se muestra como el icono genérico y
se conserva al editar el nombre. La licencia y la revisión de origen están en los assets
de la variante.

Las pruebas Compose usan Room en memoria y se habilitan con `-PcatalogLabUiTests=true`.
Con esa propiedad, los APK de prueba usan el paquete separado
`com.carlos.miflujo.cataloglab.uitest` y su APK instrumentado. El flujo normal de
`catalogLab` y la app principal conservan sus paquetes y sus bases.

### Color opcional del icono

El formulario muestra diez colores en dos filas de cinco. **Usar color predeterminado**
es un control aparte. La marca visible y el estado accesible identifican la selección.
La vista previa, el catálogo y los movimientos aplican el tinte al dibujo. Las muestras
mantienen el tono de referencia y los iconos ajustan su contraste contra la superficie
del tema cuando hace falta. [Contraste no textual, W3C](https://www.w3.org/WAI/WCAG22/Understanding/non-text-contrast).

Room añade `iconColor` anulable al catálogo y a las subcategorías. Las bases candidatas
de movimientos y catálogo migran de v4 a v5, y las bases sandbox y por cuenta migran de
v1 a v2. Se conservan los UUID, las revisiones, las marcas de archivo y los movimientos.
NULL usa el color del tema. La exportación experimental ahora genera JSON v4 y admite
importar v1–v3 con el color del tema.

El cliente de sincronización coordinada requiere el marcador 3 en la cuenta ficticia.
El paso de preparación usa `seed-android.mjs --catalog-colors`. La cuenta mantiene los
documentos de movimientos v2. El catálogo nuevo se escribe como v2 y puede leer fichas
v1 sin color. Si se cambia una ficha v1 después del corte, se escribe v2 con una
revisión nueva. Las reglas candidatas del emulador impiden que una escritura v1
sobrescriba esos documentos. Un ensayo antiguo con marcador 2 requiere elevarlo de
forma explícita. Los pendientes locales se conservan mientras el protocolo no coincide.
