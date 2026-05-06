# Business Rules

## Concepto central

La entidad central del sistema es el movimiento.

```text
Movimiento = ingreso o egreso
```

El reporte mensual se calcula a partir de los movimientos guardados.

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

No se permite conversión automática entre monedas en el MVP.

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

Categorías de egreso permitidas en el MVP:

- Costo fijo.
- Mantenimiento.
- Otro.

Si el egreso es costo fijo, debe tener una subcategoría:

- Agua.
- Luz.
- Internet.

Mantenimiento y otros egresos no requieren subcategoría en el MVP.

## Detalle del movimiento

El detalle es recomendado, pero no obligatorio.

La app debe permitir guardar un movimiento sin detalle.

El campo detalle debe estar visible en el formulario.

## Validaciones

Reglas de validación:

- El monto es obligatorio.
- El monto debe ser mayor que 0.
- La moneda es obligatoria.
- La fecha es obligatoria.
- El tipo de movimiento es obligatorio.
- Si es egreso, la categoría es obligatoria.
- Si es costo fijo, la subcategoría es obligatoria.
- El detalle es recomendado, no obligatorio.

## Reporte mensual

El reporte mensual no se guarda como una entidad independiente.

Debe calcularse desde los movimientos existentes.

Un movimiento pertenece a un mes según su fecha.

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
