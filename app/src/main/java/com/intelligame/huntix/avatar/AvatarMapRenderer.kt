package com.intelligame.huntix.avatar

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.content.res.Resources
import android.util.Log
import kotlin.math.sin

/**
 * AvatarMapRenderer — Rendering dell'avatar RPM sulla mappa Mapbox.
 *
 * Sostituisce CharacterSpriteManager quando l'utente ha un avatar RPM.
 * Se non ha avatar RPM, fallback al sistema sprite classico.
 *
 * Il renderer genera un bitmap del marker basato su:
 *  1. Thumbnail 2D dell'avatar (dalla RPM Render API o cache locale)
 *  2. Frame decorativo (dal livello/gamification)
 *  3. Accessori equipaggiati (indicatori visivi)
 *  4. Animazione camminata (bouncing)
 *
 * L'avatar sulla mappa è un marker 2D (billboard).
 * L'avatar 3D completo si usa SOLO nella scena AR (via SceneView/Filament).
 */
object AvatarMapRenderer {

    private const val TAG = "AvatarMapRenderer"
    const val MARKER_SIZE_DP = 104 // stessa dimensione del CharacterSpriteManager

    /**
     * Genera il drawable del marker per la mappa Mapbox.
     *
     * Se l'utente ha un avatar RPM con thumbnail → usa quello con frame decorativo.
     * Altrimenti → fallback al disegno Canvas classico (CharacterSpriteManager).
     *
     * @param resources  Android Resources
     * @param sizeDp     Dimensione marker in dp
     * @param walkTick   Tick animazione camminata (0..63)
     * @param level      Livello giocatore (per colore frame)
     * @param facing     Direzione in gradi (0=Nord)
     * @param context    Context per accesso avatar locale
     */
    fun makeAvatarMarkerDrawable(
        resources: Resources,
        sizeDp: Int,
        walkTick: Int,
        level: Int,
        facing: Float = 0f,
        context: Context
    ): BitmapDrawable {
        val dp = resources.displayMetrics.density
        val size = (sizeDp * dp).toInt()

        // Prova a usare il thumbnail RPM
        val thumbnail = AvatarManager.getThumbnailBitmap(context)
        if (thumbnail != null) {
            val bmp = renderAvatarMarker(thumbnail, size, walkTick, level, context)
            return BitmapDrawable(resources, bmp)
        }

        // Fallback: usa CharacterSpriteManager classico
        return com.intelligame.huntix.CharacterSpriteManager
            .makeCharacterDrawable(resources, sizeDp, walkTick, level, facing)
    }

    /**
     * Renderizza il marker avatar con:
     *  - Thumbnail RPM al centro (circolare)
     *  - Ring colorato basato sul livello
     *  - Badge accessori equipaggiati
     *  - Bouncing animation
     */
    private fun renderAvatarMarker(
        thumbnail: Bitmap,
        size: Int,
        walkTick: Int,
        level: Int,
        context: Context
    ): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f

        // ── Animazione bouncing ──────────────────────────────────
        val walkAngle = (walkTick * Math.PI / 16).toFloat()
        val bounceY = (sin(walkAngle.toDouble() * 2) * 3f).toFloat()

        // ── Ombra sotto il marker ────────────────────────────────
        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(60, 0, 0, 0)
            maskFilter = BlurMaskFilter(8f, BlurMaskFilter.Blur.NORMAL)
        }
        canvas.drawOval(
            cx - size * 0.3f, size - 16f,
            cx + size * 0.3f, size - 4f,
            shadowPaint
        )

        // ── Ring colorato (livello) ──────────────────────────────
        val ringColor = when {
            level >= 40 -> Color.parseColor("#FFD700")  // Oro
            level >= 30 -> Color.parseColor("#E879F9")  // Viola
            level >= 20 -> Color.parseColor("#FF6B35")  // Arancio
            level >= 10 -> Color.parseColor("#00B4FF")  // Blu
            else        -> Color.parseColor("#00FF88")  // Verde
        }

        val ringRadius = size * 0.38f
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ringColor
            style = Paint.Style.STROKE
            strokeWidth = size * 0.06f
        }
        canvas.drawCircle(cx, cy + bounceY - 8f, ringRadius, ringPaint)

        // ── Glow esterno ─────────────────────────────────────────
        if (level >= 20) {
            val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ringColor
                alpha = 40
                maskFilter = BlurMaskFilter(12f, BlurMaskFilter.Blur.OUTER)
            }
            canvas.drawCircle(cx, cy + bounceY - 8f, ringRadius + 4f, glowPaint)
        }

        // ── Sfondo circolare bianco ──────────────────────────────
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val avatarRadius = ringRadius - size * 0.04f
        canvas.drawCircle(cx, cy + bounceY - 8f, avatarRadius, bgPaint)

        // ── Thumbnail avatar (clippato in cerchio) ───────────────
        canvas.save()
        val clipPath = Path().apply {
            addCircle(cx, cy + bounceY - 8f, avatarRadius - 2f, Path.Direction.CW)
        }
        canvas.clipPath(clipPath)

        val scaledThumb = Bitmap.createScaledBitmap(
            thumbnail,
            (avatarRadius * 2).toInt(),
            (avatarRadius * 2).toInt(),
            true
        )
        canvas.drawBitmap(
            scaledThumb,
            cx - avatarRadius,
            cy + bounceY - 8f - avatarRadius,
            null
        )
        canvas.restore()

        // ── Badge accessori (piccoli indicatori) ─────────────────
        val loadout = AccessoryManager.getCurrentLoadout(context)
        drawAccessoryBadges(canvas, loadout, cx, cy + bounceY - 8f, ringRadius, size)

        return bmp
    }

    /**
     * Disegna piccoli badge emoji per gli accessori equipaggiati
     * attorno al cerchio dell'avatar.
     */
    private fun drawAccessoryBadges(
        canvas: Canvas,
        loadout: AccessoryManager.EquipLoadout,
        cx: Float,
        cy: Float,
        radius: Float,
        size: Int
    ) {
        val badgeSize = size * 0.15f
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = badgeSize
            textAlign = Paint.Align.CENTER
        }

        // Badge testa (top)
        if (loadout.headId.isNotBlank()) {
            val def = AccessoryManager.getAccessoryDef(loadout.headId)
            def?.let {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(it.rarity.colorHex)
                    alpha = 200
                }
                canvas.drawCircle(cx, cy - radius - badgeSize * 0.3f, badgeSize * 0.7f, bgPaint)
                canvas.drawText(it.emoji, cx, cy - radius + badgeSize * 0.2f, badgePaint)
            }
        }

        // Badge corpo (destra)
        if (loadout.bodyId.isNotBlank()) {
            val def = AccessoryManager.getAccessoryDef(loadout.bodyId)
            def?.let {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(it.rarity.colorHex)
                    alpha = 200
                }
                canvas.drawCircle(cx + radius + badgeSize * 0.3f, cy, badgeSize * 0.7f, bgPaint)
                canvas.drawText(it.emoji, cx + radius + badgeSize * 0.3f, cy + badgeSize * 0.3f, badgePaint)
            }
        }

        // Badge effetto (sinistra)
        if (loadout.effectId.isNotBlank()) {
            val def = AccessoryManager.getAccessoryDef(loadout.effectId)
            def?.let {
                val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.parseColor(it.rarity.colorHex)
                    alpha = 200
                }
                canvas.drawCircle(cx - radius - badgeSize * 0.3f, cy, badgeSize * 0.7f, bgPaint)
                canvas.drawText(it.emoji, cx - radius - badgeSize * 0.3f, cy + badgeSize * 0.3f, badgePaint)
            }
        }
    }

    /**
     * Controlla se l'utente ha un avatar RPM configurato.
     * Usato per decidere se usare AvatarMapRenderer o CharacterSpriteManager.
     */
    fun hasRpmAvatar(context: Context): Boolean =
        AvatarManager.hasLocalAvatar(context) &&
        AvatarPersistenceManager.hasLocalAvatar(context)
}
