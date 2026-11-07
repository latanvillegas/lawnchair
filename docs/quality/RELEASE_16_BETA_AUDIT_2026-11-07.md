# Auditoría de ingeniería — Lawnchair 16 Beta / Release

**Fecha:** 2026-11-07  
**Base auditada:** `16-beta`, commit `1e70fe60dc207d43ec5fb4d90ea26d2387dacd3d`  
**Correcciones:** PR #8 (`release/fix-portrait-drawer-workspace-labels-quality`)  
**Evidencia visual:** captura proporcionada por el usuario, 1536 × 960 píxeles; etiquetas truncadas en la segunda fila del escritorio.  
**Alcance:** revisión estática dirigida a rutas críticas de interfaz, memoria, accesibilidad y ciclo de vida. No sustituye instrumentación de todos los módulos en hardware real.

## Defectos prioritarios y medidas

| Severidad | Subsistema | Evidencia en código | Acción |
| --- | --- | --- | --- |
| P0 | Cajón responsive en vertical | `AllAppsProfile.createAllAppsWithResponsive` reducía los iconos a la altura fija mediante `resizeToFitCellHeight`, a diferencia del comportamiento horizontal | Las filas del RecyclerView crecen para contener icono, etiqueta y márgenes; el ancho real de columna limita el tamaño físico |
| P0 | Etiquetas en escritorio | `BubbleTextView.onMeasure` admitía padding superior negativo al centrar iconos; la medición de shortcuts no reservaba descenso tipográfico | Padding superior no negativo; reserva de alto para texto y descenso; límite para iconos dentro de celdas |
| P1 | Rendimiento UI | `ShortcutAndWidgetContainer.measureChild` asignaba padding aun sin cambios, potencialmente invalidando layouts | Comprobar valores previos antes de `setPadding` |
| P1 | Scroll del cajón | `BaseAllAppsAdapter.onBindViewHolder` construía y registraba cadenas de depuración por icono | Eliminar trazas en el camino caliente |
| P1 | Accesibilidad y CPU | `AllAppsGridAdapter.getRowsNotForAccessibility` usaba `Math.max(position, lastPosition)`, recorriendo toda la lista en consultas parciales | Acotar el recorrido a la posición solicitada y manejar lista vacía |
| P1 | Memoria de imágenes | `LawnchairIconProvider.getIcon` decodificaba imágenes importadas a 192 dp, sin límite físico alto | Tamaño objetivo 144 dp, limitado a 512 px; mantiene margen para iconos de hasta 250 % |
| P1 | Estabilidad | `DeviceProfileOverrides.close()` lanzaba `NotImplementedError` | Cierre sin excepción cuando no se poseen recursos |
| P2 | Archivos importados | `ImportedIconPickerActions` puede conservar originales no confirmados en `filesDir/custom_icons` | Pendiente: limpieza transaccional basada en confirmación y referencias activas, con pruebas de concurrencia |
| P2 | Paquetes de iconos | `LawnchairIconProvider` puede ejecutar `loadBlocking()` durante la resolución de paquetes | Pendiente: perfilar latencia/caché antes de rediseñar concurrencia |

## Análisis de memoria del decodificado

En ARGB_8888, un bitmap requiere aproximadamente `ancho × alto × 4` bytes, sin incluir metadatos/cachés:

- Densidad 480 dpi (xxhdpi): de 576 × 576 (1 327 104 bytes) a 432 × 432 (746 496 bytes): **43,75 % menos bytes por bitmap** cuando la imagen original permite ambas dimensiones.
- Densidad 640 dpi (xxxhdpi): de 768 × 768 (2 359 296 bytes) a 512 × 512 (1 048 576 bytes): **55,56 % menos bytes por bitmap** bajo las mismas condiciones.

Estos son límites matemáticos de la ruta de decodificación, **no mediciones de heap total, consumo de batería ni ahorros de CPU**. Los originales se preservan sin recomprimir y el dibujo de iconos conserva el factor configurable.

## Reglas de regresión

1. Android en vertical, cajón 3–10 columnas: el slider 50–250 % debe cambiar el icono hasta el ancho permitido; la fila crece sin recortes de etiqueta.
2. Android en horizontal (barra lateral / modo apaisado): aplicar el mismo factor de escala, respetando tamaño de columna y scroll.
3. Escritorio con iconos grandes y al menos dos filas: las etiquetas, incluidos los descendentes tipográficos, deben verse completas sin invadir el dock ni otra fila.
4. Cambios de orientación y perfil de dispositivo: invalidar ViewHolders del cajón a través del `onDeviceProfileChanged` existente; verificar que el tamaño no dependa del pool previo.
5. Trabajo / espacio privado / duplicados: verificar scroll, índices TalkBack y ausencia de excepciones en adaptadores.
6. Iconos importados PNG/JPEG/WebP en pantalla 480/640 dpi, AMOLED y escalado 250 %: comprobar nitidez, transparencia, memoria y cambios de icono.
7. Restauración de preferencias con `NaN`, `Infinity` o rangos fuera de 50–250 %: normalizar sin estropear la cuadrícula.
8. Estabilidad de cierre/recreación: navegación, fondos de pantalla, cambio de tema y reinicio del proceso.

## Plan de evaluación cuantitativa (dispositivo real)

Para una comparación antes/después utilizar mismo dispositivo, brillo, batería, paquete, cuadrícula y conjunto de apps; tomar al menos tres ejecuciones de cada escenario:

- **Arranque y ANR**: `adb shell am start -W` y traces Perfetto de hilo principal; comprobar regresiones p50/p95 y aplicaciones frías/calientes.
- **Scroll y frame pacing**: Perfetto FrameTimeline / JankStats en cajón vertical y horizontal; identificar frames lentos, tiempos de bind y GC.
- **Memoria**: `adb shell dumpsys meminfo app.lawnchair` y Android Studio Heap Dump; observar PSS, java/native heap, grandes bitmaps, fugas al rotar e importar.
- **Batería**: `adb shell dumpsys batterystats --reset` tras carga estable y métricas posteriores con el mismo patrón de interacción; aislar wakelocks, trabajo periódico y CPU. El impacto energético no debe expresarse como porcentaje sin medidas comparables.
- **Seguridad de almacenamiento**: importe cancelado / confirmado, archivo borrado, permisos URI revocados, recuperación tras process death; verificar ausencia de archivos huérfanos.
- **Calidad**: ejecutar pruebas unitarias de dimensionado, lint de variante Release y pruebas instrumentadas de UI con configuraciones diversas.

## Puerta de salida

La integración a `16-beta` requiere al menos compilación `assembleLawnWithQuickstepGithubRelease` exitosa y comparación de diff para evitar variantes Debug/Nightly. La compilación verifica sintaxis e integración Gradle, **no** certifica fidelidad visual, consumo energético, ausencia total de fugas ni compatibilidad en todos los dispositivos.

**Estado del informe:** auditoría estática y mitigaciones prioritarias preparadas; validación de laboratorio y pendientes P2 indicados explícitamente.

## Segunda inspección: interfaz de ajustes superpuesta (captura 960×1536)

La imagen muestra el cajón de aplicaciones y, por encima, la actividad de ajustes. El panel derecho de ajustes aparece cortado horizontalmente. La zona negra inferior del cajón no es por sí sola una fuga de memoria: cuando solo hay unas pocas filas de aplicaciones, el resto del contenedor queda vacío.

**Causa de código reproducible por inspección:**
- `Preferences.kt` habilitaba `TwoPane` exclusivamente por la clase de tamaño de ventana, con `splitOffset = 420.dp` fijo. En un cambio de tamaño o una ventana estrecha, el contenido podía quedar con un ancho insuficiente.
- El fallback usaba `requiredWidth(640.dp)`, que obliga a medir más ancho que el contenedor disponible.
- `AllAppsRecyclerView.onSizeChanged` no llamaba a `super.onSizeChanged`.

**Mitigaciones en PR #9:**
- Consultar `BoxWithConstraints.maxWidth/maxHeight` en cada recomposición y usar dos paneles solo con al menos 840dp de ancho y 480dp de alto.
- Usar un split de 360dp y ancho flexible para el panel de detalles.
- Sustituir `requiredWidth(640.dp)` por `fillMaxWidth().widthIn(max=640.dp)`.
- Preservar la notificación de cambios de tamaño a `RecyclerView` y no recalcular capacidad del pool si no hay cambio de dimensiones.

**Riesgos y criterios de salida:** no se ha ejecutado una batería de pruebas de ventanas flotantes o pantallas físicas; el build Release solo demuestra integración de código. La validación visual debe cubrir ventanas de 360, 600, 840 y 1024dp, modo split-screen, orientación y fuentes grandes. La auditoría integral de batería, fugas y rendimiento de toda la base (incluidos módulos de sistema y terceros) permanece abierta y necesita perfiles comparativos con dispositivos físicos; no se debe marcar como completada sin esa evidencia.
