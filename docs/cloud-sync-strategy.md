# Estrategia de identidad y sincronización cloud

## Propósito

Este documento registra la estrategia conceptual adoptada para Cloud Sync en MiFlujo.

Corresponde a:

```text
#97 docs: definir estrategia de identidad y sincronización cloud
```

Esta estrategia forma parte de:

```text
v0.3.5 - Pre-Firebase Technical Baseline
```

En su issue original, este documento no implementaba Firebase, login, Firestore ni sincronización. Define las decisiones base para evitar pérdida, duplicación o corrupción de datos.

La implementación de esas decisiones se publicó en `v0.4.0`. Cloud Sync sigue
siendo opcional, local-first y no obligatorio para usar MiFlujo.

El plan final de implementación para `v0.4.0` está en:

```text
docs/firebase-cloud-sync-plan.md
```

Si una opción exploratoria de este documento difiere del plan `v0.4.0`, prevalece el plan final.

## Principio principal

```text
Room primero. Firebase después.
```

MiFlujo debe seguir funcionando como app local-first.

La sincronización cloud debe ser una capa opcional encima de Room, no el corazón de la app.

La app debe seguir funcionando:

- sin internet,
- sin login obligatorio,
- sin backend como requisito para registrar movimientos,
- sin Firebase como fuente única de verdad,
- sin mezclar monedas.

## Objetivo de Firebase Cloud Sync

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
- Mantener Room como fuente local de verdad y fuente observable para UI.
- Usar Firestore solo como capa remota de sincronización.
- No tratar la caché offline de Firestore como fuente de verdad.
- Subir movimientos locales después de activar sync.
- Descargar movimientos remotos del mismo usuario.
- Evitar duplicados usando identidad global estable.
- No sincronizar reportes calculados.
- No sincronizar PDFs.
- No sincronizar archivos JSON de backup.

No incluir inicialmente:

- Colaboradores.
- Cuentas compartidas.
- Espacios familiares o compartidos.
- Rol administrador con acceso a movimientos de otros usuarios.
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
syncStatus: SyncStatus? // local-only
lastSyncedAt: DateTime? // local-only
deletedAt: DateTime?    // tombstone sincronizado
```

`syncStatus` y `lastSyncedAt` no se envían ni se guardan en Firestore. `LOCAL_ONLY` es metadata local. `deletedAt` sí se sincroniza porque representa una eliminación lógica.

## Firestore conceptual

La estructura remota debe aislar datos por usuario.

Estructura definida para `v0.4.0`:

```text
users/{uid}/movements/{movementUuid}
users/{uid}/metadata/sync
authorizedUsers/{uid}
```

Reglas conceptuales:

- Cada usuario solo accede a sus propios movimientos.
- El documento remoto de un movimiento usa `movementUuid` como identificador.
- Firestore no guarda el `id` local de Room.
- El campo remoto `uuid` debe coincidir con el document ID.
- `authorizedUsers/{uid}` permite usar Cloud Sync, pero no concede acceso cruzado.
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
deletedAt
schemaVersion
```

## Borrado y sincronización

Con Cloud Sync activo, `v0.4.0` usa soft delete:

```text
delete -> deletedAt + PENDING_DELETE
```

Los tombstones no se limpian físicamente en `v0.4.0`. Se mantienen en Room y Firestore, pero se ocultan de la UI normal y se excluyen de reportes y PDF.

## Conflictos

La reconciliación de `v0.4.0` usa:

```text
UUID para identidad.
updatedAt generado por la app para decidir conflictos.
```

Si el mismo UUID existe en ambos lados, gana el `updatedAt` más reciente. Un remoto ausente localmente se inserta. Un remoto con `deletedAt` se guarda como tombstone local. Descargar nunca elimina físicamente filas de Room.

`createdAt` nunca cambia. `updatedAt` cambia al editar y al hacer soft delete.

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

Los respaldos históricos usan:

```text
schemaVersion = 1
```

Este schema no incluye UUID global y sigue soportado para importación local.

Sirve para restauración local por reemplazo completo.

Limitación:

```text
No es cloud-ready.
```

## Backup schema v2

Los respaldos nuevos usan `schemaVersion = 2`.

Debe incluir:

- uuid,
- id local si todavía se necesita para restore local,
- campos actuales del movimiento,
- createdAt,
- updatedAt,
- schemaVersion.

Reglas:

- Exportar v2 por defecto.
- Mantener importación de v1 por compatibilidad.
- Al importar v1 en una app con UUID, generar UUID nuevos durante importación.
- Al importar v2, preservar UUID.

## Restauración y cloud sync

La restauración local actual reemplaza todos los movimientos después de validación y confirmación.

Ese comportamiento no debe asumirse cloud-safe.

Riesgo:

```text
Un restore local podría convertirse en una eliminación masiva remota.
```

Para `v0.4.0`, crear backup local siempre está permitido y restaurar backup local queda bloqueado mientras Cloud Sync está activo.

Backup schema v1 nunca debe restaurarse con Cloud Sync activo. Un restore cloud futuro solo podrá considerar schema v2 o superior y requerirá una política explícita de merge/restore.

## Auth futura

Firebase Cloud Sync probablemente requerirá autenticación.

Pero la autenticación no debe ser obligatoria para usar la app local.

Reglas definidas:

- El usuario puede usar la app sin cuenta.
- El usuario puede activar sync opcionalmente.
- Si no inicia sesión, Room sigue funcionando.
- Cloud Sync requiere UID autorizado.
- Una cuenta no autorizada puede copiar su UID y continuar local-only.
- La app no muestra datos de contacto del owner.
- Carlos no tiene privilegio de app para leer movimientos de otro usuario.
- Cerrar sesión no borra Room ni Firestore.
- Cerrar sesión detiene sync automático y advierte si existen cambios pendientes.

## Estados de sync futuros

Estados locales definidos:

```text
LOCAL_ONLY
PENDING_UPLOAD
SYNCED
PENDING_DELETE
SYNC_ERROR
```

`LOCAL_ONLY` corresponde a movimientos cuando Cloud Sync está apagado, todavía no fue activado o MiFlujo se comporta como `v0.3.5`.

Con Cloud Sync apagado no se encola trabajo de sync. Los estados pendientes solo se usan cuando Cloud Sync está activo.

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

Firebase Cloud Sync es una capacidad opcional.

La identidad cloud de movimientos no será `id: Long`.

La identidad cloud de movimientos debe ser un UUID estable.

Room seguirá siendo la base local de la app.

La sincronización debe respetar la simplicidad del producto y no convertir MiFlujo en una app financiera compleja.
