# MiFlujo

**MiFlujo** es una app Android local-first para registrar ingresos, egresos y consultar el flujo de efectivo mensual de forma rápida, clara y separada por moneda.

Subtítulo de la app:

```text
Flujo de efectivo mensual
```

La app está pensada para un uso simple y directo: registrar movimientos de dinero, revisar cómo va el mes y consultar un reporte mensual sin depender de nube, cuentas, login ni conversión de moneda.

Principio central del producto:

```text
Abro, registro dinero, veo cómo va el mes, cierro.
```

## Estado actual

MiFlujo ya cuenta con un MVP funcional entregado y probado en dispositivo físico.

Versión actual publicada:

```text
v0.1.1
```

La versión `v0.1.1` incluye correcciones post-MVP después de la primera prueba real:

- El diálogo de agregar/editar movimiento ya no se cierra al tocar fuera.
- El snackbar se integra mejor con el modo oscuro.
- El modo claro tiene superficies más limpias y menos pesadas.
- Los colores financieros son semánticos:
  - verde suave para ingresos y flujo positivo.
  - rojo suave para egresos y flujo negativo.
- La actualización desde `v0.1.0` a `v0.1.1` fue probada sin pérdida de datos locales.

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
- Historial de movimientos.
- Filtros por tipo de movimiento.
- Edición de movimientos.
- Eliminación de movimientos.
- Reporte mensual de flujo de efectivo.
- Navegación entre meses.
- Persistencia local con Room.

## Alcance del MVP

MiFlujo es una app Android local-first.

Incluye:

- Registro y consulta de movimientos.
- Resumen mensual.
- Reporte mensual.
- Edición y eliminación.
- Persistencia local.
- Soporte separado para C$ y US$.

No incluye en el MVP:

- Login.
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

## Reglas importantes del producto

- C$ y US$ se manejan siempre por separado.
- No se convierten monedas.
- No se calcula un total combinado entre C$ y US$.
- El dinero no se almacena como `Double` ni `Float`.
- Los montos se guardan como unidades menores usando `Long`.

Ejemplo:

```text
C$ 1,800.50 -> 180050
US$ 100.00 -> 10000
```

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
./gradlew :app:assembleDebug
```

Compila la app en modo debug para validar que el proyecto construye correctamente.

```bash
./gradlew :app:test
```

Ejecuta las pruebas unitarias disponibles.

## Flujo de ramas

```text
main      -> releases estables
dev       -> integración de cambios post-MVP
feature/* -> nuevas funcionalidades
fix/*     -> correcciones de bugs
style/*   -> cambios visuales o de UI
chore/*   -> tareas de mantenimiento
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

El archivo `AGENTS.md` contiene reglas importantes para agentes de IA o colaboradores que trabajen en el proyecto.

## Roadmap posible

Ideas futuras, sujetas a feedback real del usuario:

- Exportación de reportes.
- Backup local o manual.
- Comparación entre meses.
- Mejoras visuales adicionales.
- Estadísticas simples si aportan valor real.

Estas ideas no forman parte del MVP y no deben agregarse sin validar necesidad real.
