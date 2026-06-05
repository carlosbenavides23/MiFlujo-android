# Data Model

## Entidad principal

La entidad principal del sistema es `Movement`.

Modelo actual:

```text
Movement
- id
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

`id` es un identificador local. Funciona correctamente para persistencia local, edición, eliminación, respaldo local por reemplazo completo y restauración local validada.

Antes de Firebase Cloud Sync se debe agregar una estrategia de identidad global. No se debe asumir que `id: Long` sirve como identidad cloud entre dispositivos.

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

Para sincronización cloud futura se debe agregar un UUID estable o una estrategia equivalente de identidad global.

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

## Identidad cloud futura

Antes de implementar Firebase Cloud Sync, el modelo debe definir una identidad global estable.

Dirección esperada:

```text
Movement
- id: Long local Room id
- uuid: String global stable id
```

Reglas a definir antes de implementar:

- Cómo se genera el UUID para movimientos nuevos.
- Cómo se asigna UUID a movimientos existentes mediante migración.
- Cómo se exporta UUID en backup schema v2.
- Cómo se importa backup schema v1 sin UUID.
- Cómo se evita duplicar movimientos al sincronizar.
- Cómo se comporta restore cuando cloud sync existe.

No implementar este cambio sin migración Room y pruebas.

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

Por eso, antes de Firebase Cloud Sync se debe diseñar backup schema v2.

## Nota sobre categorías

Para el estado actual, las categorías están controladas por código.

No se implementarán categorías dinámicas todavía.

El modelo debe mantenerse suficientemente limpio para permitir una migración futura a categorías dinámicas si fuera necesario.
