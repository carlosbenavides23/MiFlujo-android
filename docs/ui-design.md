# UI Design

## Dirección visual

MiFlujo debe tener una interfaz simple, sobria, moderna y clara.

La app no debe sentirse como una herramienta contable compleja.

Debe sentirse como una herramienta rápida para registrar movimientos, consultar cómo va el mes, generar reportes y proteger datos locales. Cloud Sync es secundario y no debe competir con el flujo diario.

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
- `TextButton`.
- `FilterChip`.
- `DatePicker`.
- `AlertDialog`.

## Navegación principal

La navegación inferior tiene tres secciones:

```text
Inicio | Movimientos | Reporte
```

La pantalla para agregar movimiento se abre desde el botón principal:

```text
+ Agregar
```

No debe ser una pestaña fija en la navegación inferior.

Ajustes se abre desde el botón de engranaje en la esquina superior derecha.

Ajustes no debe ser parte del bottom navigation porque no es una sección de uso diario.

## Pantalla Inicio / Mes actual

Pregunta que debe responder:

```text
¿Cómo va el mes?
```

Después de `v0.3.0`, Home debe mantenerse como vistazo rápido, no como análisis detallado.

Debe priorizar:

- Nombre de la app.
- Subtítulo.
- Mes actual.
- Flujo neto del mes separado por moneda.
- Últimos movimientos.
- Botón `+ Agregar`.

No debe duplicar toda la información detallada del Reporte mensual.

Ejemplo conceptual:

```text
MiFlujo
Flujo de efectivo mensual

Junio 2026

Flujo neto del mes
C$ 12,500.00
US$ 380.00

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

Junio 2026

Todos | Ingresos | Egresos

05/06/26
+ C$ 5,000.00
Venta del día
Ingreso

05/06/26
- C$ 1,800.00
Pago de luz
Costo fijo · Luz
```

Al tocar un movimiento debe abrirse una pantalla o diálogo de detalle.

Acciones disponibles:

- Editar.
- Eliminar.

No usar swipe para eliminar todavía.

## Pantalla Reporte mensual

Debe mostrar el estado mensual del flujo de efectivo.

Los totales deben estar separados por moneda.

Debe incluir:

- Total ingresos.
- Total egresos.
- Flujo neto del mes.
- Desglose de egresos por categoría.
- Acción para compartir/exportar PDF.

Ejemplo:

```text
Reporte mensual
Junio 2026

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

## Exportación PDF

La exportación PDF debe sentirse como un documento profesional, no como una captura de la UI.

Reglas visuales del PDF:

- A4 vertical.
- Márgenes definidos.
- Diseño sobrio tipo tabla.
- Bordes grises.
- Headers gris claro.
- Ingresos en verde semántico.
- Egresos en rojo semántico.
- Montos alineados a la derecha.
- Sin sombras de Material Design.
- Saltos de página controlados.
- No cortar filas entre páginas.
- Repetir encabezados en continuación si aplica.
- Numeración de páginas.

El PDF sirve para lectura y compartir reporte, no como respaldo técnico.

## Pantalla Ajustes

Ajustes organiza funciones que no son parte del flujo diario.

Debe incluir:

- Sección Cloud Sync.
- Inicio de sesión con Google y estado de autorización.
- Activación o desactivación explícita de Cloud Sync.
- Acción `Sincronizar ahora`, estado de la última sincronización y feedback claro de errores.
- Sección Datos.
- Crear respaldo local.
- Restaurar respaldo.
- Información futura.
- Changelog futuro.

Reglas:

- No debe competir con Inicio, Movimientos y Reporte.
- Debe abrirse desde el engranaje superior.
- Debe manejar correctamente el botón Atrás del sistema.
- Debe evitar animaciones bruscas.
- Las acciones destructivas deben pedir confirmación clara.
- Iniciar sesión o quedar autorizado no debe activar sincronización automáticamente.
- Mientras Cloud Sync esté activo, la restauración local debe indicar por qué no está disponible y cómo volver a modo local-only.

## Backup y restore en UI

### Crear respaldo local

Flujo esperado:

```text
Ajustes -> Crear respaldo local -> Guardar en Archivos / Compartir con otra app / Cancelar
```

El texto debe explicar que el archivo contiene movimientos y debe guardarse en un lugar seguro.

### Restaurar respaldo

Flujo esperado:

```text
Ajustes -> Restaurar respaldo -> selector de archivo -> validación -> confirmación -> restauración
```

La confirmación debe indicar claramente que los movimientos actuales serán reemplazados.

El botón destructivo debe usar color de error o énfasis equivalente.

Si el archivo es inválido, la UI debe mostrar feedback y no modificar datos.

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

Home debe ser rápido.

Reporte debe ser detallado.

Ajustes debe ser seguro y explícito.
