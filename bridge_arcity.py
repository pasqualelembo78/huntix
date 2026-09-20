import io, sys

ROOT = "/root/giochi/huntix"

def patch(path, old, new, what):
    fp = ROOT + "/" + path
    s = open(fp, encoding="utf-8").read()
    if old not in s:
        print("SKIP  " + what)
        return
    s = s.replace(old, new, 1)
    open(fp, "w", encoding="utf-8", newline="").write(s)
    print("OK    " + what)

# 1) Bridge.kt: branch ArCityRequest -> apre la REALE ArCityActivity
patch(
    "app/src/main/java/com/intelligame/huntix/bridge/Bridge.kt",
    '''            "OutdoorNPCInfo" -> StoreUnityBridge.onOutdoorNPCInfo(jsonData)
            // ── MiAcitma: tap su un pedone → chat IA (RealLifeChatActivity) ──''',
    '''            "OutdoorNPCInfo" -> StoreUnityBridge.onOutdoorNPCInfo(jsonData)
            // ── Realtà Aumentata: pulsante PASSA A AR (Unity) → apre la camera
            //    reale con ARCore; il mondo OSM viene ancorato al piano
            //    inquadrato. Posizione VIRTUALE del player, MAI il GPS reale. ──
            "ArCityRequest" -> {
                val ctx = UnityPlayer.currentActivity ?: return
                val j: JSONObject = try { JSONObject(jsonData) } catch (_: Exception) { return }
                val lat = j.optDouble("lat", 0.0)
                val lng = j.optDouble("lng", 0.0)
                val intent = Intent(
                    ctx, com.intelligame.huntix.minigames.ar.ArCityActivity::class.java
                ).apply {
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_LAT, lat)
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_LNG, lng)
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_POI_LAT,
                        j.optDouble("poiLat", lat))
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_POI_LNG,
                        j.optDouble("poiLng", lng))
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_POI_NAME,
                        j.optString("poiName", "Luogo"))
                    putExtra(com.intelligame.huntix.minigames.ar.ArCityActivity.EXTRA_POI_TYPE,
                        j.optString("poiType", "luogo"))
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                ctx.startActivity(intent)
            }
            // ── MiAcitma: tap su un pedone → chat IA (RealLifeChatActivity) ──''',
    "Bridge.kt  ramo ArCityRequest",
)

# 2) AndroidManifest.xml: dichiara ArCityActivity (dopo le altre minigame ar)
patch(
    "app/src/main/AndroidManifest.xml",
    '''        <activity android:name=".minigames.ar.ARSudokuActivity" android:exported="false" android:screenOrientation="portrait" android:theme="@style/Theme.ARProto.Fullscreen"/>
''',
    '''        <activity android:name=".minigames.ar.ARSudokuActivity" android:exported="false" android:screenOrientation="portrait" android:theme="@style/Theme.ARProto.Fullscreen"/>
        <activity android:name=".minigames.ar.ArCityActivity" android:exported="false" android:screenOrientation="portrait" android:theme="@style/Theme.ARProto.Fullscreen"/>
''',
    "AndroidManifest  activity ArCityActivity",
)