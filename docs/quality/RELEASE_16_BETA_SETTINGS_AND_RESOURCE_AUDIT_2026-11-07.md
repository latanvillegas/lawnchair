# Auditoría Staff Engineering — Lawnchair 16 Beta, ventana de Ajustes y recursos

**Fecha:** 2026-11-07  
**Rama base:** `16-beta` (`f79ad56a0fa8211b361b1ef91e3dc053bbb2813a`)  
**Referencia visual:** captura de 960×1536 px enviada por el usuario. La pantalla de Ajustes aparece como ventana superpuesta y el panel derecho se extiende fuera de su área visible.  
**Entregable:** PR de corrección `release/fix-settings-responsive-window-and-quality`.

## Resumen ejecutivo

La evidencia apunta a un bug de **adaptabilidad a las dimensiones de la ventana**, no a un problema de memoria GPU o del cargador de aplicaciones. `Preferences.kt` usaba:

- `HorizontalTwoPaneStrategy(splitOffset = 420.dp)`, activado con `WindowSizeClass` aunque la ventana efectiva fuese más estrecha que ambos paneles;
- `Modifier.requiredWidth(640.dp)` en el modo de un panel expandido, que fuerza un ancho mayor que el espacio físico disponible.

En modo ventana libre, pantalla dividida, foldable o desktop, una app puede tener una **ventana menor que la pantalla física**. Es correcto que Android muestre el launcher detrás de una ventana flotante; lo incorrecto es que el contenido de esa ventana quede recortado.

### Corrección aplicada

- `Preferences` mide el viewport con `BoxWithConstraints` y exige al menos **900 dp de ancho y 480 dp de alto** además de las clases de tamaño expandidas antes de elegir dos paneles. El primer panel conserva sus 420 dp; el segundo tiene espacio utilizable.
- `Surface` llena el viewport efectivo y el panel único utiliza `widthIn(max = 640.dp)` sin `requiredWidth`, centrado y limitado al contenedor. En ventanas pequeñas la navegación vuelve a una sola columna.
- Las rutas especiales de selección de iconos siguen fuera del diseño de doble panel. La ventana del sistema puede seguir siendo flotante por elección del SO; no se fuerza el modo de pantalla completa.

## Auditoría de ingeniería aplicada

| Prioridad | Dominio | Hallazgo / riesgo | Cambio |
| --- | --- | --- | --- |
| P0 | UI / Compose | Dos paneles en una ventana real demasiado estrecha | Tamaño real de ventana con `BoxWithConstraints` y límite conservador |
| P0 | UI / Compose | `requiredWidth(640.dp)` obliga a medir un panel mayor que su contenedor | Ancho máximo limitado, `fillMaxSize` y contenido centrado |
| P1 | Persistencia | `dismissedAnnouncementIds` deserializaba JSON sin manejar corrupción | `runCatching` y valor por defecto `emptySet()` |
| P2 | Allocations | Se creaba un nuevo parser JSON al cargar ciertas preferencias | Parser `Json` inmutable compartido |
| P1 | Almacenamiento | `ImportedIconPickerActions` copiaba originales antes de confirmar; cancelar podía dejar archivos huérfanos de hasta 32 MiB | Seguimiento de archivos no confirmados, limpieza en reemplazo y `onDispose`, gestión de cancelación durante la decodificación |
| P1 | Seguridad de rutas | El identificador de archivo de icono procede de un override | La eliminación reutiliza `resolve`, que valida un identificador UUID y extensión permitida |
| P1 | Estabilidad | Corrupción de preferencias podía propagarse a observadores y bloquear Ajustes | Test de JSON inválido y `[]` |
| P2 | Batería | Un icon pack con reloj dinámico puede recibir `ACTION_TIME_TICK` periódicamente | Revisado; no se deshabilitó porque alteraría relojes en vivo. Perfilar antes de modificar |
| P2 | Arranque / I/O | `LawnchairIconProvider` puede usar `IconPack.loadBlocking()` para icon packs | Requiere traza de hilo/UI y política de caché; no reescrito sin benchmark |
| P2 | Notificaciones | `SyncLiveInformation` consulta información remota si está habilitada al abrir preferencias | Conservar comportamiento hasta disponer de perfiles de CPU/red y especificación de frescura |

**Cambios ya fusionados en el PR #8:** iconos del cajón simétricos en vertical/horizontal, etiquetas del escritorio, límites de decodificación (máx. 512 px), eliminación de logging intensivo, corrección de accesibilidad, medidas de padding sin trabajo redundante y cierre de `DeviceProfileOverrides`.

## Pruebas incluidas

- `PreferencesResponsiveLayoutTest`: ventana flotante de 540 dp, retrato 760 dp, tablet 900/1200 dp, altura baja e infinito/NaN.
- `LiveInformationParsingTest`: JSON de anuncios inválido y lista vacía.
- `ImportedIconStoreTest`: eliminación de archivos no confirmados y rechazo de rutas con traversal.

Las pruebas instrumentadas están añadidas al repositorio. La compilación `GithubRelease` verifica que el proyecto compile, pero **no acredita la ejecución de estas pruebas instrumentadas** salvo que se ejecute explícitamente el job de pruebas.

## Plan de perfilado integral — antes y después (pendiente de dispositivo real)

1. **ANR, arranque y CPU:** `adb shell am start -W`, Perfetto (main thread/scheduler/binder), comparar p50 y p95 de arranque frío y caliente.
2. **Frames:** FrameTimeline / JankStats. Recorrer 100 apps en cajón, abrir/cerrar Ajustes, rotar, minimizar ventana y probar landscape.
3. **Memoria:** `dumpsys meminfo app.lawnchair`, heap dump y análisis de Bitmap/PSS tras 20 imports confirmados, 20 cancelados y cambios de orientación.
4. **Batería:** `dumpsys batterystats --reset` con un protocolo estable de brillo, red, carga y 30 minutos de escritorio/cajón. Inspeccionar wakelocks, actualizaciones de reloj, wakeups, tráfico de red y temperatura.
5. **Concurrencia y lifecycle:** interrumpir importación con pulsación Atrás, rotación y cierre de ventana; comprobar ausencia de archivos no confirmados y disponibilidad de iconos confirmados.
6. **Ventanas:** 320dp, 540dp, 760dp, 900dp y 1200dp; split-screen, freeform, portrait, landscape, fuentes grandes y RTL. Verificar que no exista clipping horizontal de preferencias.
7. **CI:** `assembleLawnWithQuickstepGithubRelease`; adicionalmente ejecutar los tests Android instrumentados pertinentes. La variante Debug/Nightly no forma parte del artefacto solicitado.

## Alcance y límites de confianza

Este informe es una **auditoría estática centrada en puntos críticos y los defectos reproducibles por evidencia**, no una certificación exhaustiva de los miles de archivos del repositorio. No se hicieron perfiles Perfetto, mediciones de miliamperios, pruebas de hardware ni auditoría de todas las dependencias; no se afirma una mejora cuantificada de autonomía, memoria PSS ni fluidez sin medidas empíricas. No se modificaron los broadcasts de relojes ni el motor Quickstep para evitar regresiones en funciones del sistema.

**Criterio de promoción:** únicamente merge a `16-beta` después de CI `GithubRelease` exitoso. Cualquier prueba funcional pendiente se declara explícitamente.
