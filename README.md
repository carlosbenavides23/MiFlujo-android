# MiFlujo

**MiFlujo** es una app Android local-first para registrar ingresos, egresos, consultar el flujo de efectivo mensual y proteger los datos mediante respaldos locales.

Subtítulo de la app:

```text
Flujo de efectivo mensual
```

La app está pensada para un uso simple y directo: registrar movimientos de dinero, revisar cómo va el mes, generar un reporte mensual y conservar los datos sin depender de nube, cuentas, login ni conversión de moneda.

Principio central del producto:

```text
Abro, registro dinero, veo cómo va el mes, cierro.
```

## Estado actual

MiFlujo ya cuenta con un MVP funcional entregado, probado en dispositivo físico y publicado como APK firmado.

Versión estable publicada:

```text
v0.3.5
```

La versión `v0.3.5` es la baseline técnica pre-Firebase publicada:

```text
Pre-Firebase Technical Baseline
```

Incluye:

- Auditoría técnica y decisiones documentadas antes de Cloud Sync.
- Estrategia de identidad global y restauración cloud-safe documentadas.
- Política de Android Auto Backup para datos financieros.
- Exportación y versionado de schemas de Room.
- Validación centralizada de reglas de negocio de movimientos.
- UUID estable por movimiento con migración Room.
- Backup schema v2 con UUID y compatibilidad de importación con schema v1.

Firebase, login, Firestore y Cloud Sync todavía no están implementados.

## Funcionalidades

- Registro de ingresos.
- Registro de egresos.
- Moneda por movimiento:
  - C$
  - US$
- Totales separados por moneda.
- Sin conversión automática entre córdobas y dólares.
- Clasificación de egresos:
  - Costos fijos.
  - Mantenimiento.
  - Otros.
- Subcategorías de costos fijos:
  - Agua.
  - Luz.
  - Internet.
- Detalle opcional por movimiento.
- Dashboard del mes actual.
- Home simplificado con flujo neto y últimos movimientos.
- Historial de movimientos.
- Filtros por tipo de movimiento.
- Edición de movimientos.
- Eliminación de movimientos.
- Reporte mensual de flujo de efectivo.
- Exportación del reporte mensual a PDF.
- Navegación entre meses.
- Pantalla de Ajustes.
- Respaldo local JSON.
- Restauración local JSON validada.
- Persistencia local con Room.

## Alcance del producto

MiFlujo es una app Android local-first.

Incluye actualmente:

- Registro y consulta de movimientos.
- Resumen mensual.
- Reporte mensual.
- Exportación de reporte mensual a PDF.
- Edición y eliminación.
- Persistencia local.
- Soporte separado para C$ y US$.
- Backup local JSON.
- Restauración local JSON por reemplazo completo.

No incluye actualmente:

- Login.
- Firebase.
- Firestore.
- Sincronización en la nube.
- Backend.
- Integración bancaria.
- OCR.
- IA dentro de la app.
- Facturación.
- Conversión automática de moneda.
- Tipo de cambio.
- Totales combinados entre monedas.
- Reportes contables avanzados.
- Merge avanzado de respaldos.
- Sincronización multi-dispositivo.

## Stack técnico

- Kotlin.
- Jetpack Compose.
- Material Design 3.
- Room.
- Coroutines / Flow.
- MVVM.
- Repository Pattern.

## Arquitectura conceptual

```text
UI
↓
ViewModel
↓
Repository
↓
Room Database
```

La UI no accede directamente a Room. Los datos pasan por ViewModels y Repository.

Los reportes se calculan a partir de los movimientos almacenados, no como registros independientes.

Room/local es la fuente principal de datos. Cualquier sincronización futura debe ser opcional y no debe romper el uso offline-first.

## Reglas importantes del producto

- C$ y US$ se manejan siempre por separado.
- No se convierten monedas.
- No se calcula un total combinado entre C$ y US$.
- El dinero no se almacena como `Double` ni `Float`.
- Los montos se guardan como unidades menores usando `Long`.
- El reporte PDF es una salida humana para leer o compartir.
- El respaldo JSON es una salida técnica para proteger o restaurar datos.
- Backup local y PDF no son lo mismo.
- La restauración local actual reemplaza todos los movimientos después de validar el archivo y pedir confirmación.

Ejemplo de dinero en unidades menores:

```text
C$ 1,800.50 -> 180050
US$ 100.00 -> 10000
```

## Pre-Firebase Technical Baseline

La versión `v0.3.5` publicó la baseline técnica previa a Firebase Cloud Sync.

Objetivo:

```text
Auditar, limpiar y preparar la base técnica antes de sincronización cloud.
```

La baseline se completó sin implementar Firebase, login, Firestore ni Cloud Sync. El trabajo quedó dividido en issues pequeñas:

- `#96`: auditoría técnica pre-Firebase.
- `#97`: estrategia de identidad y sincronización cloud.
- `#98`: exportación de schemas de Room.
- `#99`: UUID estable para movimientos.
- `#100`: backup schema v2 con UUID.
- `#101`: validación centralizada de movimientos.
- `#102`: comportamiento cloud-safe para restauración.
- `#103`: política de Android Auto Backup para datos financieros.

## Releases

Los APKs publicados están disponibles en la sección de **Releases** del repositorio.

Para instalar manualmente:

1. Descargar el APK desde la release correspondiente.
2. Abrir el archivo en Android.
3. Permitir instalación desde el navegador o gestor de archivos si Android lo solicita.
4. Instalar o actualizar la app.

Al actualizar desde una release firmada con el mismo keystore, los datos locales de Room se conservan mientras no se desinstale la app.

## Desarrollo

Comandos útiles:

```bash
./gradlew :app:testDebugUnitTest
```

Ejecuta las pruebas unitarias debug del módulo `app`.

```bash
./gradlew :app:assembleDebug
```

Compila la app en modo debug para validar que el proyecto construye correctamente.

## Flujo de ramas

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

## Documentación del proyecto

La documentación base se encuentra en `docs/`:

- `docs/product-spec.md`
- `docs/mvp-scope.md`
- `docs/ui-design.md`
- `docs/data-model.md`
- `docs/business-rules.md`
- `docs/decisions.md`
- `docs/release-process.md`
- `docs/audit-pre-firebase.md`
- `docs/cloud-sync-strategy.md`
- `docs/cloud-safe-restore.md`
- `docs/android-auto-backup-policy.md`

El archivo `AGENTS.md` contiene reglas importantes para agentes de IA o colaboradores que trabajen en el proyecto.

## Roadmap posible

Ideas futuras, sujetas a feedback real del usuario y planificación técnica:

- Firebase Cloud Sync opcional.
- Changelog visible dentro de Ajustes.
- Acerca de MiFlujo.
- Comparación entre meses.
- Exportaciones editables futuras si aportan valor real.

Estas ideas no deben agregarse sin issue, decisión documentada y validación de necesidad real.
