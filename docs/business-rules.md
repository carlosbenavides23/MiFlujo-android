# Business Rules

## Concepto central

La entidad central del sistema es el movimiento.

```text
Movimiento = ingreso o egreso
```

El reporte mensual se calcula a partir de los movimientos guardados.

Room/local es la fuente principal de datos en la app actual.

## Tipos de movimiento

Tipos permitidos:

- Ingreso.
- Egreso.

Un ingreso suma al total mensual de ingresos de su moneda.

Un egreso suma al total mensual de egresos de su moneda.

## Monedas

Monedas permitidas:

- Córdobas: C$.
- Dólares: US$.

Las monedas se manejan por separado.

No se permite conversión automática entre monedas.

No se permite calcular un único flujo neto mezclando C$ y US$.

## Fórmulas mensuales

Para córdobas:

```text
totalIngresosCordobas = suma de ingresos en C$ del mes
totalEgresosCordobas = suma de egresos en C$ del mes
flujoNetoCordobas = totalIngresosCordobas - totalEgresosCordobas
```

Para dólares:

```text
totalIngresosDolares = suma de ingresos en US$ del mes
totalEgresosDolares = suma de egresos en US$ del mes
flujoNetoDolares = totalIngresosDolares - totalEgresosDolares
```

## Clasificación de egresos

Categorías de egreso permitidas actualmente:

- Costo fijo.
- Mantenimiento.
- Otro.

Si el egreso es costo fijo, debe tener una subcategoría:

- Agua.
- Luz.
- Internet.

Mantenimiento y otros egresos no requieren subcategoría.

## Detalle del movimiento

El detalle es recomendado, pero no obligatorio.

La app debe permitir guardar un movimiento sin detalle.

El campo detalle debe estar visible en el formulario.

## Validaciones de movimiento

Reglas de validación:

- El monto es obligatorio.
- El monto debe ser mayor que 0.
- La moneda es obligatoria.
- La fecha es obligatoria.
- El tipo de movimiento es obligatorio.
- Si es egreso, la categoría es obligatoria.
- Si es costo fijo, la subcategoría es obligatoria.
- El detalle es recomendado, no obligatorio.

Combinaciones válidas:

- Ingreso:
  - category = `GENERAL_INCOME`.
  - subcategory = `null`.
- Egreso de costo fijo:
  - category = `FIXED_COST`.
  - subcategory = `WATER`, `ELECTRICITY` o `INTERNET`.
- Egreso de mantenimiento:
  - category = `MAINTENANCE`.
  - subcategory = `null`.
- Otro egreso:
  - category = `OTHER`.
  - subcategory = `null`.

## Reporte mensual

El reporte mensual no se guarda como una entidad independiente.

Debe calcularse desde los movimientos existentes.

Un movimiento pertenece a un mes según su fecha.

Los reportes deben mantener totales separados por moneda.

## Exportación PDF

El PDF es una salida humana para leer, revisar o compartir el reporte mensual.

Reglas:

- Debe mostrar totales separados por moneda.
- No debe mezclar C$ y US$ en un total único.
- Debe representar claramente ingresos, egresos y flujo neto.
- Debe ser legible, sobrio y similar a una tabla profesional.
- No debe usarse como respaldo técnico de datos.

## Respaldo local JSON

El respaldo JSON es una salida técnica para conservar y restaurar datos.

Reglas:

- Debe incluir versión de esquema.
- Debe indicar que pertenece a MiFlujo.
- Debe incluir fecha/hora de creación del respaldo.
- Debe incluir todos los movimientos exportados.
- Debe preservar los campos reales necesarios para restaurar movimientos.
- No debe mezclar datos calculados de reportes como si fueran entidad persistida.

El respaldo local actual no implementa:

- Cifrado.
- Nube.
- Firebase.
- CSV.
- XLSX.
- Merge de datos.

## Restauración local JSON

La restauración local actual funciona por reemplazo completo.

Flujo obligatorio:

1. El usuario selecciona un archivo JSON.
2. La app lee y valida todo el archivo.
3. Si el archivo es inválido, no se modifica ningún dato.
4. Si el archivo es válido, queda pendiente.
5. La app muestra confirmación explícita y destructiva.
6. Si el usuario cancela, no se modifica ningún dato.
7. Si el usuario confirma, Room reemplaza todos los movimientos dentro de una transacción.
8. Si cualquier inserción falla, la transacción debe revertir la operación.

Reglas de validación del respaldo:

- `schemaVersion` debe ser soportado.
- `app` debe ser `MiFlujo`.
- Los campos requeridos deben existir.
- Los enums deben ser conocidos.
- Las fechas deben ser válidas.
- Los timestamps deben ser válidos.
- Los IDs deben ser positivos y únicos dentro del respaldo.
- Los montos deben ser positivos.
- Las combinaciones tipo/categoría/subcategoría deben respetar las reglas de negocio.

## Restore y cloud sync futura

La restauración local destructiva es aceptable mientras la app sea local-first sin sincronización cloud activa.

Antes de Firebase Cloud Sync, se debe definir un comportamiento cloud-safe para restore.

No se debe asumir que el restore destructivo actual puede usarse igual con datos sincronizados.

## Flujo neto negativo

Si el flujo neto del mes es negativo, debe mostrarse claramente.

Ejemplo:

```text
- C$ 2,300.00
```

No se debe ocultar ni maquillar el resultado.

## Precisión monetaria

No usar `Double` ni `Float` para almacenar dinero.

Los montos deben guardarse como `Long` en centavos o unidades menores.

Ejemplos:

```text
C$ 1,800.50 -> 180050
US$ 100.00 -> 10000
```
