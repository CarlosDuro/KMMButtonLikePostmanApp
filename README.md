# KMMButtonLikePostmanApp

🚨 Aplicación Kotlin Multiplatform Mobile (KMM) que simula un botón de emergencia y realiza llamadas automáticas a APIs, como una colección de Postman, usando lógica compartida entre Android e iOS.

## 📱 Plataformas Soportadas

- Android
- iOS (SwiftUI + CocoaPods + Xcode)

## 📦 Estructura del Proyecto

```plaintext
KMMButtonLikePostmanApp/
├── androidApp/     → Aplicación Android (Jetpack Compose + OkHttp)
├── iosApp/         → Aplicación iOS (SwiftUI + integración vía CocoaPods)
├── shared/         → Módulo compartido (Kotlin Multiplatform + Ktor)

# Proyecto de Integración KMM

## 🔧 Tecnologías Usadas

- **Kotlin Multiplatform Mobile (KMM)**
- **Ktor** para clientes HTTP
- **Jetpack Compose** en Android
- **SwiftUI** en iOS
- **OkHttp** para llamadas en Android
- **CocoaPods** para integración de código Kotlin en Swift

## 🚀 Funcionalidad Principal

- Obtención de token de autenticación para dos servicios (CCM e IoT)
- Inicio y cierre de reportes automáticos en IoT
- Envío de datos (ubicación, entidades) a ambos servicios
- Modularizado para pruebas y mantenimiento sencillo
