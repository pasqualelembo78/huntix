using System.Collections.Generic;

namespace Huntix.Outdoor
{
    /// <summary>
    /// Database di dialoghi turistici per NPC guide, organizzati per tipo di POI.
    /// Ogni tipo restituisce 2-4 frasi informative o anecdotali.
    /// </summary>
    public static class TourismDialogue
    {
        private static readonly Dictionary<string, string[]> _dialogues = new Dictionary<string, string[]>
        {
            // Cultural / historical sites
            ["church"] = new[] {
                "Questa cattedrale è un capolavoro architettronico del periodo normanno-svevo. Nota per le sue maestose navate e i vetrati colorati.",
                "La cattedrale custodisce reliquie e manoscritti risalenti al Medioevo. La cupola è stata restaurata recentemente.",
                "Questo edificio è stato costruito sulle rovine di un tempio romano. La pietra del profilo proviene del marmo delle Alpi."
            },
            ["cattedrale"] = new[] {
                "Benvenuti davanti alla Cattedrale di San [NOME]. Costruita nel XII secolo, è un esempio straordinario dell'architettura romanica locale.",
                "Le famoso mosaico nel soffitto della navata fu eseguito da maestri vergini. Osserva i dettagli: sono racconti di storie bibliche.",
                "La pietra utilizzata per i pilastri viene da cave locali. Senti l'eco quando cammini: la cupola agisce come amplificatore acustico."
            },
            ["monument"] = new[] {
                "Questo monolito commemora un evento storico fondamentale della nostra città. Le inscritte ai suoi lati raccontano la storia.",
                "Il monumento è stato inaugurato nel 19XX in onore di una figura importante. Ogni anno qui si svolgono cerimonie commemorative.",
                "Osserva i dettagli alle spalle: le armature e le spine rappresentano episodi chiave della battaglia commemorata."
            },
            ["museum"] = new[] {
                "Il museo ospita reperti archeologici e opere d'arte della tradizione locale, raccolte dal XIX secolo a oggi.",
                "La sezione più visitata è quella dei vedutisti: dipinti che mostrano la città nei diversi periodi storici.",
                "Al piano terra c'è la sala interattiva. Con l'app potrai scansionare le opere e scoprire aneddoti nascosti."
            },
            ["museo"] = new[] {
                "Questo museo conserva reperti unici della civiltà locale. La sezione romana è particolarmente ricca.",
                "Il primo piano è dedicato alle tradizioni popolari: costumi, strumenti musicali e lavori in legno.",
                "Non perdere il laboratorio interattivo: puoi assemblare reperti virtuali e creare la tua esposizione personale."
            },
            ["gallery"] = new[] {
                "Questa galleria espositiva ospita mostre temporanee di artisti emergenti. Controlla il programma della settimana!",
                "Le pareti sono arredate con sistema di illuminazione a LED a temperatura controllata per preservare le opere.",
                "Il fondo scala presenta un'installazione sonora che accompagna la visita guidata audio."
            },
            ["memorial"] = new[] {
                "Questo memoriale onora le vittime della guerra. Ogni blocco di pietra rappresenta una vita perduta.",
                "Passeggiando senti i nomi incisi: le lancette del sole li illuminano a intervalli regolari.",
                "Al piano di sotto è esposta la cronaca fotografica del periodo con immagini mai pubblicate prima."
            },
            ["statue"] = new[] {
                "Questa statua raffigura [NOME], una figura leggendaria della nostra città. La pietra è stata scelta da un'unica cava.",
                "L'autore ha lavorato la stessa pietra per oltre un anno. Osserva i dettagli: le dita stringono un simbolo leggendario.",
                "La patina verde è dovuta a reazioni chimiche naturali. Il comune ha deciso di preservarla così invece di restaurarla."
            },

            // Parks and nature
            ["park"] = new[] {
                "Questo parco fu progettato nel 1920 come luogo di svago. Il fontanile al centro è opera dello scultore locale.",
                "Il prato è mantenuto con tecniche di ripristino ecologico. Le piante sono tutte autoctone della regione.",
                "Sabato e domenica mattina ci sono Laboratori di giardinaggio per bambini. Prenota con l'app municipalia.",
                "Osserva il ramo appoggiato al tronco: è un nido di usignolo che torna qui ogni primavera."
            },
            ["parco"] = new[] {
                "Il parco è diviso in quattro aree tematiche: giochi, fitness, rosa e spettacolo.",
                "Il sentiero solitario conduce all'antica fontana Romana rinvenuta durante i lavori del 1987.",
                "I tavoli da picnic sono disponibili per prenotazione. Non dimenticare di portare l'ascella!"
            },
            ["garden"] = new[] {
                "Questo giardino è un laboratorio vivente: ogni pianta ha un cartello con il nome scientifico e la curiosità.",
                "Il sentiero a spirale rappresenta la crescita: alla base le piante giovani, in cima le arboreto mature.",
                "Il laghetto ospita rocci amadi e rane dal canto delificato. Porta rispetto: non gettare pietre!"
            },
            ["playground"] = new[] {
                "Quest'area ludica è progettata per bambini da 3 a 12 anni. Tutti gli attrezzi sono certificati antiscivolo.",
                "I mattoncini in legno a forma di animali sono disegnati da un'arte terapia locale. Trova l'animale nascosto!",
                "Il parco giochi è manutenzionato volontariamente dai genitori. Diventa socio portale per donare tempo!"
            },
            ["nature_reserve"] = new[] {
                "La riserva naturale è un'area protetta: osserva i cartelli informativi per non disturbare la fauna.",
                "I binocoli ai punti di osservazione offrono visite a 10 specie di uccelli migratori.",
                "Al primo maggio apri le finestre: l'arte è un parco a tema temporaneo. Controlla l'app per l'orario."
            },
            ["fountain"] = new[] {
                "Questa fontana fu costruita nel 1890 come regalo della città gemellata. Le quattro spigole rappresentano le stagioni.",
                "L'acqua è riciclata e raffinata: è potabile! A pochi metri c'è un distributore verde con filtro a richiesta.",
                "La fontana si illumina a intervalli: ogni sera alle 21:30 una coreografia cinetica accompagna la musica."
            },

            // Food & drink
            ["restaurant"] = new[] {
                "Questo ristorante è famoso per la pasta fatta a mano secondo la ricetta della nonna, con semola del Molise.",
                "Il menù cambia stagionalmente: primavera porta fiori commestibili, autunno funghi porcini raccolti locali.",
                "Il vino è prodotto da vigneti a 20 km. Il sommelier può consigliarti l'accostamento perfetto."
            },
            ["ristorante"] = new[] {
                "Benvenuti! La nostra specialità è il piatto combo: antipasti misti con formaggi locali e cured meat.",
                "Il cuoco è certificato dalla scuola alberghistica locale. Ogni giorno riformula un piatto segreto.",
                "Prenotando con l'app puoi scegliere il tavolo con vista: a destra c'è la parete con vetrate dipinte."
            },
            ["bar"] = new[] {
                "Questo bar ha passato tre generazioni. Il caffè è tostato in casa ogni mattina alle 6.",
                "Il pasticcerino del giorno è una specialità segreta: tenta a indovinarla dal profumo!",
                "Alle 15:00 servono biscotti salati: prova la focaccia con rosmarino locale ottenuta da un'azienda biologica."
            },
            ["cafe"] = new[] {
                "Il bar è aperto fino alle 23:00. La colazione è servita con prodotti biologici di filiera corta.",
                "Ogni tavolo ha una piccola pianta: se la ringsalvi la porti a casa!",
                "Il Wi-Fi è gratuito. La password è scritta su un bigliettino vicino al bancone. Buon lavoro!"
            },
            ["pub"] = new[] {
                "Il pub rivende birre artigianali da birrifici locali. La cardassia (birra con lambrusco) è una esplosione!",
                "Alle 19:30 ci sono quiz nascondiglio. Forma team e partecipa: vinci sconti!"
            },

            // Retail & services
            ["supermarket"] = new[] {
                "Il supermercato ha 5 corsie: prodotti freschi, secchi, Surgelati, bevande, e una corsia dedicata al locale produttore.",
                "La sezione prodotti biologici è certificata da un'associazione di consumatori. Controlla le etichette!",
                "Alle 7:00 e alle 20:00 si fa il restocking. Se sei qui a quell'ora senti musica lounge!"
            },
            ["convenience"] = new[] {
                "E' un minimarket aperto 24/7. La cassa è sempre monitorata da videosorveglianza elettronica.",
                "Il ghiaccio al polo dolce è a 99 centesimi. Il gelato vegano è in offerta."
            },
            ["shop"] = new[] {
                "Il negozio vende articoli in tessuto riciclato. Ogni prodotto ha una cartina che ne racconta la storia.",
                "Se compri oltre 50€ puoi partecipare al concorso settimanale: indovina il sostegno del giorno!"
            },
            ["tabacchi"] = new[] {
                "E' un punto di raccolta per la lotteria statale e per SMS a carattere. Pagamento con app!",
                "Vende anche articoli per fumo: sigarette, sigari, e prodotti per la passione.",
                "Il tabacchino ha un cane: un border collie che accoglie i clienti fedeli."
            },

            // Education & health
            ["school"] = new[] {
                "La scuola ha 3 piani e una palestra coperta. La sfera del basket è stata donata da uno studente celebre.",
                "Il giardino è gestito dagli alunni: hanno piantato fiori che sbociano in ordine stagionale.",
                "Nelle aule si trovano dipinti commissionati dagli studenti ogni anno. Vieni a vedere la mostra!"
            },
            ["scuola"] = new[] {
                "Benvenuti! Questa scuola offre corsi serali per adulti. Iscriviti con l'app comunale.",
                "Il cortile è un laboratorio di astronomia: ogni primo sabato d'anno si fa la notte in stellato.",
                "L'aula magna ospita conferenze aperte al pubblico: controlla il calendario!"
            },
            ["university"] = new[] {
                "L'università ha un campus verde con 50.000 piante. Studia agronomia, medicina e design digitale.",
                "Il laboratorio è aperto a visita guidata il primo venerdì del mese. Prenota con l'ufficio relazioni."
            },
            ["college"] = new[] {
                "Il college offre corsi intensivi nel settore tech. Sono disponibili borse di studio per giovani con un buon punteggio.",
                "Gli studenti dormono in residenze: sei invitato a fare una passeggiata serale e ascoltare la musica dal cortile."
            },
            ["hospital"] = new[] {
                "L'ospedale è aperto 24h. L'ingresso principale serve per urgenze; l'ingresso laterale per visite pianificate.",
                "La sala d'attesa ha un angolino giochi per bambini: il personaggio principale è un dinosauro che fa... nonna!"
            },
            ["pharmacy"] = new[] {
                "La farmacia è aperta fino alle 20:00. Il farmacista parla inglese e spesso racconta storie del centro.",
                "Se non trovi il prodotto che cerchi, chiedi al cassiere! Spesso c'è un'alternativa migliore."
            },

            // Sports & fitness
            ["gym"] = new[] {
                "La palestra è attrezzata con macchine cardio che hanno schermi: svolti video tutorial durante l'allenamento.",
                "Gli allenatori certificati offrono sessioni guidate: prenota con l'app con almeno 24h di anticipo.",
                "La sauna è a 80°C. Dopo l'uso bevi acqua: ci sono dispensatori in tutta la struttura!"
            },
            ["palestra"] = new[] {
                "Questa palestra ha una vista mozzafiato! Le macchine cardio sono poste davanti alle finestre panoramiche.",
                "Il gruppo cross-training si trova al piano 2: porta acqua e asciugamani.",
                "Dopo l'allenamento, l'angolo smoothie è perfetto: frutta fresca e proteine vegetali."
            },
            ["fitness"] = new[] {
                "Il centro fitness ha strumenti per misurare la frequenza cardiaca in tempo reale. Collega l'app!",
                "I trainer sono disponibili per consulenze di 15 minuti: prenota con un SMS al 555-FIT-APP."
            },
            ["sports_centre"] = new[] {
                "Il centro sportivo ha 3 campi: calcio, basket e tennis. Prenota con l'app comunale.",
                "Ogni fine settimana ci sono tornei aperti a tutti: iscriviti per partecipare!"
            },

            // Default fallback
            ["default"] = new[] {
                "Benvenuti! Questo luogo è una delle attrazioni principali della città.",
                "Scorciando la mappa puoi trovare altri POI vicini. Premi su uno per saperne di più.",
                "Se hai domande specifiche, chiedi pure! Sono qui per aiutarti a scoprire questa città meravigliosa."
            }
        };

        /// <summary>
        /// Returns tourism dialogue lines based on POI type.
        /// Searches by poiType first, then buildingType, then falls back to default.
        /// </summary>
        public static string[] GetDialogues(string poiType, string buildingType, string category, string poiName)
        {
            // Try poiType first
            if (!string.IsNullOrEmpty(poiType))
            {
                poiType = poiType.ToLowerInvariant().Trim();
                if (_dialogues.TryGetValue(poiType, out var lines))
                {
                    lines = SubstituteName(lines, poiName);
                    return lines;
                }
            }

            // Try buildingType
            if (!string.IsNullOrEmpty(buildingType))
            {
                buildingType = buildingType.ToLowerInvariant().Trim();
                if (_dialogues.TryGetValue(buildingType, out var lines))
                {
                    lines = SubstituteName(lines, poiName);
                    return lines;
                }
            }

            // Try category-based lookup (strip common keywords)
            if (!string.IsNullOrEmpty(category))
            {
                category = category.ToLowerInvariant().Trim();
                if (_dialogues.TryGetValue(category, out var lines))
                {
                    lines = SubstituteName(lines, poiName);
                    return lines;
                }
            }

            // Fallback to default
            if (_dialogues.TryGetValue("default", out var defaultLines))
            {
                defaultLines = SubstituteName(defaultLines, poiName);
                return defaultLines;
            }

            return new string[] { $"Benvenuti a {poiName}! Esplora la mappa per scoprire di più." };
        }

        private static string[] SubstituteName(string[] lines, string poiName)
        {
            if (string.IsNullOrEmpty(poiName)) return lines;
            var result = new string[lines.Length];
            for (int i = 0; i < lines.Length; i++)
            {
                result[i] = lines[i].Replace("[NOME]", poiName);
            }
            return result;
        }
    }
}
