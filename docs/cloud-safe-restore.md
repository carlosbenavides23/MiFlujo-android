# Comportamiento cloud-safe para restauración de backups

## Propósito

Este documento define cómo debe comportarse la restauración de respaldos locales cuando exista Cloud Sync en MiFlujo.

Corresponde a:

```text
#102 docs: definir comportamiento cloud-safe para restauración de backups
```

Este documento forma parte de:

```text
v0.3.5 - Pre-Firebase Technical Baseline
```

En su issue original, este documento no implementaba Firebase, login, Firestore ni sincronización. Define la política para evitar pérdida, duplicación o eliminación remota accidental de datos.

La política ya está aplicada en `main`: Cloud Sync es opcional y la restauración
local se bloquea únicamente mientras existe una operación de cuenta o sync, o
Cloud Sync está habilitado, activado y usa una cuenta autorizada.

La decisión final para `v0.4.0` también está consolidada en:

```text
docs/firebase-cloud-sync-plan.md
```

## Contexto

MiFlujo ya tiene restauración local JSON.

El comportamiento actual es:

1. El usuario selecciona un archivo JSON.
2. La app valida todo el archivo.
3. Si el archivo es inválido, no modifica datos.
4. Si el archivo es válido, muestra confirmación.
5. Si el usuario confirma, reemplaza todos los movimientos locales dentro de una transacción Room.

Este comportamiento es correcto cuando la app está efectivamente en modo local-only.

## Principio principal

```text
Backup local sigue disponible.
Restore destructivo requiere cuidado cuando Cloud Sync esté activo.
```

No se debe eliminar el backup local.

El respaldo JSON manual sigue siendo una función importante para todos los usuarios, incluso si Cloud Sync existe en el futuro.

Lo que se debe controlar es la restauración destructiva cuando haya datos sincronizados.

## Política general

Cloud Sync es opcional y privado/controlado.

Usuarios normales:

- usan Room local,
- pueden crear backup local JSON,
- pueden restaurar backup local JSON,
- no necesitan cuenta,
- no usan Firebase.

Usuarios autorizados:

- pueden iniciar sesión con Google,
- pueden activar Cloud Sync,
- deben estar autorizados por UID de Firebase Auth,
- inicialmente serán Carlos y el usuario principal.

Regla:

```text
Cloud Sync se activa por cuenta autorizada, no por dispositivo.
```

El correo de Google ayuda a identificar a la persona, pero la autorización real debe usar el UID de Firebase Auth.

## Estados relevantes

Para definir restore seguro, la app debe distinguir al menos estos estados conceptuales:

```text
SYNC_OFF
SYNC_ON
SYNC_ERROR
```

No es obligatorio implementar estos estados exactamente con esos nombres, pero el comportamiento debe cubrirlos.

## Caso 1: Cloud Sync apagado

Cuando Cloud Sync está apagado, la restauración local actual puede funcionar igual.

Flujo:

```text
Ajustes -> Restaurar respaldo -> seleccionar JSON -> validar -> confirmar -> reemplazar Room local
```

Reglas:

- Validar todo el archivo antes de confirmar.
- Rechazar archivo inválido sin modificar datos.
- Pedir confirmación explícita.
- Reemplazar movimientos dentro de transacción Room.
- Si falla cualquier inserción, revertir la operación.

Este modo aplica para:

- usuarios normales,
- usuarios sin login,
- usuarios autorizados que no activaron Cloud Sync.

## Caso 2: Cloud Sync activado al menos una vez

Después de que Cloud Sync complete una ejecución `SUCCESS` o `PARTIAL`, no se debe
ejecutar restore destructivo directo en esa instalación.

Riesgo:

```text
Un backup viejo puede reemplazar Room local y luego sync puede interpretar movimientos faltantes como eliminaciones remotas.
```

Ejemplo:

```text
Nube: 100 movimientos
Backup viejo: 60 movimientos
Restore local: Room queda con 60 movimientos
Sync automático: puede intentar borrar o sobrescribir los 40 movimientos faltantes
```

Esto no debe ocurrir accidentalmente.

## Política final para v0.4.0

En `v0.4.0`:

```text
Restore local queda bloqueado mientras Cloud Sync está activo y autorizado.
```

Restore vuelve a estar disponible si Cloud Sync se desactiva, la sesión se cierra
o la cuenta deja de estar autorizada. Sigue bloqueado durante una ejecución de
sync o una operación de cuenta.

Mensaje conceptual:

```text
Desactiva Cloud Sync o cierra sesión para restaurar un respaldo local.
```

Acciones posibles:

```text
Cerrar
```

## Caso 3: Restore con sync pausado en una versión futura

`v0.4.0` no implementa un modo de pausa temporal para restaurar.

Un modo futuro de restore con Cloud Sync requerirá una política explícita que defina:

- cómo combinar por UUID,
- qué lado tiene prioridad,
- cómo evitar eliminaciones remotas masivas,
- qué schemas de backup son aceptados.

## Caso 4: Cuenta no autorizada

Si un usuario inicia sesión con Google pero su UID no está autorizado, no debe poder usar Cloud Sync.

Debe poder seguir usando la app localmente.

Mensaje conceptual:

```text
Esta cuenta no tiene acceso a Cloud Sync.
Puedes seguir usando MiFlujo localmente y crear respaldos manuales.
```

Reglas:

- No borrar datos locales por no estar autorizado.
- No bloquear el uso local de la app.
- No impedir backup local JSON.
- No permitir lectura/escritura en Firebase.

## Caso 5: Cierre de sesión

Si un usuario autorizado cierra sesión, no se deben borrar datos locales automáticamente.

Regla:

```text
Cerrar sesión no equivale a borrar datos locales.
```

La app queda en modo local con los datos existentes, detiene sync automático y no borra Firestore. Si hay cambios pendientes, debe advertirlo antes de cerrar sesión o desactivar Cloud Sync.

## Backup local con Cloud Sync

Crear respaldo local JSON debe seguir disponible aunque exista Cloud Sync.

Crear backup es seguro porque no modifica datos remotos.

Reglas:

- Cualquier usuario puede exportar backup local.
- Exportar backup no requiere login.
- Exportar backup no sube archivos a Firebase.
- Exportar backup no modifica Firestore.

## Restore local con Cloud Sync

Restaurar backup sí modifica Room local.

Por eso, si Cloud Sync está activo, puede afectar la nube indirectamente.

Regla:

```text
No hacer restore destructivo mientras sync automático está activo.
```

## Relación con backup schema v1

Backup schema v1 no incluye UUID global.

Por eso, en un contexto con Cloud Sync, restaurar un backup v1 tiene riesgo adicional.

Política:

- v1 puede restaurarse localmente con sync apagado.
- v1 nunca debe restaurarse mientras Cloud Sync esté activo.
- v1 no debe subirse automáticamente a la nube como reemplazo remoto.
- al importar v1, la app genera UUID nuevos.

## Relación con backup schema v2

Backup schema v2 incluye UUID estable.

Reglas actuales:

- exportar debe preservar UUID,
- importar v2 debe preservar UUID,
- importar v1 debe generar UUID nuevos,
- sync debe usar UUID para deduplicar.

Aun con v2, `v0.4.0` bloquea restore mientras Cloud Sync está activo.

UUID reduce duplicados, pero no elimina el riesgo de borrado masivo.

Si restore con Cloud Sync se soporta en el futuro, solo schema v2 o superior podrá considerarse y deberá existir una política explícita de merge/restore.

## Relación con Firebase Auth

Cloud Sync se controla por cuenta autorizada.

Dirección esperada:

```text
Firebase Auth con Google Sign-In
UID allowlist en Firestore Security Rules
```

Usuarios permitidos inicialmente:

- Carlos.
- Usuario principal.

Reglas:

- El correo visible ayuda a reconocer la cuenta.
- El UID de Firebase Auth es la identidad de autorización.
- Usuarios no autorizados no acceden a Firestore.
- Usuarios no autorizados sí pueden usar la app localmente.

## Relación con Firestore Rules

La seguridad real debe vivir en Firestore Security Rules, no solo en la UI.

La UI puede ocultar o deshabilitar Cloud Sync para usuarios no autorizados, pero eso no es suficiente.

Regla:

```text
Nadie debe poder leer o escribir datos cloud si su UID no está autorizado.
```

Estructura conceptual:

```text
users/{userId}/movements/{movementUuid}
```

Regla conceptual:

```text
request.auth.uid == userId
```

Además, para la etapa privada, solo UIDs autorizados deben poder usar Cloud Sync.

## Política recomendada para v0.4.0

Primera versión cloud recomendada:

- Cloud Sync opcional.
- Google Sign-In para cuentas autorizadas.
- Allowlist por UID.
- Usuarios normales siguen local-only.
- Backup local JSON disponible para todos.
- Restore local disponible si Cloud Sync está apagado.
- Restore bloqueado si Cloud Sync está activo.
- No hay merge avanzado todavía.
- No hay reemplazo remoto completo desde backup todavía.

## No objetivos

Este documento no implementa:

- Firebase Auth.
- Google Sign-In.
- Firestore.
- Firestore Rules.
- Sync engine.
- Botón real de pausar sync.
- Migración UUID.
- Backup schema v2.
- Merge de respaldos.

## Criterios de aceptación para #102

La issue `#102` se considera lista cuando:

- Se define que backup local sigue disponible para todos.
- Se define que crear backup es seguro con o sin Cloud Sync.
- Se define que restore destructivo con Cloud Sync activo es riesgoso.
- Se define que restore destructivo no debe ejecutarse con sync automático activo.
- Se define política para sync off.
- Se define política para sync on.
- Se define comportamiento para cuenta no autorizada.
- Se define que Cloud Sync se controla por cuenta autorizada, no por dispositivo.
- Se define que el correo identifica visualmente, pero el UID autoriza realmente.
- Se mantiene Firebase como opcional y privado/controlado.
- No se toca código.

## Decisión final

MiFlujo conservará backup local JSON para todos los usuarios.

Cloud Sync será una función opcional y privada/controlada, inicialmente para Carlos y el usuario principal.

La restauración local destructiva es válida cuando MiFlujo está efectivamente en
modo local-only. Una ejecución `SUCCESS` o `PARTIAL` previa no bloquea restore por
sí sola; Cloud Sync debe estar desactivado, sin sesión o sin autorización.

La prioridad es evitar que un backup viejo provoque pérdida o eliminación accidental de datos remotos.
