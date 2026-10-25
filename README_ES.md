# Moorty Voice Android — conectores integrados (fuente)

Proyecto Android `com.moorty.voice` con servicio de voz en español, sonidos elegibles, conector de TikTok LIVE comunitario y conector de YouTube Data API v3.

## Configuración
1. Abrí este proyecto en Android Studio o súbelo al repositorio `byronchaves84-hash/Voice`.
2. Compila con Java 17 y Android SDK 35 usando el workflow `.github/workflows/build.yml`.
3. En la app, selecciona `@moorty_yt` o `@tocolita` y pulsa **Conectar TikTok LIVE**.
4. Para YouTube, introduce una API key de YouTube Data API v3 y el ID del canal, guarda y pulsa **Conectar YouTube LIVE**.
5. Elige los sonidos de entrada desde el teléfono. El sonido de suscriptor solo se puede clasificar si TikTok informa explícitamente ese estado.

## Limitaciones importantes
- TikTokLiveJava es comunitario/no oficial; la entrega de eventos depende de TikTok y del conector.
- La API key de YouTube permite chat público y contador público de espectadores, pero no la lista de suscriptores recientes. Esa función requiere OAuth del canal y queda pendiente de integrar.
- El ZIP contiene código fuente, no un APK probado. No se ha podido ejecutar una compilación Android en este entorno; primero debe pasar el workflow de GitHub Actions.
- Los conectores se detienen cuando se cierra la actividad en esta versión. Para uso prolongado con pantalla apagada, hay que mover la gestión de conexiones a un servicio foreground persistente; el servicio de voz ya es foreground, pero los conectores aún no están alojados dentro de él.

Consulta `CONNECTORES_Y_LIMITACIONES.md` para más detalles.
