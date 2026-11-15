# Release Process

Procedimiento para publicar versiones Android de MiFlujo sin comprometer datos reales.

## Estado de referencia

- Última GitHub Release consultada: **v0.4.0** (publicada el 14 de junio de 2026).
- **Atención:** `app/build.gradle.kts` en `main` todavía declara `versionCode = 5` y `versionName = "0.3.5"`. Verificar el origen de esta discrepancia y actualizar de manera controlada **antes** de producir el siguiente APK. No reutilizar un `versionCode` ya distribuido.
- `dev` fue una rama de integración histórica, **no obligatoria** en el flujo nuevo. Las ramas por módulo parten de `main` y sus PR vuelven a `main`.

## Flujo de integración y release

1. Completar y validar un módulo en su rama (por ejemplo `ux/quick-entry`).
2. Revisar diff, resultados de tests, riesgos de migración y seguridad; abrir PR hacia `main` para integrar el módulo.
3. Integrar solamente cambios aprobados; una fusión no publica automáticamente un APK.
4. Cuando haya cambios listos para distribuir, fijar una versión nueva y aumentar `versionCode` y `versionName` coherentemente en `app/build.gradle.kts`.
5. Ejecutar pruebas unitarias pertinentes y compilar; realizar pruebas manuales reales.
6. Generar APK release **firmado desde `main`**, usando el keystore original, que debe permanecer fuera del repositorio.
7. Instalar la nueva versión **encima** de una instalación anterior de prueba, con la misma firma y `applicationId`, sin desinstalar; comprobar persistencia Room y funcionalidad cloud cuando corresponda.
8. Crear tag `vX.Y.Z` que apunte al commit publicado y GitHub Release con APK firmado y notas de cambios.

La documentación menor puede ir directamente a `main` y no requiere release por sí sola. No es necesario crear una rama `dev` ni un PR extra de promoción.

## Validación técnica

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

- `testDebugUnitTest`: ejecuta tests unitarios locales de debug del módulo app.
- `assembleDebug`: compila el APK debug del módulo app.

Si el cambio afecta migraciones Room, respaldos, sincronización o reglas financieras, incluir las pruebas específicas y pruebas instrumentadas necesarias. La compilación no demuestra por sí sola que el APK sea seguro para producción.

## Checklist manual mínimo

- [ ] Abrir la app y confirmar movimientos y totales separados C$/US$.
- [ ] Crear, editar y eliminar ingresos y egresos; verificar categorías y validaciones.
- [ ] Comprobar historial, reportes y PDF cuando corresponda.
- [ ] Generar y compartir backup JSON; validar restauración correcta, cancelación y rechazo de archivo inválido cuando corresponda.
- [ ] Para Cloud Sync: cuenta autorizada/no autorizada, activación explícita, offline, reconciliación, borrados y restauración bloqueada según estado.
- [ ] Si cambia la UI: teclado, accesibilidad, tamaños táctiles, modo claro/oscuro y dispositivo físico.
- [ ] Actualizar **sin desinstalar** sobre la versión previa con datos ficticios; verificar que conserva Room, UUID, sesión/preferencias según comportamiento esperado y, si aplica, sincronización.
- [ ] Confirmar `versionCode` creciente, `versionName` correcto, firma y tag coherentes.
- [ ] No publicar `.jks`, `.keystore`, `local.properties`, tokens, datos reales ni APK debug.

## Firma y artefactos

Generar APK release firmado mediante Android Studio (Build → Generate Signed Bundle / APK → APK) u otro flujo de release previamente configurado y verificado. Usar **el mismo keystore** de las releases anteriores para permitir actualización directa.

Si Google Sign-In falla con OAuth `statusCode=10`, verificar el certificado del **APK real instalado**, no solo el `signingReport`. El incidente y su diagnóstico se documentan en `docs/decisions.md` (decisión 053).

No instalar accidentalmente APKs antiguos presentes en rutas intermedias de Gradle.

## Versionado

- Patch: correcciones compatibles y mejoras pequeñas.
- Minor: capacidades visibles relevantes.
- Major: cambios incompatibles deliberados (no hay obligación de adoptar 1.0 todavía).

No usar ejemplos históricos (`v0.3.5`) como si fueran la próxima release. El número de la siguiente versión se decidirá según el alcance real y el `versionCode` distribuido.

## Regla final

```text
Pruebas -> revisión -> main -> versión y firma -> actualización sin pérdida de datos -> tag y release
```
