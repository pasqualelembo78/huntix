# Terze parti — provenienza, licenze e attribuzioni

Documento relativo all'audit dei repository Unity esterni eseguito il 2026-11-23
(branch `audit-unity-esteri`). I repository sono stati clonati in `/tmp/opencode/research/`
e **non sono stati eseguiti**. Analisi dettagliata: `docs/AUDIT_REPOSITORY_UNITY_ESTERNI_20261123.md`.

## Regola applicata

**Nessun file di codice o asset dei repository sotto elencati è stato copiato tale quale.**
Dove una soluzione è stata riutilizzata, è stata **reimplementata da zero** partendo dal solo
pattern architetturale, perché la licenza del progetto d'origine non lo consentiva oppure
perché l'implementazione Huntix esistente era già più completa. L'unica eccezione è il codice
MIT di Zone Zero espressamente indicato al punto 10: adattato, con attribuzione e con le
dipendenze non permissive rimosse. Nessun asset binario di alcun repository è stato importato. Le attribuzioni sotto servono a
documentare la provenienza delle idee e a non perdere il collegamento con le licenze originali.

I marchi e i nomi dei progetti restano dei rispettivi proprietari. Huntix non incorpora modelli,
texture, audio o file originali di GTA San Andreas né di altri titoli commerciali.

---

## 1. SanAndreasUnity

- Provenienza: https://github.com/in0finite/SanAndreasUnity
- Licenza codice: **MIT**, `Copyright (c) 2015 James King`.
- Testo licenza (dal file `LICENSE` del repository):

```
MIT License

Copyright (c) 2015 James King

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

- Licenza asset: **non verificata**. Il repository non contiene file GTA proprietari, ma carica
  dati di gioco a runtime da una directory configurabile; Huntix **non** utilizza quel meccanismo.
- Uso in Huntix: **sola ispirazione** per budget temporale per frame, limitazione spawn NPC e
  state machine di ingresso/uscita veicoli (già presenti in Huntix con implementazione propria).
  Nessuna riga di codice copiata.

## 2. 3ddriving

- Provenienza: https://github.com/Tritone1/3ddriving
- Licenza codice: **nessuna licenza esplicita** (`license: null`).
- Licenza asset: `THIRD_PARTY_NOTICES.md` interno cita Khronos (CC BY 4.0), Quaternius/Kenney/
  3DAssetsDev (CC0). Non è una licenza per il codice sorgente.
- Uso in Huntix: **sola idea** per il pattern di salvataggio atomico (`.tmp` → rename → `.bak`)
  e per la stratificazione degli input touch. Il salvataggio atomico è stato **valutato e non
  implementato** (Huntix usa `PlayerPrefs` + bridge Android). Nessun codice copiato.

## 3. CarGame

- Provenienza: https://github.com/TeodossiyNushev/CarGame
- Licenza codice: **Apache License 2.0** (file `LICENSE`; `Assets/LICENSE.txt` contiene invece i
  termini Unity Monetization, non una licenza asset).
- Licenza asset: **non verificata** (EasyRoads3D, Windridge, NatureManufacture, pacchetti auto).
- Uso in Huntix: **nessuno**. Progetto non apribile nella copia clonata (ProjectSettings come
  puntatore Git LFS) e con un errore di compilazione verificato.

## 4. CarRace (Overtake)

- Provenienza: https://github.com/Yogesh283/CarRace
- Licenza codice: **nessuna licenza esplicita**.
- Licenza asset: **non verificata** (`Assets/ThirdParty/README.md` si aspetta RCC/NWH esterni).
- Uso in Huntix: **nessuno**. Solo osservazione architetturale sul setup Android centralizzato.

## 5. Grand Road Auto

- Provenienza: https://github.com/BlackRoad-Forge/Grand-Road-Auto
- Licenza codice: **proprietaria BlackRoad**; `BLACKROAD.md` dichiara il fork MIT di
  SanAndreasUnity e la `LICENSE` precisa che il codice upstream resta MIT.
- Uso in Huntix: **nessuno**. cloned risulta identico (`diff -rq`) all'upstream; si fa riferimento
  diretto all'upstream MIT, non a questo fork.

## 6. Traffic Rider Clone

- Provenienza: https://github.com/montetitan/traffic-rider-clone
- Licenza codice: **nessuna licenza esplicita**.
- Licenza asset: primitivi generati a runtime, nessun asset esterno evidente.
- Uso in Huntix: **sola idea** (non implementata in questa fase) per preset qualità mobili e
  missioni con progressione. Nessun codice copiato.

## 7. RaceRider3D

- Provenienza: https://github.com/Boxfort/RaceRider3d
- Licenza codice: **MIT**, `Copyright (c) 2019 Dillon Hemphill`.
- Licenza asset: README dichiara "CCO" (presumibilmente CC0), ma non esiste un file di licenza
  asset né attribuzioni per modelli/audio: **non verificata**.
- Uso in Huntix: **nessuno**. Nessun codice copiato.

## 8. Unity AI Racing Cars with Waypoints

- Provenienza: https://github.com/Frost199/Unity-AI-Racing-cars-with-waypoints
- Licenza codice: **nessuna licenza del repository**; l'unico file è la licenza Apache 2.0 del
  font OpenSans.
- Licenza asset: `Environmental Race Track Pack` di Spyblood Productions (2016), pacchetto
  commerciale redistribuito senza licenza: **non riutilizzabile**.
- Uso in Huntix: **nessuno**.

## 9. Third Person Controller Android

- Provenienza: https://github.com/Jay22K/unity-Third-person-controller-android
- Licenza codice: **GNU GPL v3** (testo integrale standard, senza campi di copyright compilati).
- Licenza asset: **non dichiarata**; il package include Unity Standard Assets, Joystick Pack,
  FBX e animazioni di terze parti.
- Uso in Huntix: **nessuno**. Licenza copyleft incompatibile con la distribuzione di Huntix.

## 10. Zone Zero

- Provenienza: https://github.com/Mishalbhasim/Zone-Zero
- Licenza codice: **MIT**, `Copyright (c) 2026 Mishal Bhasim`.
- Licenza asset: `Assets/StarterAssets/license.txt` = Unity Companion License; BigRookGames
  ("All rights reserved", divieto di redistribuzione di Amplify Color), Photon/PlayFab vendored:
  **non verificati / non copiabili**.
- Uso in Huntix: **codice MIT adattato**, non preso tale quale. Il file
  `unity-project/Assets/City/Scripts/UI/AlternateTouchInput.cs` deriva da
  `Assets/_Project/Scripts/UI/VirtualJoystick.cs` e `.../Player/MobileInputHandler.cs`:
  mantenuta l'attribuzione MIT nel commento in testa, rimossi `using Unity.VisualScripting`
  e il `Debug.Log` per-frame, aggiunti smoothing esponenziale, flag di attivazione e bridge
  Android. È un sistema **affiancato** a `DynamicJoystick`, non una sostituzione.
  **Non copiati**: `Assets/StarterAssets/**` (Unity Companion License), Photon/PlayFab,
  prefab, AppId Photon e ogni asset di terze parti.

## 11. Battle Island Mobile

- Provenienza: https://github.com/ebito-coder/battle-island-mobile
- Licenza codice: **MIT**, `Copyright (c) 2026 ebito-coder`.
- Licenza asset: nessun asset binario nel repository.
- Uso in Huntix: **nessuno** (codice non funzionante: input touch mai collegato, UI senza EventSystem).

## 12. FuzzyTown

- Provenienza: https://github.com/p-v-z/FuzzyTown
- Licenza codice: **Apache License 2.0**.
- Licenza asset: i 266 prefab `Simple People`/`SimpleTown` risultano acquistati
  (`Assets/SimplePeople/Changelog.txt`), licenza **non verificata**.
- Uso in Huntix: **nessuno**. Nessun codice copiato.

## 13. Smart City Digital Twin

- Provenienza: https://github.com/shivesh900/smart-city-digital-twin
- Licenza codice: **nessun file LICENSE** → diritti non concessi.
- Licenza asset: N/A (scene e prefab generati in editor, non presenti nel repository).
- Uso in Huntix: **nessuno**. Solo osservazione architetturale (IDM, semafori adattivi), non implementata.

---

## Città Gioco (2026-11-24)

Generatore `unity-project/Assets/City/Scripts/Game/FictionalCityWorld.cs` ed estrattore
`backend/preprocessing/city_recipe.py`: **codice originale Huntix**, scritto da zero a partire
dalle statistiche estratte offline dai nostri PBF OSM (griglia stradale, isolati, parchi, mix
POI). Non contiene codice, prefab, texture né idee implementative copiate dai 13 repository
auditati: la loro lezione (mondo piccolo e controllato) è stata applicata come scelta di
prodotto, non come import. Nessuna attribuzione aggiuntiva è quindi dovuta.

---

## Aggiornamenti futuri

Se in seguito verrà importato del codice reale da uno di questi progetti, prima del commit
occorre: (1) verificare la licenza specifica del file, (2) aggiungere il testo della licenza e
l'attribuzione in questo documento, (3) conservare gli avvisi di copyright nei file sorgente.
