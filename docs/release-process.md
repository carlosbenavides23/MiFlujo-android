# Release Process

Este documento define el proceso recomendado para publicar una nueva versión de MiFlujo.

El objetivo es evitar errores comunes antes de generar y publicar un APK release.

## Checklist rápido

Antes de publicar una release:

- [ ] Confirmar que los cambios están mergeados en `dev`.
- [ ] Crear PR de `dev` hacia `main`.
- [ ] Mergear a `main` solo si la app fue probada.
- [ ] Actualizar `versionCode` y `versionName` antes de generar el APK final.
- [ ] Generar APK release firmado desde `main`.
- [ ] Instalar la nueva release encima de la anterior sin desinstalar.
- [ ] Confirmar que Room conserva los datos locales.
- [ ] Crear tag de Git.
- [ ] Crear GitHub Release.
- [ ] Subir únicamente el APK como asset.
- [ ] No subir el keystore `.jks`.

## 1. Preparar `dev`

Antes de promover cambios a `main`, asegurarse de que `dev` tenga todo lo necesario.

```bash
git switch dev
git pull origin dev
./gradlew :app:assembleDebug
```

`dev` debe compilar correctamente y la app debe probarse en dispositivo físico cuando el cambio afecte UI o comportamiento del usuario.

## 2. Crear PR de `dev` hacia `main`

Las releases deben llegar a `main` mediante Pull Request.

```text
base: main
compare: dev
```

Título sugerido:

```text
Release vX.Y.Z
```

Ejemplo:

```text
Release v0.1.2
```

## 3. Actualizar versión de app

Antes de generar el APK final, revisar `app/build.gradle.kts`.

Actualizar:

```kotlin
versionCode = 3
versionName = "0.1.2"
```

Reglas:

- `versionCode` es interno para Android y debe aumentar en cada APK release.
- `versionName` es visible para humanos y debe coincidir con la versión publicada.
- No reutilizar el mismo `versionCode` para una release nueva.

Ejemplo de secuencia:

```text
v0.1.0 -> versionCode 1, versionName "0.1.0"
v0.1.1 -> versionCode 2, versionName "0.1.1"
v0.1.2 -> versionCode 3, versionName "0.1.2"
v0.2.0 -> versionCode 4, versionName "0.2.0"
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
cp app/release/app-release.apk ~/Downloads/MiFlujo-v0.1.2.apk
```

## 5. Probar actualización sin pérdida de datos

Para verificar persistencia local:

1. Instalar una release anterior.
2. Crear datos de prueba.
3. Instalar la nueva release encima sin desinstalar.
4. Confirmar que los datos siguen visibles.

Ejemplo:

```bash
adb install ~/Downloads/MiFlujo-v0.1.1.apk
adb install -r ~/Downloads/MiFlujo-v0.1.2.apk
```

`-r` reemplaza la app existente manteniendo datos si el `applicationId` y la firma coinciden.

No usar `adb uninstall` entre versiones si se quiere probar persistencia, porque desinstalar borra los datos locales de la app.

## 6. Crear tag de Git

Después de tener `main` actualizado:

```bash
git switch main
git pull origin main
git tag -a v0.1.2 -m "MiFlujo v0.1.2"
git push origin v0.1.2
```

El tag debe coincidir con la release publicada.

## 7. Crear GitHub Release

En GitHub:

```text
Repo -> Releases -> Draft a new release
```

Usar:

```text
Tag: v0.1.2
Title: MiFlujo v0.1.2
```

Subir como asset:

```text
MiFlujo-v0.1.2.apk
```

No subir:

- `.jks`
- `.keystore`
- `local.properties`
- archivos temporales
- APKs debug

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
- [ ] Probar modo claro y modo oscuro si hubo cambios visuales.

## 9. Tipos de release

### Patch release

Para bugfixes y polish pequeño.

Ejemplo:

```text
v0.1.1 -> v0.1.2
```

### Minor release

Para nuevas capacidades relevantes.

Ejemplo:

```text
v0.1.2 -> v0.2.0
```

### Major release

No aplica todavía para MiFlujo.

## Regla final

Una release no se considera lista solo porque compila.

Debe cumplir:

```text
Compila -> se instala -> actualiza sin borrar datos -> funciona en uso básico -> se publica con versión correcta
```
