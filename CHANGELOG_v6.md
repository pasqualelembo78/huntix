# 📋 CHANGELOG v6 — Street Fighter Battle System

## SISTEMA COMPLETO DI COMBATTIMENTO REAL-TIME

### Nuovi file (package `battle/`):
| File | Descrizione |
|------|-------------|
| `ElementType.kt` | 4 elementi (Fuoco/Aria/Terra/Acqua) con comportamento |
| `ComboSystem.kt` | Combo tracking, timing perfetto, colpi critici |
| `EnergySystem.kt` | Barra energia per abilità speciale |
| `BattleStats.kt` | Stats player/nemico con scaling livello |
| `BattleItem.kt` | 7 oggetti usabili in battaglia (pozioni, scudi, boost) |
| `EnemyAI.kt` | IA nemico: 3 difficoltà × 4 comportamenti elemento |
| `BattleEngine.kt` | Core game loop real-time (18s, tick 100ms) |
| `BattleRewardSystem.kt` | XP/MVC/gemme/cattura uovo + bonus |
| `BattleSpawnManager.kt` | Generazione nemici + eventi random |
| `BattleInventoryManager.kt` | Gestione oggetti battaglia (SharedPreferences) |
| `AdsManager.kt` | Ads completo: continue, replay, double, cooldown |

### File riscritto:
| File | Descrizione |
|------|-------------|
| `ui/BattleActivity.kt` | UI completa Street Fighter con animazioni |

### Meccaniche implementate:
- ⚔️ Attacco con timing-based combo system
- 🛡️ Difesa che riduce danno
- ⚡ Speciale caricato con barra energia (diverso per elemento)
- 🔥 Combo × fino a +150% danno
- ⚡ Critici su timing perfetto (±120ms)
- 🎯 Beat indicator per timing visuale
- 📳 Vibrazione su colpi
- 💥 Flash + shake su critici e speciali
- 🧪 7 oggetti usabili in battaglia
- 🤖 IA con 3 livelli difficoltà + comportamento per elemento
- 🎲 Eventi random: Uovo Raro, Instabile, Speciale
- 📺 Ads: raddoppio premio, continua dopo sconfitta, interstitial ogni 3 battaglie
