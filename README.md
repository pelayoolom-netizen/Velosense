# 🚴‍♂️ VeloSense - Ciclocomputador GPS Inteligente

<p align="center">
  <strong>Ciclocomputador GPS para ciclismo con métricas en tiempo real, potencia y cadencia estimadas, Coach con IA y sincronización con Strava.</strong>
</p>

---

## 📁 Estructura del Repositorio

El proyecto se encuentra ubicado **directamente en la raíz del repositorio**, listo para desplegarse en GitHub, servicios de hosting web o compilarse en Android:

```text
.
├── VeloSense.apk               # APK Android listo para instalar (30 MB)
├── app-debug.apk               # Copia directa del instalador APK
├── index.html                  # Página web principal y punto de entrada
├── package.json                # Configuración de scripts y dependencias
├── src/                        # Código fuente modular web
│   ├── app.js                  # Lógica del simulador y métricas
│   ├── styles.css              # Estilos visuales VeloSense
│   └── assets/                 # Recursos gráficos
├── public/                     # Recursos estáticos (incluye public/VeloSense.apk)
├── velosense-builder.zip       # Paquete ZIP listo para conversores (index.html en raíz)
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
└── gradle/                     # Wrapper y versiones de Gradle
```

---

## 📱 Cómo Obtener el Archivo APK

### Opción 1: Directamente en la raíz de los archivos del proyecto
El instalador APK ya se encuentra generado y colocado directamente en la raíz del proyecto:
- **`./VeloSense.apk`**
- **`./app-debug.apk`**
- **`./public/VeloSense.apk`**

Cuando descargues los archivos del proyecto (o mediante el botón de descarga en `index.html`), solo tienes que copiar `VeloSense.apk` a tu teléfono Android e instalarlo.

### Opción 2: Desde la ruta de compilación de Gradle
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
