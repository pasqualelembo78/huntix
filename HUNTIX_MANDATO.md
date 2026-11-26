# ðŸ¥š HUNTIX â€” MANDATO FONDANTE DEL GIOCO

> **LETTURA OBBLIGATORIA.** Questo file Ã¨ la Costituzione di HUNTIX.
> Va letto **per intero, all'inizio di OGNI richiesta**, in ogni sessione e in ogni
> nuova chat, prima di qualsiasi altro lavoro. Nessuna eccezione.
> Se una richiesta sembra non c'entrare con le uova, Ã¨ proprio il segnale che qui
> va applicata la **Regola del Riflesso** (vedi sotto): c'entra eccome.

---

## 1 Â· IdentitÃ  del gioco

**HUNTIX Ã¨ nato come CACCIA ALLE UOVA AR.** Non Ã¨ "un gioco che ha anche delle
uova": Ã¨ una caccia alle uova che vive in RealtÃ  Aumentata (GPS, indoor, cittÃ  OSM
3D Unity), nella cittÃ  fittizia generata da ricette derivate da mappe reali, e in ogni angolo dell'app. L'uovo Ã¨ il fulcro emozionale, economico e di
progressione di tutto l'universo.

Ogni feature, minigioco, missione, decorazione, sistema, menu, DA SOLIDARIETÃ€ non
Ã¨ un componente a sÃ©: Ã¨ un **luogo dove nascondere, trovare, allevare, schiudere o
sfoggiare uova**.

## 2 Â· Regola fondante NÂ°1 â€” UNIVERSO UNICO

Tutto ciÃ² che esiste nel progetto fa parte dello **stesso universo Huntix**:

- Unity (cittÃ  3D OSM, mini-cittÃ  AR, esplorazione outdoor, minigiochi)
- App Android (profilo, inventario, schiusa, classifiche, eventi, missioni, casa)
- Dati reali (GPS, POI, tile OSM, coordinate)
- Ricette di cittÃ  fittizia (parametri estratti una volta da OSM reale; nessuna cartografia a runtime)
- Profilo giocatore (XP, MVC, gemme, energia, skin, pet, famiglia, casa, bestiario)

**NIENTE Ã¨ "a parte".** Una casa arredata in Unity, un uovo trovato in un cassetto
della cucina, un parcheggio sotterraneo scoperto, un minigioco dell'app: tutto
deve essere **conosciuto dal profilo e riflettersi in entrambe le piattaforme**.
Se Android non sa che quell'uovo esiste a quelle coordinate, il lavoro NON Ã¨ finito.

## 3 Â· Regola fondante NÂ°2 â€” BRIDGE UBIDIREZIONALE OBBLIGATORIO

> **Ogni componente Unity deve avere il suo ponte verso Android, e viceversa.**

- Direzione **Unity â†’ Android**: qualsiasi cosa nuova/nascosta/raccolta/creata
  nella scena Unity deve essere esportata ad Android (evento `SendMessageToAndroid`
  + persistenza in `SharedPreferences`/profilo) entro la stessa implementazione.
- Direzione **Android â†’ Unity**: lo stato salvato lato Android deve essere
  ripristinabile/importabile da Unity (es. `CityStateSync`, `applyStoredToProfile`),
  cosÃ¬ il mondo reale del giocatore (casa, uova, progressi) Ã¨ coerente ovunque.

Criterio di verifica del lavoro: **"se Android non Ã¨ a conoscenza di questa cosa,
ho sbagliato"**. Vale per case, mobili, TV, parcheggi, uova, missioni, progressi.

## 4 Â· Regola fondante NÂ°3 â€” IL RIFLESSO DELLA CACCIA ALLE UOVA

Quando l'utente chiede una qualsiasi modifica o aggiunta:

1. **analizza** la richiesta;
2. **individua** il posto migliore dove far vivere le uova in quel contesto
   (es. arredamento cucina â†’ uova nei cassetti/nel forno; parcheggi sotterranei â†’
   uova nei livelli -1; giardino â†’ uova sotto i cespugli);
3. **PROPONI esplicitamente** l'integrazione uova all'utente, in italiano, con
   frasi tipo: *"Il gioco Ã¨ una caccia alle uovaâ€¦ che dici se [inserisco/metto
   anche] uova [nascoste] qui?"*;
4. **se l'utente accetta** â†’ implementa l'integrazione AL COMPLETO, con bridge
   Unityâ†”Android;
5. **se l'utente rifiuta** o non risponde â†’ procedi comunque con la richiesta
   originale, ma SEMPRE in ottica uova (l'uovo resta il tema).
6. Ogni uovo piazzato deve registrare la sua **posizione** (coordinate, stanza,
   contenitore) nel profilo: il profilo dovrÃ  poter mostrare *"uovo trovato in
   cucina â†’ cassetto 2"* o le coordinate esatte GPS.

## 5 Â· Coordinate e memoria delle uova (per i menu futuri)

Il profilo Android deve conservare per ogni uovo trovato/piazzato almeno:
`id`, `rarity`, `tipo`, `posizione testuale` (es. "casaâ†’cucinaâ†’cassetto2"),
`coordinate` (lat/lng se nello spazio reale), `timestamp`.
Questo permette futuri menu tipo "DOVE HO TROVATO L'UOVO X" nel profilo.

## 6 Â· La RaritÃ  Ã¨ UNA SOLA

Esiste una sola scala di raritÃ : **COMMON â†’ UNCOMMON â†’ RARE â†’ EPIC â†’ LEGENDARY**.
Nessun sistema puÃ² definirne una parallela (fixare `EggController.Rarity` Unity
per allinearla a `EggRarity` Android). Unificare Ã¨ in coda al ## 7 · TO-DO DI UNIFICAZIONE (roadmap)

Priorità di lavoro verso l'universo unico caccia-all-uova.

### ⚠️ REGOLA OBBLIGATORIA DI CONSERVAZIONE

Prima di qualsiasi attività di unificazione, refactoring, cleanup o sostituzione,
è OBBLIGATORIO preservare tutto ciò che è già presente nel progetto.

Il tecnico/developer NON DEVE eliminare, disattivare, sostituire o rendere
inutilizzabile nessun sistema, script, prefab, scena, componente, asset,
funzionalità, flusso o codice già esistente senza una CONFERMA ESPLICITA E
PREVENTIVA del proprietario del progetto.

Questa regola vale anche quando:

- un sistema sembra vecchio;
- un sistema sembra duplicato;
- un sistema sembra inutilizzato;
- esiste un nuovo sistema che sembra sostituirlo;
- il tecnico ritiene che il sistema non sia più necessario;
- la rimozione sembra migliorare l'architettura;
- la rimozione sembra risolvere un conflitto;
- si tratta di codice definito "legacy";
- si tratta di uno script apparentemente non utilizzato;
- si tratta di asset, prefab o scene apparentemente inutilizzati.

### Procedura obbligatoria per sistemi duplicati o paralleli

Quando vengono individuati due o più sistemi che svolgono funzioni simili o
sovrapposte:

1. NON eliminare nessuno dei sistemi.
2. Analizzare le funzioni di ciascun sistema.
3. Verificare quali scene, prefab, script e componenti lo utilizzano.
4. Verificare le dipendenze e i collegamenti Unity ↔ Android.
5. Documentare eventuali differenze e sovrapposizioni.
6. Proporre una soluzione di integrazione/unificazione.
7. Attendere la conferma esplicita del proprietario.
8. Solo dopo tale conferma è possibile procedere con eventuali rimozioni.

**In caso di dubbio: CONSERVARE, NON ELIMINARE.**

L'obiettivo della roadmap è UNIFICARE le funzionalità, non cancellare
automaticamente ciò che esiste già.

### Roadmap

1. **Rarità unica 5 livelli** — unificare `EggController.Rarity` (Unity, oggi 4)
   con `EggRarity` (Android, 5). Un solo enum di riferimento.

2. **Eventi stagionali collegati davvero** — `LiveEventManager` deve applicare i
   suoi moltiplicatori allo spawn reale (outdoor + città) e all'XP, non solo
   mostrarli in UI.

3. **Gemme dalle uova** — collegare un flusso uova → gemme (oggi `AddGemsFromCity`
   esiste ma non ha chiamanti Unity) con premio gemme per rarità.

4. **Unificazione dei sistemi paralleli** — analizzare e successivamente
   integrare/mergiare `OutdoorManager` (vecchio) vs `EggSpawner`,
   `EggOpening`/`BestiaryUI`/`EggInventoryManager` (scaffold), variante envelope
   `SpawnEggs`, contatori `UpdateEggCount` morti.

   **ATTENZIONE:** questa voce NON autorizza la cancellazione automatica di
   nessuno dei sistemi sopra indicati.

   Qualsiasi eliminazione, sostituzione definitiva o disattivazione richiede
   prima una conferma esplicita e preventiva del proprietario.

5. **Indoor AR = caccia alle uova** — le uova della camera in `MainActivity`
   devono alimentare il profilo (stesso universo): stats, eventi, premi.

6. **Casa Unity ↔ profilo** — arredamento/edifici creati in Unity esposti ad
   Android (con coordinate e contenitori) per i menu profilo futuri.

7. **Uova POSIZIONALI reali** — ogni uovo (GPS/POI/casa/indoor) registra la sua
   posizione nel profilo per il futuro "DOVE HO TROVATO QUESTO UOVO".

8. **Bestiario condiviso** — un solo spot per i "DOVE" delle uova: profilo Android
   + Bestiario Unity, sincronizzati bidirezionalmente (base già in `CityStateSync`).

### Regola finale sulla roadmap

NESSUNA voce della roadmap deve essere interpretata come autorizzazione
implicita alla cancellazione di sistemi esistenti.

Le parole "eliminare", "ripulire", "sostituire", "rimuovere", "legacy",
"duplicato" o "obsoleto" non costituiscono autorizzazione alla cancellazione.

Prima di qualsiasi rimozione deve essere richiesta e ottenuta una
**CONFERMA ESPLICITA DEL PROPRIETARIO DEL PROGETTO**.

## 8 · NIENTE SISTEMI ISOLATI — UNIVERSO UNICO

I sistemi possono avere implementazioni tecniche differenti, ma fanno parte
dello stesso universo Huntix e devono essere progressivamente integrati.

- Minigioco in Unity o Activity Android = stesso universo. Ricompense condivise.
- Città Unity e città AR Android = stessa città. Uova sincronizzate.
- Outdoor GPS e città Unity = stesso mondo. Stesse rarità, stessi eventi.

### Città fittizia (modalità Gioco)

La città fittizia è un quartiere dello stesso universo Huntix, non un gioco a parte e non una sostituzione della città AR/OSM. Si genera da una **ricetta** di parametri estratti offline da mappe reali (strade, isolati, parchi, POI); a runtime non usa cartografia reale, DEM né tile. Deve essere sempre affiancata e attivabile con un flag, mai impostata in modo da disattivare i sistemi esistenti senza conferma esplicita. Come ogni componente Unity ha bridge bidirezionale obbligatorio (stato → Android e ripristino → Unity), riflesso uova con posizione registrata e misure `PerfProbe`/`SessionTelemetry` prima di qualunque cambio di predefinito.

La presenza di sistemi paralleli NON autorizza la loro eliminazione.

Prima di sostituire o rimuovere un sistema deve essere verificato il suo utilizzo,
le sue dipendenze e il suo ruolo nell'universo Huntix, e deve essere ottenuta
l'autorizzazione esplicita del proprietario. 

## 9 Â· Stile

- Tutte le comunicazioni con l'utente in **italiano** (UI e chat).
- UAV nei testi: tema uovo sempre presente (nomi, descrizioni, missioni).
- Codice: seguire le convenzioni esistenti, mai commenti a scatola chiusa.

## 10 · COMMIT OBBLIGATORIO DELLE MODIFICHE (FOTOGRAFIA SANNA)

Il repository git è la **fotografia sana** del progetto: deve essere sempre
aggiornata, utile e ripristinabile, anche per fare `revert` puntuali senza
perdere lavori non correlati.

**Regole obbligatorie:**

- Ogni modifica o aggiunta va **committata man mano che viene completata e
  verificata**, mai lasciata in sospeso nella working tree.
- Un commit deve contenere un **solo concetto logico** (una feature, un fix,
  un aggiornamento di tile/POI/mappa): niente commit "cestino" con lavori
  non imparentati messi insieme.
- Il messaggio del commit in **italiano**, descrittivo: cosa fa l'aggiunta e
  perché (es. "feat: …", "fix: …", "build: …", "chore: …").
- Prima di committare: **verificare** che il codice compili e passi i check
  previsti (cscheck per Unity, build gradle per Android, ecc.).
- Alla fine di ogni sessione di lavoro la working tree deve risultare **pulita
  o giustificata**: i file modificati senza commit sono un debito da saldare
  nella sessione successiva.
- Non committare mai segreti, chiavi, token o file artefatto/derivati di
  grandi dimensioni che non fanno parte del progetto (le risorse Unity
  giustificate vanno bene).
- Il commit NON sostituisce la **Regola del Riflesso**: ogni feature committata
  deve già contenere la sua integrazione uova (o la richiesta di conferma).

Concetto cardine: **mai impilare lavoro senza salvarlo**. Se un revert è
necessario, deve poter annullare un singolo commit senza trascinare via
mezz'ora di lavoro non ancora salvato.

## 11 · AUTONOMIA DECISIONALE (24/11/2026)

Nel caso di dubbi sul **procedere**, il tecnico deve andare avanti direttamente,
senza fare domande all'utente: sceglie la soluzione migliore e più professionale
per il gioco, indipendentemente dal tempo necessario all'integrazione o alla
verifica. Non si ferma davanti a un bivio tecnico, a una priorità da ordinare o
a una funzionalità da scegliere: decide, documenta la scelta e la esegue.

Questa autonomia vale per:

- la scelta del prossimo intervento e del suo percorso implementativo;
- le decisioni di progettazione additiva (nuovo manager, nuovo evento bridge,
  nuova UI) e le verifiche da eseguire prima del commit;
- la risoluzione di ambiguità tecniche, compatibilità e priorità di lavoro.

**Non autorizza** invece a eliminare, disattivare, sostituire o cambiare i
valori predefiniti di sistemi esistenti: la REGOLA OBBLIGATORIA DI
CONSERVAZIONE del §7 e il Riflesso della caccia alle uova restano pieni e
prevalenti. In caso di dubbio su un'azione distruttiva si conserva; in caso di
dubbio su come procedere con un'aggiunta si procede.

## 12 · ASSET E LICENZE — NON SOLO CC0 (25/11/2026)

Huntix **non limita la ricerca agli asset CC0**. Possono essere cercati,
acquisiti, importati e distribuiti asset gratuiti con **qualsiasi licenza che
autorizzi esplicitamente l'uso nel gioco e la distribuzione del prodotto
finale**: CC0, CC BY (con attribuzione), MIT, licenze commerciali gratuite,
licenze Unity Asset Store utilizzabili nel prodotto e licenze equivalenti.
Una licenza non viene scartata solo perché non è CC0.

Regole obbligatorie:

1. Verificare sempre fonte autorevole, testo della licenza, autore, versione e
   data di acquisizione **prima** dell'importazione; conservare URL e prova
   della verifica nel report.
2. Se la licenza richiede attribuzione, l'attribuzione va scritta integralmente
   in `unity-project/Assets/ThirdPartyLicenses.md` e nel documentativo
   `THIRD_PARTY_NOTICES.md` (nome, autore, URL, licenza, data, modifiche,
   limitazioni, uso in Huntix).
3. Rispettare ogni restrizione (uso non commerciale, divieto di redistribuzione
   dei sorgenti, obbligo di conservare la nota, ecc.); in caso di dubbio
   **non importare** l'asset e documentare il motivo.
4. Non usare asset piratati, rubati, estratti da giochi commerciali, con licenza
   che vieta l'uso previsto o che richiede pagamenti/abbonamenti non autorizzati.
5. Ogni asset importato conserva il riflesso della caccia alle uova e il ponte
   bidirezionale Obbligatorio del §3: se è un luogo o un oggetto visitabile,
   deve poter ospitare un uovo con posizione registrata e visibile ad Android.

## 13 · MiCittà è una parte del tutto (26/11/2026)

MiCittà **non è un universo a sé stante** e non va mai trattata come un gioco
separato infilato dentro l'app: è un quartiere dello stesso universo Huntix,
connesso alla Home, al profilo, all'inventario, alle abilità, al diario e al
mondo AR/OSM. Tutto ciò che accade dentro MiCittà deve essere **visibile,
conosciuto e raggiungibile dall'intero gioco**, non solo mentre la scena Unity
è aperta.

Regole obbligatorie:

1. **Area Inventario e Area Abilità sono globali.** Devono essere raggiungibili
   dalla Home (in modo evidente), dal menu globale di ogni schermata
   (`Altro`), dalla partita AR e dal menu di MiCittà. Le voci storiche restano
   intatte: le nuove aree si aggiungono, non sostituiscono.
2. **Stato condiviso, non duplicato.** Abilità, inventario, scoperte, acquisti,
   vendite, lavori, uova e ogni altra azione di MiCittà confluiscono nello
   stesso profilo Huntix usato da Android (preferenze + `PlayerProfile` +
   `EggWhereLog`), con ponte bidirezionale obbligatorio: Unity → Android per
   l'evento e Android → Unity per il ripristino passivo (`setMode`/snapshot).
3. **Memoria del "dove".** Ogni scoperta, acquisto, vendita e azione rilevante
   di MiCittà deve poter essere raccontata dal profilo con luogo testuale,
   coordinate quando disponibili e timestamp. Il diario resta UNO solo.
4. **Riflesso della caccia alle uova.** Inventario e abilità sono luoghi dove
   nascondere e trovare uova: l'apertura delle aree globali e le azioni di
   MiCittà lasciano un uovo con posizione registrata, senza introdurre una
   seconda economia e senza duplicare uova già premiate.
5. Nessuna di queste regole autorizza a rimuovere o disattivare sistemi
   esistenti: la conservazione del §7 e l'autonomia del §11 restano pieni.

Criterio di verifica: **se un evento di MiCittà non è visibile dalla Home o dal
profilo Android, il lavoro non è finito.**
