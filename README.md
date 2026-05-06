# MiFlujo

**MiFlujo** es una app Android local-first para registrar ingresos, egresos y generar un flujo de efectivo mensual claro y separado por moneda.

Subtítulo de la app:

```text
Flujo de efectivo mensual
```

## Objetivo

Crear una app simple, rápida y directa para que el usuario principal pueda:

- Registrar ingresos.
- Registrar egresos.
- Clasificar egresos como costos fijos, mantenimiento u otros.
- Detallar a qué corresponde cada monto.
- Consultar el estado mensual del flujo de efectivo.
- Ver totales separados en córdobas y dólares.

Principio central del producto:

```text
Abro, registro dinero, veo cómo va el mes, cierro.
```

## Alcance del MVP

El MVP será una app Android local, sin nube, sin login y sin conversión automática entre monedas.

Incluye:

- Registro de ingresos.
- Registro de egresos.
- Moneda por movimiento: C$ o US$.
- Costos fijos: agua, luz e internet.
- Gastos de mantenimiento.
- Otros egresos.
- Detalle recomendado por movimiento.
- Resumen del mes actual.
- Historial de movimientos.
- Reporte mensual de flujo de efectivo.
- Edición y eliminación de movimientos.

No incluye en el MVP:

- Login.
- Sincronización en la nube.
- Integración bancaria.
- OCR.
- IA dentro de la app.
- Facturación.
- Conversión automática de moneda.
- Tipo de cambio.
- Reportes contables avanzados.

## Stack técnico previsto

- Kotlin.
- Jetpack Compose.
- Material Design 3.
- Room.
- MVVM.
- Repository Pattern.

## Arquitectura conceptual

```text
UI
↓
ViewModel
↓
Repository
↓
Room Database
```

## Documentación del proyecto

La documentación base se encuentra en `docs/`:

- `docs/product-spec.md`
- `docs/mvp-scope.md`
- `docs/ui-design.md`
- `docs/data-model.md`
- `docs/business-rules.md`
- `docs/decisions.md`

El archivo `AGENTS.md` contiene las reglas principales para cualquier agente de IA o colaborador que trabaje en el proyecto.

## Estado

Proyecto en fase inicial de planificación y estructura del repositorio.
