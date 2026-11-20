package cl.alejandro.milauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView

/** Cajón con todas las apps. Toque = abrir. Mantener presionado = info de la app (para desinstalar, permisos, etc.). */
class AppsActivity : Activity() {

    private var apps: List<AppEntry> = emptyList()
    private lateinit var adapter: AppsAdapter

    /** Deslizar hacia la izquierda cierra el cajón (vuelve a la pantalla principal). */
    private val swipe by lazy { SwipeDetector(this, toRight = false) { close() } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { close() }

        val grid = findViewById<GridView>(R.id.grid)
        adapter = AppsAdapter()
        grid.adapter = adapter
        grid.setOnItemClickListener { _, _, position, _ ->
            Apps.launch(this, apps[position].component)
            finish()
        }
        grid.setOnItemLongClickListener { _, _, position, _ ->
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + apps[position].component.packageName)
                )
            )
            true
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean =
        swipe.handle(ev) { super.dispatchTouchEvent(it) } || super.dispatchTouchEvent(ev)

    @SuppressLint("MissingSuperCall")
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() = close()

    /** Cierra el cajón con la animación hacia la izquierda. */
    private fun close() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(R.anim.stay, R.anim.slide_out_left)
    }

    override fun onResume() {
        super.onResume()
        val fresh = Apps.load(this)   // viene de memoria salvo que hayan cambiado las apps
        if (fresh !== apps) {
            apps = fresh
            adapter.notifyDataSetChanged()
        }
    }

    private class Holder(val icon: ImageView, val label: TextView)

    private inner class AppsAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false).also {
                it.tag = Holder(it.findViewById(R.id.appIcon), it.findViewById(R.id.appLabel))
            }
            val holder = view.tag as Holder
            val entry = apps[position]
            holder.icon.setImageDrawable(Apps.icon(this@AppsActivity, entry.component))
            holder.label.text = entry.label
            return view
        }
    }
}
