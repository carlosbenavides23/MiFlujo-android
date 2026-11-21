# Política de Android Auto Backup

## Propósito

Este documento define la política de MiFlujo sobre Android Auto Backup.

Corresponde a:

```text
#103 chore: definir política de Android Auto Backup para datos financieros
```

Forma parte de:

```text
v0.3.5 - Pre-Firebase Technical Baseline
```

El objetivo es evitar restauraciones automáticas inesperadas de datos financieros cuando MiFlujo ya tiene respaldo local JSON y tendrá Cloud Sync opcional/controlado.

## Decisión principal

```text
Android Auto Backup debe deshabilitarse para MiFlujo.
```

MiFlujo no debe depender de Android Auto Backup para proteger datos financieros.

La protección principal de datos será:

1. Room local como fuente de trabajo.
2. Backup JSON manual.
3. Cloud Sync opcional y privado/controlado para cuentas autorizadas.

## Razón

MiFlujo maneja datos financieros personales.

Permitir Android Auto Backup agrega un tercer mecanismo de respaldo/restauración que puede ocurrir de forma silenciosa o poco visible para el usuario.

Mecanismos posibles si no se deshabilita:

```text
1. Backup JSON manual
2. Cloud Sync Firebase opcional
3. Android Auto Backup del sistema
```

Tener tres mecanismos de restauración puede causar confusión y riesgo.

## Riesgo principal

Si Android restaura datos antiguos automáticamente y luego Cloud Sync también intenta sincronizar, la app podría quedar en un estado difícil de razonar.

Ejemplo conceptual:

```text
Teléfono nuevo
↓
Android restaura una base de datos local vieja
↓
MiFlujo abre con datos restaurados automáticamente
↓
Cloud Sync intenta descargar/subir datos
↓
Riesgo de duplicados, sobrescritura o conflicto
```

La app debe evitar restauraciones automáticas fuera de su propio flujo controlado.

## Política para v0.3.5

En `v0.3.5`, se debe documentar y preparar la decisión:

```text
MiFlujo no usará Android Auto Backup para su base de datos financiera.
```

La implementación técnica esperada en una issue posterior o en esta misma fase será deshabilitar Auto Backup desde el manifest/configuración Android.

Dirección esperada:

```xml
android:allowBackup="false"
```

Si se mantiene un archivo de reglas de backup, no debe incluir la base de datos financiera de Room.

## Política para usuarios normales

Usuarios normales:

- usan Room local,
- pueden crear backup JSON manual,
- pueden restaurar backup JSON manual,
- no necesitan cuenta,
- no dependen de Android Auto Backup.

Si cambian de teléfono, el flujo recomendado es:

```text
Teléfono viejo -> Crear respaldo local JSON
Teléfono nuevo -> Instalar MiFlujo -> Restaurar respaldo local JSON
```

Este flujo es explícito y entendible.

## Política para usuarios con Cloud Sync

Usuarios autorizados con Cloud Sync:

- usarán Room local,
- podrán usar Cloud Sync opcional,
- podrán crear backup JSON manual,
- no dependerán de Android Auto Backup.

Si cambian de teléfono, el flujo recomendado será:

```text
Teléfono nuevo -> Instalar MiFlujo -> Iniciar sesión con Google -> Descargar datos desde Cloud Sync
```

Backup JSON queda como respaldo manual de emergencia.

## Relación con backup JSON

Backup JSON es el mecanismo manual y controlado de respaldo local.

Ventajas:

- El usuario decide cuándo crear respaldo.
- El usuario decide dónde guardarlo.
- El usuario decide cuándo restaurarlo.
- La app valida el archivo antes de modificar datos.
- La app pide confirmación antes de reemplazar movimientos.

Esto es preferible a una restauración automática silenciosa para datos financieros.

## Relación con Cloud Sync

Cloud Sync es opcional y privado/controlado.

Debe estar basada en:

- Firebase Auth,
- UID autorizado,
- Firestore Rules,
- UUID estable por movimiento,
- política cloud-safe para restore.

Android Auto Backup no debe actuar como una segunda nube no controlada por la app.

## Relación con restore cloud-safe

La política de restore cloud-safe define que restaurar backup local con Cloud Sync activo es delicado.

Android Auto Backup aumenta ese riesgo porque puede restaurar datos sin pasar por el flujo explícito de MiFlujo.

Por eso debe deshabilitarse.

## Relación con privacidad

Los datos financieros de MiFlujo son sensibles.

Aunque Android Auto Backup pueda ser útil para apps generales, MiFlujo prefiere mecanismos explícitos y controlados por la propia app.

Regla:

```text
El usuario debe saber cuándo sus datos financieros se respaldan o restauran.
```

## No objetivos

Este documento no implementa todavía:

- cambios en `AndroidManifest.xml`,
- cambios en reglas XML de backup,
- Firebase,
- Cloud Sync,
- migraciones Room,
- cifrado de backups.

## Criterios de aceptación para #103

La issue `#103` se considera lista cuando:

- Se documenta que Android Auto Backup debe deshabilitarse para MiFlujo.
- Se explica por qué no debe usarse para datos financieros.
- Se define que backup JSON manual es el mecanismo local explícito.
- Se define que Cloud Sync será el mecanismo cloud opcional/controlado.
- Se documenta el riesgo de mezclar Auto Backup, backup JSON y Cloud Sync.
- Se define que la restauración debe pasar por flujos explícitos de la app.
- No se implementa Firebase.
- No se cambia el modelo de datos.

## Decisión final

MiFlujo debe deshabilitar Android Auto Backup.

La app debe proteger los datos financieros mediante mecanismos explícitos y controlados:

```text
Room local
Backup JSON manual
Cloud Sync opcional/controlado
```

Android Auto Backup no debe formar parte de la estrategia principal de respaldo o restauración.
