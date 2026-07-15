# Release Process

Este documento define el proceso recomendado para publicar una nueva versión de MiFlujo.

El objetivo es evitar errores comunes antes de generar y publicar un APK release.

## Checklist rápido

Antes de publicar una release:

- [ ] Confirmar que los cambios están mergeados en `dev` o en la rama base definida para la release.
- [ ] Confirmar que `dev` no está detrás de `main` antes de usarlo como base.
- [ ] Crear PR hacia `main`.
- [ ] Mergear a `main` solo si la app fue probada.
- [ ] Actualizar `versionCode` y `versionName` antes de generar el APK final.
- [ ] Ejecutar pruebas unitarias relevantes.
- [ ] Ejecutar build debug.
- [ ] Generar APK release firmado desde `main`.
- [ ] Instalar la nueva release encima de la anterior sin desinstalar.
- [ ] Confirmar que Room conserva los datos locales.
- [ ] Crear tag de Git.
- [ ] Crear GitHub Release.
- [ ] Subir únicamente el APK como asset.
- [ ] No subir el keystore `.jks`.

## 1. Preparar rama de integración

Antes de promover cambios a `main`, asegurarse de que la rama de integración tenga todo lo necesario.

Normalmente la rama de integración es:

```text
dev
```

Pero si `main` tiene hotfixes o commits directos que todavía no están en `dev`, primero se debe actualizar `dev` o crear la rama de trabajo desde `main` para evitar reintroducir documentación o código viejo.

Comandos sugeridos:

```bash
git switch dev
git pull origin dev
git fetch origin
git log --oneline --decorate --graph --all -20
```

Luego validar:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

`dev` debe compilar correctamente y la app debe probarse en dispositivo físico cuando el cambio afecte UI o comportamiento del usuario.

## 2. Crear PR hacia `main`

Las releases deben llegar a `main` mediante Pull Request.

Caso normal:

```text
base: main
compare: dev
```

Para ramas específicas:

```text
base: main
compare: feature/*, fix/*, chore/*, docs/*
```

Título sugerido para release:

```text
Release vX.Y.Z
```

Ejemplo:

```text
Release v0.3.5
```

## 3. Actualizar versión de app

Antes de generar el APK final, revisar `app/build.gradle.kts`.

Actualizar:

```kotlin
versionCode = 5
versionName = "0.3.5"
```

Reglas:

- `versionCode` es interno para Android y debe aumentar en cada APK release.
- `versionName` es visible para humanos y debe coincidir con la versión publicada.
- No reutilizar el mismo `versionCode` para una release nueva.

Ejemplo de secuencia actual:

```text
v0.1.0 -> versionCode 1, versionName "0.1.0"
v0.1.1 -> versionCode 2, versionName "0.1.1"
v0.2.0 -> versionCode 3, versionName "0.2.0"
v0.3.0 -> versionCode 4, versionName "0.3.0"
v0.3.5 -> versionCode 5, versionName "0.3.5"
```

## 4. Generar APK release firmado

Desde Android Studio:

```text
Build -> Generate Signed Bundle / APK -> APK
```

Usar el mismo keystore `.jks` de releases anteriores.

Importante:

- El `.jks` debe estar fuera del repositorio.
- No subir el `.jks` a GitHub.
- Guardar contraseña y keystore de forma segura.

El APK generado puede copiarse con nombre de versión:

```bash
cp app/release/app-release.apk ~/Downloads/MiFlujo-v0.3.5.apk
```

## 5. Probar actualización sin pérdida de datos

Para verificar persistencia local:

1. Instalar una release anterior.
2. Crear datos de prueba.
3. Instalar la nueva release encima sin desinstalar.
4. Confirmar que los datos siguen visibles.

Ejemplo:

```bash
adb install ~/Downloads/MiFlujo-v0.3.0.apk
adb install -r ~/Downloads/MiFlujo-v0.3.5.apk
```

`-r` reemplaza la app existente manteniendo datos si el `applicationId` y la firma coinciden.

No usar `adb uninstall` entre versiones si se quiere probar persistencia, porque desinstalar borra los datos locales de la app.

## 6. Crear tag de Git

Después de tener `main` actualizado:

```bash
git switch main
git pull origin main
git tag -a v0.3.5 -m "MiFlujo v0.3.5"
git push origin v0.3.5
```

El tag debe coincidir con la release publicada.

## 7. Crear GitHub Release

En GitHub:

```text
Repo -> Releases -> Draft a new release
```

Usar:

```text
Tag: v0.3.5
Title: MiFlujo v0.3.5
```

Subir como asset:

```text
MiFlujo-v0.3.5.apk
```

No subir:

- `.jks`.
- `.keystore`.
- `local.properties`.
- archivos temporales.
- APKs debug.

## 8. Pruebas manuales mínimas

Antes de publicar o compartir el APK:

- [ ] Abrir app después de actualizar.
- [ ] Confirmar que los datos anteriores siguen ahí.
- [ ] Agregar movimiento.
- [ ] Editar movimiento.
- [ ] Eliminar movimiento.
- [ ] Revisar Home.
- [ ] Revisar Movimientos.
- [ ] Revisar Reporte.
- [ ] Probar exportación PDF si hubo cambios en reporte/PDF.
- [ ] Crear respaldo local JSON si hubo cambios en backup.
- [ ] Guardar respaldo en Archivos si hubo cambios en backup.
- [ ] Compartir respaldo si hubo cambios en backup.
- [ ] Restaurar respaldo válido si hubo cambios en restore.
- [ ] Intentar restaurar archivo inválido si hubo cambios en restore.
- [ ] Cancelar confirmación de restore y confirmar que no modifica datos si hubo cambios en restore.
- [ ] Si hubo cambios en Cloud Sync: probar inicio y cierre de sesión, cuenta no autorizada, sincronización manual, modo sin conexión y restauración bloqueada mientras sync esté activo.
- [ ] Probar modo claro y modo oscuro si hubo cambios visuales.

## 9. Tipos de release

### Patch release

Para bugfixes, documentación, auditoría, limpieza técnica y polish pequeño.

Ejemplo:

```text
v0.3.0 -> v0.3.5
```

### Minor release

Para nuevas capacidades relevantes.

Ejemplo:

```text
v0.3.5 -> v0.4.0
```

### Major release

No aplica todavía para MiFlujo.

## 10. Release técnica v0.3.5

`v0.3.5` es una release técnica pre-Firebase.

Objetivo:

```text
Pre-Firebase Technical Baseline
```

Debe servir para:

- Auditar el estado local-first.
- Documentar riesgos antes de Firebase.
- Preparar Room antes de migraciones sync.
- Definir identidad global antes de UUID.
- Definir restore cloud-safe antes de cloud sync.
- Reducir riesgo de pérdida o corrupción de datos.

No debe incluir:

- Firebase.
- Login.
- Firestore.
- Sync engine.
- Cambios grandes de UI.
- PR gigante con muchos temas mezclados.

## Regla final

Una release no se considera lista solo porque compila.

Debe cumplir:

```text
Compila -> se instala -> actualiza sin borrar datos -> funciona en uso básico -> protege datos -> se publica con versión correcta
```
