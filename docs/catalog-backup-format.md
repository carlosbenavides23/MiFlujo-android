# Respaldo de catálogo v3 — contrato experimental

## Estado

Implementado como códec y almacén de pruebas aislados. **No activado** en BackupExporter,
BackupImporter, BackupJsonParser/Serializer ni SettingsViewModel. El respaldo de producción
sigue en schemaVersion 2 y su lector admite 1/2. La base financiera sigue en Room v3.

El códec nuevo `CatalogBackupJson` lee 1/2/3 y escribe únicamente 3. `CatalogBackup` y sus DTOs
son datos portables de respaldo, no sustituyen Movement ni MovementEntity.

## Ejemplo completo v3

Todos los campos mostrados son obligatorios; null debe ser explícito donde corresponda.

```json
{
  "schemaVersion": 3,
  "app": "MiFlujo",
  "createdAt": "2026-11-16T11:12:13.123456789",
  "categories": [
    {
      "uuid": "11111111-1111-4111-8111-111111111111",
      "name": "Hogar",
      "scope": "EXPENSE",
      "reportGroup": "OTHER",
      "iconKey": "home",
      "archived": true,
      "sortOrder": 0,
      "revision": {"counter": 7, "actorUuid": "44444444-4444-4444-8444-444444444444"}
    }
  ],
  "subcategories": [
    {
      "uuid": "22222222-2222-4222-8222-222222222222",
      "categoryUuid": "11111111-1111-4111-8111-111111111111",
      "name": "Agua",
      "iconKey": "water",
      "archived": true,
      "sortOrder": 0,
      "revision": {"counter": 4, "actorUuid": "44444444-4444-4444-8444-444444444444"}
    }
  ],
  "movements": [
    {
      "uuid": "33333333-3333-4333-8333-333333333333",
      "type": "EXPENSE",
      "amountMinor": 12345,
      "currency": "DOLLAR",
      "date": "2026-11-16",
      "categoryUuid": "11111111-1111-4111-8111-111111111111",
      "subcategoryUuid": "22222222-2222-4222-8222-222222222222",
      "detail": "Servicio del mes",
      "createdAt": "2026-11-16T11:12:13.123456789",
      "updatedAt": "2026-11-16T11:12:13.123456789"
    }
  ]
}
```

## Datos de contenido y metadata

- Identidad: UUID, nunca id/categoryId/subcategoryId Long. Los UUID de categorías y subcategorías
  son canónicos RFC según el contrato del catálogo. En el catálogo son únicos también entre
  padres e hijos; los UUID de movimientos tienen su espacio de identidad separado y conservan
  la validación canónica del lector histórico para no restringir archivos v2 previamente válidos.
- Categoría: name, scope (INCOME/EXPENSE/BOTH), reportGroup, iconKey, archived y sortOrder.
  INCOME exige grupo INCOME; EXPENSE/BOTH usan FIXED_COST/MAINTENANCE/OTHER. Se conservan nombres
  iguales con UUID diferentes e iconKey desconocidos. No se fusiona ni se traduce por nombre.
- Subcategoría: nombre, icono, archivo, orden y categoryUuid de un padre existente.
- revision: metadata causal portable del catálogo (counter Long no negativo y actorUuid canónico).
  Se conserva para no reiniciar el reloj lógico del elemento. No demuestra que la ficha esté
  sincronizada ni que deba sobrescribir un servidor. El actor de una revisión identifica su origen;
  **no se instala como actor del dispositivo restaurado**.
- Se omiten PENDING/SYNCED/ERROR, lastSyncedAt, catalog_installation y cualquier ID local. El v3
  rechaza estos campos extra para evitar que un consumidor los interprete como verdad portable.
- sortOrder es Int firmado; se admiten empates, desempatados por UUID. El orden de los arrays del
  archivo no identifica relaciones; un snapshot del almacén puede ordenarlos de otra manera.
- Este es un respaldo de contenido visible, como v2, no un journal de sincronización: no exporta
  tombstones de movimientos. Las categorías archivadas sí se incluyen aunque no tengan movimientos.

### Valores financieros y relaciones

amountMinor es un entero JSON positivo que cabe en Long, sin conversión por Double ni monedas
combinadas. Se conserva el rango histórico de respaldos, sin imponer aquí el límite visual del
formulario. type/currency deben ser enums conocidos; date debe ser fecha ISO válida. createdAt y
updatedAt son timestamps locales ISO del formato histórico, con hasta nueve decimales: no se
redondean a milisegundos ni se aplica zona horaria. No se introduce una exigencia nueva de orden
entre timestamps que pudiera invalidar respaldos antiguos previamente admitidos.

| categoryUuid | subcategoryUuid | Resultado |
| --- | --- | --- |
| null | null | Sin categoría |
| UUID existente | null | Categoría sin hijo |
| UUID existente | UUID existente de ese padre | Categoría y subcategoría |
| null | UUID | Inválido |
| UUID desconocido | cualquiera | Inválido, nunca «Sin categoría» |

El scope del padre debe admitir el tipo del movimiento. El archivo puede contener referencias
históricas a categorías/subcategorías archivadas; no es una asignación nueva de la UI. Un hijo
con padre ajeno, un UUID duplicado, metadata estructural inválida o una identidad de semilla
repurpuesta rechazan todo el archivo. Los UUID reservados del catálogo heredado conservan el
ámbito, grupo y padre definidos en LegacyCategoryCatalog. Referencias ausentes no crean semillas
ni categorías nuevas en v3. Un catálogo vacío y movimientos sin categoría son válidos.

El lector exige campos y tipos exactos en v3, incluidos los nullable; rechaza versión desconocida,
valores monetarios fraccionarios/fuera de Long, contenido posterior al objeto y campos extra.
Utiliza org.json, igual que el proyecto; los tests JVM no sustituyen comprobar el lector Android.

## Compatibilidad histórica

El códec delega primero en BackupJsonParser para v1/v2: mantiene sus reglas de IDs antiguos,
duplicados, UUID y clasificación. Después convierte los enums con las identidades congeladas:
las cuatro categorías y tres subcategorías de LegacyCategoryCatalog. La revisión inicial de
esas semillas es (0, actor reservado de semillas). No se toleran enums desconocidos.

v2 conserva UUID de movimientos; v1 genera UUID v4 nuevos al leer, como el lector actual. Leer
el mismo v1 dos veces puede generar identidades distintas; el restore sigue siendo reemplazo,
no mezcla ni deduplicación por nombre/contenido. Los IDs antiguos solo se validan al leer y se
descartan en la representación portable. Se preservan importe, moneda, tipo, fecha, detalle y
timestamps. El códec puede reescribir esa representación a v3 sin volver a enums.

## Almacén y restauración aislados

BackupSandboxDatabase tiene otro nombre fijo (`category-backup-sandbox.db`), versión experimental 1,
y ninguna migración registrada. No es CategoryCatalogDatabase v4 ni MiFlujoDatabase v3. Solo se
abre explícitamente; no se inyecta en la app. Usa entidades del catálogo y BackupMovementEntity,
filas de staging con Long locales y FK nullable, **no MovementEntity**. Las fechas/timestamps se
almacenan como ISO para conservar la precisión del respaldo. La inicialización comparte semillas
/protecciones con el catálogo y añade triggers que impiden hijo sin padre o con padre incorrecto.

ExperimentalBackupStore.snapshot lee todas las categorías y todos los hijos, incluidas archivadas,
y los movimientos dentro de una transacción. Resuelve IDs a UUID y valida el conjunto. La consulta
lee también posibles hijos huérfanos de una base corrupta: falla al resolverlos en lugar de omitirlos.
El snapshot nunca exporta el estado local de envío ni la fila de instalación.

Restaurar realiza:

1. Copiar y validar el snapshot en memoria antes del reemplazo.
2. Dentro de withTransaction, validar el estado anterior y rechazar cambios de estructura o de
   tipo padre/hijo para UUID ya conocidos. Renombres/iconos/archivo/orden sí pueden reemplazarse.
3. Retirar únicamente los dos triggers de no borrado durante esa transacción aislada.
4. Borrar staging de movimientos, luego hijos y luego categorías; conservar catalog_installation.
5. Insertar categorías y crear mapa UUID→ID generado; insertar hijos resolviendo sus padres;
   insertar movimientos resolviendo ambas referencias. No se copian IDs del archivo ni se reinician
   secuencias de IDs. Restaurar dos veces no añade movimientos, aunque sus IDs locales cambien.
6. Reinstalar los triggers y confirmar. Cualquier excepción revierte también filas y DDL de triggers.

Cada categoría/hijo conserva su revisión importada y queda PENDING; no se importa un ACK ni un
estado de error. No se incrementa la revisión porque restaurar no es una edición de contenido.
Una edición posterior usará counter+1 y el actor local existente. Los movimientos de staging
quedan LOCAL_ONLY, como el restore histórico, y no se encolan en Cloud Sync.

El almacén nunca acepta un MiFlujoDatabase ni un repositorio financiero como destino. No altera
los jobs, confirmación ni bloqueos cloud actuales. La política no autoriza restaurar un backup
viejo contra una cuenta activa ni usar sus revisiones para forzar escrituras remotas.

## Pruebas y activación pendiente

- JVM: pruebas existentes de parser/serializer v1/v2 y nuevas pruebas de v3, round-trip, archivo,
  valores financieros/nanosegundos, opcionalidad, duplicados, estructuras, campos y referencias inválidas.
- SQLite del equipo: scripts/test_catalog_backup_sqlite.py prueba FK/triggers reales de relación,
  remapeo, Long/ISO y rollback explícito de datos/DDL. La tabla staging de ese script es un fixture,
  no DDL generado por Room. No ejecuta ExperimentalBackupStore.
- Android/Room: ExperimentalBackupRoomTest prepara cinco pruebas de round-trip con el JSON Android,
  remapeo, pending/revisiones, actor local, reemplazo vacío, guardas SQL y fallo inyectado después de
  insertar catálogo y el primer movimiento. Las fuentes compilan, pero estas pruebas no se ejecutaron.

Antes de activar: completar modelo/relaciones financieros reales, migración y esquema Room,
validar los tests instrumentados, adaptar ParsedBackup/Exporter/Importer y SettingsViewModel de
forma coordinada, y conservar confirmación, reemplazo atómico y bloqueos de restore simultáneo/cloud.
Hace falta un coordinador que proteja el snapshot y la transacción completos con todos los escritores
reales; el bloqueo de esta base aislada no sustituye esos mecanismos de la app.

No desplegar v3 parcialmente: todavía no hay lector/restaurador financiero real para este payload,
ni compatibilidad de estos campos con Firestore. Todo el trabajo de esta entrega es preparatorio.

## Extensión experimental v4 para color de icono

El escritor actual usa `schemaVersion: 4`. La única diferencia de estructura frente
a v3 es el campo obligatorio `iconColor` de cada categoría y subcategoría. Su valor
es `null` para usar el color del tema, o una clave de la siguiente lista.

```text
red orange amber green mint cyan blue violet pink gray
```

El lector admite v1 y v2 con el adaptador histórico y v3 con su forma original.
Todos reciben color nulo. En v4 se rechazan las claves desconocidas, los campos de
color ausentes y los tipos incompatibles. Los movimientos y sus referencias UUID
conservan el formato de v3. Los lectores experimentales antiguos requieren
actualización para restaurar archivos v4. La aplicación de producción conserva JSON v2.
