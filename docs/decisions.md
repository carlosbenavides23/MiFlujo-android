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

## 013 - Categorías controladas en el MVP

Las categorías son controladas por código en el MVP.

No se implementan categorías dinámicas todavía.

## 014 - Reportes calculados desde movimientos

El reporte mensual se calcula desde los movimientos guardados.

No se guarda como entidad independiente en el MVP.

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

Ejemplo:

```text
v0.1.0 -> versionCode 1, versionName "0.1.0"
v0.1.1 -> versionCode 2, versionName "0.1.1"
v0.1.2 -> versionCode 3, versionName "0.1.2"
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

El respaldo manual de datos se exportará como JSON con versión de esquema y todos los movimientos guardados.

El usuario podrá guardarlo con el creador de documentos del sistema o compartirlo mediante Android Share Sheet.

Para compartir, el archivo se generará temporalmente en la caché de la app y se expondrá de forma segura usando `FileProvider`.

El usuario decidirá dónde guardar o compartir el respaldo. La exportación no implementará cifrado, nube, CSV ni XLSX.

## 029 - Restauración de respaldo local JSON

La restauración leerá archivos JSON seleccionados mediante el selector de documentos del sistema.

Antes de pedir confirmación, el archivo completo debe analizarse y validarse contra la versión de esquema, nombre de la app, campos requeridos, enums, fechas, timestamps y reglas de negocio de movimientos. Si un movimiento es inválido, se rechazará el respaldo completo.

Un respaldo válido quedará pendiente hasta que el usuario confirme explícitamente que desea reemplazar los movimientos actuales. También se permitirá confirmar un respaldo válido sin movimientos para limpiar los datos actuales.

Al confirmar, Room eliminará los movimientos actuales e insertará todos los movimientos del respaldo dentro de una sola transacción. Se preservarán los identificadores positivos y únicos del respaldo. Si cualquier inserción falla, la transacción revertirá la eliminación y conservará los datos anteriores.
