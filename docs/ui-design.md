# UI Design

## Dirección visual

MiFlujo debe tener una interfaz simple, sobria, moderna y clara.

La app no debe sentirse como una herramienta contable compleja.

Debe sentirse como una herramienta rápida para registrar movimientos y consultar cómo va el mes.

Principio de diseño:

```text
Abro, registro dinero, veo cómo va el mes, cierro.
```

## Stack visual

Usar:

- Jetpack Compose.
- Material Design 3.

Componentes recomendados:

- `Scaffold`.
- `TopAppBar`.
- `NavigationBar`.
- `NavigationBarItem`.
- `ExtendedFloatingActionButton`.
- `Card`.
- `OutlinedTextField`.
- `Button`.
- `FilterChip`.
- `DatePicker`.
- `AlertDialog`.

## Navegación principal

La navegación inferior tendrá tres secciones:

```text
Inicio | Movimientos | Reporte
```

La pantalla para agregar movimiento se abrirá desde el botón principal:

```text
+ Agregar
```

No debe ser una pestaña fija en la navegación inferior.

## Pantalla Inicio / Mes actual

Pregunta que debe responder:

```text
¿Cómo va el mes?
```

Debe mostrar:

- Nombre de la app.
- Subtítulo.
- Mes actual.
- Flujo neto del mes separado por moneda.
- Total de ingresos separado por moneda.
- Total de egresos separado por moneda.
- Resumen de egresos.
- Últimos movimientos.
- Botón `+ Agregar`.

Ejemplo conceptual:

```text
MiFlujo
Flujo de efectivo mensual

Mayo 2026

Flujo neto del mes
C$ 12,500.00
US$ 380.00

Ingresos
C$ 35,000.00
US$ 500.00

Egresos
C$ 22,500.00
US$ 120.00

Últimos movimientos
+ C$ 5,000.00 Venta del día
- C$ 1,800.00 Pago de luz
- US$ 100.00 Repuesto comprado

+ Agregar
```

## Pantalla Agregar movimiento

Primer paso:

```text
¿Qué desea registrar?
```

Opciones:

```text
Ingreso | Egreso
```

### Ingreso

Campos:

- Monto.
- Moneda.
- Fecha.
- Detalle.

### Egreso

Campos:

- Monto.
- Moneda.
- Fecha.
- Categoría.
- Subcategoría si aplica.
- Detalle.

Categorías de egreso:

```text
Costos fijos | Mantenimiento | Otros
```

Subcategorías de costos fijos:

```text
Agua | Luz | Internet
```

## Pantalla Movimientos

Debe mostrar:

- Selector de mes.
- Filtros simples:
  - Todos.
  - Ingresos.
  - Egresos.
- Lista cronológica de movimientos.

Ejemplo:

```text
Movimientos

Mayo 2026

Todos | Ingresos | Egresos

05/05/26
+ C$ 5,000.00
Venta del día
Ingreso

05/05/26
- C$ 1,800.00
Pago de luz
Costo fijo · Luz
```

Al tocar un movimiento debe abrirse una pantalla o diálogo de detalle.

Acciones disponibles:

- Editar.
- Eliminar.

No usar swipe para eliminar en el MVP.

## Pantalla Reporte mensual

Debe mostrar el estado mensual del flujo de efectivo.

Los totales deben estar separados por moneda.

Ejemplo:

```text
Reporte mensual
Mayo 2026

Total ingresos
C$ 35,000.00
US$ 500.00

Total egresos
C$ 22,500.00
US$ 120.00

Flujo neto del mes
C$ 12,500.00
US$ 380.00
```

Debe incluir desglose de egresos por categoría.

## Uso de color

Se puede usar color para ayudar a distinguir ingresos y egresos:

- Ingresos: verde.
- Egresos: rojo o naranja.
- Información neutral: azul, gris o colores del tema.

Pero la app no debe depender solo del color.

También deben usarse signos:

```text
+ C$ 5,000.00
- C$ 1,800.00
+ US$ 500.00
- US$ 120.00
```

## Regla de claridad

La app debe priorizar legibilidad sobre densidad visual.

Es mejor mostrar menos información con claridad que muchas métricas juntas.
