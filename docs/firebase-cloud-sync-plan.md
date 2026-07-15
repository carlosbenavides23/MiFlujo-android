# Firebase Cloud Sync v0.4.0

## Proposito

Este documento define el diseño y el estado de implementación de Firebase Cloud Sync para `v0.4.0`.

Corresponde a:

```text
#115 docs: define v0.4.0 Firebase Cloud Sync plan
```

La planificación inicial no implementaba Firebase, login, Firestore, reglas de seguridad, motor de sync, cambios Room ni cambios de backup schema. Esas piezas ya están integradas en `main` como trabajo de `v0.4.0`, que aún no es una release estable.

Estado actual en `main`:

- Firebase Auth con inicio de sesión Google y autorización por UID.
- Reglas de Firestore y data source remoto de movimientos.
- Motor de reconciliación y sincronización opcional.
- Control manual, activación persistente y preferencia local para Cloud Sync.
- Disparadores al volver a primer plano y recuperar conectividad, más WorkManager como respaldo de cambios pendientes.
- Room sigue siendo la fuente observable para la UI; Cloud Sync no es obligatorio.

## Tipo de release

`v0.4.0` sera una release estable, no una alpha.

Por manejar datos financieros y sincronizacion remota, requiere QA fuerte antes de publicarse:

- pruebas unitarias para reglas de sync y reconciliacion,
- pruebas manuales con red disponible, sin red y red recuperada,
- pruebas de activacion con datos locales, remotos y ambos lados con datos,
- pruebas de cuenta no autorizada,
- pruebas de cierre de sesion con cambios pendientes,
- verificacion de que Room no pierde datos ante errores.

## Principios

Cloud Sync en MiFlujo debe ser:

- opcional,
- local-first,
- individual por cuenta,
- privado/controlado,
- una capa de sincronizacion sobre Room.

Reglas base:

- Room sigue siendo la fuente local de verdad.
- La UI observa Room, no Firestore directamente.
- Firestore es una capa remota de sync, no la fuente observable principal para UI.
- La cache offline de Firestore no debe tratarse como fuente de verdad de la app.
- Con Cloud Sync apagado, MiFlujo se comporta como `v0.3.5`.
- Con Cloud Sync apagado, los movimientos usan `LOCAL_ONLY` y no se encola trabajo de sync.
- Los estados pendientes de sync solo se usan cuando Cloud Sync esta activo.

## No objetivos

`v0.4.0` no debe incluir:

- cuentas compartidas,
- espacios familiares o compartidos,
- roles administrativos que lean movimientos de otros usuarios,
- acceso cruzado entre usuarios,
- merge avanzado de respaldos,
- restore destructivo con Cloud Sync activo,
- limpieza fisica de tombstones,
- usar Firestore como reemplazo de Room.

Carlos no tendra privilegio de app para leer datos financieros de otro usuario.

## Autorizacion y privacidad

Cloud Sync requiere UID autorizado.

La estructura `authorizedUsers/{uid}` permite usar Cloud Sync, pero no otorga acceso cruzado a datos de otros usuarios.

Reglas:

- Cada usuario solo puede leer y escribir bajo su propio UID.
- El UID autenticado decide identidad y autorizacion.
- El correo puede ayudar al usuario a reconocer su sesion, pero no autoriza por si solo.
- La app no debe mostrar datos de contacto del owner o administrador.
- Si una cuenta no esta autorizada, el usuario puede copiar su UID para compartirlo por fuera de la app.
- Un usuario no autorizado debe seguir usando MiFlujo en modo local-only.
- La falta de autorizacion no borra Room ni bloquea backup local.

## Firestore

Estructura remota:

```text
users/{uid}/movements/{movementUuid}
users/{uid}/metadata/sync
authorizedUsers/{uid}
```

`users/{uid}/movements/{movementUuid}` guarda movimientos del usuario.

`users/{uid}/metadata/sync` queda reservado para metadata remota de coordinacion de sync del usuario. No guarda movimientos.

`authorizedUsers/{uid}` habilita el uso de Cloud Sync para ese UID. No concede lectura ni escritura sobre `users/{otherUid}`.

## Documento remoto de movimiento

El documento Firestore de un movimiento usa el UUID como document ID.

Reglas:

- `movementUuid` es el `uuid` del movimiento.
- Firestore no guarda el `id` local de Room.
- Firestore guarda `uuid` tambien como campo.
- El campo `uuid` debe coincidir con el document ID.
- Reportes, PDFs y archivos JSON de backup no se sincronizan como documentos remotos.

Campos remotos:

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

`deletedAt` representa borrado logico/tombstone y se sincroniza.

## Boundary remoto de movimientos

`CloudMovementRemoteDataSource` define únicamente:

- leer todos los documentos bajo `users/{uid}/movements`,
- upsert de movimiento visible,
- upsert de tombstone.

`FirestoreCloudMovementRemoteDataSource` requiere el UID explícitamente en cada
operación. Lee desde servidor y usa `movement.uuid` como document ID. Los upserts
usan transacción: leen la versión remota actual y rechazan un payload con
`updatedAt` anterior. Un tombstone remoto también gana un empate contra un payload
visible. No expone delete físico, no consulta `authorizedUsers` y no infiere otro UID.

Cada documento leído se convierte de forma independiente. Un documento inválido
produce `RemoteMovementInput.Invalid` y no cancela el mapeo de los demás documentos.
La capa no ejecuta reconciliación ni se conecta todavía con UI, startup, scheduler
o background sync.

## Modelo local para sync

Room necesitara metadata local de sincronizacion:

```text
syncStatus
lastSyncedAt
deletedAt
```

Reglas:

- `syncStatus` es local-only.
- `lastSyncedAt` es local-only.
- Firestore no guarda `syncStatus` ni `lastSyncedAt`.
- `LOCAL_ONLY` no se sube a Firestore porque es metadata local.
- `deletedAt` se sincroniza porque representa soft delete/tombstone.
- `createdAt` nunca cambia.
- `updatedAt` cambia al editar y al hacer soft delete.
- `deletedAt` marca eliminacion logica.

Estados locales esperados:

```text
LOCAL_ONLY
SYNCED
PENDING_UPLOAD
PENDING_DELETE
SYNC_ERROR
```

`LOCAL_ONLY` se usa cuando Cloud Sync esta apagado, aun no fue activado o la app se comporta como `v0.3.5`. Los estados pendientes solo se usan cuando Cloud Sync esta activo.

La implementacion puede agregar estados auxiliares si los necesita, pero no debe cambiar las reglas de negocio descritas aqui.

## Cambios locales con Cloud Sync activo

Con Cloud Sync activo:

- crear movimiento -> `PENDING_UPLOAD`,
- editar movimiento -> `PENDING_UPLOAD`,
- eliminar movimiento -> asignar `deletedAt` y `PENDING_DELETE`.

La eliminacion con Cloud Sync activo usa soft delete/tombstone.

Para impedir resurrecciones entre ejecuciones manuales, una fila que ya tiene
metadata de sync también usa tombstone al eliminarse aunque no exista sync
automático activo. Solo una fila `LOCAL_ONLY` nunca sincronizada conserva el
borrado físico local. El motor cambia la fila a estado pendiente antes del write
remoto, cerrando la carrera entre el primer upload y un delete local concurrente.

Los tombstones:

- no se limpian fisicamente en `v0.4.0`,
- se ocultan de la UI normal,
- se excluyen de reportes,
- se excluyen del PDF.

La descarga desde Firestore nunca debe borrar fisicamente filas de Room.

## Activacion inicial

Activar Cloud Sync debe ser explicito.

Casos:

### Remoto vacio y datos locales

Si Firestore esta vacio para el usuario y Room tiene movimientos, la app sube los datos locales inmediatamente despues de la activacion explicita.

### Local vacio y datos remotos

Si Room no tiene movimientos y Firestore si tiene datos, la app descarga los datos remotos.

### Local y remoto tienen datos

Si ambos lados tienen datos, la app debe pedir confirmacion antes de combinar.

Reglas de combinacion:

- combinar por UUID,
- no borrar automaticamente,
- no reemplazar todo un lado sin confirmacion,
- no crear duplicados con el mismo UUID.

## Reconciliacion

Reglas:

- El UUID decide identidad.
- `updatedAt` generado por la app decide conflictos.
- Si un remoto no existe localmente, se inserta en Room.
- Si el mismo UUID existe local y remoto, gana el movimiento con `updatedAt` mas reciente.
- Si remoto tiene `deletedAt`, se guarda tombstone local.
- Download nunca borra fisicamente filas de Room.
- `createdAt` no cambia durante reconciliacion.
- `updatedAt` no debe regenerarse durante download si se esta aceptando el valor remoto.

La capa pura `MovementSyncReconciler` no escribe Room ni Firestore. Recibe snapshots
locales y remotos, además de un tiempo de sync explícito, y devuelve un plan
inspeccionable con acciones de upload, inserción/actualización local, marcado synced
y errores por item.

Reglas adicionales:

- Un tombstone remoto con `updatedAt` mayor o igual al local gana contra un local visible.
- Un tombstone local con `updatedAt` mayor o igual al remoto gana contra un remoto visible.
- Si los timestamps son iguales y los datos remotos son equivalentes, se marca el
  movimiento local como synced.
- Si los timestamps son iguales, ambos son visibles y los datos difieren, un local
  pendiente, local-only o en error gana y solicita upload. Si el local ya estaba
  `SYNCED`, gana remoto para no subir un registro sin cambios locales pendientes.
- Datos remotos inválidos se omiten mediante una acción de error y no detienen el
  resto del plan.
- `PENDING_DELETE` sin `deletedAt` produce error local por item.
- Los payloads de upload no contienen ID Room, `syncStatus` ni `lastSyncedAt`.

## Triggers de sync

Cloud Sync puede ejecutarse cuando:

- la app entra a foreground,
- se recupera internet mientras la app esta abierta,
- aproximadamente cada 90 segundos mientras la app esta en foreground y hay cambios pendientes,
- el usuario toca `Sincronizar ahora`,
- WorkManager ejecuta respaldo de cambios pendientes con restriccion de red.

No usar:

- salir de la app como trigger de sync,
- WorkManager periodic work para el timer foreground de 90 segundos.

WorkManager debe ser respaldo para cambios pendientes, no reemplazo del ciclo foreground controlado por la app.

## Backup y restore local

Crear backup local siempre esta permitido.

Reglas:

- Crear backup no requiere login.
- Crear backup no modifica Firestore.
- Crear backup no sube archivos a Firebase.
- Crear backup debe seguir disponible con Cloud Sync encendido o apagado.

Restaurar backup local queda bloqueado mientras Cloud Sync esta activo.

Reglas:

- Backup schema v1 nunca debe restaurarse con Cloud Sync activo.
- Si restore con Cloud Sync se soporta en el futuro, solo schema v2 o superior puede considerarse.
- Un restore futuro con Cloud Sync requerira politica explicita de merge/restore.
- `v0.4.0` no implementa merge avanzado ni reemplazo remoto desde backup.

## Errores

Reglas de error:

- Los errores de sync nunca borran datos locales.
- Sin internet conserva cambios pendientes.
- Usuario no autorizado detiene escrituras cloud, pero la app local continua.
- Errores de reglas o datos pueden marcar movimientos como `SYNC_ERROR`.
- Fallos parciales por lote mantienen los items exitosos como sincronizados y los fallidos como pendientes o error.
- La UI debe explicar el problema sin bloquear el uso local.

## Desactivar Cloud Sync y cerrar sesion

Desactivar Cloud Sync o cerrar sesion:

- no borra Room,
- no borra Firestore,
- detiene sync automatico,
- debe advertir si hay cambios pendientes.

Cerrar sesion no equivale a borrar datos locales.

## QA minimo para v0.4.0

Antes de publicar `v0.4.0`, validar al menos:

- activar sync con remoto vacio y local con datos,
- activar sync con local vacio y remoto con datos,
- activar sync con datos en ambos lados y confirmacion de combinacion,
- crear, editar y eliminar con Cloud Sync activo,
- ocultar tombstones en UI, reportes y PDF,
- mantener tombstones en Room,
- perder internet y recuperar internet,
- usar `Sincronizar ahora`,
- WorkManager sube cambios pendientes con red,
- cuenta no autorizada conserva modo local-only,
- restore queda bloqueado con Cloud Sync activo,
- cerrar sesion no borra datos,
- errores parciales no borran datos locales.

## Decision final

`v0.4.0` implementara Cloud Sync como una capacidad estable, opcional y local-first.

Room seguira siendo la fuente local de verdad.

Firestore sera una capa remota de sincronizacion por UID autorizado.

La privacidad se mantiene por usuario individual, sin cuentas compartidas, sin espacios familiares y sin privilegios de lectura cruzada.

La eliminacion sincronizada usara tombstones.

La restauracion de backups locales estara bloqueada mientras Cloud Sync este activo.
