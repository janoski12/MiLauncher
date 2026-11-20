package cl.alejandro.milauncher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import android.widget.Toast

/** Una app instalada que se puede abrir. */
data class AppEntry(val label: String, val component: ComponentName)

/**
 * Listar / abrir apps.
 *
 * Leer la lista de apps y sus íconos es lento en la radio (2 GB de RAM), así que ambos
 * se guardan en memoria y solo se vuelven a leer cuando se instala, actualiza o
 * desinstala una app (o cambia el idioma). Todo se usa desde el hilo principal.
 */
object Apps {

    private var cached: List<AppEntry>? = null

    /** Sube cada vez que cambian las apps instaladas; sirve para saber si hay que redibujar. */
    var version = 0
        private set

    // Pocas entradas: lo que se ve en pantalla más el cajón; no hace falta guardar todas.
    private val icons = LruCache<ComponentName, Drawable>(80)

    private var receiverRegistered = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = invalidate()
    }

    private fun invalidate() {
        cached = null
        icons.evictAll()
        version++
    }

    private fun ensureReceiver(context: Context) {
        if (receiverRegistered) return
        val app = context.applicationContext
        val packages = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val other = IntentFilter().apply {
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
        }
        for (filter in listOf(packages, other)) {
            // Son avisos del sistema (protegidos); ninguna otra app puede enviarlos.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                app.registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                app.registerReceiver(packageReceiver, filter)
            }
        }
        receiverRegistered = true
    }

    /** Lista de apps abribles, ordenadas por nombre (sin incluir este launcher). */
    fun load(context: Context): List<AppEntry> {
        ensureReceiver(context)
        cached?.let { return it }

        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val list = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                AppEntry(
                    label = it.loadLabel(pm).toString(),
                    component = ComponentName(it.activityInfo.packageName, it.activityInfo.name)
                )
            }
            .distinctBy { it.component }
            .sortedBy { it.label.lowercase() }
        cached = list
        return list
    }

    fun icon(context: Context, component: ComponentName): Drawable? {
        val base = icons.get(component) ?: try {
            context.packageManager.getActivityIcon(component).also { icons.put(component, it) }
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        // Copia liviana (comparte la imagen): un mismo Drawable no se puede poner en dos vistas.
        return base.constantState?.newDrawable(context.resources) ?: base
    }

    fun launch(context: Context, component: ComponentName) {
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    /** Abre una app por su nombre de paquete (por ejemplo, la app que está reproduciendo música). */
    fun launchPackage(context: Context, packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, R.string.cannot_open, Toast.LENGTH_SHORT).show()
        }
    }

    fun labelOfPackage(context: Context, packageName: String): String = try {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        packageName
    }
}
