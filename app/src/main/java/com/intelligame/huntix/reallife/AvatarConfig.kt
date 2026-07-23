package com.intelligame.huntix.reallife

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader

/**
 * AvatarConfig — modello dati dell'aspetto del giocatore nel mondo Real Life.
 *
 * Persistenza: SharedPreferences "rl_avatar" (per-device, sync futura cloud).
 * Il default è un ragazzo generico con t-shirt rossa e jeans blu.
 */
data class AvatarConfig(
    val gender: String = "male",         // "male" | "female"
    val skinColor: Int = 0xFFDBA97A,     // colore pelle
    val hairColor: Int = 0xFF3B2314,     // colore capelli
    val hairStyle: String = "short",     // "short" | "long" | "bald" | "mohawk" | "ponytail"
    val shirtColor: Int = 0xFFE53935,    // colore maglia
    val pantsColor: Int = 0xFF1A237E,    // colore pantaloni
    val shoeColor: Int = 0xFF424242,     // colore scarpe
    val eyeColor: Int = 0xFF1B5E20       // colore occhi
) {
    companion object {
        private const val PREFS = "rl_avatar"

        // Keys
        private const val K_GENDER = "gender"
        private const val K_SKIN = "skin"
        private const val K_HAIR_COLOR = "hair_color"
        private const val K_HAIR_STYLE = "hair_style"
        private const val K_SHIRT = "shirt"
        private const val K_PANTS = "pants"
        private const val K_SHOES = "shoes"
        private const val K_EYES = "eyes"

        // Palette pelle (6 tonalità)
        val SKIN_COLORS = intArrayOf(
            0xFFF5D0A9.toInt(), // chiaro
            0xFFDBA97A.toInt(), // oliva
            0xFFC68642.toInt(), // medio
            0xFFA0522D.toInt(), // abbronzato
            0xFF8D5524.toInt(), // scuro
            0xFF614335.toInt()  // molto scuro
        )

        // Palette capelli
        val HAIR_COLORS = intArrayOf(
            0xFF1A1209.toInt(), // nero
            0xFF3B2314.toInt(), // castano scuro
            0xFF8B5E3C.toInt(), // castano
            0xFFD4A76A.toInt(), // biondo
            0xFFB7410E.toInt(), // rosso
            0xFF757575.toInt(), // grigio
            0xFFE91E63.toInt(), // rosa
            0xFF2196F3.toInt(), // blu
            0xFF9C27B0.toInt()  // viola
        )

        // Palette vestiti
        val SHIRT_COLORS = intArrayOf(
            0xFFE53935.toInt(), // rosso
            0xFF1E88E5.toInt(), // blu
            0xFF43A047.toInt(), // verde
            0xFFFDD835.toInt(), // giallo
            0xFFFB8C00.toInt(), // arancione
            0xFF8E24AA.toInt(), // viola
            0xFFEC407A.toInt(), // rosa
            0xFF546E7A.toInt(), // grigio
            0xFF212121.toInt(), // nero
            0xFFFFFFFF.toInt()  // bianco
        )

        val PANTS_COLORS = intArrayOf(
            0xFF1A237E.toInt(), // blu scuro (jeans)
            0xFF42A5F5.toInt(), // jeans chiari
            0xFF212121.toInt(), // nero
            0xFF5D4037.toInt(), // marrone
            0xFF757575.toInt(), // grigio
            0xFF1B5E20.toInt(), // verde militare
            0xFFFFFFFF.toInt()  // bianco
        )

        val SHOE_COLORS = intArrayOf(
            0xFF424242.toInt(), // nero
            0xFF8D6E63.toInt(), // marrone
            0xFFFFFFFF.toInt(), // bianco
            0xFFE53935.toInt(), // rosso
            0xFF1565C0.toInt(), // blu
        )

        val HAIR_STYLES = arrayOf("short", "long", "bald", "mohawk", "ponytail")
        val HAIR_STYLE_LABELS = arrayOf("Corti", "Lunghi", "Calvo", "Mohawk", "Coda")

        fun load(context: Context): AvatarConfig {
            val p = prefs(context)
            return AvatarConfig(
                gender = p.getString(K_GENDER, "male") ?: "male",
                skinColor = p.getInt(K_SKIN, SKIN_COLORS[1]),
                hairColor = p.getInt(K_HAIR_COLOR, HAIR_COLORS[1]),
                hairStyle = p.getString(K_HAIR_STYLE, "short") ?: "short",
                shirtColor = p.getInt(K_SHIRT, SHIRT_COLORS[0]),
                pantsColor = p.getInt(K_PANTS, PANTS_COLORS[0]),
                shoeColor = p.getInt(K_SHOES, SHOE_COLORS[0]),
                eyeColor = p.getInt(K_EYES, 0xFF1B5E20.toInt())
            )
        }

        fun save(context: Context, config: AvatarConfig) {
            prefs(context).edit().apply {
                putString(K_GENDER, config.gender)
                putInt(K_SKIN, config.skinColor)
                putInt(K_HAIR_COLOR, config.hairColor)
                putString(K_HAIR_STYLE, config.hairStyle)
                putInt(K_SHIRT, config.shirtColor)
                putInt(K_PANTS, config.pantsColor)
                putInt(K_SHOES, config.shoeColor)
                putInt(K_EYES, config.eyeColor)
                apply()
            }
        }

        private fun prefs(context: Context): SharedPreferences =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    // ── Rendering 2D preview (usato da AvatarCustomizeActivity) ──

    /**
     * Disegna una preview 2D dell'avatar su un Canvas.
     * Stile "chibi" semplice: testa grande, corpo compatto.
     */
    fun drawPreview(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        val s = scale

        // Ombra sotto i piedi
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(40, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawOval(cx - 20 * s, cy + 58 * s, cx + 20 * s, cy + 66 * s, shadowPaint)

        // ── SCARPE ──
        val shoePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shoeColor; style = Paint.Style.FILL }
        canvas.drawRoundRect(cx - 16 * s, cy + 48 * s, cx - 4 * s, cy + 60 * s, 4 * s, 4 * s, shoePaint)
        canvas.drawRoundRect(cx + 4 * s, cy + 48 * s, cx + 16 * s, cy + 60 * s, 4 * s, 4 * s, shoePaint)

        // ── PANTALONI ──
        val pantsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = pantsColor; style = Paint.Style.FILL }
        canvas.drawRoundRect(cx - 14 * s, cy + 22 * s, cx, cy + 50 * s, 3 * s, 3 * s, pantsPaint)
        canvas.drawRoundRect(cx, cy + 22 * s, cx + 14 * s, cy + 50 * s, 3 * s, 3 * s, pantsPaint)

        // ── MAGLIA (corpo) ──
        val shirtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shirtColor; style = Paint.Style.FILL }
        canvas.drawRoundRect(cx - 16 * s, cy - 2 * s, cx + 16 * s, cy + 26 * s, 6 * s, 6 * s, shirtPaint)

        // ── BRACCIA ──
        val armPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = skinColor; style = Paint.Style.FILL }
        canvas.drawRoundRect(cx - 22 * s, cy, cx - 16 * s, cy + 20 * s, 3 * s, 3 * s, armPaint)
        canvas.drawRoundRect(cx + 16 * s, cy, cx + 22 * s, cy + 20 * s, 3 * s, 3 * s, armPaint)

        // ── TESTA ──
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = skinColor; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy - 22 * s, 18 * s, headPaint)

        // ── CAPELLI ──
        val hairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = hairColor; style = Paint.Style.FILL }
        when (hairStyle) {
            "short" -> {
                canvas.drawArc(cx - 18 * s, cy - 40 * s, cx + 18 * s, cy - 14 * s, 180f, 180f, true, hairPaint)
            }
            "long" -> {
                canvas.drawArc(cx - 18 * s, cy - 40 * s, cx + 18 * s, cy - 14 * s, 180f, 180f, true, hairPaint)
                canvas.drawRoundRect(cx - 20 * s, cy - 30 * s, cx - 14 * s, cy + 4 * s, 4 * s, 4 * s, hairPaint)
                canvas.drawRoundRect(cx + 14 * s, cy - 30 * s, cx + 20 * s, cy + 4 * s, 4 * s, 4 * s, hairPaint)
            }
            "bald" -> {
                // niente capelli
            }
            "mohawk" -> {
                canvas.drawRoundRect(cx - 3 * s, cy - 42 * s, cx + 3 * s, cy - 18 * s, 3 * s, 3 * s, hairPaint)
            }
            "ponytail" -> {
                canvas.drawArc(cx - 18 * s, cy - 40 * s, cx + 18 * s, cy - 14 * s, 180f, 180f, true, hairPaint)
                canvas.drawRoundRect(cx - 3 * s, cy - 40 * s, cx + 3 * s, cy - 32 * s, 2 * s, 2 * s, hairPaint)
                canvas.drawRoundRect(cx - 2 * s, cy - 32 * s, cx + 2 * s, cy - 22 * s, 2 * s, 2 * s, hairPaint)
            }
        }

        // ── OCCHI ──
        val eyeWhitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
        val eyePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = eyeColor; style = Paint.Style.FILL }
        val eyeBlackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }

        // occhio sinistro
        canvas.drawOval(cx - 11 * s, cy - 26 * s, cx - 5 * s, cy - 20 * s, eyeWhitePaint)
        canvas.drawCircle(cx - 8 * s, cy - 23 * s, 2.2f * s, eyePaint)
        canvas.drawCircle(cx - 7.8f * s, cy - 23.2f * s, 1f * s, eyeBlackPaint)

        // occhio destro
        canvas.drawOval(cx + 5 * s, cy - 26 * s, cx + 11 * s, cy - 20 * s, eyeWhitePaint)
        canvas.drawCircle(cx + 8 * s, cy - 23 * s, 2.2f * s, eyePaint)
        canvas.drawCircle(cx + 8.2f * s, cy - 23.2f * s, 1f * s, eyeBlackPaint)

        // ── BOCCA (sorriso) ──
        val mouthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#C62828"); style = Paint.Style.STROKE
            strokeWidth = 1.5f * s; isAntiAlias = true
        }
        canvas.drawArc(cx - 5 * s, cy - 18 * s, cx + 5 * s, cy - 12 * s, 10f, 160f, false, mouthPaint)
    }

    /**
     * Genera un Bitmap preview dell'avatar.
     */
    fun previewBitmap(sizePx: Int = 256): android.graphics.Bitmap {
        val bmp = android.graphics.Bitmap.createBitmap(sizePx, sizePx, android.graphics.Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)

        // Sfondo cerchio
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                sizePx / 2f, sizePx / 2f, sizePx / 2f,
                intArrayOf(Color.parseColor("#1A1030"), Color.parseColor("#0D0620")),
                floatArrayOf(0.7f, 1f), Shader.TileMode.CLAMP
            )
        }
        c.drawCircle(sizePx / 2f, sizePx / 2f, sizePx / 2f, bgPaint)

        drawPreview(c, sizePx / 2f, sizePx / 2f + 10f, sizePx / 160f)
        return bmp
    }
}
