import io, sys, os

ROOT = "/root/giochi/huntix"

def patch(path, old, new, what):
    fp = os.path.join(ROOT, path)
    with io.open(fp, "r", encoding="utf-8") as f:
        s = f.read()
    if old not in s:
        print("SKIP  " + what + "  (non trovato)")
        return
    n = s.count(old)
    s = s.replace(old, new)
    with io.open(fp, "w", encoding="utf-8", newline="") as f:
        f.write(s)
    print("OK    " + what + "  (x%d)" % n)

# 1) Game.cs: rimuovi hook VirtualArController.Ensure
patch(
    "unity-project/Assets/City/Scripts/Game.cs",
    '''            catch (System.Exception e) { OsmDiag.Log("[Game] EggRadar.EnsureHud FALLITO: " + e.Message); }

            // AR Virtuale: pulsante ATTIVA AR quando il player raggiunge
            // (con il joystick) un POI/edificio della mappa virtuale.
            try { City.AR.VirtualArController.Ensure(); }
            catch (System.Exception e) { OsmDiag.Log("[Game] VirtualArController.Ensure FALLITO: " + e.Message); }

            // Telemetria di sessione verso AppLog Android (analisi pregi/difetti)''',
    '''            catch (System.Exception e) { OsmDiag.Log("[Game] EggRadar.EnsureHud FALLITO: " + e.Message); }

            // Telemetria di sessione verso AppLog Android (analisi pregi/difetti)''',
    "Game.cs  hook VirtualArController",
)

# 2) POICustomPageActivity.kt: rimuovi import + bottone
patch(
    "app/src/main/java/com/intelligame/huntix/ui/POICustomPageActivity.kt",
    '''import com.intelligame.huntix.reallife.LocalNeeds
import com.intelligame.huntix.minigames.ar.VirtualArActivity
import com.intelligame.huntix.reallife.PoiJsonFactory''',
    '''import com.intelligame.huntix.reallife.LocalNeeds
import com.intelligame.huntix.reallife.PoiJsonFactory''',
    "POICustomPageActivity  import VirtualAr",
)

patch(
    "app/src/main/java/com/intelligame/huntix/ui/POICustomPageActivity.kt",
    r'''        content.addView(Button(this).apply {
            text = "\uD83C\uDF0D  AR Virtuale (scena 3D)"
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor(UiKit.INDIGO))
            setOnClickListener {
                val intent = Intent(this@POICustomPageActivity, VirtualArActivity::class.java).apply {
                    putExtra(VirtualArActivity.EXTRA_LAT, poiLat)
                    putExtra(VirtualArActivity.EXTRA_LNG, poiLng)
                    putExtra(VirtualArActivity.EXTRA_POI_LAT, poiLat)
                    putExtra(VirtualArActivity.EXTRA_POI_LNG, poiLng)
                    putExtra(VirtualArActivity.EXTRA_POI_NAME, poiName)
                    putExtra(VirtualArActivity.EXTRA_POI_TYPE, poiType)
                }
                startActivity(intent)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(16, 8, 16, 16)
            }
        })''',
    "",
    "POICustomPageActivity  bottone AR Virtuale",
)

# 3) Bridge.kt: rimuovi ramo VirtualArRequest
patch(
    "app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt",
    r'''            // ── AR Virtuale: il player (mosso con il joystick) ha raggiunto un
            //    POI virtuale → apri la fotocamera/scena AR ancorata alla sua
            //    posizione VIRTUALE (WorldOrigin.ToGeo), MAI al GPS reale. ──
            "VirtualArRequest" -> {
                val ctx = UnityPlayer.currentActivity ?: return
                val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
                val lat = j.optDouble("lat", 0.0)
                val lng = j.optDouble("lng", 0.0)
                val intent = Intent(
                    ctx, com.intelligame.huntix.minigames.ar.VirtualArActivity::class.java
                ).apply {
                    putExtra(com.intelligame.huntix.minigames.ar.VirtualArActivity.EXTRA_LAT, lat)
                    putExtra(com.intelligame.huntix.minigames.ar.VirtualArActivity.EXTRA_LNG, lng)
                    putExtra(com.intelligame.huntix.minigames.ar.VirtualArActivity.EXTRA_POI_LAT,
                        j.optDouble("poiLat", lat))
                    putExtra(com.intelligame.huntix.minigames.ar.VirtualArActivity.EXTRA_POI_LNG,
                        j.optDouble("poiLng", lng))
                    putExtra(com.intelligame.huntix.minigames.ar.VirtualArActivity.EXTRA_POI_NAME,
                        j.optString("poiName", "Luogo"))
                    putExtra(com.intelligame.huntix.minigames.ar.VirtualArActivity.EXTRA_POI_TYPE,
                        j.optString("poiType", "luogo"))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
            // ── MiAcitma: tap su un pedone → chat IA (RealLifeChatActivity) ──''',
    '''            // ── MiAcitma: tap su un pedone → chat IA (RealLifeChatActivity) ──''',
    "Bridge.kt  ramo VirtualArRequest",
)

# 4) AndroidManifest.xml: rimuovi dichiarazione activity
patch(
    "app/src/main/AndroidManifest.xml",
    '''        <activity android:name=".minigames.ar.VirtualArActivity" android:exported="false" android:screenOrientation="portrait" android:theme="@style/Theme.ARProto.Fullscreen"/>

            <activity android:name=".TutorialActivity"''',
    '''            <activity android:name=".TutorialActivity"''',
    "AndroidManifest  activity VirtualArActivity",
)