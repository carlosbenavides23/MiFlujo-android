# Data Model

## Entidad principal

La entidad principal del sistema es `Movement`.

Modelo actual:

```text
Movement
- id
- uuid
- type
- amountMinor
- currency
- date
- category
- subcategory
- detail
- createdAt
- updatedAt
- syncStatus
- lastSyncedAt
- deletedAt
```

## Estado actual del modelo

El modelo actual está diseñado para una app local-first basada en Room.

`id` sigue siendo el identificador local de Room.

`uuid` es la identidad global estable preparada para una futura sincronización cloud.

Room ya incluye metadata local preparada para Cloud Sync, pero esta metadata no activa sincronización. El plan de `v0.4.0` está documentado en `docs/firebase-cloud-sync-plan.md`.

## Campos

### id

Identificador único local del movimiento.

Tipo actual:

```kotlin
Long
```

Uso actual:

- Identificar movimientos en Room.
- Editar movimientos locales.
- Eliminar movimientos locales.
- Preservar IDs durante restauración local por reemplazo completo.

Limitación:

```text
id no es identidad global.
```

No debe usarse como identidad cloud entre dispositivos.

### uuid

Identificador global estable del movimiento.

Tipo:

```kotlin
String
```

Reglas:

- Se genera con un UUID aleatorio al crear un movimiento.
- No depende del ID local ni de campos editables.
- No cambia al editar un movimiento.
- Es obligatorio y único en Room.
- Los movimientos existentes reciben UUID durante la migración Room `1 -> 2`.
- Backup schema v2 lo exporta y preserva.
- Restaurar schema v1 genera UUID nuevos para los movimientos importados.

### type

Define si el movimiento es ingreso o egreso.

Valores permitidos:

```text
INCOME
EXPENSE
```

### amountMinor

Monto guardado en unidades menores usando `Long`.

No usar `Double` ni `Float` para almacenar dinero.

Ejemplos:

```text
C$ 1,800.50 -> 180050
US$ 100.00 -> 10000
```

### currency

Moneda del movimiento.

Valores permitidos:

```text
CORDOBA
DOLLAR
```

Representación visual:

```text
C$
US$
```

### date

Fecha del movimiento.

Formato visible:

```text
dd/MM/yy
```

Ejemplo:

```text
05/05/26
```

Internamente debe guardarse como fecha real, no como texto formateado para UI.

### category

Categoría del movimiento.

Valores actuales:

```text
GENERAL_INCOME
FIXED_COST
MAINTENANCE
OTHER
```

### subcategory

Subcategoría opcional.

Para costos fijos, valores permitidos:

```text
WATER
ELECTRICITY
INTERNET
```

Para ingresos, mantenimiento y otros egresos debe ser `null`.

### detail

Detalle o descripción del movimiento.

Es recomendado, pero no obligatorio.

### createdAt

Fecha y hora de creación del registro.

Uso actual:

- Ordenamiento.
- Información histórica local.
- Exportación/importación de respaldo JSON.

Limitación:

`createdAt` no resuelve por sí solo conflictos cloud entre dispositivos.

### updatedAt

Fecha y hora de última actualización del registro.

Uso actual:

- Información histórica local.
- Exportación/importación de respaldo JSON.

Para `v0.4.0`, `updatedAt` generado por la app decide conflictos entre registros con el mismo UUID.

Reglas:

- `createdAt` nunca cambia.
- `updatedAt` cambia al editar.
- `updatedAt` cambia al hacer soft delete.

## Metadata local para Cloud Sync

Room guarda estos campos:

```text
syncStatus
lastSyncedAt
deletedAt
```

Reglas:

- `syncStatus` es local-only.
- `lastSyncedAt` es local-only.
- Firestore no guarda `syncStatus` ni `lastSyncedAt`.
- `LOCAL_ONLY` no se sube a Firestore porque es metadata local.
- `deletedAt` se sincroniza porque representa soft delete/tombstone.
- Los estados pendientes solo se usan cuando Cloud Sync está activo.
- Con Cloud Sync apagado, la app conserva el comportamiento local de `v0.3.5` y no encola trabajo de sync.

Estados esperados:

```text
LOCAL_ONLY
SYNCED
PENDING_UPLOAD
PENDING_DELETE
SYNC_ERROR
```

`LOCAL_ONLY` se usa cuando Cloud Sync está apagado o todavía no fue activado.

Crear o editar con Cloud Sync activo asigna `PENDING_UPLOAD`. Eliminar asigna `deletedAt` y `PENDING_DELETE`.

Los tombstones permanecen en Room durante `v0.4.0`, pero se ocultan de la UI normal y se excluyen de reportes y PDF.

La migración Room `2 -> 3` asigna `LOCAL_ONLY`, `lastSyncedAt = null` y
`deletedAt = null` a los movimientos existentes.

Mientras Cloud Sync no esté activo, crear o restaurar movimientos usa esos mismos
valores por defecto y eliminar conserva el borrado físico actual.

Las consultas visibles excluyen cualquier fila con `deletedAt`, preparando UI,
reportes, PDF, historial y respaldo visible para futuros tombstones sin cambiar el
resultado actual.

## Modelo remoto planificado para v0.4.0

Ruta:

```text
users/{uid}/movements/{movementUuid}
```

Firestore usa `uuid` como document ID, no guarda el `id` local de Room y también guarda `uuid` como campo. Ese campo debe coincidir con el document ID.

Campos remotos:

```text
uuid
type
amountMinor
currency
date
category
subcategory
detail
createdAt
updatedAt
deletedAt
schemaVersion
```

La capa de mapeo remoto usa `RemoteMovementDto` exclusivamente como representación
del documento Firestore. No incluye `id`, `syncStatus` ni `lastSyncedAt`.

Reglas del mapper:

- `Movement -> RemoteMovementDto` preserva el UUID y usa remote schema version 1.
- `date` se representa como texto ISO `YYYY-MM-DD`.
- `createdAt`, `updatedAt` y `deletedAt` usan `Firebase Timestamp`.
- Los timestamps convierten `LocalDateTime` usando UTC y preservan nanosegundos.
- `RemoteMovementDto -> Movement` exige que el UUID coincida con el document ID.
- La entrada remota valida enums, monto positivo y reglas de clasificación.
- Un movimiento remoto válido vuelve al dominio con `id = 0`, `SYNCED` y
  `lastSyncedAt = null`; la futura capa de persistencia decidirá el ID Room y el
  momento efectivo de última sincronización.

Esta capa no realiza lecturas ni escrituras Firestore.

## Identidad local y global

```text
Movement
- id: Long local Room id
- uuid: String global stable id
```

La migración Room `1 -> 2` preserva el ID local y todos los campos existentes, y asigna un UUID aleatorio distinto a cada fila.

Backup schema v2 preserva el UUID estable al exportar y restaurar movimientos.

## Enums sugeridos

```kotlin
enum class MovementType {
    INCOME,
    EXPENSE
}
```

```kotlin
enum class Currency {
    CORDOBA,
    DOLLAR
}
```

```kotlin
enum class MovementCategory {
    GENERAL_INCOME,
    FIXED_COST,
    MAINTENANCE,
    OTHER
}
```

```kotlin
enum class MovementSubcategory {
    WATER,
    ELECTRICITY,
    INTERNET
}
```

## Ejemplos

### Ingreso en córdobas

```text
id: 1
uuid: 3f83ad74-77f1-4625-a525-66d860a86e76
type: INCOME
amountMinor: 500000
currency: CORDOBA
date: 2026-05-05
category: GENERAL_INCOME
subcategory: null
detail: Venta del día
```

### Egreso fijo de luz

```text
id: 2
uuid: bfa01442-30ed-4d90-83ab-cee48d00dfe3
type: EXPENSE
amountMinor: 180000
currency: CORDOBA
date: 2026-05-05
category: FIXED_COST
subcategory: ELECTRICITY
detail: Pago de factura mensual
```

### Egreso de mantenimiento en dólares

```text
id: 3
uuid: 07e63d69-a318-4ab8-a915-9dbb04db944d
type: EXPENSE
amountMinor: 10000
currency: DOLLAR
date: 2026-05-04
category: MAINTENANCE
subcategory: null
detail: Repuesto comprado
```

## Backup schema v1

Schema version 1 sigue soportado para importación de respaldos existentes.

Incluye:

- id.
- type.
- currency.
- category.
- subcategory.
- amountMinor.
- detail.
- date.
- createdAt.
- updatedAt.

Limitación:

```text
schemaVersion 1 no incluye UUID global.
```

Al importar schema v1, la app genera UUID nuevos porque ese formato no contiene identidad global.

## Backup schema v2

Los respaldos nuevos se exportan con schema version 2.

Incluyen los mismos campos de schema v1 y además:

- uuid.

Reglas:

- El UUID debe tener formato canónico válido.
- Los UUID deben ser únicos dentro del respaldo.
- Exportar preserva el UUID almacenado; no genera otro UUID.
- Importar schema v2 preserva el UUID del movimiento.
- Versiones distintas de 1 y 2 se rechazan.

## Nota sobre categorías

Para el estado actual, las categorías están controladas por código.

No se implementarán categorías dinámicas todavía.

El modelo debe mantenerse suficientemente limpio para permitir una migración futura a categorías dinámicas si fuera necesario.
