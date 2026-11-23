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


## Orden y dimensiones de la primera fila

- La fila de captura ahora presenta monto flexible, moneda y tipo, en ese orden. Ambos alternadores comparten 56 dp de ancho y 48 dp de alto; se mantienen los espacios de 4 dp, el detalle a ancho completo y los controles secundarios existentes. Estas medidas sustituyen los anchos descritos en la sección anterior.
- Se conservaron el selector de subcategoría para costos fijos, los callbacks, la háptica y las animaciones. El borrador ya tenía categoría nullable; no se cambió el modelo ni se implementó el futuro selector integrado.
- Revisión estática y `git diff --check`; por instrucción del usuario no se ejecutaron Gradle ni ADB. Pendiente en teléfono: US$ con fuentes ampliadas, monto largo y botón de limpieza, teclado, pantallas pequeñas, edición, háptica y transferencia animada.

## Tarjetas pendientes y acciones inferiores

- El importe con signo y moneda usa tipografía destacada y los colores financieros existentes para ingreso y egreso. El detalle, cuando existe, ocupa su propia línea; fecha y categoría/subcategoría aparecen en líneas secundarias separadas y el contenido puede ocupar varias líneas. La edición mantiene el borde animado y añade la etiqueta «En edición».
- Editar usa un icono con descripción accesible; editar y menú reservan 48 × 48 dp. La tarjeta conserva altura adaptable, su medición completa y las animaciones de lista. El resumen de la transferencia comparte la composición de la tarjeta y reserva el espacio de las acciones, sin cambiar las transiciones de estado.
- Las acciones inferiores usan una distribución adaptable: Confirmar y Guardar cambios son primarias, Agregar otro y Cancelar tienen contorno, y Salir es terciaria. Los botones conservan sus callbacks y al menos 48 dp de alto.
- Revisión estática y `git diff --check` sin errores. No se ejecutaron Gradle ni ADB. Pendiente verificar en teléfono contraste en ambos temas, importes y detalles largos, fuentes ampliadas, accesibilidad de iconos, botones con teclado y pantallas pequeñas, edición, eliminación y transferencia con lista desplazada o movimiento reducido. La distribución adaptable puede ocupar más de una fila y reducir el espacio vertical disponible.

## Compactación de tarjetas pendientes

- La tarjeta muestra importe y acciones en la primera fila y detalle, categoría, subcategoría y fecha en una segunda línea secundaria. Los metadatos largos se truncan con puntos suspensivos; su texto completo sigue en el contenido del componente. El importe puede ocupar más líneas y la tarjeta crecer sin altura fija. Esto sustituye la distribución de metadatos de la sección anterior.
- Se redujo el espacio vertical y se conservan los objetivos de 48 × 48 dp, los colores financieros y el borde animado de edición. La descripción accesible del icono sigue indicando el estado de edición. La transferencia comparte la misma composición y reserva el espacio de las acciones, con medición del destino sin cambios.
- Revisión estática y `git diff --check` sin errores; no se ejecutaron Gradle ni ADB. Pendiente revisar en teléfono truncamiento de metadatos, importes largos, fuentes ampliadas, accesibilidad, edición y transferencia con teclado, lista desplazada y movimiento reducido.

## Densidad de tarjetas y foco del teclado

- La disposición anterior sumaba una fila de al menos 48 dp, una línea de metadatos y 6 dp inferiores. Ahora importe y metadatos comparten una columna junto a las acciones: el mínimo habitual resulta de 48 dp de acciones más 8 dp arriba y abajo (64 dp), sin fijar la altura; el texto puede hacerla crecer. Los metadatos siguen truncándose en una línea. La transferencia reutiliza la composición y los límites medidos de la tarjeta.
- Las fuentes locales de Material 3 1.4.0 muestran que `DropdownMenu` usa por defecto un `PopupProperties` enfocable; las fuentes de Compose indican que un popup enfocable puede tomar el foco del método de entrada. Es una causa probable del teclado oculto, no una confirmación en dispositivo. Categoría y subcategoría usan ahora popup no enfocable; selección y toque exterior conservan sus callbacks. Como un popup no enfocable no recibe Atrás, se agregó `BackHandler` mientras esté abierto. No se solicita foco ni se reabre el teclado.
- Revisión estática de APIs locales y `git diff --check` sin errores; no se ejecutaron Gradle ni ADB. Pendiente en teléfono verificar apertura, selección, toque exterior y Atrás con teclado abierto y cerrado, navegación accesible/TalkBack, subcategoría, tamaños grandes, transferencia y acciones. El comportamiento del teclado y la accesibilidad del popup no están confirmados.

## Detalles largos y alternancia del selector

- Las tarjetas muestran el detalle por separado con hasta tres líneas y conservan categoría, subcategoría y fecha en otra línea secundaria. La altura sigue adaptándose al contenido y las acciones conservan 48 × 48 dp. La transferencia comparte el mismo resumen y medición.
- El selector sigue usando `DropdownMenu` no enfocable. Queda pendiente corregir la alternancia: el cierre por toque exterior puede propagarse al control y volver a abrirlo; cambiar solo `expanded = true` por una inversión de estado no evita esa secuencia. No se cambió el comportamiento que el usuario verificó para el teclado.
- Revisión estática y `git diff --check` sin errores; no se ejecutaron Gradle ni ADB. Pendiente validar altura y lectura con fuentes grandes y detalles largos, y resolver y probar la alternancia de categoría y subcategoría en teléfono.

## Corrección de reapertura de los selectores

- Diagnóstico estático: `CategoryMenu` y `SubcategoryMenu` seguían usando `DropdownMenu` no enfocable, con escrituras de estado en el control, dismiss, selección y Atrás. No había modificadores de foco adicionales. `AndroidPopup.android.kt` (Compose UI 1.10.4) llama a dismiss en DOWN/OUTSIDE y documenta la propagación a otras ventanas cuando el popup no es enfocable. Así, el toque en el ancla puede cerrar el menú antes de que el callback del control vuelva a abrirlo; falta confirmar la secuencia en ejecución.
- Se desactivó únicamente el dismiss exterior automático de esos popups. La superficie del diálogo observa el inicio del toque sin consumirlo: si está fuera de los límites medidos del ancla, cierra el selector; si está dentro, deja que el control alterne el estado una sola vez. Selección, Atrás y desmontaje cierran el menú. La categoría y la subcategoría comparten este manejo. No hay temporizadores ni solicitudes de foco, y los popups siguen sin ser enfocables.
- Esta implementación sustituye la tarea pendiente de alternancia de la sección anterior; el resultado en teléfono sigue pendiente. Revisar primer/segundo toque, toques rápidos, selección, cancelación exterior y Atrás, cambio entre ambos selectores, teclado abierto/cerrado, foco/cursor del campo y TalkBack. Los eventos sobre ventanas del sistema (por ejemplo el propio teclado) no pasan por el observador del diálogo; comprobar también ese caso.
- Revisión estática y `git diff --check` sin errores. No se ejecutaron Gradle ni ADB. No se cambiaron tarjetas pendientes, botones inferiores ni reglas de movimientos.

## Refinamientos de bajo riesgo tras la auditoría

- El padding horizontal de la cápsula pasa de 12 a 8 dp por lado y el padding inicial de los campos de 12 a 8 dp. En la primera fila esto recupera 12 dp de espacio para el texto; moneda y tipo mantienen 56 × 48 dp y limpiar conserva 48 × 48 dp. El campo sigue usando `BasicTextField` con `singleLine = true`; las fuentes locales de Compose Foundation 1.10.4 documentan su desplazamiento horizontal. La interacción y el cursor requieren comprobación en teléfono.
- En las tarjetas, categoría/subcategoría y fecha se miden por separado para reservar espacio a la fecha antes de truncar la categoría. El detalle sigue limitado a tres líneas y la altura es adaptable. Si el espacio es muy estrecho o la fuente es grande, la fecha puede ocupar varias líneas y la categoría truncarse. La transferencia comparte la composición y una sola medida de 48 dp por acción.
- US$ usa `#5B7C64` con texto blanco: contraste sRGB calculado de **4,654:1**, frente a 4,461:1 del color anterior. Los demás colores y las transiciones no se cambiaron; el contraste durante las transiciones sigue pendiente de revisión.
- El lápiz se deshabilita si la captura está modificada o hay una edición activa, sin cambiar las protecciones de `QuickEntryState`. La tarjeta actual conserva el borde animado y muestra «En edición»; los botones deshabilitados describen el motivo y la lista también lo explica mediante texto. Los errores usan una única región accesible de anuncios corteses, sin solicitar foco ni modificar la háptica.
- Revisión estática y `git diff --check` sin errores. No se ejecutaron Gradle ni ADB. Pendiente compilar y comprobar en teléfono: montos largos y limpieza, fuentes grandes y dimensiones fijas de alternadores, fecha y categoría en anchos estrechos, motivo de edición deshabilitada y TalkBack, anuncios de errores sin duplicados, transferencia y reacomodo, selector con teclado abierto/cerrado, movimiento reducido y disponibilidad vertical con teclado. La corrección anterior de los selectores y la navegación accesible siguen sin validación en dispositivo.

## Transformación visual de moneda

- El contenido de C$/US$ usa `AnimatedContent` con transiciones simultáneas: el símbolo saliente se comprime hasta 0,88 de su escala, se desvanece y se desplaza 2 dp; el entrante parte de la misma escala reducida y se expande con un `spring` de amortiguación 0,9, mientras aparece y recorre 2 dp en sentido inverso. El sentido cambia con la moneda. Sustituye la transición vertical descrita en «Animación de alternadores»; no es un morph geométrico de glifos.
- El contenido reserva todo el espacio del botón durante la transición, sin animar sus dimensiones; se conservan el recorte exterior, los colores y su interpolación, la semántica del botón, el cambio inmediato de moneda, la háptica y la animación de presión. Las APIs públicas se contrastaron con las fuentes locales de Compose Animation 1.10.4. Las transiciones de Compose usan la escala de duración del contexto; no se añadieron temporizadores ni dependencias.
- Revisión estática y `git diff --check` sin errores; no se ejecutaron Gradle ni ADB. Pendiente en teléfono: pulsaciones rápidas e inversión a mitad de animación, fluidez y rebote, contraste durante las interpolaciones, TalkBack, escala de animación reducida o cero, y texto US$ con fuentes ampliadas. El botón conserva el tamaño fijo de 56 × 48 dp y su recorte previo, por lo que no se ha demostrado que US$ quepa con todas las escalas de fuente.

## Morphing geométrico de moneda

- Se sustituyó `CurrencyWheel` y la transición anterior completa por `CurrencyMorph`, un `Canvas` con trazos Bézier propios. El desplazamiento reportado se corresponde con el `fillMaxSize()` añadido a `AnimatedContent`: sus hijos reciben las restricciones completas y el `Text` puede ocupar el botón mientras dibuja su texto desde arriba a la izquierda; alinear ese hijo ya expandido no centra los glifos. Las fuentes locales de Compose Animation 1.10.4 confirman que su política de medición pasa esas restricciones a los hijos. La nueva versión centra las coordenadas dibujadas directamente y no usa texto, capas de entrada/salida ni escalas de contenido superpuestas.
- `CurrencyMorphGeometry.kt` separa datos y funciones puras. C y U tienen tres segmentos cúbicos compatibles; se interpolan el punto inicial, los controles y los extremos de cada segmento. La segunda posición interpola tres segmentos inicialmente degenerados en un solo punto hacia una S. Se omite el trazo de longitud cero únicamente en el extremo C$, para no dibujar un punto con la terminación redondeada. El dólar combina una S y un asta cúbica recta: conserva su forma y se traslada continuamente al crecer la composición. Son contornos abiertos con trazo uniforme y extremos/uniones redondeados; no son contornos extraídos de una fuente ni un crossfade.
- Un solo progreso `animateFloatAsState` con `spring` de amortiguación 0,85 coordina toda la geometría y permite retomar posición y velocidad al cambiar de destino. Se limita el progreso geométrico a [0, 1] para evitar invertir la trayectoria degenerada por un pequeño sobrepaso del spring. El reloj de Compose aplica la escala de duración del sistema; sus fuentes locales muestran que con escala cero se evalúa el estado final.
- Se calculan los extremos reales de las curvas resolviendo las raíces de sus derivadas y se añade medio grosor de trazo. Esos límites se usan para centrar cada fotograma y ajustar el dibujo a un margen de 4 dp dentro del botón de 56 × 48 dp. La escala preferida sigue el tamaño tipográfico del tema, con un límite para que el dibujo quepa con fuentes ampliadas. La adaptación al espacio no sustituye la deformación de curvas. Se mantienen `CapsuleToggle`, la interpolación de colores, su semántica, la presión, la háptica y el cambio inmediato de moneda.
- Verificación: cinco pruebas de geometría pasaron mediante compilador Kotlin 2.2.10 y JUnit 4.13.2 ejecutados directamente en JVM con dependencias locales, sin Gradle. Cubren topología y extremos, rechazo de segmentos incompatibles, desplazamiento del dólar y sobrepaso, continuidad, límites y centrado (101 progresos, tres densidades y cinco escalas). Se renderizó y revisó una lámina SVG generada desde la geometría real con cinco estados intermedios en dos escalas; esto no ejecuta Compose ni valida la animación de Android. `git diff --check` y revisión de los archivos nuevos sin seguimiento, sin errores. No se ejecutaron Gradle ni ADB.
- Pendiente en teléfono: compilar la app; revisar C$ → US$ y US$ → C$, ambas monedas en reposo, centrado y recortes, pulsaciones rápidas e inversión a mitad de transición, claridad de las formas intermedias, háptica y presión, colores/contraste en ambos temas, fuentes grandes, TalkBack y escala de animación reducida o cero. Fluidez, duración percibida y ausencia de saltos en Android no están confirmadas.

## Investigación de morphing tipográfico y reagrupación

- La versión original en `HEAD` dibujaba `Text(..., style = MaterialTheme.typography.titleSmall)`. El tema local solo personaliza `bodyLarge`; los tokens locales de Material 3 1.4.0 resuelven `titleSmall` a `FontFamily.SansSerif`, peso Medium/500, 14 sp, altura de línea de 20 sp y tracking de 0,1 sp. La fuente concreta depende de la resolución del sistema Android; no se verificó el archivo de fuente del teléfono.
- El morph actual no conserva esa tipografía: utiliza curvas abiertas propias y un grosor fijo, y del estilo solo toma el tamaño. Su distribución fija (segunda posición en 12 unidades y dólar entre 14 y 24) tampoco reproduce avances, kerning ni tracking originales. Corregir únicamente esas constantes no recuperaría el espaciado tipográfico.
- Extracción viable con APIs públicas: resolver el Typeface Android del mismo `FontFamily.Resolver`, configurar tamaño/peso/estilo y extraer los contornos rellenos con `Paint.getTextPath`. Compose Graphics 1.10.4 ya dispone de `PathIterator` respaldado por la dependencia existente `androidx.graphics:graphics-path`; no hace falta una dependencia nueva. `TextLayoutResult.getPathForRange` no sirve para extraer glifos: su implementación Android devuelve la geometría de selección del texto.
- Limitación: extraer una ruta no produce automáticamente correspondencia entre contornos, número de segmentos, orientación y comienzo de C/U; pueden existir curvas cónicas o contornos adicionales según la fuente. Líneas/cuadráticas se pueden elevar a cúbicas y subdividir exactamente cuando la topología es compatible; no se debe asumir compatibilidad por compartir el carácter. El trazado vectorial tampoco garantiza la misma rasterización/hinting que el renderizado de texto de Compose a tamaños pequeños.
- Propuesta pendiente de decisión del usuario: usar el `TextStyle` original y renderizado de texto de Compose en reposo; extraer la geometría real de esa misma fuente para la transición y normalizar su correspondencia, con aproximación geométrica acotada solo si resulta necesaria y se autoriza. No introducir crossfade ni letras diseñadas a mano. Validar el posible cambio de rasterización entre texto y trazado en teléfono.
- Las posiciones propuestas deben partir del layout medido de las cadenas completas C$ y US$ (avances por carácter y baseline), no de anchos individuales sumados. Una separación adicional transitoria, por ejemplo proporcional a `4t(1−t)`, vale cero en ambos extremos; se aplica sobre las posiciones interpoladas y se recentra el conjunto. La S surge de contornos degenerados y el dólar conserva su contorno. Esta sección describe el diseño propuesto, no comportamiento implementado.
- En esta revisión solo se actualizó este checkpoint y se ejecutó `git diff --check`. El código permanece intacto hasta decidir la estrategia de compatibilización. No se ejecutaron Gradle ni ADB. Pendiente: decidir si aceptar aproximación acotada únicamente durante el morph; implementar y probar tipografía/espaciado en reposo, ambos sentidos, inversiones rápidas, escalas de fuente y animación, centrado, dólar reconocible, TalkBack y ausencia de recortes en dispositivo.

## Tipografía original y separación transitoria de moneda

- El usuario autorizó aproximación geométrica acotada exclusivamente durante el morph. Se sustituyeron las letras manuales por contornos extraídos de la fuente Android resuelta con el mismo estilo del selector. En reposo se dibuja exactamente `Text("C$"/"US$", style = MaterialTheme.typography.titleSmall, maxLines = 1)`, centrado en un Box que no fuerza el tamaño del Text. Esto recupera la familia SansSerif del sistema, peso Medium/500, 14 sp, altura de línea 20 sp y tracking 0,1 sp del estilo original. El código no fija un archivo Roboto ni introduce fuentes.
- `CurrencyMorphFont.kt` concentra la adaptación Android: mide ambas cadenas completas con Compose, obtiene su baseline y posiciones horizontales, configura Paint con el Typeface resuelto y extrae el run completo con `getTextPath`. Asigna los contornos a las celdas de sus glifos y lleva sus posiciones al mismo origen centrado que usa el Text. Los contornos rellenos conservan también los huecos del dólar; su forma permanece constante durante la traslación. El modelo se recuerda por estilo, fuente, densidad, dirección y restricciones, sin recalcularlo por cambios de color o de progreso.
- Durante la transición, `PathIterator` convierte cónicas con tolerancia 0,01 píxel y `Path.approximate` aplana curvas con tolerancia 0,02 píxel. La normalización de los bordes añade como máximo 0,07 píxel de error geométrico (paso de muestreo de 0,14 píxel). Estas cotas se refieren a los contornos antes de colocarlos; no son una garantía de igualdad de píxeles frente al hinting/rasterizador de texto. No se aplica esta aproximación al Text de reposo.
- C/U se emparejan identificando sus dos terminales y normalizando por separado sus bordes exterior e interior. La revisión con una fuente local detectó que el emparejamiento inicial por cercanía cerraba excesivamente la abertura; se sustituyó por esta correspondencia de bordes. No se emparejan agujeros arbitrariamente. La S crece desde contornos degenerados; el dólar se traslada con su geometría intacta. Todo depende de un único progreso reversible y un spring críticamente amortiguado con umbral pequeño para reducir el salto al entregar el dibujo al Text.
- El espaciado permanente sale del layout de C$ y US$, no de las antiguas constantes de posición. Se añade una apertura temporal de hasta 2 dp por lado, limitada por el espacio disponible y multiplicada por `4t(1−t)`, que vale cero en ambos extremos. La corrección de centrado sigue los centros ópticos de los estados tipográficos medidos y mantiene sus offsets originales en los extremos; no escala los glifos para alterar su tamaño de reposo.
- Si no se pueden extraer los glifos, C/U tienen contornos no admitidos, no se reconocen sus terminales, se excede el límite de muestreo o el texto medido ya desborda, se conserva el Text original sin deformación. No se vuelve a las letras genéricas. Una escala de fuente que no quepa en los 56 × 48 dp sigue siendo una limitación del tamaño fijo del botón; no se reduce silenciosamente la fuente.
- Verificación: siete tests de geometría pasaron en JVM directa con Kotlin 2.2.10/JUnit 4.13.2 (topología, correspondencia y grosor, error de normalización, posiciones, separación temporal, dólar y continuidad). También pasó una compilación aislada de los auxiliares del selector con el plugin Compose y las dependencias locales, sin Gradle; esto no compila ni prueba la app completa. Se revisó una lámina de seis estados con contornos de Noto Sans obtenidos mediante AWT en el equipo, que no reproduce Android ni la fuente del teléfono. `git diff --check` y comprobación de archivos nuevos sin seguimiento sin errores. No se ejecutaron Gradle ni ADB.
- Pendiente en dispositivo: comparar C$/US$ en reposo con el Text original; observar el primer y último fotograma del morph por diferencias de hinting/peso/baseline; comprobar separación y reagrupación en ambos sentidos, pulsaciones rápidas e inversiones, dólar reconocible, centrado y recortes con distintas escalas de fuente, TalkBack, háptica y ambos temas; verificar escala de animación reducida/cero. La continuidad de rasterización y el adaptador nativo de extracción aún no se han ejecutado en Android.

## Renderizado aislado de glifos durante el morph

- Se confirmó que `CurrencyMorph.kt` agrupaba los contornos de C/U, S y `$` en un solo `Path` con `EvenOdd`. Ahora convierte y dibuja cada `GlyphOutline` por separado, manteniendo `EvenOdd` en el Path de ese glifo para conservar los huecos internos (incluido el del dólar). No se tocaron geometrías, posiciones, separación, spring, texto de reposo ni controles.
- La interacción de `EvenOdd` entre contornos de glifos superpuestos es una causa plausible del artefacto, pero la hipótesis visual no queda confirmada por inspección estática. Las pruebas JVM de geometría no ejercitan el rasterizador de Compose ni los píxeles de las intersecciones; confirmar en teléfono si desaparecen las deformaciones intermedias del `$` y revisar ambos sentidos, pulsaciones rápidas, legibilidad y ausencia de huecos/saltos.
- Los tests JVM de geometría y `git diff --check` se ejecutaron sin Gradle; no se ejecutó ADB. La verificación visual del artefacto queda pendiente en dispositivo.

## Continuidad del renderizado del dólar

- El usuario confirmó que separar los Paths no eliminó el ajuste final de la línea vertical del `$`. La inspección identifica un cambio instantáneo de representación: mientras `transition.isRunning` se dibujaban polígonos rellenos; al terminar se sustituían por `Text`. El antialiasing de un Path no reproduce necesariamente el hinting del texto, aun con la misma fuente. El Paint de extracción usa hinting, pero extraer y aplanar contornos no aplica el rasterizado de texto al dibujarlos. Esta es la causa probable del salto; no se ha confirmado mediante comparación de píxeles en Android, ni se puede afirmar que aparezca sin animación usando solo las pruebas JVM disponibles.
- Las posiciones parten del mismo layout centrado y baseline de Compose. El desplazamiento y el recentrado producen posiciones fraccionarias que también pueden cambiar la cobertura de una línea fina durante el recorrido. No se encontró otro cambio explícito de grosor o color al terminar: Path y Text toman `LocalContentColor`, cuyo tween y el del fondo pertenecen a `CapsuleToggle`. No se modificaron esos tweens ni se redondearon las posiciones para intentar ocultar el problema.
- Corrección localizada en `CurrencyMorph.kt`: C/U y S siguen dibujándose como contornos geométricos. El dólar se dibuja durante todo el recorrido con `drawText(TextLayoutResult)`, que usa el renderizador de párrafos de Compose, igual que Text. Se mide la cadena completa C$ con su prefijo transparente (solo cambia la pintura, no las métricas); así se conservan el contexto tipográfico, tracking y baseline. Se traslada ese layout siguiendo exactamente el desplazamiento del contorno del dólar, incluido el recentrado, y se aplica el color animado actual. El Text original de la cadena completa se conserva en reposo. No se añadió crossfade ni se modificaron las curvas, correspondencias, spring, separación o reagrupación.
- Las fuentes locales de Compose UI Text 1.10.4 confirman que `drawText` pinta `multiParagraph` y que el prefijo transparente se aplica con un span de color. Pasaron siete tests JVM de geometría y la compilación aislada del selector con el plugin Compose y dependencias locales, sin Gradle. `git diff --check` sin errores. Esto no ejecuta el rasterizador de Android ni valida la app completa; no se ejecutaron Gradle ni ADB.
- Pendiente en teléfono: observar específicamente el asta vertical del dólar antes, durante y después del último fotograma en ambos sentidos; repetir con escala de animación lenta y normal, e invertir rápidamente el destino. Comprobar que no cambia bruscamente su grosor, baseline o posición al entrar/salir del reposo, que no aparece el prefijo C duplicado y que no hay recortes ni cambios de color. Revisar ambas monedas en reposo, ambos temas, fuentes ampliadas y animaciones desactivadas. Si el ajuste persiste, comparar capturas del extremo animado y del Text en reposo con el mismo color y posición para distinguir diferencias de cobertura subpíxel de diferencias de layout; no retocar C/U sin esa evidencia.

## Inspector manual del morph de moneda

- En instalaciones con `ApplicationInfo.FLAG_DEBUGGABLE` activo, «Registro con cápsulas» ofrece «Inspeccionar morph de moneda». El diálogo tiene slider, porcentaje, accesos a 0 %, 50 % y 100 % y cierre; su estado es local y no modifica la moneda ni movimientos de la demostración. No se muestra en una instalación no depurable.
- `CurrencyMorph` acepta progreso manual opcional y comparte geometría, posiciones y dibujo con el selector; 0 % y 100 % muestran el Text de reposo, igual que el selector normal, y los valores interiores muestran los contornos con el dólar dibujado mediante texto. El fondo y el texto interpolan directamente sus colores. La vista se mide en 56 × 48 dp y se amplía 3× solo en la capa visual; el escalado puede alterar la apariencia de los píxeles frente al tamaño normal. Si la fuente no permite construir el modelo o desborda, se mantiene el fallback de texto existente y no se verá morph.
- Pendiente en teléfono: abrir la demostración en una instalación debug, abrir el inspector y detenerse en 0 %, 25 %, 50 %, 75 % y 100 %; revisar ambos sentidos, el asta del dólar y los saltos cerca de los extremos. Comprobar cierre, accesibilidad, fuente ampliada y que la moneda y movimientos de la cápsula sigan intactos. Verificar en una instalación release que el acceso no se muestra. No se ha validado visualmente en Android.
- Verificación local: siete tests JVM de geometría y compilación aislada de los auxiliares del selector con el plugin Compose, sin Gradle; `git diff --check` sin errores. La compilación aislada no incluye la pantalla completa ni valida el diálogo en Android. No se ejecutaron Gradle ni ADB.

## Acceso oculto al inspector de moneda

- Se retiró el botón visible del encabezado. En instalaciones debug, una pulsación prolongada sobre el selector de moneda abre el diálogo inspector existente; al cerrarlo se conserva el estado de la demostración. La disponibilidad se determina con `ApplicationInfo.FLAG_DEBUGGABLE`, por lo que el reconocedor de pulsación prolongada no se instala en release. El toque normal sigue en el `Button` original y conserva su alternancia/háptica.
- Revisión estática y `git diff --check`; no se ejecutaron Gradle ni ADB. En teléfono, probar toque breve (una alternancia y una háptica), pulsación larga (abre inspector sin alternar ni háptica), mantener y soltar, cerrar con Atrás/toque fuera y verificar que el borrador/cola quedan iguales. Comprobar también que en release el toque breve funciona y la pulsación larga no abre el inspector. No está validado en dispositivo.

## Base de categorías personalizables (fase preparatoria)

- Diseño y contratos en [customizable-categories.md](customizable-categories.md): categorías/subcategorías con identidad local y portable, iconKey, archivado, orden, scopes y grupos de reporte independientes del nombre. El destino de Movement usa categoryId/subcategoryId opcionales; se implementó CategorySelection como contrato validable de esa relación.
- Se añadieron modelos, mapeo exhaustivo de los enums a semillas, clasificación base de reportes, entidades Room y DAO de catálogo. Una migración candidata 3 -> 4 crea/siembra el catálogo, pero **no se registra**, no modifica movimientos y no cambia la base v3. No se ha migrado la información del usuario. Movement, el formulario y los reportes vigentes siguen usando enums; no se habilitan todavía categorías personalizadas ni «Sin categoría» en la captura.
- La activación requiere cambiar de forma coordinada referencias de Movement/Room, validación, JSON de respaldo, DTO/reglas y sincronización del catálogo por UID y UUID. Los formatos actuales solo admiten enums; activar IDs locales antes de resolver esto perdería relaciones en backup/cloud. El documento detalla backfill sin pérdida, claves de semillas, FKs y chequeos, instalación limpia, compatibilidad y verificaciones necesarias. También deja pendiente el repositorio con validación/transacciones antes de conectar el DAO a una pantalla de administración.
- Reportes: resolvedor nuevo para INCOME/FIXED_COST/MAINTENANCE/OTHER/UNCATEGORIZED, separado de la suma monetaria. La integración en resumen mensual/PDF, el desglose personalizado y las líneas heredadas de agua/luz/internet están pendientes. Se propone no cambiar reportGroup una vez utilizado para evitar reclasificar el historial al editar nombres.
- Verificación: seis pruebas JVM de los contratos/mapeo/grupos pasaron con el compilador Kotlin/JUnit locales, sin Gradle. Se ejecutaron el DDL y las semillas candidatas sobre SQLite del equipo: cuatro categorías, tres subcategorías, FK y RESTRICT correctos. No equivale a ejecutar la migración con Room: faltan esquema generado/KSP, compilación Android e instrumentación sobre una copia ficticia de las versiones históricas. `git diff --check` sin errores; no se ejecutaron Gradle ni ADB. No se modificaron los cambios locales previos de morphing/inspector.
- Próximo hito: completar el corte de persistencia/transportes/reportes antes de la UI. Después, único selector integrado con «Sin categoría», iconos y navegación compacta; administración CRUD, archivado y orden con protección de identidades. Ver el documento enlazado para la secuencia y pruebas pendientes.

## Contratos definitivos del catálogo (iteración 1)

- Decisiones en [customizable-categories.md](customizable-categories.md): UUID canónico portable, siete semillas v3 determinísticas congeladas y creación de usuario v4 aleatoria. Se sustituyeron las claves preparatorias `miflujo:…` en LegacyCategoryCatalog; no había migración registrada ni datos reales con esas claves. Long sigue siendo solo identidad local.
- Se añadieron errores explícitos para referencias desconocidas, pertenencia, ámbito y archivado. La edición histórica puede conservar exactamente la selección archivada y tipo almacenados, sin eludir integridad. Se añadieron operaciones de metadata y guardas de estructura para el repositorio futuro; scope/reportGroup/padre quedan inmutables desde creación. Los data classes y DAO aún requieren que ese repositorio aplique las guardas.
- Comparadores de orden `(sortOrder, uuid)` y primitiva de revisión lógica `(counter, actorUuid)`, con validación y rechazo de overflow. Se documentó resolución determinística de ficha completa, cambios offline, ACK por revisión, dependencias pendientes y recuperación de identidades al restaurar. El contrato puede descartar uno de dos cambios concurrentes; no es merge por campo ni garantía de archivo global instantáneo. No se implementó un motor de sincronización.
- Verificación: 15 tests JVM (seis existentes y nueve nuevos) pasaron mediante Kotlin 2.2.10/JUnit locales, sin Gradle. Cubren UUID/algoritmo heredado, formato, creación aleatoria, relaciones, archivadas, estructura, orden y revisiones. `git diff --check` y revisión de whitespace de archivos nuevos sin errores. No se ejecutaron Gradle ni ADB.
- Pendiente para persistencia: adoptar las guardas en todas las escrituras, orden UUID en DAO (aún desempata por id), revisión/pendientes transaccionales, validación histórica y migración completa con FKs/esquemas/pruebas Room. Movement, reportes, formatos de backup, Firebase y registro de migraciones siguen intactos. No se modificaron los cambios previos de la interfaz.

## Persistencia transaccional aislada del catálogo (iteración 2)

- Se implementaron CategoryRepository y RoomCategoryRepository con creación, búsquedas por UUID, listas por tipo/archivo, edición de metadata, reordenamiento atómico y consultas históricas. Se aplican las guardas de dominio, se exige revisión esperada para evitar escrituras obsoletas y se comprueba padre existente/no archivado al crear hijos. Archivar no elimina ni altera sus hijos.
- CategoryCatalogDatabase es experimental, sin inyección en la app, con archivo fijo separado `category-catalog-experiment.db`. La candidata 3→4 se registra únicamente en esa base; MiFlujoDatabase y su contenedor siguen sin cambios. Movement/MovementEntity, datos financieros, reportes, respaldos y Cloud Sync permanecen intactos.
- Schema común para candidata e inicialización limpia: UUID únicos, FK padre RESTRICT, triggers de estructura inmutable y no borrado, estados/revisiones válidos, actor UUID singleton. Nuevas columnas revisionCounter/revisionActorUuid/syncState y tabla catalog_installation. El DAO ordena por posición y UUID portable, con filtro de archivado/ámbito.
- Creación local usa revisión 1; edición efectiva incrementa la revisión y persiste PENDING en la misma transacción. No-op conserva estado/revisión. Semillas con revisión 0 y actor reservado quedan PENDING. ACK/error son condicionales a UUID y revisión exactos: no limpian cambios nuevos ni convierten una revisión ya reconocida en ERROR por un fallo tardío. ERROR sigue siendo reintentable. Sin red ni modificación del scheduler actual.
- Verificación ejecutada: 19 pruebas JVM (15 contratos y cuatro de mappers/metadata), nueve pruebas SQLite del equipo sobre SQL real y consultas del DAO. Compilación Kotlin aislada del código nuevo y fuentes de siete pruebas instrumentadas con dependencias locales, sin KSP. `git diff --check` y whitespace de archivos nuevos sin errores. No se ejecutaron Gradle ni ADB.
- Pendiente Android/Room: ejecutar CategoryCatalogRoomTest sobre la base in-memory y el archivo de test separado; validar DAO generado, transacciones/rollback, actor persistente al reabrir y equivalencia real de tabla/índices/FKs/triggers entre creación Room y candidata. El test de migración usa una fuente v3 mínima con sentinel financiero; no sustituye la futura prueba completa desde los esquemas financieros 1/2/3. No se generó un JSON de esquema ficticio ni se afirma equivalencia Room por los resultados SQLite.
- Respaldos compatibles aún pendientes: snapshot transaccional de catálogo incluyendo archivadas/revisiones y padre por UUID, parser nuevo con lectura v1/v2, remapeo a IDs locales y restore coordinado con movimientos/bloqueos cloud. El actor de instalación no se copia entre dispositivos. El restore futuro necesita un camino de mantenimiento probado porque el DAO habitual prohíbe borrar físicamente el catálogo. Ver [customizable-categories.md](customizable-categories.md).

## Respaldo compatible de catálogo (iteración 3, experimental)

- Contrato completo en [catalog-backup-format.md](catalog-backup-format.md), enlazado desde [customizable-categories.md](customizable-categories.md). CatalogBackupJson lee v1/v2/v3 y escribe v3, sin conexión al exportador/importador ni SettingsViewModel. Producción conserva parser/serializer v1/v2 y Room v3.
- DTOs portables separados de Movement/MovementEntity: catálogo con UUID, ámbito, grupo, iconKey, archivo, orden y revisión causal; relaciones por UUID y nullable explícito. Se omiten IDs locales, estados de envío y fila de instalación. Validación completa de estructura, referencias, duplicados y valores financieros antes de restaurar. Se permiten nombres iguales y referencias históricas archivadas; referencias desconocidas no crean categorías ni se convierten en «Sin categoría».
- Compatibilidad histórica: delegación al parser actual para conservar todas sus validaciones y posterior mapeo de enums a semillas UUID congeladas. v2 conserva UUID de movimiento; v1 sigue generándolos al leer. Los valores monetarios Long, monedas, fechas, detalle y timestamps con nanosegundos se mantienen. La única lectura nueva del DAO obtiene todos los hijos para no omitir huérfanos de una base corrupta durante un snapshot.
- ExperimentalBackupStore usa exclusivamente BackupSandboxDatabase, archivo distinto `category-backup-sandbox.db`, versión experimental 1, sin migraciones registradas ni inyección en la app. Las filas BackupMovementEntity son staging, no el modelo financiero real. Snapshot y restore usan transacción: validar estructura conocida, retirar solo dos protecciones de borrado, limpiar movimientos/hijos/padres, insertar catálogo y remapear UUID→IDs locales, insertar movimientos y reinstalar protecciones antes de confirmar. Fallar revierte datos y DDL. El actor de instalación permanece; catálogo queda PENDING con revisión preservada y movimientos staging LOCAL_ONLY.
- Pruebas ejecutadas: 40 tests JVM (parser/serializer históricos y códec nuevo) aprobados; cuatro tests SQLite del equipo sobre primitivas de relación, valores exactos, remapeo y rollback explícito de datos/triggers aprobados. Compilación Kotlin aislada de los nuevos componentes y de cinco pruebas instrumentadas, sin KSP. `git diff --check` y whitespace de archivos nuevos sin errores. No se ejecutaron Gradle ni ADB.
- Limitación: SQLite host no ejecuta ExperimentalBackupStore ni prueba DAO generado. Los cinco tests de ExperimentalBackupRoomTest están preparados, no ejecutados en Android; verifican JSON Android, relaciones remapeadas, archivo/pendientes/revisiones, actor, reemplazo vacío y fallo después del primer movimiento con rollback. El esquema Room del sandbox deberá generarse y verificarse al compilar con las herramientas del proyecto; no se inventó un JSON de esquema.
- Pendiente de activación: modelo/referencias financieras reales, migración completa validada, lectura/exportación/restauración v3 en los consumidores de producción y coordinación con confirmación/bloqueos de restore simultáneo y Cloud Sync. Firestore y reportes siguen pendientes del corte coordinado. No se modificaron los mecanismos actuales ni los cambios locales previos de UI/inspector. El esquema experimental v4 del catálogo ya existía en el árbol al retomar esta sesión y no se modificó.

## Contrato remoto del catálogo (iteración 4A)

- El usuario informó 7/7 pruebas instrumentadas del catálogo y 5/5 del respaldo sandbox en Android físico. Corresponden a las iteraciones 2 y 3.
- [Contrato remoto Firestore](catalog-firestore-contract.md) documenta la auditoría de movimientos v1, las colecciones por UID, los campos estrictos del catálogo v1 y una transición por cuenta con bloqueo de escrituras v1 antes de habilitar movimientos con referencias UUID.
- DTO, mapa documental y funciones puras en `data/cloud/catalog`. La reconciliación compara la revisión lógica y protege la estructura. Se exige una confirmación exacta y se distinguen referencias ausentes de inválidas. La ficha ganadora puede descartar cambios concurrentes en otro campo.
- Propuesta de reglas aislada en `scripts/firestore-catalog/firestore.rules` para propietarios autorizados, versiones, campos, tamaños, estructura, revisiones, archivo y referencias a padres. Suite con datos ficticios en `scripts/firestore-catalog`. Las reglas actuales de movimientos y metadatos conservan su comportamiento.
- Pasaron 55 pruebas JVM, nueve nuevas y 46 de regresión, con compilación Kotlin aislada y JUnit. Pasaron la comprobación de sintaxis JavaScript y `git diff --check`. En la primera ejecución, el entorno restringido impidió iniciar el emulador con `EPERM`. Se completó la comprobación en la verificación posterior.
- La conexión con Cloud Sync, la persistencia de ganadores y la adopción de referencias UUID en movimientos corresponden a iteraciones posteriores. Producción sigue en Room v3, respaldo v2 y movimientos remotos v1.

### Verificación de Firestore Emulator (4A)

- El entorno restringido rechazó la creación de sockets TCP IPv4 e IPv6 con `EPERM` antes de `bind`. Node presentó el fallo como `listen EPERM`. Una prueba mínima ajena a Firebase reprodujo el error, y la misma configuración del proyecto funcionó con permisos de red local.
- Pasaron 5/5 casos con Firebase CLI 15.20.0 y Firestore Emulator 1.21.0 en una copia temporal de `demo-miflujo-catalog`. Cubren autorización, estructura, inmutabilidad, revisiones, archivado, relaciones padre/hijo y compatibilidad v1. Los rechazos de permisos de los casos negativos eran esperados.
- Se usaron identidades ficticias, loopback y una configuración nueva de la CLI. El código y las reglas quedaron idénticos a los probados. La [guía para Fedora con fish](catalog-firestore-contract.md#ejecutar-las-pruebas-en-fedora-con-fish) deja los comandos de reproducción.

## Motor experimental de catálogo (iteración 4B)

- `CatalogSyncEngine` coordina sesión, fuente local y fuente remota, con resultados tipados. `ScopedCatalogStore` liga los pendientes y el actor persistente a un archivo Room por UID y reutiliza `RoomCategoryRepository`. `EmulatorCatalogClient` usa una app Firebase separada con Auth y Firestore Emulator del proyecto fijo `demo-miflujo-catalog`.
- La corrida lee las dos colecciones desde el servidor, reconcilia y publica primero categorías, y luego aplica o publica subcategorías con padre confirmado. Las transacciones Firestore comparan la revisión actual en servidor. Room aplica respuestas contra la ficha más reciente y preserva ediciones posteriores a la solicitud. Un ACK perdido se recupera al releer el documento por UUID.
- Una lectura incompleta aborta antes de tocar filas. Documentos inválidos y conflictos bloquean el UUID afectado. Un padre ausente aplaza a sus hijos. Un fallo transitorio puede marcar `ERROR` en la revisión consultada y mantiene el pendiente. Las revisiones ganan por ficha completa, por lo que dos ediciones concurrentes en campos distintos pueden perder una.
- Pasaron 68 pruebas JVM, incluidas 13 nuevas de coordinación, más 5/5 de Firebase Emulator reutilizadas de 4A. Pasó la preparación de la cuenta ficticia en Auth y Firestore Emulator. El usuario confirmó 3/3 pruebas instrumentadas en Android físico, incluidas las dos de Room y la de extremo a extremo contra el proyecto demo. `git diff --check` pasó.
- Arquitectura, límites y comandos de reproducción en el [contrato Firestore](catalog-firestore-contract.md#motor-experimental-del-catálogo-4b). La configuración HTTP de emuladores se limita a debug. El motor carece de llamadas desde Cloud Sync, programadores, UI y base financiera. La integración con producción y la activación pertenecen a un corte posterior.

## Reportes y PDF experimentales (iteración 5)

- La [auditoría y el contrato de reportes flexibles](customizable-categories.md#reportes-y-pdf-experimentales-iteración-5) cubren los repositorios de movimientos, el cálculo, Inicio, Reportes y el PDF. El flujo activo sigue usando los modelos heredados.
- Se prepararon el cálculo mensual, los adaptadores de enums y del snapshot experimental, filas dinámicas para Compose y un generador PDF independiente con paginación. Las referencias sin resolver mantienen sus importes y tienen un renglón distinto de «Sin categoría».
- Verificación local con el compilador Kotlin, la caché Compose, el SDK Android y JUnit, sin Gradle ni ADB. Pasaron 15 pruebas JVM, 10 nuevas y 5 del cálculo anterior. Cubren equivalencia exacta en ambas monedas, fechas límite, subtotales, archivo, UUID y orden, referencias inválidas, importes grandes y paginación lógica. La prueba instrumentada compiló en el mismo chequeo aislado.
- El usuario confirmó que el cambio a la caché privada de la app permitió generar el PDF en el POCO. El archivo abrió con al menos tres páginas, pero falló la comparación de las páginas que contenían las dos marcas extraídas. Se detectó que `Layout.draw` puede incluir en el dibujo la primera línea del fragmento siguiente al usar el límite inferior del clip. El generador ahora entrega únicamente las líneas de cada fragmento y conserva su posición y dirección. Pasaron 18 pruebas JVM, incluidas tres nuevas para el límite exacto de una página, una fila de 20 000 líneas y el detalle íntegro que recibe el PDF.
- Pendiente en Android: volver a ejecutar `FlexibleReportPdfGeneratorTest.exportsSeveralPagesWithFullAmountsAndLongContent` desde el margen de Android Studio con la variante debug y un dispositivo de pruebas o AVD con API 35 o superior. Usa datos sintéticos y un PDF temporal en la caché privada. Comprueba el renderizado dentro de los márgenes y, por separado, la extracción de los renglones numerados completos, en orden y sin duplicados, de los nombres y de los importes. Logcat informa el número de páginas y las marcas extraídas de cada una bajo `FlexibleReportPdfTest`.
- El usuario observó seis páginas en el POCO, con miles de caracteres pero cero marcas reconocidas en las páginas 2 a 5. Solo se reconocieron `LINEA235` a `LINEA240` y `FINDETALLE` en la página 6. La comparación anterior insertaba un espacio entre bloques de Android y podía partir una marca. Un caso sintético reproduce esa limitación. Falta atribuir el fallo del POCO a esos límites de bloque o al propio texto extraído. En esta revisión se añadió diagnóstico en la prueba instrumentada sin cambiar el generador.
- Al repetir la prueba, `FlexibleReportPdfTest` registra muestras escapadas de las páginas 1, 2, 3 y 5, cantidad de bloques, filas de píxeles con tinta en el cuerpo y la primera marca ausente con sus vecinas y coordenadas de `searchText` cuando estén disponibles. El argumento opcional del runner `flexiblePdfKeepDiagnostic=true` conserva el PDF sintético solo si falla una aserción. La ruta privada se registra para abrirlo desde Device Explorer o contrastar el texto con Poppler. Pasaron 22 pruebas JVM, cuatro de ellas de comparación y diagnóstico de texto. Validación en Android pendiente.
- La integración coordinada deberá suministrar una lectura coherente de movimientos y catálogo, consumir las filas en las pantallas actuales y conectar el generador con el exportador. Room de producción sigue en v3 y el respaldo activo en v2.

### Verificación final del PDF experimental

El PDF recuperado del POCO X7 Pro tiene seis páginas y 74 121 bytes. Poppler recupera los 242 marcadores del detalle en orden. La extracción Android había reconocido siete y ahora se registra únicamente como diagnóstico. El generador se conserva. La prueba instrumentada valida la apertura, los márgenes de la imagen, varias páginas consecutivas con renglones visibles y los anchos, la alineación y la zona del punto decimal de los importes. Poppler valida por separado el contenido exacto de los renglones por orden de página, su continuidad, los nombres y las ocurrencias de los importes.

**Recuperación en Android Studio.** En la configuración Android Instrumented Tests del método `exportsSeveralPagesWithFullAmountsAndLongContent` y con la variante debug, añadí el argumento del runner `flexiblePdfInspect=true`. Poné un breakpoint en el `Log.i` de `logPdfForInspection` y lanzá **Debug** sobre el POCO. Al detenerse, el PDF ya está cerrado y el APK sigue instalado. Consultá `file.absolutePath` en el depurador, abrí **View → Tool Windows → Device Explorer** y usá **Save As** sobre el archivo de la caché privada. Guardalo como `~/Downloads/miflujo-diagnostico.pdf` o ajustá el comando siguiente. Después reanudá el depurador para que prosiga la limpieza y puedas consultar el resultado final de la prueba. El breakpoint también se alcanza si falla una aserción después de generar el PDF.

**Fedora con fish.** Desde la raíz del repositorio, instalá las herramientas si faltan y ejecutá el verificador sobre la copia recién obtenida.

```fish
sudo dnf install poppler-utils python3
python3 scripts/pdf-report/verify_flexible_report.py "$HOME/Downloads/miflujo-diagnostico.pdf"
python3 -B -m unittest discover -s scripts/pdf-report -v
```

El PDF anterior pasó el verificador final. El raster de Poppler confirma el punto decimal visible, aunque su capa de texto extrae, por ejemplo, `758 07`. El script admite ese separador concreto y exige todos los dígitos, los separadores de miles y las ocurrencias esperadas. Se conservaron y pasaron las 22 pruebas JVM y las cuatro pruebas Python del verificador. Queda pendiente ejecutar la versión final en el POCO y verificar en Fedora la copia de esa misma ejecución.

## Migración Room de movimientos con catálogo (iteración 6A)

- Se auditó el camino activo de Room v3 y sus consumidores. La [candidata 6A](customizable-categories.md#movimientos-con-catálogo-y-migración-room-iteración-6a) representa UUID en el borde del repositorio e IDs anulables en Room. Migra por semillas, copia y verifica el conteo antes de sustituir la tabla, conserva metadatos y detiene la migración ante filas incompatibles. La creación limpia instala el mismo catálogo y los mismos triggers. El único registro de la migración candidata vive en las pruebas.
- Pasaron 70 pruebas JVM con Kotlin/JUnit, incluidas dos nuevas de modelo, y tres casos host SQLite sobre copia literal, FKs, triggers y rollback del cambio de tabla. Compilaron las fuentes de seis pruebas instrumentadas en un chequeo Kotlin aislado. El chequeo no ejecuta Room ni KSP. Para repetir las de SQLite, ejecutar `python3 -B scripts/test_catalog_movement_sqlite.py`.
- El usuario informó que pasaron las seis pruebas instrumentadas de 6A en un dispositivo físico. En el árbol está el esquema exportado de `CatalogMovementDatabase` v4. La verificación instrumentada del PDF de la iteración 5 sigue pendiente.

## Integración de respaldos en la candidata Room (iteración 6B)

- Rama `ux/quick-entry`. `CatalogMovementBackupStore` conecta el contenido visible de `CatalogMovementDatabase` con el códec experimental v3. Comparte con el sandbox la lectura y el reemplazo del catálogo. El lector prueba sintaxis JSON y claves repetidas antes de aplicar los contratos de v1/v2/v3. Las semillas heredadas comparten su revisión inicial entre el lector y la base candidata.
- La [auditoría y el contrato de 6B](customizable-categories.md#respaldo-y-restauración-con-catálogo-iteración-6b) describen la equivalencia financiera, la conversión a milisegundos, los límites de compatibilidad histórica y el reemplazo atómico con remapeo por UUID. Se admite únicamente el destino local aislado. Se bloquea el reemplazo cuando hay vínculo de cuenta, tombstones o evidencia de sincronización en la candidata.
- Pasaron 72 pruebas JVM mediante Kotlin 2.2.10 y JUnit, cinco nuevas. Pasaron 18 pruebas host SQLite, dos nuevas para las consultas de backup y el rollback de la sustitución parcial. El comando de SQLite fue `python3 -B -m unittest discover -s scripts -p 'test_catalog*_sqlite.py' -v`. Pasó `git diff --check`. Compilaron las fuentes instrumentadas en un chequeo Kotlin aislado. El chequeo host carece de generación KSP y de ejecución del DAO y del lector JSON de Android.
- Pendiente en Android Studio con la variante `debug` y una instalación de pruebas aislada. Ejecutar los seis casos de `CatalogMovementBackupRoomTest`, el caso nuevo `CatalogMovementMigrationTest.movedV3RowsCanBeExportedAndRestoredWithoutChangingFinancialContent` y la regresión de `ExperimentalBackupRoomTest` para el sandbox compartido. Las pruebas usan bases en memoria o archivos sintéticos y cubren JSON Android, precisión, remapeo, FKs, triggers, cuentas y rollback sobre SQLite. No se ejecutaron Gradle ni ADB.
- Producción sigue en Room v3 y respaldo JSON v2. La siguiente iteración requiere acordar cómo recuperar una base con historial cloud y validar el corte coordinado de repositorios, formularios, reportes y respaldo con confirmación y exclusión de sync. 6C queda por iniciar.

## Cloud Sync coordinado (iteración 6C)

- El usuario informó que las pruebas Android de respaldo y restauración de 6B pasaron. La
  [integración aislada de 6C](customizable-categories.md#cloud-sync-con-catálogo-y-movimientos-iteración-6c)
  reutiliza el motor de catálogo, la base de modelos y las validaciones de 6A. Una partición
  Room por UID conserva la revisión base de movimientos v2. El protocolo candidato y sus
  reglas viven en `scripts/firestore-catalog/6c/`.
- Pasaron **125 pruebas JVM** con Kotlin 2.2.10 y JUnit, incluidas 10 nuevas, y **18 pruebas SQLite**
  del equipo de las iteraciones anteriores. Compilaron las fuentes de las pruebas Android
  mediante Kotlin aislado. Quedan por ejecutar tres casos de `AccountMovementSyncTest`
  sobre Android, incluidas la partición y exclusión de restauraciones, la edición concurrente
  con el ACK y la prueba de Room + Firebase SDK con Auth y Firestore Emulator.
- Pasaron **6/6 pruebas de Firestore Emulator** 1.21.0 con Firebase CLI 15.20.0 y se verificó
  el fixture de cuenta y marcador v2 en Auth y Firestore Emulator para el proyecto
  `demo-miflujo-catalog`. Pasó `git diff --check`. La verificación usó una copia temporal y
  sockets locales. El chequeo Kotlin carece de KSP, ejecución Android, Gradle y ADB.
- La prueba con el SDK Firebase admite un [dispositivo físico por USB](customizable-categories.md#prueba-android-con-firebase-emulator-en-avd-o-por-usb)
  con `catalogMovementEmulatorHost=127.0.0.1` y conserva `10.0.2.2` como valor por defecto
  para AVD. Pendiente ejecutar la prueba instrumentada en el POCO X7 Pro.
- En el primer intento en el POCO, el registro de Android Studio mostró
  `catalogMovementEmulatorHost=` vacío. Se completó el valor en la configuración local,
  se añadió un mensaje de validación y se cubrieron ambos hosts y el rechazo de otros
  valores con pruebas JVM. Pendiente repetir la ejecución en el POCO.
- Producción usa Room v3, respaldo v2 y el motor vigente. El corte propuesto requiere migración
  administrativa por UID, recuperación de pendientes v1, exclusión de restauraciones,
  tratamiento de conflictos y verificación del flujo Android. 6D queda por iniciar.

## Integración funcional y validación (iteración 6D)

- El usuario informó que 6A, 6B y 6C aprobaron sus pruebas Android. La
  [variante catalogLab](customizable-categories.md#integración-funcional-experimental-iteración-6d)
  conecta cápsulas y selección por UUID con Room, reportes, PDF, JSON y el coordinador de
  emulador. Su paquete y sus bases están aislados. Producción usa Room v3, respaldo v2
  y el motor cloud actual.
- Pasaron 133 pruebas JVM con Kotlin/JUnit, 18 de SQLite del equipo y 6/6 de Firestore
  Emulator con datos ficticios. Pasó `git diff --check`. Compilaron las fuentes Compose
  e instrumentadas en un chequeo Kotlin aislado. La fusión aislada del manifiesto base,
  la variante y Firebase Common confirmó el paquete separado, la actividad de pruebas
  y la eliminación del proveedor de inicialización automática. Queda el empaquetado
  completo con KSP y la ejecución Android. Esta iteración se verificó sin Gradle ni ADB.
- Queda ejecutar los dos casos `labWorkflow*` y la comparación de esquemas en
  `CatalogMovementMigrationTest`, la prueba SDK actualizada de `AccountMovementSyncTest`
  y el recorrido de la interfaz en `catalogLab`. Usar bases sintéticas y los dos emuladores
  preparados para 6C. Verificar también el manifiesto fusionado y la instalación separada.
- Para el corte quedan la procedencia de pendientes v3, el relevo de WorkManager,
  la resolución de conflictos, el backfill por UID y la recuperación de errores.
  El flujo integrado está preparado para su validación Android aislada.

- Android Studio confirmó el mismo error de compilación en `MiFlujoAppContainer.kt` y
  `MiFlujoApp.kt`. Las fuentes compartidas referencian `default_web_client_id` y Google
  Services omite ese recurso en `catalogLab` porque su archivo ficticio carece de OAuth web.
  La variante ahora define el símbolo como cadena vacía en su propio conjunto de recursos.
  La inicialización Firebase predeterminada sigue excluida de su manifiesto. Queda recompilar
  la variante completa desde Android Studio.

## Consolidación de quick-entry para dev

- Se consolidaron el prototipo de cápsulas y las iteraciones de catálogo, Room v4, JSON v3,
  reportes, sincronización y `catalogLab`. Las reglas del catálogo 4A quedaron en
  `scripts/firestore-catalog/` junto a su configuración candidata de emulador. El archivo
  raíz y la configuración de despliegue conservan el contrato actual.
- Gradle offline aprobó las 346 pruebas JVM y ensambló `debug`, `catalogLab` y el APK
  instrumentado de debug. La compilación de la variante confirmó la corrección del
  recurso OAuth y su manifiesto fusionado conserva el paquete separado, sin actividad
  principal ni proveedor Firebase predeterminado. Pasaron 21 pruebas Python de catálogo
  y configuración, cuatro del verificador PDF y 11 de Firestore Emulator.
- Queda ejecutar la validación integral de 6D en Android y verificar el recorrido visual
  de la variante aislada. El PR hacia `dev` conserva los bloqueos de activación documentados
  en [6D](customizable-categories.md#integración-funcional-experimental-iteración-6d).

## Editor funcional de categorías y subcategorías

- `ux/category-editor` parte de `origin/dev` en `8e57378`. El editor y el selector de
  iconos funcionan en [catalogLab](customizable-categories.md#gestión-de-categorías)
  con el repositorio experimental existente. Configuración abre la lista activa o
  archivada. El formulario de movimientos conserva el borrador al crear una categoría
  y mantiene la clasificación histórica cuando corresponde.
- Pasaron **346 pruebas JVM en debug**, **349 en catalogLab** y **21 comprobaciones
  Python** de SQLite y configuración. Se ensamblaron los APK de debug, catalogLab
  y las pruebas instrumentadas. Pasaron **3/3 pruebas Compose en el POCO X7 Pro** con
  Room en memoria y los paquetes independientes `com.carlos.miflujo.cataloglab.uitest`
  y `com.carlos.miflujo.cataloglab.uitest.test`. Cubrieron guardado contextual y
  restauración del estado, JSON v3 y el contrato remoto, creación y edición de fichas,
  archivo, restauración y referencias históricas. La configuración instrumentada evitó
  reemplazar la instalación previa de catalogLab firmada con otra clave.
- Pendientes la evaluación visual y los bloqueos de activación de 6D. Producción
  sigue usando Room v3, respaldo v2 y su Cloud Sync vigente.

### Color opcional para iconos

- `catalogLab` ofrece diez colores con vista previa, selección accesible y un control
  independiente para volver al tema. El icono y su tinte se muestran en el catálogo,
  los selectores y las referencias históricas.
- Las bases experimentales añadieron una columna anulable mediante migraciones 4 → 5
  y 1 → 2. Los colores se escriben en JSON v4 y en las fichas remotas v2 del marcador
  experimental 3. Los lectores conservan compatibilidad con los archivos y documentos
  anteriores. Ver el [contrato actualizado](customizable-categories.md#color-opcional-del-icono).
- Pasaron **348 pruebas JVM en debug**, **352 en catalogLab**, **22 comprobaciones Python**,
  **7 pruebas de Firestore Emulator** y **31 instrumentadas en el POCO X7 Pro**. Se
  ensambló catalogLab. El POCO ejecutó la suite en los paquetes aislados de pruebas.
  Las reglas y formatos de producción conservan su configuración vigente.

### Pulido del formulario de categorías

- En `catalogLab`, «Color del icono» empieza plegado y muestra el color actual o
  «Predeterminado». Al desplegarlo están disponibles la cuadrícula de diez muestras
  y el control para volver al color del tema. El formulario conserva el color al
  plegar la sección y se puede desplazar con la paleta abierta.
- Se retiró «Grupo del reporte». La edición conserva el grupo guardado y las
  nuevas categorías se crean en Ingresos si son exclusivas de ingreso, o en Otros
  si admiten egresos. Los cálculos y la agrupación de los reportes siguen vigentes.
- Pasaron 348 pruebas JVM de debug, 352 de catalogLab y las cuatro pruebas de
  `CatalogEditorFlowTest` en el POCO X7 Pro con los paquetes aislados. Se ensamblaron
  debug, catalogLab y los APK de pruebas. Queda pendiente la revisión manual del
  diseño en pantallas pequeñas, con fuente ampliada y con TalkBack.

### Catálogo de un nivel

- En [catalogLab](customizable-categories.md#gestión-de-categorías), el catálogo, el selector
  de movimientos, el historial y el reporte muestran categorías. Los importes históricos se
  agrupan por categoría en el reporte mensual y su PDF. Se conserva el editor con 50 iconos,
  diez colores opcionales y el control plegado por defecto.
- Las altas desde el formulario guardan la categoría elegida con referencia de subcategoría
  vacía. La edición conserva el UUID histórico siempre que se guarde la categoría original.
  Se mantienen los registros, relaciones y contratos de Room, JSON y Cloud Sync.
- Antes de integrar con la aplicación principal, revisar la validación y los flujos de captura
  y edición que aún exigen subcategoría en costos fijos. La activación también conserva los
  [bloqueos de integración de 6D](customizable-categories.md#integración-funcional-experimental-iteración-6d).
- Pasaron 348 pruebas JVM de debug, 352 de catalogLab, 22 comprobaciones Python y
  47 pruebas instrumentadas en el POCO X7 Pro. Gradle ensambló debug, catalogLab y
  los APK instrumentados. Las cuatro de Compose usaron bases temporales
  con datos ficticios y verificaron gestión, iconos, color, alta con categoría y
  edición histórica con reapertura de Room, historial, reporte y respaldo JSON.
  La primera corrida incluyó `ExampleInstrumentedTest`, que exige el ID de
  producción. Se excluyó en la repetición con el paquete aislado. Se omitieron
  dos casos que requieren Firebase Emulator.

## Integración experimental de categorías en la interfaz principal

- [catalogPreview](customizable-categories.md#vista-integrada-del-catálogo) reutiliza
  la navegación, Ajustes y la cápsula de la app. Su instalación y Room están
  aisladas y el APK carece de permiso de red. El administrador y los recursos de
  catálogo se comparten con catalogLab.
- Las altas de la cápsula persisten el lote en una sola transacción. La edición
  desde el historial trabaja con el movimiento candidato por UUID. Los nombres
  personalizados se ven en Inicio, búsqueda, historial y Reporte. La exportación
  de PDF y las operaciones de respaldo y Cloud Sync quedan fuera de esta vista.
- La aprobación visual precede a cualquier decisión sobre migración definitiva,
  respaldo de producción o corte de Cloud Sync. Siguen vigentes las condiciones
  de activación de [6D](customizable-categories.md#integración-funcional-experimental-iteración-6d).
- Se ensamblaron `debug`, `catalogLab`, `catalogPreview` y los APK de pruebas.
  Pasaron 348 pruebas JVM de debug, 352 de catalogLab, 348 de catalogPreview y
  24 comprobaciones Python. En el POCO X7 Pro pasaron los dos recorridos Compose
  de catalogPreview, con cierre y reapertura de la actividad real, comprobaciones
  de Room y verificación del aislamiento. La regresión instrumentada de catalogLab
  se detuvo antes de ejecutar casos porque el teléfono rechazó la instalación
  de su APK de pruebas (`INSTALL_FAILED_USER_RESTRICTED`).
- El POCO tiene instalada **MiFlujo · Vista de catálogo**. Se verificaron su
  actividad de inicio y su UID independiente. El APK de la vista carece de
  permiso de Internet y de inicialización predeterminada de Firebase. Los
  metadatos de la instalación de producción coincidieron antes y después.

### Hoja de selección y categorías para ambos tipos

- En [la vista integrada](customizable-categories.md#vista-integrada-del-catálogo),
  el control redondeado conserva su lugar junto a la fecha. Muestra **Elegir categoría**
  o la ficha elegida. La hoja Material 3 comparte buscador, listado desplazable y
  acción tonal de creación entre las variantes experimentales.
- El editor compartido guarda las fichas nuevas como `BOTH` con grupo `OTHER`.
  Los ingresos se contabilizan como ingresos. Los egresos de fichas nuevas se
  agrupan en Otros. Los ámbitos, grupos y UUID históricos se conservan.
- Se mantienen los borradores y la confirmación transaccional de la cola. Se
  conservan los esquemas y los contratos de respaldo y sincronización.
- Se ensamblaron `debug`, `catalogLab`, `catalogPreview` y los APK instrumentados
  de ambas variantes experimentales. Pasaron 348 pruebas JVM de debug, 352 de
  catalogLab, 348 de catalogPreview y 24 comprobaciones Python.
- En el POCO X7 Pro pasaron los cuatro recorridos Compose de catalogPreview y los
  cuatro de catalogLab. Se probó la búsqueda y el desplazamiento con 80 fichas,
  el cierre desde el control accesible, la captura y edición con persistencia,
  y el control estrecho con texto al 180 % en ambos temas.
- Se actualizó y se abrió el APK normal de **MiFlujo · Vista de catálogo** en
  el POCO. Quedan pendientes la revisión visual directa de la hoja, Atrás del
  sistema y TalkBack. El dispositivo rechazó los eventos remotos de teclado y toque.

### Selector contextual de Quick Entry

- [catalogPreview](customizable-categories.md#vista-integrada-del-catálogo) vuelve al
  control compacto junto a la fecha y usa una lista flotante anclada de hasta 260 dp.
  La acción de crear queda al pie. Selector y ordenación se reutilizan en catalogLab.
- El orden cuenta movimientos persistidos visibles registrados en los últimos 90 días.
  La frecuencia y la última fecha original de registro determinan la prioridad. Los
  empates se resuelven de forma estable y todas las fichas activas compatibles se
  pueden alcanzar desplazando la lista. El cálculo reutiliza la instantánea de Room.
- Compilaron catalogPreview y catalogLab. Pasaron dos pruebas JVM de ordenación
  y el único caso instrumentado de catalogPreview en el POCO, con 80 fichas,
  selección, cierre al tocar fuera y creación conservando el borrador. Se instaló
  la variante aislada. La valoración visual y de ergonomía queda para la revisión
  manual del usuario.

### Suspensión de borrador al editar un pendiente

- En `ux/category-editor`, Quick Entry conserva el borrador nuevo en el estado
  guardable del formulario. Al editar se carga una copia del pendiente. **Guardar**
  actualiza solo esa entrada por UUID y **Cancelar** descarta sus cambios. Ambas
  acciones recuperan el borrador con el importe textual y el UUID de categoría.
  **Confirmar** sigue guardando el lote en una transacción.
- **Salir** mantiene la confirmación de descarte. Si hay un borrador suspendido,
  el aviso lo incluye. **Continuar** conserva los dos borradores y la cola.
- **Salir**, **Cancelar** y **Guardar** comparten una fila adaptable de Material 3.
  Los botones tienen un alto mínimo de 48 dp y pueden pasar a otra línea si el
  ancho o el tamaño del texto lo exige.
- Pasaron `git diff --check`, la compilación de `catalogPreview`, los 15 casos
  de `QuickEntryStateTest` y dos recorridos Compose focalizados en el POCO X7 Pro.
  Se probó la recreación de la actividad con una edición activa, la recuperación
  tras cancelar o guardar y la confirmación del lote. El APK normal se actualizó
  por ADB con `install -r`. Se comprobó la conservación del archivo de la base
  experimental y la coincidencia de los metadatos de la app de producción.
  Queda pendiente la revisión visual manual de la fila de acciones.
- Al recuperar el borrador con **Guardar** o **Cancelar**, el cursor se coloca al
  final de monto y detalle. La edición se indica con **Editando movimiento** y
  el borde de la tarjeta activa. Pasaron la revisión estática,
  `git diff --check`, la compilación de `catalogPreview` y la actualización en
  el POCO X7 Pro con `adb install -r`. Queda pendiente la validación manual.

### Selector de fecha Material 3 en Quick Entry

- `ux/quick-entry-polish` parte de la integración del PR #148 en `dev`.
  El control compacto abre `DatePickerDialog` con `DatePicker` en el día y mes
  del borrador activo. **Aceptar** aplica la fecha como `LocalDate`. **Cancelar**,
  Atrás y tocar fuera cierran el diálogo conservando la fecha del formulario.
- Pasaron la revisión estática, `git diff --check` y la compilación de
  `catalogPreview`. La actualización por ADB en el POCO X7 Pro conservó la
  base experimental y los metadatos de producción. Queda pendiente la revisión
  visual directa en claro y oscuro.

### Categoría opcional en Quick Entry

- [La vista integrada](customizable-categories.md#vista-integrada-del-catálogo)
  acepta ingresos y egresos sin categoría en el borrador, la cola y la edición.
  La referencia nula se guarda con el esquema Room existente y los importes
  siguen contando en los totales. El desglose muestra los egresos sin clasificar
  en **Sin categoría**.
- Pasaron 20 pruebas JVM de Quick Entry y cálculo mensual y dos recorridos
  Compose en el POCO X7 Pro. Cubrieron el lote mixto, la edición con y sin
  asignación de categoría, los totales y el historial archivado. Pasaron
  `git diff --check` y la compilación de `catalogPreview`. El APK normal se
  actualizó con `adb install -r` y conservó la base experimental.

### Cola de Quick Entry junto a la cápsula

- La lista usa el espacio entre el título y la cápsula. Mientras hay espacio
  libre, las tarjetas se alinean abajo. La más reciente queda junto a la
  cápsula y las anteriores suben en su orden de registro. El desplazamiento
  aparece al desbordar la lista y deja el formulario y las acciones fuera.
- Se retiró el mensaje de la cola vacía. La transferencia conserva el destino
  medido de la tarjeta, el desplazamiento para revelarla y la animación
  existente. El overlay requiere un destino con límites visibles.
- Pasaron la revisión estática, `git diff --check`, la compilación de
  `catalogPreview` y dos recorridos Compose en el POCO X7 Pro. Se revisaron
  capturas de la cola vacía, una y dos tarjetas, desbordamiento, edición,
  cancelación, guardado y teclado visible. La actualización por ADB conservó
  la base experimental.

### Altura dinámica del diálogo de Quick Entry

- Los contenedores del diálogo conservan su ancho y pasan a medir la altura
  del contenido. La cola se compone cuando hay pendientes. Recibe como límite
  el espacio disponible después de medir el título, la cápsula y las acciones.
  El límite de la ventana se ajusta al teclado y solo la cola se desplaza
  cuando su contenido lo supera.
- La transferencia usa la posición de la cápsula después de que el diálogo
  crece. El overlay se mide aparte del contenido, por lo que el tamaño
  del diálogo depende del formulario y de la lista.
- Pasaron la revisión estática, `git diff --check`, la compilación de
  `catalogPreview` y los recorridos Compose de altura y edición en el POCO
  X7 Pro. Se revisaron capturas con la cola vacía, una, dos y varias tarjetas,
  con desbordamiento, con teclado visible y en edición. La actualización por
  ADB conservó la base experimental.

### Estabilidad de la transferencia con el teclado visible

- En el POCO X7 Pro, al insertar la tercera tarjeta con el teclado de texto
  abierto, los límites visibles del destino alternaban entre 200 y 88 px
  mientras la tarjeta seguía midiendo 200 px. La ventana y la cápsula se
  recolocaban durante la transferencia. Con el teclado cerrado, la tercera
  inserción seguía ampliando el diálogo.
- El diálogo aplica los insets del sistema con `safeDrawingPadding` y mide
  el origen y el destino en el sistema de coordenadas de la capa animada.
  En tres repeticiones con el teclado abierto, la tercera tarjeta se reveló
  mediante scroll y mantuvo su altura visible durante toda la transferencia.
- Se verificaron las primeras cuatro inserciones, el límite de la cola y la
  edición con restauración del borrador. Pasaron `git diff --check` y la
  compilación de `catalogPreview`. La actualización en el POCO conservó la
  base experimental.

### Apertura del teclado en Quick Entry

- En dos reproducciones de `c46e90f` con la cola vacía, la primera apertura
  midió un descenso de 90 px de la ventana antes del primer inset de teclado
  distinto de cero. El panel interior conservó su altura y ubicación relativa.
  El salto no se observó en la comparación focalizada con `7a30842`.
- Una ventana transparente de tamaño estable aplica `safeDrawingPadding` una
  vez y centra el panel dinámico en el área disponible. El toque fuera conserva
  la salida y la confirmación de descarte. El formulario y las acciones siguen
  fuera del scroll de la cola.
- En el POCO X7 Pro se revisaron 16 ciclos de apertura y cierre con Monto y
  Detalle y con 0, 1, 2 y 8 pendientes. La transferencia 2→3 con el teclado
  abierto midió un destino de 200 px al inicio y al final. Pasaron los
  recorridos focalizados de altura, edición y toque fuera. Se retiraron las
  trazas y se actualizó `catalogPreview` por ADB conservando su base.

### Posición de Quick Entry con el teclado visible

- El panel toma como referencia el centro con el teclado cerrado. Si el IME
  ocupa parte de esa posición, el panel sube solo hasta caber en el área segura
  y conserva el margen inferior existente. La cola sigue usando el espacio
  restante y se desplaza cuando hace falta.
- Pasaron la revisión estática, `git diff --check` y la compilación de
  `catalogPreview`. Se actualizó el APK en el POCO X7 Pro conservando la base
  experimental. La validación de ergonomía queda para la revisión manual.

### Validación monetaria de Quick Entry

- Quick Entry valida el monto al agregar, confirmar o guardar una edición. Acepta
  un importe positivo con punto o coma decimal y hasta dos decimales. Las
  entradas con separadores de miles se rechazan y los borradores conservan
  su texto mientras se editan.
- El límite compartido con el formulario normal es 99,999,999,999 unidades
  menores por movimiento (999,999,999.99). El formulario normal conserva su
  interpretación anterior de los separadores.
- Los totales mensuales de datos existentes fuera de rango siguen pendientes:
  detectar un desborde requiere que las pantallas puedan manejar un total que
  no cabe en `Long`.
- Pasaron las cuatro pruebas JVM de `QuickEntryAmountTest`, ejecutadas con
  `:app:testDebugUnitTest --tests`, y se compiló `:app:assembleCatalogPreview`.
  También pasó `git diff --check`.

### Confirmación de Quick Entry durante recreación o fallos de lectura

- La confirmación bloquea los manejadores de entrada, cola y descarte antes
  de iniciar Room. Un fallo de escritura conserva la captura y sus UUID.
  Solo se cierra la sesión al confirmar el éxito.
- El ViewModel de `catalogPreview` conserva la escritura y su resultado
  durante una recreación. El diálogo conserva el identificador del intento
  junto con su borrador y cola restaurables. Si se restaura un intento sin
  la escritura en memoria, compara los UUID y el contenido exacto en Room
  antes de reactivar la captura. Si falla esa consulta, vuelve a consultar
  manteniendo la captura bloqueada. Un conflicto no autoriza sobrescribir
  el movimiento existente. El lote sigue usando una sola transacción.
- El último catálogo y los movimientos válidos se conservan aunque falle
  la última lectura. El error se informa por separado y la lectura ya no
  controla si se desmonta el diálogo de una sesión abierta.
- Pasaron las cuatro pruebas JVM de `CatalogPreviewQuickEntryTest`, el
  ensamblado de `catalogPreview` y `git diff --check`. Se revisaron
  estáticamente los manejadores de mutación. La recreación se cubrió
  reemplazando al consumidor del resultado en JVM, sin una Activity real.

### Categorías de Quick Entry al cambiar de tipo

- Las categorías activas compatibles, incluidas las de alcance `BOTH`, se
  conservan al alternar entre ingreso y egreso. Una categoría incompatible
  queda fuera de la selección para el nuevo tipo. Una categoría recién
  creada conserva su alcance `BOTH` mientras se actualiza el catálogo.
- En una edición histórica, volver al tipo original recupera la relación
  anterior si el usuario no eligió otra categoría ni la quitó. Esto incluye
  categorías y subcategorías archivadas en su relación original. Con una
  categoría activa que admite el nuevo tipo, solo se conserva una
  subcategoría histórica compatible con ese cambio.
- **Sin categoría** está disponible para ambos tipos. Es una selección
  explícita que guarda referencias nulas y evita restaurar la relación
  anterior al volver al tipo original.
- Pasaron las ocho pruebas JVM focalizadas de `QuickEntryCategoryTest`,
  `:app:assembleCatalogPreview` y `git diff --check`.
