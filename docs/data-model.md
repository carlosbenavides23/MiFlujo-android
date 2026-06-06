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
```

## Estado actual del modelo

El modelo actual está diseñado para una app local-first basada en Room.

`id` sigue siendo el identificador local de Room.

`uuid` es la identidad global estable preparada para una futura sincronización cloud.

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
- Backup schema v1 no lo exporta ni lo preserva.
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

Limitación:

`updatedAt` no debe considerarse suficiente para resolver conflictos cloud sin una estrategia documentada.

## Identidad local y global

```text
Movement
- id: Long local Room id
- uuid: String global stable id
```

La migración Room `1 -> 2` preserva el ID local y todos los campos existentes, y asigna un UUID aleatorio distinto a cada fila.

Backup schema v2 deberá preservar UUID, pero pertenece a la issue `#100`.

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

El respaldo JSON actual usa schema version 1.

Incluye los campos actuales del movimiento:

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

Al importar schema v1, la app genera UUID nuevos. La exportación continúa omitiendo UUID hasta implementar backup schema v2 en `#100`.

## Nota sobre categorías

Para el estado actual, las categorías están controladas por código.

No se implementarán categorías dinámicas todavía.

El modelo debe mantenerse suficientemente limpio para permitir una migración futura a categorías dinámicas si fuera necesario.
