# Decisions

Este documento registra decisiones importantes del proyecto.

## 001 - Nombre de la app

La app se llamará:

```text
MiFlujo
```

## 002 - Subtítulo

El subtítulo será:

```text
Flujo de efectivo mensual
```

## 003 - Nombre del repositorio

El repositorio se llamará:

```text
MiFlujo-android
```

## 004 - Package name

El package name será:

```text
com.carlos.miflujo
```

## 005 - App local-first

El MVP será local-first.

Los datos se guardarán en el dispositivo.

No habrá nube ni sincronización en la primera versión.

## 006 - Repositorio privado durante desarrollo

El repositorio será privado durante el desarrollo.

Cuando el MVP esté funcional, limpio y usable, se evaluará hacerlo público como proyecto de portafolio.

## 007 - Soporte de córdobas y dólares

El MVP soportará movimientos en:

```text
C$
US$
```

Cada movimiento tendrá una moneda asociada.

## 008 - No conversión automática entre monedas

No habrá conversión automática entre córdobas y dólares en el MVP.

La app no manejará tipo de cambio.

## 009 - Totales separados por moneda

Los reportes mostrarán totales separados por moneda.

No se debe calcular un único flujo neto mezclando C$ y US$.

## 010 - Formato visible de fecha

El formato visible de fecha será:

```text
dd/MM/yy
```

Ejemplo:

```text
05/05/26
```

## 011 - Detalle recomendado, no obligatorio

El detalle de un movimiento será recomendado, pero no obligatorio.

La app debe permitir guardar movimientos sin detalle.

## 012 - Dinero como Long en unidades menores

Los montos se guardarán internamente como `Long` en centavos o unidades menores.

No se usará `Double` ni `Float` para almacenar dinero.

## 013 - Categorías controladas en el MVP

Las categorías serán controladas por código en el MVP.

No se implementarán categorías dinámicas todavía.

## 014 - Reportes calculados desde movimientos

El reporte mensual se calculará desde los movimientos guardados.

No se guardará como entidad independiente en el MVP.

## 015 - Stack visual

Se usará:

```text
Jetpack Compose + Material Design 3
```

## 016 - Persistencia

Se usará Room para persistencia local.

## 017 - Arquitectura

Se usará:

```text
MVVM + Repository Pattern
```

## 018 - Entorno principal de desarrollo

El entorno principal de desarrollo será Linux, específicamente Fedora.

Android Studio será el IDE principal para el proyecto Android.

Git y GitHub se usarán para control de versiones, documentación, issues y organización del trabajo.

## 019 - Estrategia de ramas simple

No se usará GitFlow completo.

Estrategia recomendada:

```text
main
dev
feature/nombre-de-la-tarea
```

`main` representará la versión estable.

`dev` se usará para integración.

Las ramas `feature/*` se usarán para tareas específicas.

## 020 - Exportación PDF de reportes

Se probó generar reportes PDF desde HTML y CSS renderizados en un `WebView`, pero el enfoque fue abandonado.

La implementación se removió porque el renderizado fuera de pantalla de `WebView` produjo PDFs en blanco o recortados de forma no confiable.

También se probó OpenPDF, pero el intento fue abandonado porque la dependencia falló en Android al cargar clases de `java.awt`, específicamente `java.awt.Color`.

No se parcheará `java.awt` ni se usará OpenPDF para la exportación.

La exportación PDF usa `android.graphics.pdf.PdfDocument` nativo de Android.

El diseño del PDF es sobrio, profesional, basado en tablas y similar a una exportación de Excel.

Esta función genera un reporte mensual legible para compartir o revisar fuera de la app. No implementa respaldo, restauración, nube, CSV ni XLSX.

## 021 - Respaldo local JSON

El respaldo manual de datos se exportará como JSON con versión de esquema y todos los movimientos guardados.

El usuario podrá guardarlo con el creador de documentos del sistema o compartirlo mediante Android Share Sheet.

Para compartir, el archivo se generará temporalmente en la caché de la app y se expondrá de forma segura usando `FileProvider`.

El usuario decidirá dónde guardar o compartir el respaldo. No se implementará restauración, cifrado, nube, CSV ni XLSX como parte de esta función.
