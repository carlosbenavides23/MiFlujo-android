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

## Propuesta UX en evaluación: registro rápido (noviembre de 2026)

**Estado:** diseño acordado para explorar, **no implementado**. La pantalla actual se encuentra en `ui/movement/AddMovementDialog.kt` y utiliza un `AlertDialog` con secciones verticales, elecciones separadas de tipo/moneda y fecha editable como texto. No describir esta propuesta como comportamiento presente en la app.

### Problema y objetivo

La persona usuaria ha registrado movimientos en notas breves como `+ C$100 compra de tornillos 3/4 14/11`. Queremos mantener esa rapidez mental y reducir escritura manual, omisiones y errores.

El objetivo es una **cápsula compacta de dos líneas** para controles principales, con descripción accesible debajo y posibilidad de capturar varios movimientos sin salir de la pantalla.

### Distribución conceptual

- **Línea 1:** un solo control que alterna **ingreso/egreso** (`+` ↔ `−`) en cada toque; otro solo control que alterna **C$/US$** en cada toque; monto numérico.
- **Línea 2:** fecha compacta como `Hoy` cuando corresponda (tocar abre calendario) y categoría contextual para egresos. Mostrar subcategoría si es costo fijo, sin romper las reglas actuales.
- **Debajo:** detalle opcional y acción de guardar; opción de continuar capturando tras un guardado confirmado.

No sustituir los dos controles alternables por grupos de dos chips: son **dos controles únicos**, cada uno con dos estados.

### Diseño y accesibilidad

- Tomar Material 3 Expressive como inspiración **ligera**: forma redondeada, movimiento sutil y respuesta háptica donde aporte confirmación; no introducir dependencia/API experimental sin revisión.
- Mantener texto, contraste y superficies táctiles accesibles; el signo y el símbolo monetario deben ser legibles, no depender solamente del color.
- La categoría puede ocupar más espacio o ajustar distribución si hay texto largo, fuente grande o ancho limitado. No sacrificar ergonomía por forzar una sola línea.
- Fecha inicial de un movimiento nuevo: día local actual; en edición: **fecha guardada**. El rótulo `Hoy` debe representar correctamente la fecha seleccionada.
- Considerar estados de error, teclado, accesibilidad y prevención de doble guardado.

### Comportamientos todavía por validar

- Valores iniciales de tipo, moneda y categoría para **nuevo** movimiento (sin interferir con edición).
- Qué campos conservar al guardar y continuar, y cómo avisar visualmente del éxito.
- Presentación de subcategorías de costos fijos y de errores de validación dentro del diseño compacto.
- Forma final de la superficie (diálogo, hoja inferior u otra): debe decidirse tras probar tamaños de pantalla y teclado.

### Límite técnico

Una primera iteración visual no debe modificar esquemas Room, identificadores UUID, reglas financieras, formatos de backup ni sincronización cloud. Se integrará con ViewModel y repositorio existentes cuando el prototipo haya demostrado ser usable. La rama de trabajo prevista es `ux/quick-entry`, con varias iteraciones y un PR de módulo hacia `main`.
