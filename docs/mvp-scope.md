# MVP Scope

## Propósito del documento

Este documento separa el alcance del MVP original del alcance post-MVP actual.

MiFlujo ya superó el MVP inicial. Por eso, este archivo debe leerse como referencia histórica y como guardarraíl de alcance, no como lista completa del estado actual.

## Incluido en el MVP original

El MVP de MiFlujo incluyó únicamente las funciones necesarias para registrar movimientos y consultar el flujo de efectivo mensual.

Funciones incluidas en el MVP:

- Registrar ingresos.
- Registrar egresos.
- Seleccionar moneda por movimiento:
  - C$
  - US$
- Clasificar egresos como:
  - Costos fijos.
  - Mantenimiento.
  - Otros.
- Dentro de costos fijos, seleccionar:
  - Agua.
  - Luz.
  - Internet.
- Agregar detalle o descripción a cada movimiento.
- Registrar fecha del movimiento.
- Ver resumen del mes actual.
- Ver últimos movimientos.
- Ver historial de movimientos.
- Editar movimientos.
- Eliminar movimientos.
- Generar reporte mensual de flujo de efectivo.
- Mostrar totales mensuales separados por moneda.
- Persistir datos localmente con Room.

## Agregado después del MVP

Estas funciones no formaron parte del MVP inicial, pero fueron implementadas después por necesidad real del proyecto:

- Exportación de reporte mensual a PDF.
- Pantalla de Ajustes.
- Respaldo local JSON.
- Guardado de respaldo mediante el creador de documentos del sistema.
- Compartir respaldo mediante Android Share Sheet.
- Restauración local JSON.
- Validación estricta de respaldo antes de restaurar.
- Confirmación explícita antes de reemplazar datos.
- Restauración transaccional en Room.
- Inicio de sesión con Google y autorización por UID para Cloud Sync.
- Cloud Sync opcional con Firestore para cuentas autorizadas.
- Activación explícita, sincronización manual y disparadores seguros para cambios pendientes.

Estas funciones siguen respetando la filosofía local-first.

## Excluido actualmente

No implementar sin issue, decisión documentada y planificación previa:

- Backend obligatorio.
- Sincronización entre dispositivos.
- Integración bancaria.
- Escaneo de facturas.
- OCR.
- IA dentro de la app.
- Facturación.
- Presupuestos avanzados.
- Multiempresa.
- Multiusuario.
- Contabilidad formal completa.
- Gráficas avanzadas.
- Conversión automática entre monedas.
- Tipo de cambio.
- Merge avanzado de respaldos.
- Restauración cloud-safe sin estrategia previa.

## Justificación

El usuario principal necesita una herramienta rápida para registrar dinero, ver cómo va el mes y proteger sus datos.

Agregar funciones avanzadas sin preparación aumenta la complejidad y el riesgo de pérdida o corrupción de datos.

## Regla de alcance

Si una función no ayuda directamente a registrar movimientos, consultar el flujo mensual, generar un reporte útil o proteger los datos, debe quedar fuera hasta que exista feedback real y planificación.

## Riesgos a evitar

- Convertir MiFlujo en una app contable compleja.
- Agregar funciones por curiosidad técnica.
- Mezclar córdobas y dólares en un solo total.
- Meter nube antes de auditar la base local.
- Implementar Firebase antes de definir identidad global y comportamiento de restore.
- Diseñar pantallas antes de cerrar reglas de negocio.
- Cambiar Room sin migración clara.
