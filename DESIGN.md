# MiFlujo — Design system e identidad visual

> **Estado:** guía de diseño vigente para desarrollo e integración en `dev`. No es una especificación visual inmutable ni implica que los experimentos estén publicados.
>
> **Principio del producto:** «Abro, registro dinero, veo cómo va el mes, cierro».

## 1. Propósito y alcance

Este documento describe la identidad visual observada en la aplicación Android y fija criterios compartidos para mantener coherencia al crear o modificar pantallas con Jetpack Compose. Prioriza una experiencia clara para una persona adulta no técnica que registra ingresos y egresos frecuentemente.

**No es una prohibición de experimentar.** Se pueden probar composiciones, componentes, animaciones, colores y patrones nuevos, especialmente en las variantes y ramas experimentales. El objetivo es evaluar su utilidad en contexto, no perpetuar cada decisión actual. Un experimento no se convierte automáticamente en regla global: primero se prueba, se revisa y se aprueba. Cuando una decisión se consolide, actualizar este documento y, si corresponde, los tokens o componentes de código.

Separación de contextos:
- **`catalogLab`:** validar funcionalidades y contratos experimentalmente; su interfaz diagnóstica no define por sí sola el aspecto final.
- **Interfaz real de `app` en entorno aislado:** evaluar composición, navegación e interacción integradas sin afectar instalaciones ni datos reales.
- **`main` y releases:** implementación publicada, que solo cambia tras una decisión explícita y validación de actualización.

No confundir rama Git con aislamiento de aplicación, Room o Firebase. Los trabajos de UI no autorizan migraciones ni cambios de producción.

## 2. Principios de diseño

1. **Claridad primero.** Presentar lo importante con jerarquía legible; evitar superficies, etiquetas y controles que no ayuden a completar la tarea.
2. **Captura rápida.** Registrar un movimiento debe requerir pocas decisiones y permitir corregirlo sin perder el borrador.
3. **Sobriedad cálida.** Superficies neutras, color de acento contenido, formas suaves y movimiento con propósito; evitar decoración que compita con los importes.
4. **Finanzas legibles.** C$ y US$ siempre identificables y separados. Ingreso/egreso se distinguen también por signos y texto, nunca solo por color.
5. **Complejidad progresiva.** Ajustes opcionales, personalizaciones y acciones menos frecuentes pueden permanecer detrás de una entrada o sección desplegable.
6. **Seguridad comprensible.** Operaciones destructivas y estados de sincronización deben explicarse con lenguaje humano y confirmación adecuada.
7. **Accesibilidad práctica.** Diseñar para letra ampliada, pantallas pequeñas, teclado, TalkBack, contraste, toque y temas claro/oscuro.
8. **Exploración deliberada.** Probar alternativas cuando resuelvan un problema; no exigir conformidad rígida con un patrón anterior si existe una solución mejor.

## 3. Tecnología y fuentes de verdad

- Android nativo con **Jetpack Compose** y **Material 3**.
- `app/src/main/java/com/carlos/miflujo/ui/theme/Theme.kt`: resolución del tema y colores dinámicos.
- `ui/theme/Color.kt`: colores propios de superficies y semántica financiera.
- `ui/theme/Type.kt`: configuración tipográfica.
- `ui/MiFlujoApp.kt`: estructura y navegación principales.
- `ui/home`, `ui/movement`, `ui/report` y `ui/settings`: componentes y composiciones reales.
- `docs/ui-design.md`: antecedentes y propuestas de UX; algunos ejemplos allí reflejan el modelo heredado y **no** deben interpretarse como requisitos definitivos.

**Prioridad al implementar:** decisiones explícitas actuales del producto → este documento para criterios visuales aprobados → código existente para comportamiento real → documentación histórica como contexto. Si difieren, inspeccionar y documentar la discrepancia, no copiar automáticamente patrones obsoletos.

## 4. Color

### 4.1 Identidad cromática

El tema usa `MaterialTheme.colorScheme` y, por defecto, **color dinámico en Android 12+**. MiFlujo **no tiene un color de marca obligatorio para todas las pantallas**: su carácter visual depende de las superficies limpias, la tipografía y los acentos semánticos.

En tema claro, incluso con color dinámico activo, `withCleanLightSurfaces()` reemplaza varias superficies por neutros propios. Con color dinámico desactivado se utilizan las paletas estáticas Material de `Theme.kt`. El tema oscuro utiliza la paleta oscura de Material o la dinámica del dispositivo; **no** se debe asumir que los neutros claros se aplican en oscuro.

| Token actual | Valor claro | Uso orientativo |
| --- | --- | --- |
| `LightBackground` | `#FCFCF9` | Fondo general |
| `LightSurface` | `#FFFFFF` | Superficies principales |
| `LightSurfaceContainer` | `#F7F8F4` | Contenedores sutiles |
| `LightSurfaceContainerHigh` | `#F1F3EF` | Agrupación con más presencia |
| `LightSurfaceContainerHighest` | `#E9ECE7` | Jerarquía adicional |
| `LightSurfaceVariant` | `#EEF1EC` | Superficies alternativas |
| `LightOnSurface` | `#1A1C19` | Texto primario en superficie clara |
| `LightOnSurfaceVariant` | `#4F5A53` | Información secundaria |
| `LightOutlineVariant` | `#DDE2DC` | Separadores y bordes suaves |

Otros tokens definidos en `Color.kt` incluyen `LightOutline = #7D8780`, `LightSurfaceDim = #DDDCD8`, `LightSurfaceBright = #FCFCF9`, `LightSurfaceContainerLowest = #FFFFFF` y `LightSurfaceContainerLow = #FFFFFF`. No es necesario usar cada nivel en cada pantalla.

### 4.2 Semántica financiera

| Significado | Claro | Oscuro |
| --- | --- | --- |
| Ingreso / valor positivo | `#2E7D4F` | `#81C995` |
| Egreso / valor negativo | `#B3261E` | `#FFB4AB` |

Usar las funciones `financePositiveColor()` y `financeNegativeColor()` donde corresponda; no dispersar hexadecimales. Estas funciones seleccionan el tono a partir de la luminancia del fondo del tema. Para mensajes de error, utilizar roles Material como `error` y `errorContainer`, no confundir «egreso» con «error».

Conservar signos, símbolos monetarios, etiquetas y estados: `+ C$…`, `− US$…`. No combinar saldos entre monedas ni convertir la codificación cromática en el único indicador.

### 4.3 Colores de categorías

La personalización de iconos y colores del catálogo es una **funcionalidad experimental en integración**, no un cambio de paleta global. Sus diez colores opcionales son metadatos de categoría; no reemplazan los roles `primary`, `surface`, `error` ni los colores financieros. Su selección es opcional, admite reutilización y contempla el valor predeterminado del tema. Ver el PR de categorías y la implementación vigente antes de asumirla como publicada.

## 5. Tipografía

- Fuente actual: `FontFamily.Default`, sin tipografía de marca externa.
- `Type.kt` personaliza explícitamente `bodyLarge`: **16 sp**, **24 sp** de interlineado, peso normal y `0.5.sp` de espaciado entre letras.
- Los demás estilos provienen principalmente de la escala por defecto de Material 3.
- En el código se emplean `headlineMedium`, `titleLarge`, `titleMedium`, `titleSmall`, `bodyLarge`, `bodyMedium`, `bodySmall` y `labelMedium/Small` para crear jerarquías locales.

**Criterio:** usar los estilos semánticos de `MaterialTheme.typography`, con ajustes de peso solo cuando aporten jerarquía. Los importes principales deben sobresalir por posición, tamaño y peso; las etiquetas auxiliares deben ser secundarias pero legibles.

**No hay aún una escala tipográfica específica de MiFlujo completamente definida.** No inventar una tabla de tamaños «oficiales» distinta del código. Si un experimento demuestra la necesidad de nuevos tokens, consolidarlos después de la revisión.

Respetar escalado de fuente; evitar tamaños fijos de contenedor que corten importes, títulos o textos de ayuda.

## 6. Espaciado, superficies y formas

La aplicación emplea principalmente valores `dp` locales, no una colección central de tokens de espaciado. Patrones observados:

- **8 dp**: separación corta dentro de grupos o entre elementos relacionados.
- **12–16 dp**: separación y padding de filas, controles y tarjetas.
- **20 dp**: margen horizontal habitual de pantallas como Inicio, Reporte y Ajustes.
- **18–22 dp**: distancia vertical entre secciones en algunas pantallas.
- **24 dp y mayores**: separación contextual y áreas especiales; no son obligaciones globales.

Estas son **convenciones existentes**, no una retícula inmutable. Antes de crear un token global, comprobar que el patrón se repite y que no rompe una pantalla.

Se usan `Card`, `Surface`, contenedores del esquema Material y `MaterialTheme.shapes.small/medium`. `MiFlujoTheme` no instala actualmente una colección de formas personalizada; predominan las formas por defecto de Material 3.

Preferir separaciones y contraste de superficie a bordes innecesarios. Evitar anidar tarjetas por decoración. Mantener agrupación clara y densidad adecuada para números y textos.

## 7. Navegación y jerarquía de pantallas

La estructura actual de `MiFlujoApp.kt` utiliza:
- `Scaffold` principal y `TopAppBar` con el nombre **MiFlujo**.
- `NavigationBar` inferior para **Inicio**, **Movimientos** y **Reporte**.
- Acceso a **Ajustes** desde el icono de engranaje superior; no ocupa una pestaña principal.
- `ExtendedFloatingActionButton` con «+ Agregar» como acción primaria de captura.
- Ajustes como superficie secundaria con navegación de vuelta.

Responsabilidades:
- **Inicio:** vistazo del mes, flujo neto separado por moneda y movimientos recientes. No duplicar el reporte entero.
- **Movimientos:** búsqueda/consulta, filtros y acciones sobre registros; favorecer lectura rápida de importes, fecha y clasificación.
- **Reporte:** mayor densidad justificada por el análisis mensual, desglose y exportación PDF.
- **Ajustes:** agrupar funciones poco frecuentes y operaciones sensibles. Las opciones de gestión extensas deben tener su propia superficie, no saturar la lista principal.
- **Captura:** registro y edición de movimientos con mínima fricción, conservando datos al alternar opciones o regresar desde una selección secundaria.

No añadir secciones a la navegación inferior solo porque una funcionalidad nueva exista.

## 8. Componentes e interacciones

Preferir componentes Material 3 estándar (`Card`, `Button`, `TextButton`, `OutlinedTextField`, `NavigationBar`, `AlertDialog`, `Snackbar`, etc.) antes de implementar equivalentes artesanales.

Aplicar variaciones propias cuando aporten valor claro: la captura rápida en cápsula, selección contextual de categorías y patrones de visualización financiera pueden justificar componentes especializados.

Reglas de composición:
- **Una acción primaria reconocible** por contexto; secundarias con menor énfasis.
- **Estados explícitos:** seleccionado, disponible, deshabilitado, cargando, guardado, error, archivado.
- **Jerarquía progresiva:** opciones opcionales (p. ej., color del icono) pueden empezar contraídas y expandirse a petición.
- **Persistencia del contexto:** abrir selectores o crear una categoría no debería descartar el borrador de un movimiento.
- **Acciones peligrosas:** usar confirmación adecuada, lenguaje claro y feedback que no dependa solo del color.
- **Listas:** mostrar la información necesaria; evitar que detalles secundarios compitan con importe y fecha.

El catálogo personalizado debe conservar la independencia entre nombre, icono y color; ninguno determina los otros. No volver a introducir subcategorías en flujos de usuario solo por imitar documentación antigua: la decisión experimental actual es un catálogo visible de un solo nivel, preservando referencias históricas internamente.

## 9. Captura rápida y movimiento expresivo

La **cápsula** es una exploración distintiva de MiFlujo. Su incorporación a la interfaz real se evalúa en `app`; no confundir una demostración o variante diagnóstica con un comportamiento aprobado para producción.

Dirección UX:
- Alternadores compactos para tipo de movimiento (`+` / `−`) y moneda (C$ / US$).
- Importe como protagonista; fecha, categoría y detalle como información contextual.
- Categoría accesible desde el control integrado en la cápsula, sin sobrecargar la superficie con opciones permanentes.
- Registro consecutivo, edición y revisión sin pérdida de estado y sin guardados duplicados.
- Animación y háptica como feedback útil, no espectáculo; conservar acciones inmediatas y semántica accesible.

Usar Compose y APIs públicas. Preferir transiciones discretas, interrumpibles y compatibles con la escala de animación del sistema; ofrecer resultado comprensible incluso sin animaciones. La transición de Ajustes en `MiFlujoApp.kt` utiliza actualmente alrededor de **200 ms** al entrar y **180 ms** al salir: ejemplo del código actual, **no** duración obligatoria para todos los componentes.

No imponer una duración, layout de cápsula ni componente Expressive como regla universal. Cualquier iteración nueva puede reemplazar un patrón anterior tras revisión UX.

## 10. Accesibilidad y robustez visual

Antes de considerar consolidado un patrón, revisar:
- **Tema claro y oscuro**; en Android 12+ comprobar el efecto del color dinámico.
- **Contraste** de texto, iconos, controles y estados, incluyendo colores de categorías.
- **Fuente ampliada** y ancho reducido: permitir salto de línea, desplazamiento o reorganización sin ocultar acciones.
- **TalkBack**: etiquetas que expresen control, valor y estado; no anunciar iconos decorativos innecesarios.
- **Áreas táctiles** adecuadas; como referencia de la cápsula experimental se han preservado controles alrededor de **48 dp** cuando corresponde.
- **Teclado e IME**: que el formulario, acciones y foco permanezcan utilizables.
- **Movimiento reducido/desactivado** y pulsaciones rápidas.
- **Feedback de acciones**: éxito, error y estados intermedios entendibles por texto y semántica.

Estas son pautas de validación, no afirmaciones de que cada pantalla ya pasó todas las pruebas manuales.

## 11. PDF y documentos exportados

El informe PDF no tiene que imitar las tarjetas Compose. Mantener presentación documental sobria: formato A4 vertical, tabla legible, importes alineados, encabezados y saltos de página cuidados; usar verde/rojo solo como apoyo semántico. Ver `docs/ui-design.md` para las pautas documentales. El PDF es para lectura y compartir; no sustituye el respaldo JSON.

## 12. Cómo experimentar sin perder coherencia

1. **Identificar el problema:** qué fricción de uso, accesibilidad o consistencia pretende resolver el cambio.
2. **Experimentar en el contexto correcto:** `catalogLab` para funcionalidad; interfaz real `app` aislada para evaluar UX integrada.
3. **Reutilizar antes de duplicar:** estudiar temas y componentes existentes; crear uno nuevo solo cuando la variante estándar no resuelva el problema.
4. **Comparar y validar:** interacción real, estado, teclado, pantallas pequeñas, claro/oscuro, accesibilidad y casos de error.
5. **Decidir explícitamente:** **experimental** (se sigue probando), **aprobado para integración** (funciona en el entorno real aislado) o **adoptado para producción** (tras revisión y release).
6. **Actualizar documentación solo al consolidar:** sustituir la regla anterior si corresponde y dejar constancia breve del motivo. No convertir cada ensayo visual en un estándar obligatorio.

**Regla central:** `DESIGN.md` es una línea base flexible, no una cárcel creativa. Una solución nueva puede desviarse de ella deliberadamente; solo hay que explicar qué mejora, verificarla y documentarla si se adopta.

## 13. Alcance y mantenimiento

Este archivo es una referencia para quienes diseñan y modifican UI (incluidos agentes de código); **no ejecuta ni valida por sí solo cambios**. No requiere rediseñar componentes existentes para «cumplir» sus recomendaciones. No es una guía de bases de datos, migración, Firebase ni publicación.

Al modificar decisiones visuales globales, revisar conjuntamente `ui/theme/*`, las pantallas afectadas y este archivo. Mantener `docs/ui-design.md` como fuente histórica de evolución sin copiar propuestas obsoletas a la interfaz actual.

Última revisión documental: **2026-11-21**. Base inspeccionada: rama `dev`; evolución de categorías bajo experimentación en PR #148.
