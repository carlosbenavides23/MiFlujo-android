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
