# AGENTS.md

## Proyecto y estado verificado

MiFlujo es una app Android **local-first** para registrar ingresos y egresos, consultar flujo de efectivo mensual, generar PDF y crear/restaurar respaldos JSON.

- Repositorio: `MiFlujo-android`.
- Paquete: `com.carlos.miflujo`.
- Usuario principal: adulto no técnico; la captura rápida y la protección de sus datos son prioritarias.
- Última GitHub Release publicada al revisar esta documentación: **v0.4.0**.
- `v0.3.5` fue la baseline técnica pre-Firebase; **no es trabajo pendiente**.
- `v0.4.0` ya incorpora Google Sign-In, autorización por UID y Cloud Sync opcional con Firebase/Firestore. **No está prohibido trabajar en el código cloud existente**; cualquier cambio debe preservar sus garantías de privacidad y seguridad.
- **Inconsistencia pendiente de verificar antes de la próxima release:** `app/build.gradle.kts` declara `versionCode = 5` y `versionName = "0.3.5"`, aunque GitHub Releases y README identifican `v0.4.0` como publicación más reciente. No asumir que el Gradle actual corresponde al APK publicado.

Principio del producto:

> Abro, registro dinero, veo cómo va el mes, cierro.

## Reglas de producto que no deben romperse

- Registrar, editar y eliminar ingresos/egresos; mostrar resumen e historial mensual.
- C$ y US$ permanecen **separados**, sin conversión ni saldo combinado.
- El dinero se almacena como `Long` en unidades menores; **nunca** `Float` ni `Double` para importes.
- Montos positivos; tipo, moneda y fecha válidos. En egresos la categoría es obligatoria; en costos fijos también la subcategoría (agua, luz o internet).
- Detalle recomendado, no obligatorio.
- Reportes mensuales se calculan desde movimientos persistidos. El PDF es para lectura; **no** reemplaza al backup JSON.
- Backup JSON v2 conserva UUID; la importación admite v1 y v2 según reglas vigentes. Restaurar valida primero, pide confirmación y reemplaza movimientos localmente de forma transaccional.
- No permitir restauración cuando los controles actuales de Cloud Sync la bloqueen. Ver `docs/business-rules.md` y `docs/cloud-safe-restore.md`.

## Arquitectura real

Kotlin, Jetpack Compose, Material 3, Coroutines/Flow, MVVM, Repository, Room, Firebase Auth/Firestore y WorkManager.

```text
UI -> ViewModel -> Repository -> Room
                        |
                        +-> Cloud Sync opcional (Firestore)
```

Room es la fuente observable de la interfaz. Cloud Sync requiere autorización y activación explícita; no es requisito para uso offline. Mantener identidad remota por UUID, separación por UID, tombstones y comportamiento seguro ante errores. No escribir directamente a Room desde Composables ni usar Firestore como fuente exclusiva de la UI.

Carpetas relevantes:

- `data/local`, `data/model`, `data/repository`: persistencia y acceso a movimientos.
- `data/cloud/auth`, `data/cloud/firestore`, `data/cloud/sync`: autenticación y sincronización opcional.
- `domain/model`, `domain/validation`, `domain/sync`, `domain/usecase`: reglas y lógica.
- `ui/home`, `ui/movement`, `ui/report`, `ui/settings`, `ui/backup`, `ui/theme`: interfaz y operaciones de usuario.
- `app/schemas/`: esquemas exportados de Room, necesarios para migraciones.

No reorganizar módulos ni introducir nuevas dependencias o migraciones sin beneficio concreto y revisión.

## Dirección UI y experimentación

Mantener una interfaz sencilla, legible y accesible para el usuario principal. Material 3 Expressive se contempla **de forma ligera**, no como rediseño global automático.

La captura rápida en cápsula de **dos líneas** es una **propuesta pendiente**, no una función implementada:

- Primera línea: **un único control alternable** de ingreso/egreso (`+` / `−`), **un único control alternable** de moneda (C$ / US$) y monto.
- Segunda línea: fecha compacta (`Hoy` cuando corresponda) con selector de calendario, y categoría contextual; subcategoría cuando aplique.
- Descripción disponible y registro consecutivo como objetivo a diseñar y probar.
- Feedback háptico y movimiento discreto, sin reducir objetivos táctiles, accesibilidad ni claridad.
- Al editar, preservar valores existentes; no sustituirlos por valores por defecto.
- Probar persistencia, validaciones y prevención de envíos duplicados antes de entregar.

Ver `docs/ui-design.md` para límites y preguntas abiertas. No tratar propuestas UX como requisitos ya aceptados para producción.

## Flujo de trabajo vigente

Este es un proyecto de un desarrollador; evitar proceso innecesariamente granular.

- `main`: base estable; documentación y correcciones documentales pequeñas pueden actualizarse **directamente en main**.
- Ramas temporales **por módulo o conjunto coherente de trabajo**, no una rama por ajuste: por ejemplo `ux/quick-entry`, `feature/reporting`, `fix/cloud-sync`.
- Iterar con varios commits dentro de la rama del módulo; revisar y crear **un PR hacia main** al concluir el módulo.
- No son obligatorias una issue por cambio, una PR por ajuste, ni una rama `dev` de integración. `dev` es heredada, no base obligatoria.
- Evitar mezclar cambios de módulos independientes en una rama. Nunca publicar automáticamente al fusionar.
- Antes de editar archivos, inspeccionar implementación y documentos existentes; documentar hechos actuales por separado de propuestas.
- ChatGPT ayuda a definir arquitectura, alcance y UX; Codex puede implementar, probar y preparar PR; la decisión final de integración y release es humana.

## Seguridad de datos y entornos de prueba

- La app es utilizada con datos financieros reales. No borrar, restaurar ni modificar datos de producción para probar UI.
- Para experimentos, usar datos ficticios y un entorno/instalación aislado. Si se crea una variante debug instalable en paralelo, separar `applicationId` y configuración Firebase o desactivar Cloud Sync: **una rama Git no aísla datos de un dispositivo ni de Firestore**.
- Nunca subir keystores (`.jks`, `.keystore`), credenciales, tokens, `local.properties` ni datos financieros de usuario.
- No cambiar esquema Room o formato de backup sin estrategia de migración, pruebas y documentación.

## Verificación

Para cambios solamente documentales: comprobar enlaces, coherencia con código y fechas/versiones; no hace falta compilar.

Para cambios Kotlin:

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

El primer comando ejecuta tests unitarios de la variante debug del módulo `app`. El segundo compila su APK debug. Ejecutar pruebas instrumentadas pertinentes en dispositivo/emulador cuando se modifique Room o UI; los tests locales no sustituyen validación visual y de interacción.

Para publicar, seguir `docs/release-process.md`: revisar versión del Gradle, compilar y firmar desde `main`, probar actualización **sin desinstalar**, verificar conservación de Room y reglas de Cloud Sync, tag y GitHub Release.

## Documentos de referencia

- `README.md`: panorama y release publicada.
- `docs/product-spec.md`, `docs/mvp-scope.md`: producto y límites históricos.
- `docs/business-rules.md`, `docs/data-model.md`: invariantes y persistencia.
- `docs/ui-design.md`: experiencia actual y propuestas.
- `docs/cloud-sync-strategy.md`, `docs/firebase-cloud-sync-plan.md`, `docs/cloud-safe-restore.md`: sincronización y restauración.
- `docs/decisions.md`: decisiones y su estado.
- `docs/release-process.md`: publicación.

Actualizar documentos cuando cambie una decisión material. Evitar reescribir documentación histórica que permanece útil como antecedente.
