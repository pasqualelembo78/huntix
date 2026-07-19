package com.intelligame.huntix

import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray

/**
 * SaveSlotActivity — gestione slot di salvataggio locale (sessioni indoor).
 */
class SaveSlotActivity : BaseNavActivity() {

    override fun activeTab() = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val c = this
        val prefs = getSharedPreferences("save_slots", MODE_PRIVATE)
        val box = LinearLayout(c).apply { orientation = LinearLayout.VERTICAL }

        fun load(): MutableList<String> {
            val arr = JSONArray(prefs.getString("slots", "[]"))
            val list = mutableListOf<String>()
            for (i in 0 until arr.length()) list.add(arr.getString(i))
            return list
        }
        fun save(list: MutableList<String>) {
            prefs.edit().putString("slots", JSONArray(list).toString()).apply()
        }
        fun render() {
            box.removeAllViews()
            val list = load()
            if (list.isEmpty()) box.addView(UiKit.comingSoon(c, "Nessuno slot", "Crea uno slot per salvare la sessione."))
            list.forEachIndexed { i, name ->
                box.addView(UiKit.card(c,
                    android.widget.TextView(c).apply {
                        text = "💾 $name"; textSize = 14f; setTextColor(android.graphics.Color.WHITE)
                        setTypeface(android.graphics.Typeface.DEFAULT_BOLD)
                    },
                    UiKit.button(c, "🗑️ Elimina", "#C62828") {
                        val l = load(); l.removeAt(i); save(l); render()
                    }
                ))
            }
        }
        render()

        val input = EditText(c).apply {
            hint = "Nome slot (es: Soggiorno)"; setHintTextColor(android.graphics.Color.parseColor("#555577"))
            setTextColor(android.graphics.Color.WHITE); textSize = 15f; maxLines = 1
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = UiKit.dp(c, 10).toFloat()
                setColor(android.graphics.Color.parseColor(UiKit.BG_CARD))
                setStroke(1, android.graphics.Color.parseColor("#334466"))
            }
            setPadding(UiKit.dp(c, 14), UiKit.dp(c, 12), UiKit.dp(c, 14), UiKit.dp(c, 12))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = UiKit.dp(c, 10) }
        }

        val content = UiKit.scroll(c,
            UiKit.title(c, "Slot di salvataggio", "💾"),
            UiKit.subtitle(c, "Salva e riprendi le sessioni di caccia indoor."),
            input,
            UiKit.button(c, "➕  Nuovo slot", UiKit.ACCENT) {
                val name = input.text.toString().trim()
                if (name.isBlank()) { Toast.makeText(c, "Inserisci un nome", Toast.LENGTH_SHORT).show(); return@button }
                val l = load(); l.add(name); save(l); input.setText(""); render()
            },
            UiKit.section(c, "Slot esistenti"), box
        )
        setContentView(content)
    }
}
