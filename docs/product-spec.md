# Product Spec

## Nombre

MiFlujo

## Subtítulo

```text
Flujo de efectivo mensual
```

## Descripción

MiFlujo es una app Android local-first para registrar ingresos, egresos, consultar el estado mensual del flujo de efectivo, generar reportes y proteger los datos mediante respaldo local.

Está diseñada para un usuario principal específico que necesita una herramienta simple, directa y adaptada a su forma de trabajar.

No busca ser una app financiera genérica ni una herramienta contable avanzada.

## Usuario principal

El usuario principal es una persona adulta no técnica que necesita registrar movimientos de dinero de forma rápida y consultar cómo va el mes.

El usuario trabaja con córdobas y dólares por separado.

La app debe priorizar claridad, bajo riesgo y continuidad de datos sobre complejidad técnica o funciones avanzadas.

## Problema

El usuario probó Excel y apps de la Play Store, pero las encontró incómodas, demasiado generales o poco adaptadas.

Necesita una app que responda rápido a estas preguntas:

```text
¿Cuánto entró este mes?
¿Cuánto salió este mes?
¿Cuánto queda según el flujo del mes?
¿En qué se fue el dinero?
¿Puedo conservar o recuperar mis datos si cambio de archivo/app/dispositivo?
```

## Principio central

```text
Abro, registro dinero, veo cómo va el mes, cierro.
```

## Estado del producto

MiFlujo ya superó el MVP inicial.

`v0.3.0` agregó reportes PDF, Ajustes, respaldo local JSON y restauración local JSON validada.

La etapa `v0.3.5` no agrega Firebase todavía. Su objetivo es preparar técnicamente la app para una futura sincronización cloud opcional sin romper la base local-first.

## Objetivos actuales del producto

- Registrar ingresos.
- Registrar egresos.
- Manejar córdobas y dólares por separado.
- Clasificar egresos.
- Registrar detalles de movimientos.
- Ver resumen rápido del mes actual.
- Ver historial de movimientos.
- Ver reporte mensual.
- Exportar reporte mensual a PDF.
- Editar movimientos.
- Eliminar movimientos.
- Crear respaldo local JSON.
- Guardar o compartir respaldo local.
- Restaurar respaldo local validado.
- Mantener Room como fuente principal de datos.

## Pantallas principales

La app tiene estos flujos principales:

1. Inicio / Mes actual.
2. Agregar movimiento.
3. Movimientos.
4. Reporte mensual.
5. Ajustes.

La navegación inferior mantiene tres secciones principales:

```text
Inicio | Movimientos | Reporte
```

La pantalla para agregar movimiento se abre desde el botón principal `+ Agregar`.

Ajustes se abre desde el botón de engranaje y no forma parte de la navegación inferior porque no es una sección de uso diario.

## Resultado esperado

El usuario debe poder registrar un movimiento en pocos pasos, entender el estado del mes sin conocimientos técnicos ni contables, generar reportes legibles y conservar sus datos mediante respaldos locales.

## Dirección futura

Firebase Cloud Sync podrá evaluarse después de `v0.3.5` como una capa opcional encima de Room.

La app debe seguir funcionando:

- sin internet,
- sin cuenta,
- sin backend obligatorio,
- sin mezclar monedas,
- sin depender de Firebase como fuente única de verdad.
