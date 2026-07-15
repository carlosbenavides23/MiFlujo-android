# Auditoría técnica pre-Firebase

## Propósito

Este documento registra la auditoría técnica histórica previa a Firebase Cloud Sync.

Forma parte de la fase:

```text
v0.3.5 - Pre-Firebase Technical Baseline
```

El objetivo de esta fase es reducir riesgo antes de introducir sincronización cloud en una app que ya maneja datos reales.

Esta auditoría no implementaba Firebase. Sus hallazgos se resolvieron en la
baseline `v0.3.5`; el trabajo de Cloud Sync posterior está integrado en `main`
para `v0.4.0`, aún sin publicar como release estable.

## Contexto actual

MiFlujo es una app Android local-first para flujo de efectivo mensual.

Estado estable actual:

```text
v0.3.0 - Reportes, Ajustes y Respaldo Local
```

La app ya soporta:

- Registro de ingresos.
- Registro de egresos.
- Córdobas y dólares separados.
- Dashboard del mes actual.
- Movimientos.
- Reporte mensual.
- Exportación de reporte mensual a PDF.
- Ajustes.
- Respaldo local JSON.
- Restauración local JSON validada.
- Persistencia local con Room.

## Principio arquitectónico actual

```text
Room/local = fuente principal.
```

La app debe funcionar:

- sin internet,
- sin cuenta,
- sin backend,
- sin Firebase,
- sin mezclar monedas.

Firebase futuro, si se implementa, debe ser una capa opcional de respaldo/sincronización, no el corazón de la app.

## Conclusión ejecutiva

MiFlujo está en buen estado como app local-first.

Sin embargo, no está listo para Firebase Cloud Sync sin una pasada técnica previa.

La razón principal es que varias decisiones actuales son correctas para uso local, pero insuficientes para sincronización entre dispositivos.

Riesgo principal:

```text
Implementar Firebase encima del modelo actual podría causar duplicación, pérdida o corrupción de movimientos.
```

## Hallazgos principales

### 1. `Movement.id` es local

El modelo actual usa `id: Long` como identificador de movimiento.

Esto es correcto para Room local.

Funciona para:

- insertar localmente,
- editar localmente,
- eliminar localmente,
- restaurar backup local por reemplazo completo,
- preservar IDs positivos y únicos dentro de un backup.

Pero no sirve como identidad global entre dispositivos.

Riesgo con cloud sync:

```text
Dos dispositivos pueden crear movimientos distintos con el mismo id local.
```

Decisión necesaria:

Agregar una identidad global estable, probablemente `uuid`, antes de sincronización.

### 2. Backup schema v1 no incluye identidad global

En el momento de la auditoría, el backup JSON usaba:

```text
schemaVersion = 1
```

Incluye los campos actuales del movimiento:

- id,
- type,
- currency,
- category,
- subcategory,
- amountMinor,
- detail,
- date,
- createdAt,
- updatedAt.

Limitación:

```text
No incluye UUID global.
```

Riesgo:

Un backup restaurado en un contexto cloud podría crear duplicados o pisar datos incorrectos si se usa `id` como identidad.

Resolución:

`#100` implementa `schemaVersion = 2` con UUID estable para nuevos respaldos y mantiene importación compatible de schema v1 generando UUID nuevos.

### 3. Timestamps actuales no bastan para resolución de conflictos

`createdAt` y `updatedAt` existen y se preservan en backups.

Esto es útil localmente.

Pero no basta para resolver conflictos cloud de forma segura.

Preguntas pendientes:

- ¿Qué pasa si dos dispositivos editan el mismo movimiento offline?
- ¿Qué campo gana?
- ¿Se usa last-write-wins?
- ¿Se conserva historial?
- ¿Se evita resolver conflictos en v0.4.0 manteniendo sync simple?

Decisión necesaria:

Definir una estrategia mínima de conflictos antes de Firestore.

### 4. Restore destructivo local no es cloud-safe por defecto

La restauración actual reemplaza todos los movimientos locales después de validar el backup y pedir confirmación.

Esto es aceptable en una app local-first sin cloud sync.

Riesgo con cloud sync:

```text
Un restore local podría interpretarse como eliminación masiva remota.
```

Decisión necesaria:

Definir comportamiento cloud-safe para restore antes de activar Firebase.

Opciones a evaluar:

- Desactivar restore si cloud sync está activo.
- Restaurar solo localmente y pedir re-subida manual.
- Crear un modo import/merge no destructivo.
- Tratar restore como reemplazo total con confirmación adicional y estrategia remota explícita.

No elegir implementación todavía sin documento específico.

### 5. Reglas de negocio duplicadas

Las reglas de movimiento existen en UI/importador y lógica relacionada.

Ejemplo de reglas:

- ingreso usa `GENERAL_INCOME` y `subcategory = null`,
- egreso de costo fijo requiere subcategoría,
- mantenimiento y otros no usan subcategoría,
- monto debe ser positivo.

Riesgo:

A futuro, UI, backup parser y sync podrían validar distinto.

Decisión necesaria:

Centralizar validación de movimientos en una única fuente reutilizable.

### 6. Room schema export debe habilitarse antes de migraciones importantes

Antes de agregar campos como UUID, conviene tener Room schema export habilitado.

Riesgo:

Sin schema export, las migraciones son más difíciles de auditar y revisar.

Decisión necesaria:

Configurar export de schemas antes de migraciones sync.

### 7. Android Auto Backup necesita política explícita

La app maneja datos financieros personales.

Android Auto Backup puede interactuar con datos locales según configuración.

Riesgo:

Datos locales podrían respaldarse/restaurarse por mecanismos del sistema sin que la política del proyecto esté claramente definida.

Decisión necesaria:

Definir si la app permite, limita o excluye Android Auto Backup para datos financieros.

### 8. Documentación post-MVP tenía drift

Algunos documentos seguían describiendo el proyecto como si estuviera en `v0.1.1` o solo en MVP.

Pero `v0.3.0` ya incluye PDF, Ajustes, backup y restore.

Riesgo:

Codex o un colaborador podría tomar decisiones basadas en documentación vieja.

Acción:

Actualizar README, Product Spec, MVP Scope, Business Rules, Data Model, UI Design, Decisions, Release Process y AGENTS.

## Must-fix antes de Firebase

Antes de implementar Firebase Cloud Sync, completar:

1. Guardar esta auditoría en docs.
2. Definir estrategia de identidad y sincronización cloud.
3. Definir comportamiento cloud-safe para restauración.
4. Definir política de Android Auto Backup.
5. Habilitar Room schema export.
6. Centralizar validación de movimientos.
7. Agregar UUID estable a movimientos con migración.
8. Crear backup schema v2 con UUID.

## Orden recomendado de issues

```text
#96 docs: guardar auditoría técnica pre-Firebase
#97 docs: definir estrategia de identidad y sincronización cloud
#102 docs: definir comportamiento cloud-safe para restauración de backups
#103 chore: definir política de Android Auto Backup para datos financieros
#98 chore: habilitar Room schema export antes de migraciones sync
#101 refactor: centralizar validación de reglas de negocio de movimientos
#99 feature: agregar UUID estable a movimientos
#100 feature: crear backup schema v2 con UUID
```

## Criterios de aceptación para v0.3.5

`v0.3.5` debe considerarse lista cuando:

- La documentación post-MVP esté actualizada.
- La auditoría pre-Firebase esté guardada.
- La estrategia cloud sync esté definida antes de implementar Firebase.
- El comportamiento de restore con cloud sync esté definido.
- Room schema export esté preparado antes de migraciones.
- Las reglas de movimiento tengan una validación centralizada o un plan claro.
- UUID esté implementado solo después de estrategia y migración.
- Backup schema v2 esté definido después de UUID.
- No se haya agregado Firebase todavía.

## No objetivos de v0.3.5

No hacer en esta fase:

- Configurar Firebase.
- Crear login.
- Crear Firestore collections.
- Implementar sync engine.
- Resolver conflictos remotos.
- Rediseñar la app.
- Cambiar el flujo principal del usuario.
- Mezclar muchas issues en un PR gigante.

## Nota final

La meta no es avanzar rápido hacia Firebase.

La meta es evitar que Firebase introduzca riesgo sobre datos reales.

```text
Primero base técnica segura.
Luego sync.
```
