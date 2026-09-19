# 🚴‍♂️ VeloSense - Ciclocomputador GPS Inteligente

<p align="center">
  <strong>Ciclocomputador GPS para ciclismo con métricas en tiempo real, potencia y cadencia estimadas, Coach con IA y sincronización con Strava.</strong>
</p>

---

## 📁 Estructura del Repositorio

El proyecto se encuentra ubicado **directamente en la raíz del repositorio**, listo para desplegarse en GitHub, servicios de hosting web o compilarse en Android:

```text
.
├── index.html                  # Página web principal y simulador interactivo
├── package.json                # Configuración de scripts y metadatos
├── README.md                   # Documentación del proyecto
├── metadata.json               # Configuración de AI Studio
├── .github/
│   └── workflows/
│       └── build-apk.yml       # GitHub Actions para compilar APK automáticamente
├── app/                        # Módulo nativo Android (Kotlin + Jetpack Compose)
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── AndroidManifest.xml
│           ├── java/com/example/
│           └── res/
├── build.gradle.kts            # Configuración raíz de Gradle
├── settings.gradle.kts         # Configuración de módulos Gradle
└── public/                     # Recursos estáticos
```

---

## 📱 Cómo Obtener el Archivo APK

### Opción 1: Desde Google AI Studio (Recomendado)
- En el menú superior derecho de **AI Studio**, haz clic en el botón de menú / exportación y pulsa en **"Download APK"** o **"Generate APK"**.

### Opción 2: Archivo APK ya compilado
Si has clonado o descargado el proyecto con la carpeta `build/`, el instalador se encuentra en:
```text
app/build/outputs/apk/debug/app-debug.apk
```

### Opción 3: Compilación con Gradle / Android Studio
Abre la raíz del proyecto en **Android Studio** o ejecuta en tu terminal:
```bash
gradle assembleDebug
```
El archivo se generará en `app/build/outputs/apk/debug/app-debug.apk`.

### Opción 4: GitHub Actions
Al hacer `git push` a la rama `main` en GitHub, el workflow automático `.github/workflows/build-apk.yml` compilará la aplicación y podrás descargar el instalador directamente desde la pestaña **Actions > Artifacts**.

---

## 🌐 Despliegue Web / Conversores ZIP a APK

- El archivo `index.html` está ubicado en la raíz del proyecto.
- Si usas herramientas de conversión como **Website 2 APK Builder**, **AppsGeyser** o **Cordova**, comprime directamente el contenido de este directorio asegurándote de que `index.html` quede en el primer nivel del archivo `.zip`.
- Si usas plataformas como **Vercel**, **Netlify** o **GitHub Pages**, se detectará automáticamente el archivo `index.html` y `package.json` en la raíz.
