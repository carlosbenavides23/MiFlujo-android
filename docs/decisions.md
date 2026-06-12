# Decisions

Este documento registra decisiones importantes del proyecto MiFlujo.

## 001 - Nombre de la app

La app se llama:

```text
MiFlujo
```

## 002 - Subtítulo

El subtítulo visible de la app es:

```text
Flujo de efectivo mensual
```

## 003 - Nombre del repositorio

El repositorio se llama:

```text
MiFlujo-android
```

## 004 - Package name

El package name es:

```text
com.carlos.miflujo
```

## 005 - App local-first

MiFlujo es una app local-first.

Los datos se guardan en el dispositivo usando Room.

No hay nube, backend, login ni sincronización en el MVP.

## 006 - Repositorio público después del MVP

Durante el desarrollo inicial, el repositorio se mantuvo privado.

Después de completar y entregar el MVP, el repositorio pasó a ser público como proyecto real y posible pieza de portafolio.

## 007 - Soporte de córdobas y dólares

MiFlujo soporta movimientos en:

```text
C$
US$
```

Cada movimiento tiene una moneda asociada.

## 008 - No conversión automática entre monedas

No hay conversión automática entre córdobas y dólares.

La app no maneja tipo de cambio.

## 009 - Totales separados por moneda

Los reportes muestran totales separados por moneda.

No se calcula un único flujo neto mezclando C$ y US$.

## 010 - Formato visible de fecha

El formato visible de fecha es:

```text
dd/MM/yy
```

Ejemplo:

```text
05/05/26
```

## 011 - Detalle recomendado, no obligatorio

El detalle de un movimiento es recomendado, pero no obligatorio.

La app permite guardar movimientos sin detalle.

## 012 - Dinero como Long en unidades menores

Los montos se guardan internamente como `Long` en centavos o unidades menores.

No se usa `Double` ni `Float` para almacenar dinero.

Ejemplos:

```text
C$ 1,800.50 -> 180050
US$ 100.00 -> 10000
```

## 013 - Categorías controladas inicialmente

Las categorías están controladas por código.

No se implementan categorías dinámicas todavía.

## 014 - Reportes calculados desde movimientos

El reporte mensual se calcula desde los movimientos guardados.

No se guarda como entidad independiente.

## 015 - Stack visual

Se usa:

```text
Jetpack Compose + Material Design 3
```

## 016 - Persistencia

Se usa Room para persistencia local.

## 017 - Arquitectura

Se usa:

```text
MVVM + Repository Pattern
```

Flujo conceptual:

```text
UI
↓
ViewModel
↓
Repository
↓
Room Database
```

La UI no debe acceder directamente al DAO.

## 018 - Entorno principal de desarrollo

El entorno principal de desarrollo es Linux, específicamente Fedora.

Android Studio es el IDE principal para el proyecto Android.

Git y GitHub se usan para control de versiones, documentación, issues, pull requests y releases.

## 019 - Estrategia de ramas simple

No se usa GitFlow completo.

Estrategia actual:

```text
main      -> releases estables
dev       -> integración de cambios post-MVP
feature/* -> nuevas funcionalidades
fix/*     -> correcciones de bugs
style/*   -> cambios visuales o de UI
chore/*   -> tareas de mantenimiento
docs/*    -> documentación
```

Los cambios deben trabajarse en ramas pequeñas y luego integrarse mediante Pull Request.

## 020 - Release v0.1 MVP

`v0.1.0` representa el primer MVP funcional entregado al usuario principal.

Incluyó:

- Registro de ingresos y egresos.
- Soporte separado para C$ y US$.
- Dashboard del mes actual.
- Historial de movimientos.
- Edición y eliminación.
- Reporte mensual.
- Persistencia local con Room.
- Ícono propio de app.

## 021 - Release v0.1.1 post-MVP

`v0.1.1` fue una release de correcciones y polish después de la primera prueba real.

Incluyó:

- Evitar que el diálogo de agregar/editar movimiento se cierre al tocar fuera.
- Corregir la apariencia del snackbar en modo oscuro.
- Mejorar el modo claro.
- Separar colores de identidad de app y colores financieros semánticos.

## 022 - Dynamic color y colores financieros semánticos

MiFlujo mantiene Android dynamic color para la identidad general de la app cuando está disponible.

Esto aplica a elementos como:

- FAB.
- Botones.
- Navegación.
- Selecciones.
- Componentes Material generales.

Los colores financieros no deben depender completamente del dynamic color.

Regla visual:

```text
Dynamic color -> identidad general de la app
Verde suave   -> ingresos, montos positivos y flujo positivo
Rojo suave    -> egresos, montos negativos y flujo negativo
```

## 023 - APK firmado y keystore

Las releases se distribuyen como APK firmado.

El archivo `.jks` del keystore no debe subirse al repositorio.

Debe guardarse fuera del repo y respaldarse de forma segura.

## 024 - Datos locales y actualización

Room conserva los datos locales cuando se instala una nueva release firmada con el mismo keystore y el mismo `applicationId`, siempre que la app no sea desinstalada.

La actualización de `v0.1.0` a `v0.1.1` fue probada sin pérdida de datos.

## 025 - Versionado de app

Antes de generar cada APK release se debe actualizar:

```kotlin
versionCode
versionName
```

Regla:

- `versionCode` debe aumentar en cada APK release.
- `versionName` debe coincidir con la versión publicada en GitHub Release.

Secuencia actual:

```text
v0.1.0 -> versionCode 1, versionName "0.1.0"
v0.1.1 -> versionCode 2, versionName "0.1.1"
v0.2.0 -> versionCode 3, versionName "0.2.0"
v0.3.0 -> versionCode 4, versionName "0.3.0"
v0.3.5 -> versionCode 5, versionName "0.3.5"
```

## 026 - Post-MVP guiado por feedback real

Después del MVP, las mejoras deben priorizar feedback real del usuario principal.

Regla de trabajo:

```text
Feedback real -> issue pequeña -> rama -> implementación -> revisión -> PR -> dev -> main -> release
```

No se deben agregar features grandes sin validar necesidad real.

## 027 - Exportación PDF de reportes

Se probó generar reportes PDF desde HTML y CSS renderizados en un `WebView`, pero el enfoque fue abandonado.

La implementación se removió porque el renderizado fuera de pantalla de `WebView` produjo PDFs en blanco o recortados de forma no confiable.

También se probó OpenPDF, pero el intento fue abandonado porque la dependencia falló en Android al cargar clases de `java.awt`, específicamente `java.awt.Color`.

No se parcheará `java.awt` ni se usará OpenPDF para la exportación.

La exportación PDF usa `android.graphics.pdf.PdfDocument` nativo de Android.

El diseño del PDF es sobrio, profesional, basado en tablas y similar a una exportación de Excel.

Esta función genera un reporte mensual legible para compartir o revisar fuera de la app. No implementa respaldo, restauración, nube, CSV ni XLSX.

## 028 - Respaldo local JSON

El respaldo manual de datos se exporta como JSON con versión de esquema y todos los movimientos guardados.

El usuario puede guardarlo con el creador de documentos del sistema o compartirlo mediante Android Share Sheet.

Para compartir, el archivo se genera temporalmente en la caché de la app y se expone de forma segura usando `FileProvider`.

El usuario decide dónde guardar o compartir el respaldo. La exportación no implementa cifrado, nube, CSV ni XLSX.

## 029 - Restauración de respaldo local JSON

La restauración lee archivos JSON seleccionados mediante el selector de documentos del sistema.

Antes de pedir confirmación, el archivo completo debe analizarse y validarse contra la versión de esquema, nombre de la app, campos requeridos, enums, fechas, timestamps y reglas de negocio de movimientos. Si un movimiento es inválido, se rechaza el respaldo completo.

Un respaldo válido queda pendiente hasta que el usuario confirme explícitamente que desea reemplazar los movimientos actuales. También se permite confirmar un respaldo válido sin movimientos para limpiar los datos actuales.

Al confirmar, Room elimina los movimientos actuales e inserta todos los movimientos del respaldo dentro de una sola transacción. Se preservan los identificadores positivos y únicos del respaldo. Si cualquier inserción falla, la transacción revierte la eliminación y conserva los datos anteriores.

## 030 - Release v0.3.0 Reportes, Ajustes y Respaldo Local

`v0.3.0` representa una release post-MVP grande y estable.

Incluyó:

- Exportación de reporte mensual a PDF tipo tabla.
- Home simplificado.
- Pantalla de Ajustes.
- Exportación de respaldo local JSON.
- Guardado de respaldo en Archivos.
- Compartir respaldo con otras apps.
- Restauración de respaldo local JSON.
- Validación fuerte antes de restaurar.
- Restauración transaccional en Room.
- Tests unitarios para backup/parser y cálculo mensual.

Esta release dejó la app más seria y preparada para uso real con datos importantes.

## 031 - v0.3.5 Pre-Firebase Technical Baseline

Antes de implementar Firebase Cloud Sync, MiFlujo debe pasar por una fase técnica llamada:

```text
v0.3.5 - Pre-Firebase Technical Baseline
```

Objetivo:

```text
Auditar, limpiar y preparar la base técnica antes de sincronización cloud.
```

Esta fase no debe implementar Firebase, login, Firestore ni sincronización.

Debe reducir riesgo antes de trabajar con datos sincronizados.

## 032 - Firebase como capa opcional futura

Firebase Cloud Sync, si se implementa, debe ser una capa opcional encima de la app local-first.

Room/local sigue siendo la fuente principal para el funcionamiento de la app.

La app debe seguir funcionando:

- sin internet,
- sin cuenta,
- sin backend obligatorio,
- sin Firebase como requisito para registrar o consultar movimientos.

## 033 - `id: Long` es identidad local, no identidad cloud

El `id: Long` actual de `Movement` es una identidad local de Room.

Funciona para:

- persistencia local,
- edición local,
- eliminación local,
- respaldo local por reemplazo completo,
- restauración local validada.

No debe tratarse como identidad global entre dispositivos.

Antes de Firebase Cloud Sync se debe definir y agregar una identidad global estable, probablemente mediante UUID.

## 034 - Backup schema v1 no es cloud-ready

El respaldo JSON actual usa `schemaVersion = 1`.

Este schema preserva los campos actuales del movimiento, incluyendo IDs locales positivos y únicos.

Limitación:

```text
schemaVersion 1 no incluye UUID global.
```

Antes de sincronización cloud se debe diseñar backup schema v2 con identidad global.

## 035 - Restore destructivo local no debe asumirse cloud-safe

La restauración local actual reemplaza todos los movimientos actuales después de validación y confirmación.

Este comportamiento es aceptable mientras la app sea local-first sin cloud sync activa.

Antes de Firebase Cloud Sync se debe definir un comportamiento cloud-safe para restauración.

No se debe asumir que un restore destructivo local puede ejecutarse igual cuando existan datos remotos sincronizados.

## 036 - Documentar antes de cambiar datos para sync

Antes de tocar Room, UUID, backup schema v2 o comportamiento cloud-safe, se deben documentar las decisiones técnicas.

Orden recomendado:

1. Auditoría técnica pre-Firebase.
2. Estrategia de identidad y sincronización cloud.
3. Comportamiento cloud-safe para restauración de backups.
4. Política de Android Auto Backup.
5. Room schema export.
6. Centralización de validación de movimientos.
7. UUID estable.
8. Backup schema v2.

El objetivo es evitar que Firebase se implemente encima de decisiones locales incompletas.

## 037 - Exportar y versionar schemas de Room

Room exporta su schema al directorio versionado del proyecto:

```text
app/schemas/
```

Los archivos generados deben mantenerse en Git para revisar cambios de estructura y preparar migraciones futuras.

Habilitar el export no cambia la versión actual de la base de datos, el modelo `Movement` ni el comportamiento de la app.

## 038 - Validación centralizada de reglas de movimientos

Las reglas de negocio compartidas de `Movement` se validan desde una fuente pura de dominio:

```text
domain/validation/MovementBusinessRuleValidator
```

Esta validación cubre monto positivo y combinaciones válidas de tipo, categoría y subcategoría.

La UI conserva sus validaciones de formato y campos requeridos. El parser de backups conserva la validación estructural, de enums, fechas, timestamps e IDs. Después de esas validaciones, ambos reutilizan las mismas reglas de dominio.

## 039 - UUID estable para movimientos

`Movement` conserva dos identidades:

```text
id: Long     -> identidad local y primary key de Room
uuid: String -> identidad global estable futura
```

Los movimientos nuevos reciben un UUID aleatorio al crearse. Editar un movimiento preserva su UUID.

Room usa schema version 2. La migración `1 -> 2` conserva IDs y datos existentes, asigna un UUID distinto a cada fila y crea un índice único sobre `uuid`.

Backup schema v1 sigue sin incluir UUID. Restaurar un backup v1 genera UUID nuevos. Backup schema v2 exporta y preserva el UUID estable.

## 040 - Backup schema v2 preserva UUID

Los respaldos nuevos usan:

```text
schemaVersion = 2
```

Cada movimiento exportado incluye su UUID estable. La exportación rechaza UUID inválidos o duplicados y nunca genera una identidad distinta durante la serialización.

La importación mantiene compatibilidad con schema v1:

- schema v1 no requiere UUID y genera UUID nuevos al importar;
- schema v2 requiere UUID canónico válido y único;
- schema v2 preserva el UUID al restaurar;
- versiones distintas de 1 y 2 se rechazan.

La restauración continúa reemplazando todos los movimientos locales dentro de una transacción después de validación y confirmación explícita.

## 041 - Plan final de Firebase Cloud Sync v0.4.0

`v0.4.0` será una release estable, no una alpha, con QA fuerte antes de publicación.

Cloud Sync será opcional y local-first:

- Room sigue siendo la fuente local de verdad y la fuente observable para UI.
- Firestore es una capa remota de sincronización.
- La caché offline de Firestore no es fuente de verdad.
- Con Cloud Sync apagado, MiFlujo se comporta como `v0.3.5`, usa `LOCAL_ONLY` y no encola trabajo de sync.

Cloud Sync será individual por cuenta. No habrá cuentas compartidas, espacios familiares, lectura cruzada ni rol administrador con acceso a movimientos ajenos. Carlos no tendrá privilegio de app sobre los datos financieros de otro usuario.

Estructura Firestore:

```text
users/{uid}/movements/{movementUuid}
users/{uid}/metadata/sync
authorizedUsers/{uid}
```

`authorizedUsers/{uid}` habilita Cloud Sync para ese UID, pero no concede acceso a datos de otros usuarios. La app no mostrará datos de contacto del owner. Una cuenta no autorizada podrá copiar su UID y continuar local-only.

Firestore usará UUID como document ID y como campo coincidente. No guardará el `id` local de Room.

Room necesitará `syncStatus`, `lastSyncedAt` y `deletedAt`. Los dos primeros serán local-only y no se guardarán en Firestore; `deletedAt` se sincronizará como tombstone.

`LOCAL_ONLY` será el estado local para movimientos cuando Cloud Sync esté apagado o todavía no fue activado. No se subirá a Firestore. Los estados pendientes solo se usarán con Cloud Sync activo.

Con Cloud Sync activo:

- crear/editar -> `PENDING_UPLOAD`,
- eliminar -> `deletedAt` + `PENDING_DELETE`,
- los tombstones no se limpiarán físicamente en `v0.4.0`,
- los tombstones no aparecerán en UI normal, reportes ni PDF.

La reconciliación usará UUID para identidad y `updatedAt` generado por la app para conflictos. Descargar nunca borrará físicamente filas Room.

Crear backup local siempre estará permitido. Restaurar quedará bloqueado después de
la primera ejecución completada de Cloud Sync en la instalación. Backup schema v1
nunca se restaurará después de esa activación; cualquier soporte futuro requerirá
schema v2 o superior y una política explícita.

Los triggers serán foreground, red recuperada con la app abierta, aproximadamente cada 90 segundos en foreground con pendientes, `Sincronizar ahora` y WorkManager con restricción de red como respaldo. Salir de la app no será trigger y WorkManager periódico no implementará el timer de 90 segundos.

Desactivar Cloud Sync o cerrar sesión no borrará Room ni Firestore, detendrá sync automático y advertirá si existen cambios pendientes.

El plan completo está en:

```text
docs/firebase-cloud-sync-plan.md
```

## 042 - Reglas de seguridad Firestore aisladas por UID

Las reglas versionadas de Firestore viven en:

```text
firestore.rules
```

`firebase.json` configura Firebase CLI para usar ese archivo.

Las reglas niegan acceso por defecto. Cloud Sync requiere autenticación, coincidencia entre `request.auth.uid` y el UID de la ruta, y un documento `authorizedUsers/{uid}` con `enabled = true`.

Cada usuario solo puede acceder a sus propios movimientos y a su documento `users/{uid}/metadata/sync`. Los clientes solo pueden consultar su propio documento `authorizedUsers/{uid}` y no pueden listar ni modificar la allowlist.

Los movimientos remotos aceptan únicamente los campos documentados, excluyen el ID local de Room y la metadata local `syncStatus` y `lastSyncedAt`, exigen que el campo `uuid` coincida con el document ID y preservan `uuid` y `createdAt` durante actualizaciones.

Las reglas representan `date` como texto ISO `YYYY-MM-DD`; `createdAt`, `updatedAt` y `deletedAt` usan timestamps de Firestore. El documento reservado `users/{uid}/metadata/sync` solo acepta `schemaVersion` y `updatedAt`.

La eliminación física de movimientos y metadata está bloqueada. En `v0.4.0`, las eliminaciones sincronizadas deben representarse mediante `deletedAt`.

## 043 - Inicio de sesión y estado de autorización sin activar sync

MiFlujo usa Credential Manager con Google ID tokens y Firebase Auth para identificar la cuenta. Firebase Auth conserva la sesión y expone el usuario actual; no se agrega DataStore porque el proyecto no tenía ese patrón y no existe metadata adicional que deba persistirse para esta issue.

Después de iniciar sesión, la app consulta únicamente:

```text
authorizedUsers/{uid}
```

Si `enabled == true`, Ajustes muestra la cuenta como autorizada. Si el documento falta, no se puede leer o no está habilitado, Ajustes muestra la cuenta como no autorizada, permite copiar el UID y mantiene la app en modo local-only.

Iniciar sesión o resultar autorizado no activa Cloud Sync, no sube movimientos y no descarga movimientos. Cerrar sesión no modifica Room, Firestore, movimientos ni respaldos.

La UI no muestra datos de contacto del owner y el cliente no crea ni modifica documentos `authorizedUsers`.

## 044 - Metadata local de sync en Room sin activar Cloud Sync

Room schema version 3 agrega a cada movimiento:

```text
syncStatus
lastSyncedAt
deletedAt
```

La migración `2 -> 3` conserva todos los movimientos y asigna `LOCAL_ONLY`, `null`
y `null` respectivamente. Los movimientos nuevos y restaurados usan los mismos
valores mientras Cloud Sync no esté activo.

Las consultas normales excluyen filas con `deletedAt`, incluyendo UI, historial,
reportes, PDF y exportación de backup visible. El borrado sigue siendo físico
mientras sync está inactivo; esta issue no crea ni sube tombstones.

Backup schema v2 permanece sin cambios. No exporta `syncStatus`, `lastSyncedAt` ni
`deletedAt` porque son metadata operativa local. Restaurar backups schema v1 o v2
continúa siendo compatible y reinicia esa metadata a sus valores locales por defecto.

Esta preparación no implementa lecturas, escrituras, colas ni reconciliación de
movimientos con Firebase.

## 045 - DTO y mapper remoto de movimientos

El documento futuro `users/{uid}/movements/{movementUuid}` se representa mediante
`RemoteMovementDto` con únicamente los campos permitidos por las reglas Firestore.
No contiene el ID local de Room, `syncStatus` ni `lastSyncedAt`.

La versión inicial del documento remoto es `schemaVersion = 1`. `date` usa texto ISO
`YYYY-MM-DD`; `createdAt`, `updatedAt` y `deletedAt` usan `Firebase Timestamp`.
La conversión entre `LocalDateTime` y `Timestamp` usa UTC y conserva nanosegundos.

El mapper remoto valida UUID canónico, igualdad entre UUID y document ID, versión,
campos requeridos, enums y reglas de negocio. Un DTO remoto válido produce un
`Movement` sin ID Room, con estado local `SYNCED` y sin asignar `lastSyncedAt`.

Esta decisión agrega únicamente el contrato y mapeo puros. No activa lecturas,
escrituras ni sincronización de movimientos con Firestore.

## 046 - Núcleo puro de reconciliación Cloud Sync

`MovementSyncReconciler` recibe movimientos locales, snapshots remotos puros y un
tiempo de sync explícito. Devuelve `SyncReconciliationPlan` con acciones declarativas;
no ejecuta escrituras Room o Firestore.

Las acciones distinguen uploads visibles, uploads de tombstones, inserciones y
actualizaciones locales, marcado synced, errores locales y remotos inválidos.
Los payloads remotos no contienen ID Room, `syncStatus` ni `lastSyncedAt`.

UUID decide identidad y `updatedAt` decide conflictos. Los tombstones ganan empates
contra registros visibles. Si dos registros visibles tienen el mismo `updatedAt`
pero contenido distinto, un local pendiente, local-only o en error gana y solicita
upload; si el local ya estaba `SYNCED`, gana remoto. Esta regla protege cambios
locales todavía no confirmados sin volver a subir registros ya sincronizados.

Aceptar un remoto para una fila local preserva el ID Room y el `createdAt` local,
mantiene tombstones en vez de borrar físicamente y asigna `SYNCED` con el tiempo de
sync recibido. Un remoto nuevo usa ID local `0` hasta que la futura capa de
persistencia lo inserte.

Esta fase no agrega llamadas Firestore, ejecución de planes, scheduler, background
sync, cambios Room, DAO, backup, reglas ni UI.

## 047 - Boundary Firestore remoto de movimientos

`CloudMovementRemoteDataSource` separa el futuro motor de sync del SDK Firestore.
Su implementación concreta recibe UID explícito y opera únicamente sobre:

```text
users/{uid}/movements/{movementUuid}
```

Fetch usa fuente servidor y devuelve cada documento como input válido o inválido
para reconciliación. Un documento mal formado no invalida toda la colección.

Los writes son upserts mediante `set` con `RemoteMovementDto`; el document ID es el
UUID canónico del payload. Movimiento visible exige `deletedAt = null` y tombstone
exige `deletedAt` presente. No se escriben ID Room, `syncStatus` ni `lastSyncedAt`.

El boundary no ofrece delete físico, no modifica `authorizedUsers`, no está
registrado en el app container y no activa sync, UI, startup, WorkManager ni tareas
en background.

## 048 - Motor manual de Cloud Sync

`CloudSyncEngine.syncNow()` coordina una ejecución manual y explícita de Cloud Sync.
Primero exige una sesión autorizada y usa únicamente el UID de esa cuenta para leer
y escribir `users/{uid}/movements`.

El motor captura un tiempo de sync, lee Room incluyendo tombstones, obtiene los
documentos remotos y delega todas las decisiones de conflicto a
`MovementSyncReconciler`. Luego aplica cada acción de forma independiente:

- los uploads usan `CloudMovementRemoteDataSource` y después actualizan solo
  `syncStatus` y `lastSyncedAt` en Room;
- los remotos aceptados se insertan o actualizan como `SYNCED`;
- los tombstones se conservan local y remotamente sin borrado físico;
- los errores por item marcan `SYNC_ERROR` cuando existe una fila local;
- los documentos remotos inválidos se omiten y producen resultado parcial.

Las escrituras remotas usan una transacción Firestore y comparan `updatedAt` contra
el documento actual. Una versión remota más nueva rechaza el write; un tombstone
remoto también gana un empate contra un payload visible. El rechazo produce error
remoto por item y no descarta datos locales.

Las actualizaciones descargadas y los cambios de metadata local usan compare-and-set
transaccional sobre `id`, `uuid`, `updatedAt`, `deletedAt` y `syncStatus`. Si la fila
cambió después de reconciliar o durante un upload, el motor no la sobrescribe ni la
marca `SYNCED`; devuelve resultado parcial para reintentar con un snapshot nuevo.

El delete local conserva borrado físico únicamente para filas `LOCAL_ONLY` sin
historial de sync. Una fila que ya pudo existir remotamente se convierte en
`PENDING_DELETE` con `deletedAt` y permanece en Room, evitando que un documento
remoto anterior la resucite en la siguiente ejecución manual. Antes de iniciar un
write remoto, el motor mueve la fila a estado pendiente mediante compare-and-set;
así, un delete concurrente durante el primer upload también crea tombstone.

`CloudSyncResult` distingue éxito, resultado parcial, sesión cerrada, cuenta no
autorizada y fallo general, con conteos sin datos financieros.

El motor queda registrado en el app container, pero ninguna UI ni lifecycle lo
invoca automáticamente. Esta decisión no agrega scheduler, WorkManager, sync al
inicio, indicador Home, cambios de backup ni cambios de reglas Firestore.

## 049 - Control manual de Cloud Sync en Ajustes

Ajustes muestra la acción explícita `Sincronizar ahora` únicamente para una cuenta
autorizada. `SettingsViewModel` invoca `CloudSyncEngine` mediante `CloudSyncRunner`;
los composables solo reciben estado y callbacks.

Un state holder pequeño representa `idle`, ejecución, éxito, resultado parcial,
sesión cerrada, cuenta no autorizada y fallo. Durante la ejecución bloquea llamadas
repetidas y la UI deshabilita el botón. Los resultados muestran conteos operativos
sin detalles financieros.

La sincronización solo inicia al tocar el botón. Esta decisión no agrega ejecución
en startup, cierre, foreground, login, scheduler, WorkManager, background,
notificaciones ni indicador Home.

## 050 - Inicio de sesión legacy compatible con canary

La acción explícita de inicio de sesión en Ajustes usa temporalmente
`GoogleSignInClient` directamente. Credential Manager queda fuera de este botón
porque cancelaba antes de devolver credenciales y un canary aislado confirmó que
el flujo legacy funciona con la misma app Firebase, package, firma y web client ID.

El cliente legacy se crea con la Activity que posee la UI, usa el web client ID
generado como `default_web_client_id`, solicita ID token y email, y procesa una
sola vez el Intent devuelto por Google. El launcher solo devuelve el ID token a la
capa de datos; la conversión a credencial Firebase,
`FirebaseAuth.signInWithCredential`, la verificación de autorización y el refresh
del estado de Ajustes permanecen compartidos.

Cancelar el fallback mantiene la sesión sin completar y muestra feedback visible
de cancelación. Recibir un resultado sin ID token muestra un fallo seguro distinto.
El código legacy `10` (`DEVELOPER_ERROR`) muestra feedback de configuración sin
exponer client IDs, tokens ni datos de cuenta en logs.
El flujo legacy nunca se inicia por lifecycle ni se relanza automáticamente.
Credential Manager puede reevaluarse después, pero no debe bloquear el camino
compatibilidad confirmado. Esta decisión no cambia sync, scheduler, Room, reglas
Firestore, backup, restore ni comportamiento de movimientos.

Cerrar sesión limpia primero la cuenta seleccionada por `GoogleSignInClient` desde
la capa UI propietaria de la Activity. Después, aunque esa limpieza legacy falle,
el ViewModel continúa con el cierre de Firebase Auth y la limpieza del estado de
Credential Manager mediante el repositorio. Ninguna referencia a Activity se
guarda en el ViewModel.

## 051 - Bloqueo persistente de restore después de activar Cloud Sync

La primera ejecución manual de Cloud Sync que termine con `SUCCESS` o `PARTIAL`
guarda un flag local persistente en preferencias. `SIGNED_OUT`, `UNAUTHORIZED` y
`FAILURE` no activan el flag. El flag sobrevive reinicios y cierre de sesión, no se
sincroniza con Firestore y no usa Room.

Después de activar el flag, Ajustes mantiene disponible la creación de backups pero
bloquea la restauración local destructiva. Esto evita que un restore reemplace Room
sin tombstones y que una sincronización posterior mezcle o resucite registros
remotos.

Mientras una sincronización manual está ejecutándose, Ajustes deshabilita y el
ViewModel rechaza defensivamente inicio de sesión, cierre de sesión y refresh de
autorización. No se cancela la sincronización en curso.
