import java.security.SecureRandom
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// ---------------------------------------------------------------------------
// Firma propia del APK.
// Android solo deja actualizar una app si el APK nuevo viene firmado con la MISMA llave.
// La llave de debug es distinta en cada PC, así que se usa una llave propia guardada en
// keystore.properties + milauncher.jks (fuera de git). Se crea una vez con:
//     ./gradlew crearLlave        (en Windows: gradlew.bat crearLlave)
// Respalda esos dos archivos: sin ellos no se puede actualizar la app sin desinstalarla.
// ---------------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val hasReleaseKey = keystorePropsFile.exists()

android {
    namespace = "cl.alejandro.milauncher"
    compileSdk = 34

    defaultConfig {
        applicationId = "cl.alejandro.milauncher"
        // minSdk bajo a propósito: muchas radios chinas dicen "Android 11/12"
        // pero por dentro corren una versión más antigua.
        minSdk = 24
        targetSdk = 34
        versionCode = 4
        versionName = "1.3"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                // Sin llave propia: se firma con la de debug de este PC (se instala igual,
                // pero no se podrá actualizar con un APK compilado en otro PC).
                logger.warn(
                    "AVISO: no existe keystore.properties; el APK se firma con la llave de debug. " +
                        "Ejecuta './gradlew crearLlave' para crear una llave propia."
                )
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

// Sin dependencias externas: solo el SDK de Android, para que sea liviano (la radio tiene 2 GB de RAM).
dependencies {
}

/** Crea milauncher.jks y keystore.properties con una contraseña aleatoria. Se ejecuta una sola vez. */
tasks.register("crearLlave") {
    group = "firma"
    description = "Crea la llave propia para firmar el APK (milauncher.jks + keystore.properties)."
    doFirst {
        // La llave se lee al iniciar Gradle: si se compila en la misma ejecución,
        // el APK saldría firmado con la llave de debug.
        if (gradle.startParameter.taskNames.any { !it.endsWith("crearLlave") }) {
            throw GradleException("Ejecuta crearLlave solo, y después compila en otro comando.")
        }
    }
    doLast {
        val jks = rootProject.file("milauncher.jks")
        if (keystorePropsFile.exists() || jks.exists()) {
            throw GradleException(
                "Ya existe una llave (keystore.properties o milauncher.jks). " +
                    "No se crea otra para no perder la actual."
            )
        }
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789"
        val random = SecureRandom()
        val password = (1..24).map { chars[random.nextInt(chars.length)] }.joinToString("")
        val keytool = File(
            System.getProperty("java.home"),
            if (System.getProperty("os.name").startsWith("Windows")) "bin/keytool.exe" else "bin/keytool"
        )
        val process = ProcessBuilder(
            keytool.absolutePath, "-genkeypair", "-noprompt",
            "-keystore", jks.absolutePath, "-storetype", "PKCS12",
            "-storepass", password, "-keypass", password,
            "-alias", "milauncher", "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
            "-dname", "CN=Mi Launcher"
        ).inheritIO().start()
        if (process.waitFor() != 0) throw GradleException("keytool falló al crear la llave.")

        keystorePropsFile.writeText(
            """
            |# Llave para firmar Mi Launcher. NO la subas a git y guarda un respaldo
            |# (junto con milauncher.jks): sin ella no se puede actualizar la app.
            |storeFile=milauncher.jks
            |storePassword=$password
            |keyAlias=milauncher
            |keyPassword=$password
            |""".trimMargin()
        )
        println("Llave creada: ${jks.absolutePath} y ${keystorePropsFile.absolutePath}")
        println("Respalda ambos archivos (por ejemplo en un pendrive o en tu nube).")
        println("Ahora compila con: ./gradlew assembleRelease")
    }
}
