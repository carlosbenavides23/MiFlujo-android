# Contrato remoto del catálogo · Iteración 4A

## Alcance y auditoría del estado actual

Esta entrega prepara DTO, codificación de documentos, reglas y funciones puras. La app usa
el contrato remoto de movimientos v1, el respaldo de producción v2 y Room principal v3.

- `RemoteMovementDto`, `RemoteMovementMappers` y `MovementRemoteSnapshot` esperan enums de
  clasificación y versión **exactamente 1**. Las reglas de movimientos exigen la forma exacta
  del payload y las combinaciones heredadas. El chequeo de `schemaVersion` en esas reglas
  acepta cualquier entero positivo con la forma antigua, así que el número de versión por sí
  solo no protege una transición. Añadir campos UUID a la forma actual tampoco es compatible.
- `FirestoreCloudMovementRemoteDataSource` lee desde servidor y escribe con `transaction.set`
  del documento completo. La transacción comprueba identidad y vigencia y conserva `createdAt`.
  El reconciliador compara por UUID y `updatedAt`, favorece tombstone en empate contra una
  fila visible y distingue equivalencia y estado local para resolver otros empates. Un documento
  inválido bloquea su UUID en el plan. Los tombstones usan `deletedAt` y la regla deniega borrado físico.
  El catálogo usa un reloj lógico propio.
- `CloudSyncEngine` usa el UID devuelto por la cuenta autorizada y serializa corridas con un
  mutex. Las reglas exigen `request.auth.uid` igual al UID de la ruta y
  `authorizedUsers/{uid}.enabled == true`. Las reglas de Firestore aíslan datos del servidor por UID.
  La base del catálogo experimental es local a la instalación. Antes de conectarla, 4B debe ligar
  su snapshot, pendientes y confirmaciones al UID activo y evitar que el catálogo de una cuenta se
  envíe bajo otra al cambiar de sesión.
- `CloudSyncBackupWorkScheduler` programa trabajo único con WorkManager. El coordinador considera
  habilitación, activación, conectividad, autorización y corridas en curso. El proveedor de
  pendientes solo consulta estados en `MovementDao`. Para enviar cambios que solo afecten
  al catálogo, habrá que extender la detección de pendientes e integrar el orden del catálogo.

## Colecciones y documentos

| Ruta bajo `users/{uid}` | ID | Datos |
| --- | --- | --- |
| `movements/{movementUuid}` | UUID del movimiento | Contrato actual v1 durante 4A |
| `categories/{categoryUuid}` | UUID de categoría | Ficha de categoría v1 |
| `subcategories/{subcategoryUuid}` | UUID de subcategoría | Ficha v1 con `categoryUuid` del mismo usuario |
| `metadata/sync` | `sync` | Metadatos actuales del motor, ajenos a la versión del catálogo |

El cliente obtiene el UID de la sesión autorizada y construye la ruta con ese contexto. No hay
UID, propietario, ID de Room, estados de envío ni timestamps de sincronización en un documento
de catálogo. Dos usuarios pueden usar los mismos UUID de semillas. Un UUID del catálogo
identifica solo una ficha padre o hija dentro del UID. Los UUID de movimientos usan su propio espacio.

`RemoteCategoryDto`, `RemoteSubcategoryDto` y `RemoteCatalogFields` definen la frontera de
documentos. Se codifican como mapas de Firestore. El lector recibe el ID y los campos sin
conversión automática para rechazar campos extra, ausentes, nulos o coerciones de tipos.

| Campo | Requisito |
| --- | --- |
| `schemaVersion` | Entero 1 en ambos DTO, independiente de la versión de movimientos o del respaldo |
| `uuid` | UUID canónico RFC en minúsculas, versiones 1–5, igual al ID documental |
| `name` | Texto con contenido, hasta 480 bytes UTF-8 |
| `iconKey` | Texto con contenido, hasta 128 bytes UTF-8. Se conservan claves desconocidas |
| `sortOrder` | Entero firmado de 32 bits. Desempate por UUID |
| `archived` | Booleano. Archivo y desarchivo son cambios de ficha |
| `revisionCounter` | Entero de 64 bits no negativo |
| `revisionActorUuid` | UUID canónico de instalación que originó esa revisión |
| `scope`, `reportGroup` | Solo categoría. INCOME requiere grupo INCOME. EXPENSE/BOTH requieren un grupo de egreso |
| `categoryUuid` | Solo subcategoría. UUID del padre, distinto del propio |

Los límites de bytes son parte del contrato remoto. El dominio, Room aislado y el respaldo
admiten textos más largos. La futura integración debe validarlos antes de encolar el envío y
exponer el rechazo sin truncar datos. El lector usa la validación de texto de Kotlin además de
los límites. Las reglas usan `trim()` de Firebase, por lo que el lector sigue siendo la frontera
para casos de espacios Unicode que las plataformas puedan tratar distinto.

Las semillas conservan el UUID, grupo, ámbito y padre establecidos. Renombrar, cambiar
icono, ordenar y archivar una semilla conserva su identidad. Las revisiones de las semillas
pueden empezar en 0 con el actor reservado. El actor marca procedencia para el desempate.
Su formato no certifica qué instalación efectuó la escritura.

## Reglas propuestas

Las [reglas candidatas](../scripts/firestore-catalog/firestore.rules) incorporan las dos rutas
del catálogo con el control de acceso existente.
Cada documento exige campos y tipos exactos, coincidencia con el ID, estructura de semillas
y límites del contrato. En actualización se protegen UUID, versión, ámbito y grupo, o padre
según el tipo. Se admite escribir la misma ficha idéntica como reintento. Todo cambio
requiere una revisión mayor, incluso si solo cambia el archivo. El borrado físico se deniega.

Las escrituras de subcategorías usan `existsAfter` para verificar el padre en el mismo UID.
Al crear una ficha también se exige la ausencia del mismo UUID en la otra colección. Funciona tanto con un padre confirmado previamente
como con padre e hijo en el mismo lote. Un hijo enviado en una operación separada antes
de su padre será rechazado. El padre puede estar archivado, para permitir recibir historia.
Su UUID es inmutable y las reglas del catálogo impiden borrarlo mediante los clientes.

Las comprobaciones entre documentos consumen el presupuesto de reglas. La autorización
usa una lectura. Verificar UUID cruzado al crear usa otra y una escritura de subcategoría
consulta al padre. Firestore limita a 10 accesos por operación y a 20 para el conjunto en una
transacción o lote, con posibles accesos cacheados. El motor debe probar el tamaño de los
lotes y no asumir que puede confirmar catálogos completos en una sola operación.
[Condiciones y límites de reglas](https://firebase.google.com/docs/firestore/security/rules-conditions)
y [referencia de existsAfter](https://firebase.google.com/docs/reference/rules/rules.firestore).

Las reglas no resuelven descargas incompletas, no validan aún relaciones de movimientos UUID
y no garantizan atomicidad entre escrituras separadas. Las operaciones administrativas con
SDK de servidor se rigen por IAM y pueden saltar estas reglas. El cliente debe comprobar
tipo documental, UUID, padre y pertenencia antes de aplicar datos locales. Una referencia
válida pero ausente se aplaza y se reintenta, sin reemplazar datos locales ni convertirla en
«Sin categoría». Una relación conocida incompatible o mal formada se rechaza como error de
integridad. El par nulo permite «Sin categoría». Para movimientos históricos, los archivos
son referencias válidas. Las nuevas asignaciones conservan la guarda de dominio.

## Reconciliación y ACK

`reconcileCatalog` comprueba primero UUID, tipo de ficha y estructura inmutable. Un valor
diferente de cualquiera de esos campos es incompatible aunque traiga una revisión mayor.
La ficha ganadora se elige por `CatalogRevision`, comparando primero el contador y luego
el UUID del actor. Igual revisión exige contenido idéntico. Toda discrepancia con esa
revisión señala un problema de integridad que debe diagnosticarse sin sobrescribir fichas.

Se resuelve la ficha completa. Dos ediciones concurrentes de campos distintos, como
renombrar y archivar, pueden perder uno de esos cambios. El dispositivo que edita sobre
una revisión conocida usa su contador + 1 y su actor UUID. El futuro adaptador debe aplicar
la comparación dentro de la transacción remota, releer al perder y conservar el ganador.
Un historial de restauración o una respuesta de red no autoriza reescribir sobre la revisión ganadora.

`canAcknowledgeCatalogWrite` exige que coincidan la ficha local actual, la enviada y la
confirmada. La persistencia aún debe revalidar UID y revisión en el UPDATE local condicional
del DAO. Un ACK de la revisión anterior no limpia el pendiente de otra edición. La
reconciliación pura tampoco asigna `SYNCED` ni encola reintentos.

## Compatibilidad y momento de exigir actualización

1. **Expansión aditiva del catálogo.** Introducir las nuevas colecciones independientemente de
   los documentos de movimientos. Cualquier cliente que solo maneje v1 podrá seguir leyendo
   y escribiendo movimientos v1 mientras se mantenga ese modo. El catálogo no debe causar una
   actualización de movimientos v1 al desplegar sus propias reglas.
2. **Lectores primero.** Entregar y verificar un lector de movimientos v1 y v2 antes de escribir
   v2. Convertirá los enums v1 usando UUID de semillas, incluidos los tombstones. El esquema
   v2 futuro vive en el mismo documento y UUID y usa `categoryUuid` y `subcategoryUuid`
   explícitos y nullable. Evitar una segunda colección financiera que permita duplicar el
   mismo movimiento. No representar una categoría personalizada mediante un enum aproximado.
3. **Corte por cuenta controlado en servidor.** Cuando las relaciones nuevas estén listas,
   usar un marcador de protocolo por UID con escritura exclusiva de administración y separado
   de `metadata/sync`, que hoy permite escritura cliente. Para los UID convertidos, las reglas
   futuras deben denegar **todas** las escrituras v1, incluidos creates, updates y tombstones.
   Antes del corte, denegar v2. Después, permitir solo v2 con contrato estricto para esos UID
   y proteger por documento la versión contra degradación y conservar `createdAt`. En particular
   un `set` completo v1 contra un v2 y un create v1 de un dispositivo offline se deben denegar.
   El lector nuevo seguirá leyendo v1 durante el backfill. La restricción de versión es de
   payload y no una prueba de la versión del binario.
4. **Actualización obligatoria para Cloud Sync de esa cuenta.** Avisar y actualizar los
   dispositivos antes del corte. Resolver cambios locales pendientes en equipos antiguos o
   proporcionar recuperación explícita en el cliente actualizado. Un cliente viejo puede seguir
   viendo v1 o considerar v2 inválido y sus escrituras serán rechazadas. Las reglas no pueden
   actualizar el cliente, mostrarle un aviso ni distinguir qué versión tiene al leer. El cliente
   nuevo debe leer el marcador y presentar el estado de compatibilidad, sin interpretar rechazo
   como éxito o resolverlo borrando filas locales. Mantener los UUID y tombstones durante un
   backfill reanudable que valide las referencias antes de habilitar escrituras nuevas.

Los pasos 2–4 son dependencias de la futura integración financiera. Las reglas de 4A siguen
rechazando el payload con campos UUID en `movements`. La suite lo comprueba junto al flujo v1.

## Verificación y dependencias de 4B

Pasaron 55 pruebas JVM con Kotlin y JUnit locales, incluidas nueve nuevas del contrato remoto.
Cubren round-trip de los mapas, formas inválidas, semillas, estructura, revisiones, archivado,
ACK y resolución de referencias. Las pruebas existentes cubren
el contrato v1 de movimientos y su reconciliador. Pasaron también los cinco casos de reglas con Firestore Emulator 1.21.0 y Firebase CLI
15.20.0, ejecutados desde una copia temporal del proyecto demo con permisos de red local.
Cubrieron autorización y aislamiento por UID, forma y tamaños, relaciones padre/hijo y
colisiones en lotes, inmutabilidad y archivo, revisiones y regresión de movimientos v1.

### Ejecutar las pruebas en Fedora con fish

Para la CLI 15.20.0 usada en esta verificación, usar Node.js 22 o 24 con npm y Java 21
o posterior. Si falta Java, Fedora permite instalarlo con
`sudo dnf install java-21-openjdk-headless`. Comprobar el Node y Java activos con
`node --version` y `java -version`. La primera ejecución descarga las dependencias públicas
de npm y el binario del emulador.

Desde la raíz del repositorio, en fish:

```fish
set -l catalog_test_dir (mktemp -d /tmp/miflujo-emulator.XXXXXX)
mkdir -p "$catalog_test_dir/scripts/firestore-catalog" "$catalog_test_dir/config" "$catalog_test_dir/emulators"
and cp scripts/firestore-catalog/{package.json,catalog.test.mjs,firebase.json,firestore.rules} "$catalog_test_dir/scripts/firestore-catalog/"
and cd "$catalog_test_dir"
and npm --cache "$catalog_test_dir/npm-cache" --prefix scripts/firestore-catalog install --ignore-scripts --no-audit --no-fund
and env -u FIREBASE_TOKEN -u GOOGLE_APPLICATION_CREDENTIALS -u GOOGLE_CLOUD_PROJECT -u GCLOUD_PROJECT -u FIREBASE_CONFIG \
    XDG_CONFIG_HOME="$catalog_test_dir/config" FIREBASE_EMULATORS_PATH="$catalog_test_dir/emulators" CI=1 \
    npx --yes --package firebase-tools@15.20.0 firebase emulators:exec --config scripts/firestore-catalog/firebase.json --project demo-miflujo-catalog --only firestore 'npm --prefix scripts/firestore-catalog test'
```

La suite fija el proyecto `demo-miflujo-catalog` y exige un endpoint de loopback. Usa
identidades ficticias generadas por `@firebase/rules-unit-testing` y limpia los datos del
proyecto demo en el emulador entre casos. La copia temporal contiene solo las reglas,
la configuración y la suite. La CLI usa un directorio de configuración nuevo y no requiere
`firebase login`. Se esperan cinco casos y cero fallos. Si el proceso indica que un puerto
está ocupado en Fedora, comprobar los puertos 4400, 4500, 8080 y 9150 con `ss -ltn`.

En la ejecución restringida de Codex, Node informó `listen EPERM`. Una prueba independiente
de Firebase reprodujo el error al crear el socket TCP IPv4 o IPv6, antes de `bind`. Por
eso, cambiar los puertos de `firebase.json` no resolvería ese bloqueo.

[Documentación del emulador y de las pruebas de reglas](https://firebase.google.com/docs/firestore/security/test-rules-emulator).

## Motor experimental del catálogo (4B)

`CatalogSyncEngine` se ejecuta de forma manual con tres puertos. `CatalogSession` da
el UID activo, `CatalogSyncLocalSource` lee pendientes y aplica respuestas en Room, y
`CatalogRemoteSource` lee el servidor y publica de forma condicional. `ScopedCatalogStore`
implementa el puerto local y reutiliza `RoomCategoryRepository` para las ediciones,
confirmaciones y errores. `EmulatorCatalogClient` implementa el puerto remoto y la sesión
con una instancia de Firebase separada, el proyecto fijo `demo-miflujo-catalog` y un host
local. Usa caché en memoria, `Source.SERVER` y rechaza lecturas con escrituras pendientes
superpuestas. [SDK Android del emulador](https://firebase.google.com/docs/emulator-suite/connect_firestore)
y [semántica de Source.SERVER](https://firebase.google.com/docs/reference/android/com/google/firebase/firestore/Source).

Cada UID usa un archivo Room experimental distinto, `category-catalog-sync-<SHA-256 del UID>.db`.
No se adopta el catálogo experimental anterior sin dueño. Las semillas y el actor nacen
en la base de esa cuenta. El actor de revisión queda estable al reabrirla. No equivale a
un identificador compartido entre todas las cuentas que pasen por el dispositivo.
Al cambiar de cuenta se detiene el uso del motor anterior y se abre explícitamente la
partición del UID nuevo. Una ejecución captura la partición y revisa la sesión antes y
después de la red y antes de aplicar respuestas. El transporte también verifica el UID
dentro de la transacción. Una petición ya iniciada podría terminar bajo la cuenta previa.
Su ruta y su archivo local están ligados al UID anterior y nunca pueden escribir en la
partición nueva. Las reglas son la última comprobación del token en servidor.

### Orden y recuperación

1. Leer categorías y subcategorías desde el servidor. Si falla una consulta, abandonar la
   lectura antes de cambiar Room o publicar. Las dos consultas no hacen un snapshot global
   atómico. Si ambas se completan, los documentos inválidos o duplicados bloquean ese
   UUID y sus dependencias mientras las fichas independientes pueden avanzar.
2. Aplicar categorías recibidas, publicar categorías pendientes y luego procesar hijos.
   Los hijos esperan un padre confirmado y válido. Room resuelve el UUID a un ID local
   dentro de una transacción. Se comprueban las revisiones y la estructura contra la fila
   más reciente. Una categoría archivada sigue siendo un padre válido del historial.
3. Publicar hijos pendientes. La transacción Firestore lee el padre, lee el documento
   actual, compara revisiones y escribe por UUID solo si gana la ficha enviada. Devuelve
   la ficha confirmada, que puede ser la ganadora remota. Los IDs locales no salen de Room.
4. Aplicar la respuesta en una transacción Room únicamente si la ficha local sigue
   coincidiendo con la que originó la petición. Si cambió, conservar su revisión y pendiente
   y devolver `LOCAL_CHANGED`. Un snapshot posterior de la misma ficha confirma una
   escritura cuyo ACK se perdió, sin crear otro documento.

Un cambio efectivo del repositorio marca `PENDING` e incrementa su revisión. Una edición
sin cambios conserva revisión y estado. Una ficha remota aplicada o una identidad confirmada
queda `SYNCED`. Un fallo transitorio de escritura puede marcar `ERROR` para esa revisión,
que sigue siendo reintentable. Un permiso denegado detiene la corrida y conserva pendientes.
`CatalogSyncResult` devuelve contadores y distingue autenticación, cambio de cuenta, permisos,
fallos de red o de almacenamiento, documentos inválidos, padres ausentes y conflictos.
Los resultados de una corrida no son un registro durable de diagnósticos. Las fichas y sus
estados sí quedan persistidos. La ausencia remota no elimina ni vuelve a publicar una
ficha que ya estaba sincronizada. Así un hijo puede seguir aplazado si un administrador
borra o altera su padre. Los dos lectores son completos por colección y no hay paginación
ni política automática de reintento en esta etapa.

Se conserva la reconciliación por ficha y por par `(contador, UUID de instalación)`.
Renombrar y archivar en paralelo puede perder uno de esos cambios. Un UUID incompatible
o dos contenidos distintos con la misma revisión se reportan como conflicto y se conservan
las dos versiones en sus orígenes. El UUID del actor no prueba la identidad del dispositivo.
Un contador extremo de una cuenta autorizada puede agotar la revisión.

### Pruebas de 4B

- Pasaron 68 casos JVM con el compilador y las dependencias locales. Incluyen 13 nuevos
  del motor con puertos falsos y 55 de regresión del contrato, de dominio y de movimientos.
- Pasaron los cinco casos de reglas 4A sin cambios con Firestore Emulator 1.21.0.
  También se verificó la creación y autorización de la cuenta ficticia mediante Auth
  Emulator y el script `scripts/firestore-catalog/seed-android.mjs`.
- `ScopedCatalogSyncTest` tiene dos pruebas Room de reapertura, partición por cuenta,
  aplicación condicional y relaciones, más una integración opcional que usa el adaptador
  Android real con ambos emuladores. Sus fuentes compilaron de forma aislada. Ejecutarlas
  requiere Android y código Room generado por KSP. Quedaron pendientes.

Para preparar la integración Android en una copia temporal como la indicada arriba, copiar
también `seed-android.mjs` al directorio de la suite y crear esta configuración en la raíz
de esa copia:

```json
{
  "firestore": { "rules": "scripts/firestore-catalog/firestore.rules" },
  "emulators": {
    "auth": { "host": "127.0.0.1", "port": 9099 },
    "firestore": { "host": "127.0.0.1", "port": 8080 },
    "ui": { "enabled": false }
  }
}
```

Guardarla como `firebase-4b.json` y ejecutar en una primera terminal desde la copia temporal:

```fish
env -u FIREBASE_TOKEN -u GOOGLE_APPLICATION_CREDENTIALS -u GOOGLE_CLOUD_PROJECT -u GCLOUD_PROJECT -u FIREBASE_CONFIG \
    XDG_CONFIG_HOME="$PWD/config" FIREBASE_EMULATORS_PATH="$PWD/emulators" \
    npx --yes --package firebase-tools@15.20.0 firebase emulators:start --config firebase-4b.json --project demo-miflujo-catalog --only firestore,auth
```

En una segunda terminal, desde la misma copia:

```fish
env FIREBASE_AUTH_EMULATOR_HOST=127.0.0.1:9099 FIRESTORE_EMULATOR_HOST=127.0.0.1:8080 node scripts/firestore-catalog/seed-android.mjs
```

Ejecutar `ScopedCatalogSyncTest` en un emulador Android con variante debug y argumento
de instrumentación `catalogEmulator=true`. El cliente usa `10.0.2.2` para llegar al
host. La configuración debug permite HTTP solo para el alias del emulador y loopback.
La suite de reglas limpia sus fixtures, así que si se ejecuta junto con esta prueba
la cuenta ficticia debe prepararse después. No se requieren credenciales de una cuenta real.

La integración con la app requiere decisiones posteriores sobre enlace de catálogo local
a cuenta, vida útil de su sesión, programación, activación y migración en el corte coordinado.
El motor experimental carece de llamadas desde el contenedor, WorkManager o la UI.

## Extensión de color para el catálogo coordinado

El marcador administrativo del emulador `metadata/movementProtocol` admite la fase 3.
Con esa fase, los movimientos conservan su esquema v2 y las fichas de catálogo
se escriben como v2 con `iconColor` obligatorio. El campo es anulable y su valor
es una de las diez claves del catálogo. Los clientes de fase 3 leen fichas v1 como
color nulo. Un cambio de ficha v1 avanza la revisión y escribe v2. Las reglas
candidatas deniegan degradar una ficha v2 a v1 incluso ante un cambio posterior
del marcador. La fase 2 sigue disponible para los ensayos históricos del protocolo
sin color. Evitar bajar el marcador de una cuenta ya convertida.

Para activar la fase 3 en el fixture de Auth y Firestore Emulator, usar
`seed-android.mjs --catalog-colors`. El cliente detiene la corrida y conserva los
pendientes si el marcador difiere. La prueba aislada 4B conserva el escritor v1
y rechaza enviar fichas con color.
