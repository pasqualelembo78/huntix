================================================================================
HUNTIX - BASELINE MISURATA
================================================================================
Data: 2026-11-20
Scopo: avere un riferimento numerico PRIMA di altre modifiche, per dire se un
pezzo migliora o peggiora. Se non c'e' baseline, ogni ottimizzazione e' a occhio.

I numeri qui sotto vengono dal logcat incollato il 19 Nov 2026 durante il test
dell'APK della fluidita'. Sono dati REALI di device, ma parziali: il log era
troncato a meta' di un [Building][Spawn] e non contiene il tombstone del crash.
I campi da compilare col prossimo test sono marcati "DA RIEMPIRE".

--------------------------------------------------------------------------------
1. COSA E' STATO MISURATO
--------------------------------------------------------------------------------

APK prova: 19 Nov 2026 17:20, app-release-signed.apk, 192.385.043 byte
AAR:       19 Nov 2026 17:18, unityLibrary-release.aar, 93.416.811 byte
Contenuto: fluidita' + Giorno 1 + Giorno 2 + review 1/2 (vedi RUNBOOK sez. 2)
NON contiene: Giorno 3a (materiali/clip condivisi), Giorno 3b (abitacoli lazy)

Scena del campione: citta' in streaming, 9 chunk caricati (7 costruiti),
2 tile, stile edifici Kenney (useQuaternius=False), modalita' WorldHeights.Flat.

--------------------------------------------------------------------------------
2. FRAME E MEMORIA (beacon [AR])
--------------------------------------------------------------------------------

Riga grezza del beacon (T+14 s):

  [AR] [T+14s] fps=10 frame=66,7ms best=33,33 worst=300,0
       ram=356MB peak=356MB
       hist=16:0/33:0/50:2/100:13/400:5/X:0
       uova=I0/O0 player=(0,0) heading=0
       ground=ColliderShort/Strade camY=0 camD=7,0
       vehOn=1/770 LAG

Interpretazione:

  fps                10          -> sotto la soglia di fluidita' (30)
  frame              66,7 ms     -> ~15 fps istantanei al momento del beacon
  best / worst       33,33 / 300 ms
  istogramma         16:0  33:0  50:2  100:13  400:5  X:0
                     -> 20 frame in 2 s: 13 fra 100 e 400 ms, 5 oltre 400 ms
                     -> il problema NON e' un frame lento isolato: e' una
                        distribuzione tutta spostata oltre 100 ms.
  ram / peak         356 / 356 MB
                     -> ATTENZIONE: e' memoria RISERVATA da Unity
                        (Profiler.GetTotalReservedMemoryLong), NON PSS/RSS.
                        L'OOM killer Android guarda il PSS: serve meminfo.txt.
  uova               I0 / O0     -> nessun uovo contato in questa finestra
  ground             ColliderShort/Strade
                     -> snap corretto, sorgente Busy/Short; non e' un problema
                        di terreno (regole Mandato rispettate).
  vehOn              1 / 770     -> 1 auto attiva su 770 tracciate (culling)
  LAG                presente    ->Telemetry ha etichettato il frame come lag

--------------------------------------------------------------------------------
3. STREAMING E CONTENUTO (perche' e' lento)
--------------------------------------------------------------------------------

  build chunk       5.454 ms e 3.535 ms (due chunk)
  pending           40 / 41 chunk in coda
  chunk caricati    9 (7 costruiti), tiles=2
  stradeMesh        9/9, vSum=58.652 vertici totali
  edifici/chunk     5.811 e 6.082
  porta/interno     True / si su OGNI edificio
  stile             Kenney (useQuaternius=False)
  NPC               30/30 per chunk, tutti animator=False
                    -> BuildProcedural (scheletro Kenney)
  parcheggi         71 per chunk (~770 totali)
  veicoli in moto   1 (vedi vehOn)

Ordine di grandezza del contenuto in volo (9 chunk):
  ~52.000 edifici x (BoxCollider + Porta trigger + InteriorGenerator
                     + BuildingEntrance [+ Shop])
  ~270 NPC x (3 AnimationClip + materiali per renderer)
  ~770 auto x (VehicleInterior: 11 GameObject + 3 materiali + 3 Shader.Find)

E' da qui che nascono i fix del Giorno 3:
  - 3a: materiali skin NPC, clip procedurali e materiali abitacolo condivisi
  - 3b: abitacolo costruito solo all'ingresso a bordo

NOTA: il collider del terreno e' spento oltre TerrainColliderLod e la mesh e'
a 45 m (1058 tri/chunk), quindi il costo PhysX del terreno NON e' la causa.
Non alzare il passo mesh sopra la risoluzione DEM (250 m).

--------------------------------------------------------------------------------
4. CAMMINATA (controllo che il Giorno 1 non sia regresso)
--------------------------------------------------------------------------------

  [CharacterWalker][Player] ik=True isHuman=True ikFired=True
                            y=1,08 grounded=True feetFromPivot=1,00
  [GroundDiag] feetY=0.08 surf='Strade' src=ColliderShort near=True
               dem=0.0 visGap=0.05

Lettura: avatar Humanoid, OnAnimatorIK consegnato, piedi a 8 cm dal suolo,
superficie Strade presa dal collider corto. La camminata NON e' il problema
del crash; non toccare lo snap per "ottimizzare".

--------------------------------------------------------------------------------
5. DA RIEMPIRE CON IL PROSSIMO APK (Giorno 3 dentro)
--------------------------------------------------------------------------------

Eseguire: ./scripts/raccogli_crash.sh (vedi RUNBOOK sez. 4), poi compilare:

  device / modello                   DA RIEMPIRE
  Android version                    DA RIEMPIRE
  versionName installata             DA RIEMPIRE (dal file versione.txt)
  PSS processo (meminfo.txt)         DA RIEMPIRE  <-- decisivo per OOM
  heap Unity ram/peak dal beacon     DA RIEMPIRE
  fps medio a regime (dopo streaming)DA RIEMPIRE
  pending massimo durante streaming  DA RIEMPIRE
  build chunk piu' lenta (ms)        DA RIEMPIRE
  crash presente? segnale esatto     DA RIEMPIRE (SIGSEGV/SIGABRT/ANR/nessuno)
  [VehicleInterior] lazy presente?   DA RIEMPIRE (una volta per auto entrata)
  uova=I/O alla fine della sessione  DA RIEMPIRE
  due personaggi visibili?           DA RIEMPIRE (bug noto HideLegacyModels)

Confronto atteso dopo 3a+3b (IPOTESI, da verificare, non un risultato):
  - calo del PSS durante lo streaming se il crash era OOM
  - numero di materiali/clip creation in calo (meno GC hitch)
  - fps a regime invariato: i fix sono di memoria, non di draw call visibili
  - build chunk NON dovrebbe migliorare molto: resta il costo edifici/Porta

Se la build chunk resta >4 s e compare ANR, il prossimo pezzo e' il budget
di build, non la memoria. Se compare SIGSEGV con backtrace in ARCore/Il2Cpp,
i fix di memoria non centrano: serve il tombstone.

--------------------------------------------------------------------------------
6. RIFLESSO UOVA (obbligatorio per il Mandato)
--------------------------------------------------------------------------------

Ogni pezzo di stabilita' ha un riflesso nella caccia alle uova:
  - meno hitch durante lo streaming = meno scatti mentre ci si avvicina alla
    porta del cassetto (ContainerEgg) o a un uovo indoor;
  - il beacon "uova=I<n>/O<n>" dice se la caccia indoor (AR) e' viva.
    Nella baseline e' I0/O0: non e' una prova che la caccia non funzioni
    (la sessione era di streaming outdoor), ma va controllata in Fase 3.

Il bridge bidirezionale, l'inventario, EggWhereLog, i mini-giochi e lo snapshot
non sono stati toccati dai commit 5f0b14b / 94e664f.
