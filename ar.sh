#!/bin/bash

FILE="MainActivity.kt"

# 1. Aggiunge la variabile currentSafeCloudAnchorId dopo la dichiarazione della classe
sed -i '/class MainActivity/a \    private var currentSafeCloudAnchorId: String? = null' $FILE

# 2. Correzione placeSafe: Sposta il callback prima della chiamata hostSafeAnchor
# Cerchiamo il blocco e riorganizziamo (usa un approccio a blocchi)
sed -i '/IndoorArSync.hostSafeAnchor(session, anchor)/i \        IndoorArSync.onSafeHosted = { cloudId ->\n            currentSafeCloudAnchorId = cloudId\n            if (indoorRoomCode.isNotEmpty()) {\n                IndoorSessionManager.updateSafeCloudAnchor(indoorRoomCode, cloudId)\n            }\n        }' $FILE

# Rimuoviamo eventuali vecchie assegnazioni di callback se presenti dopo la chiamata
sed -i '/IndoorArSync.hostSafeAnchor/,/}/ { /IndoorArSync.onSafeHosted =/d }' $FILE

# 3. Correzione saveCurrentSession: Inserisce il cloudAnchorId nel costruttore SafeData
# Sostituisce la riga che crea SafeData per includere il campo cloudAnchorId
sed -i 's/val safeData = SafeData(/val safeData = SafeData(\n            cloudAnchorId = currentSafeCloudAnchorId ?: "",/g' $FILE

# 4. Correzione restoreEggsFromCloud: Implementazione logica Resolve
# Qui usiamo un'espressione più complessa per sostituire la logica di recupero
# Nota: questo comando cerca il punto in cui viene estratto safeCloudId e inietta la logica
sed -i '/val safeCloudId = snap.safe.cloudAnchorId/a \            if (!safeCloudId.isNullOrBlank()) {\n                val session = binding.sceneView.arSession\n                IndoorArSync.onSafeResolved = { anchor ->\n                    runOnUiThread { \n                        currentSafeCloudAnchorId = safeCloudId\n                        recreateSafeFromAnchor(anchor, snap.safe.safeType)\n                        proceedToRestoreEggs(snap.eggs)\n                    }\n                }\n                if (session != null) IndoorArSync.resolveSafeAnchor(session, safeCloudId)\n                return@getRoomSnapshot\n            }' $FILE

echo "Patch applicata con successo a $FILE"
