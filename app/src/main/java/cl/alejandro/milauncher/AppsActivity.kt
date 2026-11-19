package cl.alejandro.milauncher

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_apps)

        findViewById<ImageButton>(R.id.btnClose).setOnClickListener { finish() }

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

    override fun onResume() {
        super.onResume()
        apps = Apps.load(this)
        adapter.notifyDataSetChanged()
    }

    private inner class AppsAdapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(position: Int) = apps[position]
        override fun getItemId(position: Int) = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_app, parent, false)
            val entry = apps[position]
            view.findViewById<ImageView>(R.id.appIcon).setImageDrawable(Apps.icon(this@AppsActivity, entry.component))
            view.findViewById<TextView>(R.id.appLabel).text = entry.label
            return view
        }
    }
}
