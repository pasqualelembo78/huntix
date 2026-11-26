# FancyScrollView (MIT) — dipendenza UI additiva

Sorgente: https://github.com/setchi/FancyScrollView — Copyright (c) 2020 setchi.
Licenza: MIT, testo integrale in `LICENSE` (vale anche per `EasingCore`, MIT).

HUNTIX importa il runtime come libreria indipendente (`Runtime/FancyScrollView.asmdef`)
senza sostituire nessuno ScrollRect, pannello o flusso esistente. L'uso attuale e'
il solo `EasingCore` (sempre MIT, incluso nel repo upstream) per lo scorrimento
morbido della lista acquisti in `City/Scripts/UI/UiScrollTween.cs`: lo ScrollRect
storico resta il proprietario del contenuto, la tween aggiunge solo l'interpolazione.

Nessuna preferenza, snapshot, uovo o route e' stata modificata da questa libreria.
