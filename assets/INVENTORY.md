# Inventario asset CC0 grezzi — `assets/`

Libreria di sorgenti grafiche **grezze** (FBX/PNG, non importate in Unity)
richiesta dall'utente. Unity continua a usare i propri reference in
`unity-project/Assets`; i file qui sotto servono da archivio verificabile e da
sorgente per nuovi import. Rigenerazione idempotente: `./assets/restore.sh`
(legge i pacchetti già tracciati in `kit/`).

## Pacchetti estratti

| Cartella | Pacchetto sorgente | Licenza | Link | File | Utilizzo in Huntix |
|---|---|---|---|---:|---|
| `characters/` | `kit/characters.zip` | CC0 1.0 | https://kenney.nl/assets/animated-characters-1 | 12 | Modello `characterMedium.fbx`, 4 skin e 3 animazioni (idle/jump/run): already integrate in `Assets/Resources/Characters` e `Assets/Art/kenney_animated-characters-1` |
| `road/` | `kit/road.zip` | CC0 1.0 | https://kenney.nl/assets/city-kit-roads | 372 | Sorgente dei lampioni/cori/dissuasori importati in `Assets/Resources/Cinematic/RawCc0` e usati dalla modalità cinematografica |
| `nature/` | `kit/kenney_nature-kit.zip` | CC0 1.0 | https://kenney.nl/assets/nature-kit | 3618 | Alberi/fiori già presenti in `Assets/Resources/Nature`; sorgente per la vegetazione opt-in |
| `skyboxes/` | `kit/kenney_skyboxes.zip` | CC0 1.0 | https://kenney.nl/assets/skyboxes | 12 | Skybox già integrate da `DayNightManager` |
| `car-kit/` | `kit/kenney_car-kit.zip` | CC0 1.0 | https://kenney.nl/assets/car-kit | 259 | Modelli già presenti in `Assets/Resources/Vehicles` |
| `city-commercial/` | `kit/kenney_city-kit-commercial_2.1.zip` | CC0 1.0 | https://kenney.nl/assets/city-kit-commercial | 219 | Edifici commerciali già presenti in `Assets/Resources/Buildings/Commercial` |
| `city-suburban/` | `kit/kenney_city-kit-suburban_20.zip` | CC0 1.0 | https://kenney.nl/assets/city-kit-suburban | 216 | Edifici residenziali già presenti in `Assets/Resources/Buildings/Suburban` |
| `city-industrial/` | `kit/kenney_city-kit-industrial_1.0.zip` | CC0 1.0 | https://kenney.nl/assets/city-kit-industrial | 141 | Edifici industriali già presenti in `Assets/Resources/Buildings/Industrial` |
| `furniture/` | `kit/furniture.zip` | CC0 1.0 | https://kenney.nl/assets/furniture-kit | 1546 | Arredi interni già presenti in `Assets/Resources/Furniture` |
| `cube-pets/` | `kit/kenney_cube-pets_1.0.zip` | CC0 1.0 | https://kenney.nl/assets/cube-pets | 129 | Compagni/pet già presenti in `Assets/Resources/Pets` |
| `quaternius-downtown/` | `kit/quaternius.zip` | CC0 1.0 | https://quaternius.com/packs/downtowncitymegakit.html | 691 | Edifici already presenti in `Assets/Resources/Quaternius` e `Assets/Resources/Buildings/Quaternius` |

`manifest.json` riporta SHA-256 del pacchetto sorgente e numero di file
effettivamente estratti. Ogni cartella conserva il proprio `License.txt`
originale; le attribuzioni aggregate sono in `THIRD_PARTY_NOTICES.md`.

## Non importato (licenza non verificabile)

- `character/character1.unitypackage` — «Stylized Player Character (Free)»:
  il README interno dichiara le specifiche tecniche ma **non riporta una
  licenza**. Per il vincolo "solo asset con licenza verificabile" non viene
  importato né usato. I personaggi in gioco restano quelli Kenney CC0.

## Texture PBR aggiuntive (già importate)

13 texture Poly Haven CC0 in `unity-project/Assets/Resources/Cinematic/Textures`
(asfalto, asfalto usurato, pavimentazione, cemento, mattone, erba), con mappe
Diffuse/Normal/AO/Roughness verificate via MD5 dalle API ufficiali. Dettagli in
`THIRD_PARTY_NOTICES.md` e nel report `docs/HUNTIX_OPEN_SOURCE_IMPROVEMENTS_AUDIT.md`.
