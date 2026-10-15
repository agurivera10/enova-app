# Enova IPTV

Reproductor IPTV **para Google TV / Android TV** desarrollado en Kotlin. Proyecto personal de demostración para listas M3U propias o cuyo uso esté autorizado.

## Funciones de esta primera versión

- Importar una lista M3U por URL HTTP(S) o archivo local desde el selector del sistema.
- Organizar canales por grupo, filtrar, ver logotipos y marcar favoritos.
- Navegar con el control remoto (D-pad, Enter y Atrás).
- Reproducir canales con AndroidX Media3 (HLS y formatos progresivos compatibles).
- Guardar la última lista y los favoritos en el dispositivo.
- Sin servidor propio, cuentas ni listas de canales incluidas.

## Obtener la APK sin Android Studio

1. Abrí la pestaña **Actions** de GitHub y habilitá los workflows del repositorio, si te lo pide.
2. Seleccioná **Build debug APK** y ejecutá **Run workflow** (o esperá a la ejecución automática tras un push).
3. Cuando termine en verde, descargá el artifact **enova-iptv-debug-apk**.
4. Descomprimí el ZIP y obtené `app-debug.apk`.

> Estado: código fuente inicial; la APK todavía debe compilarse y probarse en un Google TV real. Una compilación exitosa en Actions confirmará la compatibilidad de dependencias pero no reemplaza la prueba en hardware.

## Instalar en Google TV

En tu televisor Enova con Google TV, habilitá las opciones de desarrollador y la depuración ADB (la ubicación depende del modelo). Desde una PC con `adb`:

```bash
adb connect IP_DEL_TELEVISOR:5555
adb install -r app-debug.apk
```

En dispositivos con *Depuración inalámbrica* y emparejamiento, usá `adb pair` antes de `adb connect`. Solo activá la depuración en una red de confianza y desactivala cuando termines.

## Desarrollo local

Requisitos: JDK 17, Android SDK Platform 35, Android Gradle Plugin 8.9.2, Gradle 8.11.1. En la raíz:

```bash
gradle testDebugUnitTest assembleDebug
```

El proyecto utiliza Media3 1.8.1 y Glide 4.16.0.

## Notas y limitaciones

- Se admite una lista activa a la vez. Cargar otra reemplaza la anterior.
- No incluye EPG, DRM, Chromecast, descarga de emisiones, ni acceso a servicios IPTV.
- Se aceptan URLs HTTP para compatibilidad con algunas listas; HTTP **no cifra** datos ni contenido, preferí HTTPS. Las URLs de la lista pueden contener tokens: evitá compartirlas, especialmente en repositorios públicos.
- La selección de archivos requiere un proveedor de documentos instalado en Google TV; algunas TVs no tienen uno.
- Las transmisiones pueden fallar por codecs, DRM, georrestricciones, conectividad, expiración de URLs o configuración de servidores. La aplicación no evita restricciones de acceso.
- Los favoritos y la lista guardada se almacenan en el dispositivo (SharedPreferences, sin cifrado). No almacenes listas con credenciales sensibles.
- Permití contenido únicamente cuando tengas derechos o autorización de acceso.

## Próximas mejoras

EPG XMLTV, varias listas, buscador y recomendaciones de navegación TV, mejores mensajes de error y pruebas instrumentadas en dispositivos reales.
