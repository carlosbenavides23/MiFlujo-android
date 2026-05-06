# Data Model

## Entidad principal

La entidad principal del sistema es `Movement`.

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

## Campos

### id

Identificador único del movimiento.

Tipo sugerido:

```kotlin
Long
```

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

Valores iniciales:

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

Para ingresos, mantenimiento y otros egresos puede ser `null`.

### detail

Detalle o descripción del movimiento.

Es recomendado, pero no obligatorio.

### createdAt

Fecha y hora de creación del registro.

### updatedAt

Fecha y hora de última actualización del registro.

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
type: EXPENSE
amountMinor: 10000
currency: DOLLAR
date: 2026-05-04
category: MAINTENANCE
subcategory: null
detail: Repuesto comprado
```

## Nota sobre categorías

Para el MVP, las categorías serán controladas por código.

No se implementarán categorías dinámicas todavía.

El modelo debe mantenerse suficientemente limpio para permitir una migración futura a categorías dinámicas si fuera necesario.
