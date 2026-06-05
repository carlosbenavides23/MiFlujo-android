# Estrategia de identidad y sincronización cloud

## Propósito

Este documento define la estrategia conceptual para una futura sincronización cloud en MiFlujo.

Corresponde a:

```text
#97 docs: definir estrategia de identidad y sincronización cloud
```

Esta estrategia forma parte de:

```text
v0.3.5 - Pre-Firebase Technical Baseline
```

Este documento no implementa Firebase, login, Firestore ni sincronización. Solo define las decisiones base para evitar pérdida, duplicación o corrupción de datos cuando se implemente cloud sync en una versión futura.

## Principio principal

```text
Room primero. Firebase después.
```

MiFlujo debe seguir funcionando como app local-first.

La sincronización cloud futura debe ser una capa opcional encima de Room, no el corazón de la app.

La app debe seguir funcionando:

- sin internet,
- sin login obligatorio,
- sin backend como requisito para registrar movimientos,
- sin Firebase como fuente única de verdad,
- sin mezclar monedas.

## Objetivo de Firebase Cloud Sync futura

Firebase Cloud Sync debe servir para:

- respaldar datos fuera del dispositivo,
- permitir recuperación si se pierde o cambia el teléfono,
- eventualmente permitir sincronización entre dispositivos del mismo usuario,
- reducir riesgo de pérdida de datos.

No debe convertir MiFlujo en:

- app social,
- app multiusuario empresarial,
- app contable avanzada,
- app bancaria,
- sistema de facturación,
- app dependiente de internet.

## Alcance inicial recomendado para cloud sync

La primera versión de Firebase Cloud Sync debe ser mínima y conservadora.

Alcance recomendado:

- Sincronizar movimientos del usuario autenticado.
- Mantener Room como cache/base local.
- Subir movimientos locales después de activar sync.
- Descargar movimientos remotos del mismo usuario.
- Evitar duplicados usando identidad global estable.
- No sincronizar reportes calculados.
- No sincronizar PDFs.
- No sincronizar archivos JSON de backup.

No incluir inicialmente:

- Colaboradores.
- Multiempresa.
- Permisos avanzados.
- Sync compartido entre usuarios.
- Merge manual complejo.
- Historial completo de conflictos.
- Auditoría contable formal.

## Identidad local vs identidad global

### Identidad local actual

Actualmente `Movement` usa:

```text
id: Long
```

Ese `id` funciona como identificador local de Room.

Sirve para:

- edición local,
- eliminación local,
- restauración local por reemplazo completo,
- ordenamiento y persistencia local.

Pero no debe usarse como identidad cloud.

Problema:

```text
Dos dispositivos pueden crear movimientos distintos con el mismo id local.
```

### Identidad global requerida

Antes de Firebase Cloud Sync, cada movimiento debe tener una identidad global estable.

Dirección esperada:

```text
Movement
- id: Long local Room id
- uuid: String global stable id
```

`uuid` debe ser:

- único globalmente,
- estable durante toda la vida del movimiento,
- generado al crear movimientos nuevos,
- asignado a movimientos existentes mediante migración,
- exportado en backup schema v2,
- usado como clave lógica para sincronización cloud.

## Reglas para UUID

Reglas esperadas:

- Todo movimiento nuevo debe tener UUID al crearse.
- Todo movimiento existente debe recibir UUID mediante migración Room.
- El UUID no debe cambiar al editar un movimiento.
- El UUID no debe depender del `id` local.
- El UUID no debe depender de fecha, monto o detalle.
- El UUID debe preservarse al exportar/importar backup schema v2.
- El UUID debe ser la identidad usada para deduplicación cloud.

No hacer:

- No usar `id: Long` como documento Firestore.
- No generar UUID diferente cada vez que se exporta.
- No regenerar UUID al editar.
- No derivar UUID de campos editables.

## Modelo conceptual futuro

Movimiento local:

```text
id: Long                // identidad local Room
uuid: String            // identidad global estable
type: MovementType
amountMinor: Long
currency: Currency
date: LocalDate
category: MovementCategory
subcategory: MovementSubcategory?
detail: String?
createdAt: LocalDateTime
updatedAt: LocalDateTime
syncState: SyncState?   // futuro, si se necesita
lastSyncedAt: DateTime? // futuro, si se necesita
```

No todos los campos futuros deben agregarse en el primer cambio. El cambio obligatorio antes de cloud sync es `uuid`.

## Firestore conceptual

La estructura remota debe aislar datos por usuario.

Estructura conceptual posible:

```text
users/{userId}/movements/{movementUuid}
```

Reglas conceptuales:

- Cada usuario solo accede a sus propios movimientos.
- El documento remoto de un movimiento usa `movementUuid` como identificador.
- No se guarda un reporte mensual como entidad remota independiente.
- Los reportes se recalculan desde movimientos sincronizados.
- No se mezclan monedas en un total remoto.

Campos remotos conceptuales:

```text
uuid
type
amountMinor
currency
date
category
subcategory
detail
createdAt
updatedAt
deletedAt?       // si se implementa soft delete
schemaVersion?
```

## Borrado y sincronización

La eliminación local actual puede borrar un movimiento de Room.

Para cloud sync, se debe decidir si se usará:

1. borrado físico remoto,
2. soft delete con `deletedAt`,
3. cola de operaciones pendientes.

Recomendación inicial:

```text
Usar soft delete o una estrategia explícita antes de multi-device sync real.
```

Razón:

Si un dispositivo elimina un movimiento offline y otro dispositivo lo edita, se necesita una regla clara.

No implementar borrado cloud sin estrategia de conflicto.

## Conflictos

La primera estrategia debe ser simple.

Opción mínima posible:

```text
Last-write-wins por updatedAt controlado por la app.
```

Pero esta opción tiene riesgos:

- Puede perder cambios si dos dispositivos editan el mismo movimiento offline.
- Requiere timestamps confiables.
- Requiere definir si se usa hora local, servidor o ambos.

Recomendación para primera versión:

- Evitar edición multi-dispositivo compleja inicialmente.
- Documentar que el último cambio sincronizado puede ganar.
- Preferir comportamiento simple y observable.
- No prometer merge perfecto.

Antes de implementar conflictos, definir:

- Qué campo decide el ganador.
- Qué pasa si `updatedAt` es igual.
- Qué pasa si un movimiento fue eliminado en un dispositivo y editado en otro.
- Qué feedback ve el usuario si hay error de sync.

## Backup local y cloud sync

PDF, backup JSON y cloud sync son conceptos distintos.

PDF:

```text
Documento humano para leer o compartir.
```

Backup JSON:

```text
Archivo técnico manual para exportar/restaurar datos.
```

Cloud Sync:

```text
Copia remota opcional para respaldo/sincronización.
```

No mezclar responsabilidades.

## Backup schema v1

El backup actual usa:

```text
schemaVersion = 1
```

Este schema no incluye UUID global.

Sirve para restauración local por reemplazo completo.

Limitación:

```text
No es cloud-ready.
```

## Backup schema v2

Después de agregar UUID, se debe crear `schemaVersion = 2`.

Debe incluir:

- uuid,
- id local si todavía se necesita para restore local,
- campos actuales del movimiento,
- createdAt,
- updatedAt,
- schemaVersion.

Reglas esperadas:

- Exportar v2 por defecto después de implementar UUID.
- Mantener importación de v1 por compatibilidad.
- Al importar v1 en una app con UUID, generar UUID nuevos durante importación.
- Al importar v2, preservar UUID.

No implementar schema v2 antes de agregar UUID.

## Restauración y cloud sync

La restauración local actual reemplaza todos los movimientos después de validación y confirmación.

Ese comportamiento no debe asumirse cloud-safe.

Riesgo:

```text
Un restore local podría convertirse en una eliminación masiva remota.
```

Antes de activar cloud sync, debe existir un documento específico de restore cloud-safe.

Opciones a evaluar en `#102`:

- Desactivar restore cuando cloud sync esté activo.
- Permitir restore solo si sync está apagado.
- Restaurar localmente y pedir confirmación para subir cambios.
- Implementar import/merge no destructivo.
- Implementar reemplazo remoto completo con confirmación extrema.

No decidir implementación final aquí. Este documento solo define que restore debe tratarse como riesgo crítico de sincronización.

## Auth futura

Firebase Cloud Sync probablemente requerirá autenticación.

Pero la autenticación no debe ser obligatoria para usar la app local.

Reglas conceptuales:

- El usuario puede usar la app sin cuenta.
- El usuario puede activar sync opcionalmente.
- Si no inicia sesión, Room sigue funcionando.
- Si cierra sesión, debe definirse qué pasa con datos locales.
- No borrar datos locales automáticamente al cerrar sesión sin confirmación explícita.

## Estados de sync futuros

Si se implementa sync real, puede ser útil representar estados internos.

Ejemplos conceptuales:

```text
LOCAL_ONLY
PENDING_UPLOAD
SYNCED
PENDING_DELETE
SYNC_ERROR
```

No agregar estos estados todavía si no son necesarios para el primer paso.

## Secuencia recomendada antes de Firebase

Orden técnico recomendado:

1. Definir estrategia de identidad y sincronización cloud. // este documento
2. Definir restore cloud-safe.
3. Definir política de Android Auto Backup.
4. Habilitar Room schema export.
5. Centralizar validación de movimientos.
6. Agregar UUID estable con migración.
7. Crear backup schema v2 con UUID.
8. Diseñar Firebase Auth opcional.
9. Diseñar estructura Firestore y reglas de seguridad.
10. Implementar sync mínima.

## No objetivos de este documento

Este documento no implementa:

- Firebase.
- Login.
- Firestore.
- Reglas de seguridad.
- Sync engine.
- Migración Room.
- UUID en código.
- Backup schema v2 en código.
- Restore cloud-safe.

## Criterios de aceptación para #97

La issue `#97` se considera lista cuando:

- Existe una estrategia escrita de identidad local vs global.
- Se define que `id: Long` no es identidad cloud.
- Se define que cada movimiento debe tener UUID estable antes de sync.
- Se define una estructura conceptual de Firestore.
- Se define que Room sigue siendo fuente local principal.
- Se separan claramente PDF, backup JSON y cloud sync.
- Se documenta que backup schema v1 no es cloud-ready.
- Se documenta que backup schema v2 depende de UUID.
- Se documenta que restore cloud-safe se resolverá en issue separada.
- No se implementa Firebase.
- No se toca código.

## Decisión final

Firebase Cloud Sync será una capacidad opcional futura.

La identidad cloud de movimientos no será `id: Long`.

La identidad cloud de movimientos debe ser un UUID estable.

Room seguirá siendo la base local de la app.

La sincronización debe respetar la simplicidad del producto y no convertir MiFlujo en una app financiera compleja.
