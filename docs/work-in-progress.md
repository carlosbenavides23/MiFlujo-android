# Trabajo en curso: `ux/quick-entry`

## Objetivo

Explorar una captura de movimientos más rápida y compacta, conservando las
validaciones, persistencia local y reglas actuales. La propuesta UX anterior está documentada en `docs/ui-design.md`; la demostración de tres líneas se describe al final de este documento.

## Rama y estado

- Rama activa: `ux/quick-entry`, creada desde `main` actualizado en `d1d3f56`.
- El árbol contiene documentación de preparación y una demostración de interfaz sin persistencia.
- Base verificada: `main` se avanzó por fast-forward desde `cf6c025` hasta
  `origin/main` (`d1d3f56`).

## Formulario persistente existente

- `ui/movement/AddMovementDialog.kt` presenta un `AlertDialog` con formulario
  vertical. Tipo y moneda son dos grupos separados de opciones; el monto es
  texto decimal y la fecha se escribe en formato `dd/MM/yy`.
- Categoría aparece para egresos y subcategoría solo para costo fijo. El detalle
  es opcional.
- El mismo diálogo se usa para crear y editar. En edición precarga valores
  existentes; en alta inicia sin tipo ni moneda, con fecha de hoy.
- La validación del formulario usa `MovementBusinessRuleValidator`; el envío
  pasa por `MovementViewModel` y `MovementRepository` hacia persistencia.

## Alcance inicial propuesto (antecedente)

- Prototipar una cápsula compacta de dos líneas: un único alternador ingreso/
  egreso, un único alternador C$/US$ y monto; fecha compacta con calendario;
  categoría condicional y subcategoría para costos fijos.
- Mantener detalle opcional y explorar guardar/continuar para capturas seguidas.
- Probar teclado, tamaños y accesibilidad; preservar edición, validaciones y
  feedback de guardado.
- Decidir superficie final, valores iniciales y qué campos conservar al
  continuar. No asumirlos resueltos antes de probar.
- Excluir cambios a Room, UUID, reglas financieras, backup y Firebase/Cloud Sync.

## Comprobaciones al preparar

- `./gradlew :app:testDebugUnitTest :app:assembleDebug`: **BUILD SUCCESSFUL**.
- Tests unitarios: 32 suites, 232 tests; 0 fallos, 0 errores, 0 omitidos.
- Advertencias no bloqueantes: `ConnectivityManager.allNetworks` obsoleta en
  `MiFlujoApp.kt:583`; dos bibliotecas nativas se empaquetaron sin stripping.
- No se ejecutaron pruebas instrumentadas ni validación visual: `adb` no pudo
  iniciar su servidor (`Operation not permitted` al abrir el listener; USB no
  está disponible en este entorno).

## Decisiones y referencias

- La propuesta acordada para explorar y sus preguntas abiertas están en
  `docs/ui-design.md`; el flujo por módulos está en `docs/decisions.md`.
- Mantener `main` como base estable y trabajar el módulo en esta rama con
  iteraciones; revisión mediante un PR al concluir.
- Hay un desfase ya documentado entre `versionName/versionCode` de Gradle y la
  release `v0.4.0`; no es parte de este módulo.

## Pendientes y próximo paso

- Revisar la demostración con datos ficticios en una instalación aislada de los datos reales y Cloud Sync.
- Validar manualmente las transiciones provisionales y la conservación de estado descritas en el último checkpoint antes de integrar el guardado.

## Iteración visual de cápsulas (15 de noviembre de 2026)

- La opción **Probar cápsulas** en el diálogo de alta abre una demostración aislada; el formulario existente de alta y edición sigue disponible y conserva su guardado habitual.
- La cápsula activa presenta alternadores, monto, detalle, calendario, categoría y subcategoría condicional. Se eligieron egreso y C$ como valores iniciales solo para esta prueba.
- Los movimientos se mantienen en memoria y se validan con las reglas del formulario existente. Se pueden añadir, revisar y editar; para cambiar de pendiente hay que aplicar o descartar los cambios de la cápsula activa. Confirmar muestra un resumen explícito de que no se guardó nada. Salir solicita confirmación si hay trabajo provisional.
- No hay persistencia por lotes ni cambios a Room, Firebase o las reglas financieras. Los estados provisionales se pierden al cerrar o recrear la pantalla.
- Pendiente: revisión visual y de interacción con teclado, tamaños de letra y accesibilidad en instalación aislada con datos ficticios; definir persistencia transaccional del conjunto, prevención de duplicados y comportamiento ante errores antes de integrar el flujo real. La propuesta previa de dos líneas en `docs/ui-design.md` es antecedente; esta iteración explora tres líneas según el alcance solicitado.
- Verificación de esta iteración: `git diff --check` sin errores. Las pruebas y la compilación no se pudieron completar: el wrapper no puede escribir en la caché habitual; con caché temporal, Gradle falla al obtener una IP local dentro del aislamiento y, fuera de él, el daemon quedó detenido al arrancar, incluso con `--no-daemon`. Por tanto, esta iteración no está compilada ni probada. `adb devices` tampoco pudo iniciar el servidor por permisos, por lo que no se realizaron pruebas instrumentadas ni visuales.

## Refinamiento visual de la cápsula

- Se redujeron márgenes y espacios verticales, se retiró el título permanente y se conservaron las tres líneas. El detalle y el monto usan campos compactos de una línea; los alternadores tienen anchos definidos y el monto ocupa el espacio restante. Los controles comparten esquinas redondeadas y altura mínima de 48 dp; la indicación de edición sigue visible cuando corresponde.
- El flujo provisional y las reglas financieras no cambiaron. La reducción porcentual y la legibilidad con fuentes grandes, teclado y lectores de pantalla siguen pendientes de medición visual en un dispositivo aislado.
- `git diff --check` pasó. Se intentó ejecutar `:app:testDebugUnitTest` y `:app:assembleDebug` con Gradle offline y caché temporal fuera del aislamiento: el proceso quedó detenido al iniciar el daemon y terminó por el límite de 60 segundos, sin compilar ni ejecutar pruebas. La validación visual e instrumentada continúa pendiente.

## Ajustes de interacción y densidad

- Se retiró la acción global de descarte y se añadieron botones discretos para limpiar monto y detalle cuando contienen texto, con etiquetas accesibles y zona táctil de 48 dp. Los alternadores de tipo y moneda son algo más anchos y visibles; el detalle usa tipografía secundaria y sigue en una línea.
- Las alturas táctiles de detalle, fecha y categoría se conservaron en 48 dp; no se ha comprobado visualmente una reducción adicional de altura. No se modificaron persistencia ni reglas financieras.
- Al retirar el descarte global, una edición de pendiente debe aplicarse antes de seleccionar otro; limpiar campos no cancela la edición. Queda pendiente revisar una salida localizada y discreta para ese caso, así como ancho en pantallas pequeñas, teclado, lector de pantalla y tamaños de letra grandes en una instalación aislada.
- `git diff --check` pasó. El intento offline de `:app:testDebugUnitTest` y `:app:assembleDebug`, con caché temporal y fuera del aislamiento, quedó detenido al arrancar el daemon y alcanzó el límite de 60 segundos. No se ejecutaron pruebas ni se verificó compilación o aspecto visual en esta iteración.

## Tercera línea sin contornos

- Fecha y categoría muestran icono, texto y chevron contiguo sin borde ni fondo individual. Mantienen sus selectores existentes y un área táctil invisible de al menos 48 dp; sus etiquetas de accesibilidad identifican el campo y el valor. La subcategoría y el resto de la cápsula no se modificaron.
- `git diff --check` pasó. El intento offline de `:app:testDebugUnitTest` y `:app:assembleDebug` con caché temporal fuera del aislamiento volvió a detenerse al iniciar el daemon y alcanzó el límite de 60 segundos: compilación y pruebas no verificadas. Queda pendiente revisar visualmente la distribución, truncamiento con fuentes grandes y accesibilidad en dispositivo aislado.

## Ajuste mínimo de tercera línea

- Iconos y chevrons reducidos de 16 a 14 dp y texto cambiado a `labelMedium`; el contenido se desplaza ligeramente hacia abajo mediante padding interno, conservando el área táctil mínima de 48 dp.
- `git diff --check` pasó. La compilación `:app:assembleDebug` offline, con caché temporal y fuera del aislamiento, volvió a detenerse al iniciar el daemon y alcanzó el límite de 30 segundos. Compilación y revisión visual pendientes.

## Resúmenes pendientes compactos

- Las tarjetas agrupan importe destacado y edición a la derecha, con detalle, fecha y categoría/subcategoría debajo, sin numeración repetida. El texto puede ocupar más líneas cuando sea necesario; la acción de edición conserva al menos 48 dp de altura.
- Se quitó el espacio entre detalle y tercera línea de la cápsula activa sin cambiar sus controles ni la lógica provisional.
- `git diff --check` pasó. El intento offline de `:app:testDebugUnitTest` y `:app:assembleDebug` con caché temporal fuera del aislamiento quedó detenido al iniciar el daemon y alcanzó el límite de 30 segundos. Compilación, pruebas y revisión visual con fuentes grandes siguen pendientes.


## Cola provisional funcional — checkpoint vigente

La composición visual de tres líneas fue aprobada por el usuario; las propuestas
anteriores son antecedentes. La demostración continúa accesible desde **Probar
cápsulas** en el formulario de alta, sin invocar su callback de persistencia.

- `QuickEntryState.kt` separa las transiciones de la UI: alta validada, edición
  exclusiva, guardar/cancelar, solicitud y confirmación de eliminación, salida y
  resumen de demostración. Reutiliza la validación financiera existente.
- Cada pendiente tiene un UUID provisional estable, independiente de los UUID de
  persistencia. Editar reemplaza el mismo elemento; cancelar restaura la captura
  nueva sin modificar el original. Se bloquea otra edición si hay captura sin
  resolver o una edición abierta, con explicación en la UI.
- Agregar conserva tipo y moneda; inicia fecha local actual y limpia monto,
  detalle, categoría y subcategoría. La transición es síncrona y el siguiente
  envío vacío falla la validación: no duplica por pulsaciones repetidas. Guardar
  edición y eliminar también son idempotentes frente a una repetición.
- En captura se ofrecen **Agregar otro / Confirmar** y en edición **Cancelar /
  Guardar cambios**. El menú discreto de cada tarjeta permite solicitar su
  eliminación con confirmación; eliminar el elemento editado descarta solo esa
  edición y recupera la captura nueva.
- Confirmar admite un movimiento individual o una cola más la captura válida;
  muestra únicamente el conteo de demostración, sin cambiar la cola ni anunciar
  guardado real. Salir con trabajo exige confirmar su descarte; desde el resumen
  la salida también se denomina **Descartar y salir**.
- El estado serializable y la apertura de la demostración usan
  `rememberSaveable` para las recreaciones soportadas por Android. Es estado de
  sesión, no un respaldo ni almacenamiento definitivo; la restauración real y
  los límites del estado guardado del sistema requieren prueba manual.
- Se añadieron 15 pruebas en `QuickEntryStateTest.kt`: validación, datos
  consecutivos, dobles pulsaciones e identidades, editar/guardar/cancelar,
  exclusividad, eliminación confirmada y durante edición, salida, confirmación
  individual/conjunta y serialización del estado.

Verificación: revisión estática de transiciones, callbacks y referencias, más
`git diff --check` sin errores. Por instrucción del usuario **no se ejecutaron
Gradle ni ADB**; las pruebas añadidas no se han ejecutado y la compilación no está
verificada. No interpretar las comprobaciones históricas como validación actual.

Pendientes: ejecutar manualmente tests unitarios y compilación; probar teclado,
edición/cancelación, eliminación y Atrás, doble toque, recreación de pantalla y
accesibilidad en instalación aislada con datos ficticios. La persistencia
transaccional definitiva, errores de guardado y su integración continúan fuera de
esta iteración. Room, Firebase, sincronización y reglas financieras no cambian.

## Color y movimiento de las cápsulas

- La cápsula activa mezcla localmente `surface` con un 38 % de `primaryContainer`, con contenido `onSurface`; reduce la intensidad del acento sin cambiar el tema global. Las pendientes usan `surfaceContainerHigh` y contorno `outlineVariant`; la pendiente en edición anima el contorno hacia `primary`. Los controles mantienen sus dimensiones y formas existentes.
- Tipo y moneda usan un fundido de 150 ms; las tarjetas pendientes aparecen con un fundido de 180 ms y el cambio de contorno de edición dura 180 ms. La acción de confirmación mantiene el feedback del botón Material y abre de inmediato el diálogo explícito de demostración. No se alteraron las transiciones de la cola.
- El proyecto usa Compose BOM `2026.02.01`; Material 3 está gestionado por ese BOM y la caché local contiene Material 3 Android 1.4.0. Se utilizan componentes y animaciones Compose existentes sin actualizar dependencias ni introducir APIs Expressive experimentales. La animación es una interpretación visual ligera, no una migración a componentes Expressive específicos.
- El acento depende del tema dinámico y puede no ser azul en todos los dispositivos. Contraste, diferencias entre cápsulas, fuentes grandes, comportamiento con movimiento reducido y entrada de tarjetas al desplazarse requieren revisión manual en claro y oscuro; el fundido puede repetirse cuando una tarjeta vuelve a componerse al desplazarse.
- Verificación: revisión estática y `git diff --check`. Por instrucción del usuario no se ejecutaron Gradle ni ADB; compilación, pruebas y validación visual quedan pendientes en el dispositivo aislado.

## Movimiento Expressive de cápsulas

- El BOM `2026.02.01` asigna Material 3 Android `1.4.0` en los metadatos locales; el tema sigue usando `MaterialTheme` con colores dinámicos cuando corresponde. La fuente local de Material 3 declara `MotionScheme.expressive()` como `internal`, por lo que no se puede configurar desde la app con esta versión. No se actualizaron dependencias ni se usan APIs experimentales; se emplean especificaciones públicas de Compose como alternativa.
- Tipo y moneda usan `AnimatedContent` con fundido y pequeño desplazamiento vertical; `MutableInteractionSource` y `animateFloatAsState` dan compresión y recuperación con `spring` sin cambiar el espacio reservado. El acento del tipo usa `animateColorAsState`. Los cambios de estado y la validación son inmediatos.
- Al añadir, solo el identificador recién creado inicia una entrada con `Animatable`: opacidad y desplazamiento corto con `spring`. Las tarjetas usan claves estables y `animateItem` para salida y reacomodo; no se desplaza la lista mediante código ni se vuelve a animar una tarjeta por desplazarse fuera y dentro. El contorno ya existente anima el paso a edición. Los botones Material conservan su feedback de interacción y el diálogo de confirmación sigue siendo inmediato.
- Las animaciones de Compose utilizan la escala de duración del contexto; queda pendiente comprobar en dispositivo la desactivación o reducción de movimiento, pulsaciones rápidas, lista desplazada, transiciones de eliminación y aspecto con fuentes grandes. La animación de entrada solo se inicia si la nueva tarjeta llega a componerse; no cambia el resultado de la operación.
- Revisión estática de las APIs en las fuentes locales y `git diff --check`; no se ejecutaron Gradle ni ADB por instrucción del usuario. Compilación, pruebas y comportamiento visual quedan pendientes de verificación manual.

## Transferencia espacial al agregar

- Se reemplazó la entrada local de la tarjeta por una representación visual temporal del importe y sus datos. Tras el alta provisional inmediata, viaja desde la parte superior de la cápsula activa hasta los límites medidos de la nueva tarjeta, interpolando posición, tamaño y superficie, con asentamiento `spring` moderado. La cápsula activa no se traslada; los controles quedan disponibles para seguir capturando.
- Se evaluaron las APIs públicas `SharedTransitionLayout`/`sharedBounds` en las fuentes de Compose Animation 1.10.4. Dado que captura y cola coexisten y el destino es un elemento lazy, se eligió un overlay medido (`onGloballyPositioned`, `Animatable`, `graphicsLayer`) en lugar de coordinar dos superficies mediante shared elements. No hay dependencias nuevas ni APIs internas.
- El estado visual (`PendingTransfer`, progreso y coordenadas) es independiente de `QuickEntryState`. El destino mantiene su sitio en la lista y se oculta visualmente durante el traslado; el overlay carece de semántica accesible. Las demás tarjetas conservan sus claves y `animateItem` para el reacomodo.
- La lista usa `rememberLazyListState`: solo hace `animateScrollToItem` si el destino no está completamente visible y espera su medición antes de iniciar el traslado. No se usa scroll instantáneo. Si falla la medición, se revela la tarjeta sin transferencia. Cancelar el scroll, añadir otro movimiento o abrir edición/diálogos cancela el efecto anterior y revela su destino; nunca se revierte ni se repite la operación de datos.
- Las animaciones usan la escala de duración de Compose; con escala cero se omite el traslado. No se guardan vuelos en recreaciones de pantalla. Se mantienen límites de espera para no ocultar indefinidamente una tarjeta si no llega a medirse.
- Verificación: revisión estática y `git diff --check` sin errores. No se ejecutaron Gradle ni ADB. Pendiente validar en teléfono el trayecto con teclado, scroll largo o interrumpido, pulsaciones rápidas, fuentes grandes y animaciones desactivadas/reducidas. El overlay es un resumen visual, no un morph exacto campo por campo; tarjetas más altas que el área visible y cambios de tamaño durante el vuelo requieren especial revisión.

## Animación de alternadores

- El alternador de tipo dibuja el signo mediante trazos: el trazo vertical se extiende o recoge con `animateFloatAsState` y `spring`, mientras el color de contenido sigue la transición de acento existente. Se mantiene la compresión y recuperación del botón mediante su interacción de presión; el área reservada y el cambio inmediato de tipo son los mismos.
- La moneda usa `AnimatedContent`, desvanecimiento y desplazamiento vertical con `spring`: C$ sale hacia arriba al pasar a US$, que entra desde abajo; la dirección se invierte al regresar. El contenido se recorta dentro del botón y los cambios de estado no esperan la animación. Las etiquetas accesibles permanecen en los controles, sin duplicar la semántica del contenido animado.
- Se emplean APIs públicas ya disponibles, sin añadir dependencias. La transferencia a la cola y la lógica de movimientos no se modificaron. Las animaciones de Compose siguen la escala de duración del sistema; falta verificar en teléfono movimiento reducido o desactivado, pulsaciones rápidas, contraste y texto ampliado.
- Revisión estática y `git diff --check` sin errores. Por instrucción del usuario no se ejecutaron Gradle ni ADB; compilación y pruebas visuales quedan pendientes.

## Paleta sólida de alternadores

- Los fondos de tipo y moneda usan los cuatro tokens locales solicitados (`#218B53`, `#C34850`, `#8060AE`, `#5E7F67`), independientes del color dinámico y comunes a modo claro y oscuro. El fondo anima su transición con `animateColorAsState`; los símbolos son blancos y se conservan la forma, dimensiones, etiquetas y animaciones anteriores.
- Contraste calculado con luminancia relativa sRGB frente a blanco: ingreso **4,30:1**, egreso **4,81:1**, C$ **5,00:1**, US$ **4,46:1**. Los colores de ingreso y US$ no alcanzan el umbral de 4,5:1 para texto normal; el signo gráfico de ingreso supera 3:1. Se mantuvieron los valores expresamente definidos y queda pendiente decidir si ajustar la paleta para mejorar el contraste del texto US$. Las fases de desvanecimiento tienen contraste visual transitorio menor.
- Revisión estática y `git diff --check` sin errores. Por instrucción del usuario no se ejecutaron Gradle ni ADB; compilación y comprobación visual con modos claro/oscuro y movimiento reducido quedan pendientes.

## Ajuste de color C$

- El token de C$ cambia a gris cálido `#8A8176`; los otros tres tokens, los símbolos blancos y la animación del fondo siguen vigentes. Esta decisión sustituye el color y la medición de C$ de la sección anterior.
- Contraste sRGB calculado contra blanco en ambos modos: ingreso **4,30:1**, egreso **4,81:1**, C$ **3,83:1** y US$ **4,46:1**. C$ y US$ quedan por debajo de 4,5:1 para texto normal; el signo de ingreso supera 3:1 para un símbolo gráfico. Se conservan los valores definidos; queda pendiente decidir si ajustar los fondos para cumplir el contraste de texto. Las transiciones de desvanecimiento presentan una reducción temporal adicional del contraste.
- Revisión estática y `git diff --check` sin errores. Por instrucción del usuario no se ejecutaron Gradle ni ADB; compilación y validación visual con ambos temas y movimiento reducido quedan pendientes.

## Plata acero para C$

- C$ usa fondo `#8995A5` y texto `#20242B`, con contraste sRGB calculado de **5,12:1** en estado estable, común a los temas claro y oscuro. Sustituye el gris y la medición de C$ de la sección anterior. Ingreso `#218B53`, egreso `#C34850` y US$ `#5E7F67` conservan sus valores.
- Fondo y texto de moneda animan su color; las transiciones de símbolo y presión, las dimensiones y la disposición se conservan. El contraste durante el cambio de color y de símbolo necesita validación visual; US$ permanece en 4,46:1 frente a blanco en estado estable.
- Revisión estática y `git diff --check` sin errores. No se ejecutaron Gradle ni ADB por instrucción del usuario; compilación y pruebas visuales quedan pendientes.

## Respuesta háptica de cápsulas

- Compose UI local 1.10.4 y minSdk 26: `LocalHapticFeedback` delega en `ViewCompat.performHapticFeedback`, que aplica la compatibilidad y los ajustes del sistema. `QuickEntryHaptics` centraliza los efectos; no requiere permiso `VIBRATE` ni controla el vibrador directamente.
- Tipo: `VirtualKey`; moneda y eliminación confirmada: `SegmentFrequentTick`; inicio de edición válido: `SegmentTick`; incorporación a la cola: `Confirm`; error de validación nuevo: `Reject`. Los impulsos se solicitan desde callbacks tras comprobar el cambio real, no desde recomposición ni desde la animación. La confirmación háptica de alta ocurre al incorporar el elemento, antes del posible scroll y traslado visual; su sincronía perceptiva necesita prueba manual.
- AndroidX Core aplica alternativas según versión: antes de API 34 los ticks pueden usar `CLOCK_TICK` o `CONTEXT_CLICK`; antes de API 30 `Confirm` puede usar `VIRTUAL_KEY`. Se omite `Reject` antes de API 30 porque su alternativa es `LONG_PRESS`. En errores sucesivos idénticos se evita repetir el rechazo hasta que cambie o se limpie el mensaje. El dispositivo puede no ofrecer un efecto, o ejecutarlo con intensidad distinta; se respeta su configuración sin forzar háptica.
- Revisión estática de código y fuentes locales de Compose/Core y `git diff --check` sin errores. Por instrucción del usuario no se ejecutaron Gradle ni ADB. Compilación y pruebas en teléfono quedan pendientes, incluidos ajustes desactivados, variantes de Android, acciones bloqueadas, validación y sincronía con la transferencia.


## Refinamiento de superficie y espaciado (16 de noviembre de 2026)

- La cápsula activa usa carbón `#1B1E23` en esquema oscuro y `surfaceContainerHigh` en claro, conservando los colores y el contenido propios de cada tema. Sustituye la mezcla azulada descrita en la sección de color anterior. La transferencia reutiliza la misma superficie de origen.
- Se uniformó el espacio entre grupos a 4 dp, el margen horizontal a 12 dp y la alineación vertical de fecha y categoría. Los alternadores pasan a 52 y 64 dp de ancho para dar algo más de espacio al monto; mantienen 48 dp mínimos de alto y sus colores, animaciones y háptica. No se modificaron las tarjetas pendientes ni los botones inferiores.
- Revisión estática y `git diff --check`; no se ejecutaron Gradle ni ADB por instrucción del usuario. Queda pendiente comprobar en teléfono ambos temas, contraste, tamaños de fuente grandes, pantallas pequeñas, teclado y subcategoría, así como la transferencia y la respuesta táctil.
