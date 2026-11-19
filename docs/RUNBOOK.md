================================================================================
HUNTIX - RUNBOOK BUILD / ROLLBACK / RACCOLTA CRASH
================================================================================
Data: 2026-11-20
Branch: flat-world (locale, NESSUN push)
Package Android: com.intelligame.huntix

Questo documento risponde a tre domande operative:
  1. come si ricostruisce l'APK con i commit del giorno,
  2. come si torna indietro se un pezzo rompe,
  3. come si raccoglie un crash senza perdere la coda del log.

I numeri misurati sono in docs/BASELINE.md. Il piano dei pezzi e' in
docs/piano_lavoro.txt.

--------------------------------------------------------------------------------
1. BUILD RELEASE
--------------------------------------------------------------------------------

REGOLA: non ricostruire mentre l'utente sta provando un APK. Chiedere sempre
il via libera; ricostruire sovrascrive app-release-signed.apk.

Build COMPLETA (fonti Unity cambiate -> AAR ricreato Automaticamente):

    cd /root/giochi/huntix
    SKIP_MAP_UPDATE=1 ./build_release.sh --auto

  - SKIP_MAP_UPDATE=1 evita il prompt "Vuoi aggiornare la mappa?" (idempotente
    comunque, ma inutile durante un ciclo di prova).
  - I sorgenti Unity vengono fingerprintati: se sono cambiati, lo script
    ricrea l'AAR da solo (log: "Fonti Unity modificate: ricreo AAR
    automaticamente"). Non serve FORCE_UNITY_AAR in condizioni normali.
  - FORCE_UNITY_AAR=1 forza la ricreazione anche se il fingerprint e' uguale.
    Utile dopo un export .meta/fbx rigenerato a mano.
  - SKIP_UNITY_AAR=1 compila SOLO l'APK contro gli stub: veloce, ma il gioco
    non contiene le nuove logiche Unity. Usarlo solo per prove di packaging.
  - SKIP_UNITY_LICENSE_CHECK=1 salta il check licenza (ultima ratio).

Build con mappa aggiornata (quando serve davvero):

    ./build_release.sh --auto
    # rispondere alle domande: area Italia, area Mondo
    # lo script svuota anche la cache del tile server a fine aggiornamento

Output (percorso assoluto):

    app/build/outputs/apk/release/app-release-signed.apk   (~184 MB, firmato v3)
    app/libs/unityLibrary-release.aar                      (~89 MB)

Firma: DN CN=Huntix, OU=Huntix, O=Huntix, L=IT, ST=IT, C=IT
(keystore huntix-release.keystore, apksigner + zipalign -p 4).

Installazione sul device:

    adb install -r app/build/outputs/apk/release/app-release-signed.apk

Note: il build e' lento (export Unity + Il2Cpp + Gradle). Il log del buildup
fluidita' e' in /tmp/build_release_fluidita.log; non lanciare due build
in parallelo (Unity Hub e Gradle si contendono lock e licenza).

--------------------------------------------------------------------------------
2. COSA C'ERA NELL'APK IN PROVA (19 Nov 2026)
--------------------------------------------------------------------------------

APK: 19 Nov 17:20, AAR: 19 Nov 17:18.

Contiene (confermato dai campi nel logcat incollato):
  - fluidita' (376fbc9): Kenney di default, toggle Quaternius, chunk leggeri
  - Giorno 1 (4c178ef): sensore FootIK / verifica avatar Humanoid
  - Giorno 2 (b2fdb04): beacon con hist= e ground=
  - review 1 e 2 (2fd82ce, dc775fe)

La presenza di "hist=..." e "ground=..." nel beacon prova che Giorno 2 c'e'.
La presenza di "ikFired=True" prova che il sensore del Giorno 1 e' attivo.

NON contiene (commit successivi alla creazione dell'AAR):
  - Giorno 3a (5f0b14b): materiali/clip condivisi
  - Giorno 3b (94e664f): abitacoli lazy

Quindi: i fix di memoria del Giorno 3 NON sono ancora stati provati su device.
Il crash eventualmente riportato dall'APK in prova e' PREcedente a quei fix.

--------------------------------------------------------------------------------
3. ROLLBACK
--------------------------------------------------------------------------------

Il repo lavora su flat-world con commit locali piccoli e reversibili.
NON eseguire git push (blocco esplicito dell'utente).

Rollback SOLO dell'APK (senza toccare il sorgente):
  - l'APK precedente NON viene conservato dallo script di build: viene
    sovrascritto. PRIMA di una build che si teme possa rompere, copiarlo:

        cp app/build/outputs/apk/release/app-release-signed.apk \
           /tmp/huntix_apk_buono_<data>.apk

  - per rimetterlo:

        adb install -r /tmp/huntix_apk_buono_<data>.apk

Rollback del codice di un pezzo:

    git log --oneline -12              # trovare il commit del pezzo
    git revert --no-edit <sha>         # crea un commit inverso, non riscrive la storia
    git diff --stat                    # verificare cosa e' cambiato
    SKIP_MAP_UPDATE=1 ./build_release.sh --auto

  - se il pezzo ha toccato solo C# Unity, il revert viene rilevato dal
    fingerprint e l'AAR viene ricreato da solo.
  - se il pezzo ha toccato asset .prefab/.controller/.meta, ricontrollare i GUID:
    la build riscrive PlayerHero.prefab e PlayerLocomotion.controller, quindi
    quei file possono presentarsi come "M" anche senza edit manuali.

Ripristino completo a un tag:

    git tag            # i tag previsti: m1-walk ... m6-beta (non ancora creati)
    git checkout <tag>
    # cambio ramo/pezzo: ricordarsi di ricreare l'AAR (FORCE_UNITY_AAR=1)

Backup dati backend (separato dal codice):

    cd backend && ./backup.sh
    # /var/backups/huntix/db_<timestamp>.db.gz, retention 30 giorni

--------------------------------------------------------------------------------
4. RACCOLTA DI UN CRASH / ANR
--------------------------------------------------------------------------------

Il logcat incollato il 19 Nov era troncato a meta' di un [Building][Spawn]:
mancava la coda, quindi non e' stato possibile distinguere SIGSEGV / SIGABRT
per OOM / ANR / LOW_MEMORY. NON ripetere l'errore: usare lo script.

Prima di riprodurre, azzerare i buffer:

    adb logcat -c
    adb logcat -b crash -c

Riproduce il crash, poi:

    ./scripts/raccogli_crash.sh
    # oppure, se serve il bugreport completo (piu' lento):
    ./scripts/raccogli_crash.sh --bugreport

Lo script crea /tmp/opencode/huntix_crash_<timestamp>/ con:

    RIEPILOGO.txt        segnale, ultimo beacon [AR], tombstone disponibili
    crash_buffer.txt     adb logcat -b crash -d   <-- LA FONTE DECISIVA
    huntix_filtrato.txt  tag Huntix + FATAL/SIG*/ANR/OutOfMemory
    logcat_full.txt      ultime 20000 righe con threadtime
    meminfo.txt          PSS reale del processo (diverso da ram=/peak=)
    package.txt/versione.txt
    tombstone_list.txt / tombstones/
    devices.txt

Se non c'e' un device collegato qui, e' l'utente a eseguire lo script e a
incollare RIEPILOGO.txt + crash_buffer.txt.

Comandi manuali equivalenti (se lo script non gira):

    adb logcat -b crash -d
    adb shell ls -l /data/tombstones/
    adb shell dumpsys meminfo com.intelligame.huntix
    adb shell dumpsys activity anr com.intelligame.huntix

--------------------------------------------------------------------------------
5. COME LEGGERE IL SEGNALE (albero decisionale)
--------------------------------------------------------------------------------

"signal 11 (SIGSEGV)" nel crash buffer o tombstone
  -> crash nativo (Il2Cpp/Unity/ARCore). Serve la backtrace del tombstone.
     Guardare se il frame top e' in libc/Unity/ARCore. Non e' un problema
     di memoria gestita: i fix 3a/3b non lo risolvono.

"signal 6 (SIGABRT)" + "abort" + "Could not allocate" / "Out of memory"
  -> OOM nativo. Qui i fix 3a/3b (materiali/clip/abitacoli condivisi) sono
     la direzione giusta. Confrontare meminfo.txt con il limite del device.

"ActivityManager: ANR in com.intelligame.huntix" + trace del main thread
  -> main thread bloccato. Sospetto: build chunk da 5,4 s (vedi BASELINE).
     In questo caso il pezzo successivo e' il budget di build, NON la memoria.

"lowmemorykiller" / "Killed process ... adj ..." in logcat
  -> il sistema ha ucciso il processo per pressione di memoria globale.
     Guardare PSS in meminfo.txt e il numero di chunk/auto in quel momento.

Nessun segnale ma il gioco sparisce
  -> controllare se e' un crash del renderer (Vulkan/OpenGL) o un rebind
     della licenza Unity. Cercare "OpenGLRenderer"/"Vulkan" nel log.

--------------------------------------------------------------------------------
6. GATE DI VERIFICA DOPO LA PROSSIMA BUILD
--------------------------------------------------------------------------------

Con i commit del Giorno 3 dentro l'APK, verificare in log:

    [FootIK] VERIFICA ... isHuman=True        # gate Giorno 1
    [AR] ... hist=... ground=... uova=I0/O0   # gate Giorno 2
    [VehicleInterior] abitacolo costruito lazy su <nome>
                                              # UNA volta per auto, solo dopo
                                              # essere entrati (gate Giorno 3b)

Assenze che devono allarmare:
  - nessun [VehicleInterior] dopo l'ingresso -> StartDriving non lo chiama
  - [VehicleInterior] decine di volte all'avvio -> non piu' lazy
  - due personaggi visibili -> bug preesistente HideLegacyModels, documentato
    in piano_lavoro.txt (REVIEW 2), da affrontare con i log del device.

Riflesso uova (Mandato): il beacon distingue uova indoor (I) da outdoor (O).
Se uova=I0/O0 per tutta la sessione AR indoor, la caccia al container non
sta contando: controllare SessionTelemetry.EggCollected e ContainerEgg prima
di passare ai pezzi di Fase 3/4.

NON toccare (regole del Mandato, vedi AGENTS.md):
  - hit piu' alto della colonna per "il terreno"
  - proxy altimetrico dalle altezze degli edifici
  - passo mesh DEM > risoluzione dato
  - WorldHeights.Flat e toggle follower
  - ContainerEgg, bridge uova, indoor AR, snapshot bridge
