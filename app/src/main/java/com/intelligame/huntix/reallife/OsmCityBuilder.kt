package com.intelligame.huntix.reallife

import android.graphics.Color
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.SphereNode

/**
 * OsmCityBuilder — genera la città 3D usando dati OSM con visual Brookhaven.
 *
 * Prende i dati OSM parsati e li trasforma in nodi SceneView.
 * Ogni elemento della città viene posizionato seguendo la geometria reale.
 */
class OsmCityBuilder(
    private val sceneView: SceneView
) {
    private val engine get() = sceneView.engine
    private val ml get() = sceneView.materialLoader

    // ── Palette colori Roma ──────────────────────────────────────────────
    private val bodyColors = intArrayOf(
        0xFFE8D4B9.toInt(), // crema chiaro
        0xFFD4B896.toInt(), // ocra chiaro
        0xFFC9A66C.toInt(), // giallo Roma
        0xFFE0C9A6.toInt(), // beige sabbia
        0xFFDDB892.toInt(), // terracotta chiaro
        0xFFC4A484.toInt(), // travertino
        0xFFE8DCC8.toInt(), // crema caldo
        0xFFD4C4A8.toInt(), // pietra chiara
        0xFFF5E6C8.toInt(), // giallo pallido
        0xFFE8C5A0.toInt(), // pesca chiaro
        0xFFD0B8A0.toInt(), // mattone chiaro
        0xFFE0D8C8.toInt(), // grigio caldo
    )
    private val roofColorsArr = intArrayOf(
        0xFF8B4513.toInt(), // tegola scura
        0xFFA0522D.toInt(), // tegola media
        0xFFCD853F.toInt(), // tegola chiara
        0xFFB8860B.toInt(), // coppo antico
        0xFF8B7355.toInt(), // pietra tetto
        0xFF6B5B45.toInt(), // ardesia
    )
    private val awningColors = intArrayOf(
        0xFF8B0000.toInt(), // rosso tenda
        0xFF2E8B57.toInt(), // verde tenda
        0xFFDAA520.toInt(), // giallo tenda
        0xFF8B4513.toInt(), // marrone tenda
    )

    // ── Materiali riutilizzabili (creati una volta sola) ───────────────
    private val grassDarkMat by lazy { ml.createColorInstance(color = 0xFF3D7A33.toInt()) }
    private val grassLightMat by lazy { ml.createColorInstance(color = 0xFF5C9E4A.toInt()) }
    private val asphaltMat by lazy { ml.createColorInstance(color = 0xFF555565.toInt()) }
    private val roadMat by lazy { ml.createColorInstance(color = 0xFF484858.toInt()) }
    private val yellowLineMat by lazy { ml.createColorInstance(color = 0xFFFFC107.toInt()) }
    private val sidewalkMat by lazy { ml.createColorInstance(color = 0xFFBDBDBD.toInt()) }
    private val windowMatInst by lazy { ml.createColorInstance(color = 0xFF88CCEE.toInt()) }
    private val doorMatInst by lazy { ml.createColorInstance(color = 0xFF5D4037.toInt()) }
    private val trunkMatInst by lazy { ml.createColorInstance(color = 0xFF6B4226.toInt()) }
    private val leafMatInst by lazy { ml.createColorInstance(color = 0xFF2E7D32.toInt()) }
    private val leafLightMatInst by lazy { ml.createColorInstance(color = 0xFF43A047.toInt()) }
    private val leafDarkMatInst by lazy { ml.createColorInstance(color = 0xFF1B5E20.toInt()) }
    private val bushMatInst by lazy { ml.createColorInstance(color = 0xFF388E3C.toInt()) }
    private val poleMatInst by lazy { ml.createColorInstance(color = 0xFF424242.toInt()) }
    private val lightMatInst by lazy { ml.createColorInstance(color = 0xFFEEEEAA.toInt()) }
    private val benchWoodMatInst by lazy { ml.createColorInstance(color = 0xFF8B5E3C.toInt()) }
    private val trashCanMatInst by lazy { ml.createColorInstance(color = 0xFF2E7D32.toInt()) }
    private val carWheelMatInst by lazy { ml.createColorInstance(color = 0xFF212121.toInt()) }
    private val carWindshieldMatInst by lazy { ml.createColorInstance(color = 0xFF90CAF9.toInt()) }
    private val carHeadlightMatInst by lazy { ml.createColorInstance(color = 0xFFFFFFE0.toInt()) }
    private val travertineMatInst by lazy { ml.createColorInstance(color = 0xFFD4C5A9.toInt()) }
    private val travertineDarkMatInst by lazy { ml.createColorInstance(color = 0xFFC4B599.toInt()) }
    private val arenaMatInst by lazy { ml.createColorInstance(color = 0xFF8B7355.toInt()) }
    private val waterMatInst by lazy { ml.createColorInstance(color = 0xFF42A5F5.toInt()) }
    private val grassDetailMatInst by lazy { ml.createColorInstance(color = 0xFF66BB6A.toInt()) }
    // Window glass variants
    private val windowGlassMatLit by lazy { ml.createColorInstance(color = 0xFFFFEE88.toInt()) }   // warm lit
    private val windowGlassMatDark by lazy { ml.createColorInstance(color = 0xFF1A1A2E.toInt()) } // dark/off
    private val windowGlassMat by lazy { ml.createColorInstance(color = 0xFF88CCEE.toInt()) }     // reflective day

    // ── Stato ──────────────────────────────────────────────────────────
    val windowMaterials = mutableListOf<com.google.android.filament.MaterialInstance>()
    var lampLightMaterial: com.google.android.filament.MaterialInstance? = null
        private set
    val buildingAABBs = mutableListOf<AABB>()

    private var currentNodeCount = 0
    private val MAX_NODES = 5000

    private fun addNode(node: CubeNode) {
        if (currentNodeCount >= MAX_NODES) return
        sceneView.addChildNode(node)
        currentNodeCount++
    }

    private fun addNode(node: SphereNode) {
        if (currentNodeCount >= MAX_NODES) return
        sceneView.addChildNode(node)
        currentNodeCount++
    }

    fun getCurrentNodeCount(): Int = currentNodeCount

    // ══════════════════════════════════════════════════════════════════════
    // FASE 1 — TERRENO
    // ══════════════════════════════════════════════════════════════════════

    fun buildTerrain(mapSize: Float = 1000f) {
        addNode(CubeNode(engine, Size(mapSize, 0.3f, mapSize), materialInstance = grassDarkMat).apply {
            position = Position(0f, -0.15f, 0f)
        })
        addNode(CubeNode(engine, Size(mapSize, 0.02f, mapSize), materialInstance = grassLightMat).apply {
            position = Position(0f, 0.01f, 0f)
        })
        addNode(CubeNode(engine, Size(mapSize, 0.04f, mapSize), materialInstance = asphaltMat).apply {
            position = Position(0f, 0.02f, 0f)
        })
    }

    // ══════════════════════════════════════════════════════════════════════
    // FASE 2 — STRADE DA OSM
    // ══════════════════════════════════════════════════════════════════════

    fun buildRoads(osmData: OsmData) {
        for (way in osmData.roads) {
            if (way.nodes.size < 2) continue
            val width = roadWidthForType(way.highway)
            for ((nodeA, nodeB) in way.segments()) {
                val dx = nodeB.localX - nodeA.localX
                val dz = nodeB.localZ - nodeA.localZ
                val length = Math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
                if (length < 0.5f) continue

                val cx = (nodeA.localX + nodeB.localX) / 2f
                val cz = (nodeA.localZ + nodeB.localZ) / 2f
                val angle = Math.atan2(dz.toDouble(), dx.toDouble()).toFloat()

                addNode(CubeNode(engine, Size(length, 0.06f, width), materialInstance = roadMat).apply {
                    position = Position(cx, 0.03f, cz)
                    rotation = Position(0f, -angle, 0f)
                })

                if (way.highway in listOf("primary", "secondary", "tertiary") && length > 5f) {
                    addNode(CubeNode(engine, Size(length, 0.07f, 0.12f), materialInstance = yellowLineMat).apply {
                        position = Position(cx, 0.04f, cz)
                        rotation = Position(0f, -angle, 0f)
                    })
                }

                if (way.highway in listOf("primary", "secondary", "residential") && length > 8f) {
                    val sidewalkWidth = 0.5f
                    val offset = width / 2f + sidewalkWidth / 2f + 0.05f
                    val perpX = -Math.sin(angle.toDouble()).toFloat() * offset
                    val perpZ = Math.cos(angle.toDouble()).toFloat() * offset

                    addNode(CubeNode(engine, Size(length, 0.08f, sidewalkWidth), materialInstance = sidewalkMat).apply {
                        position = Position(cx + perpX, 0.04f, cz + perpZ)
                        rotation = Position(0f, -angle, 0f)
                    })
                    addNode(CubeNode(engine, Size(length, 0.08f, sidewalkWidth), materialInstance = sidewalkMat).apply {
                        position = Position(cx - perpX, 0.04f, cz - perpZ)
                        rotation = Position(0f, -angle, 0f)
                    })
                }
            }
        }
    }

    private fun roadWidthForType(highway: String): Float {
        return when (highway) {
            "motorway" -> 12f; "primary" -> 10f; "secondary" -> 8f
            "tertiary" -> 7f; "residential" -> 6f; "service" -> 4f
            "footway" -> 2f; "pedestrian" -> 3f; "unclassified" -> 6f
            else -> 5f
        }
    }

    /** Determina il tipo di edificio dai tag OSM */
    private fun getBuildingType(way: OsmWay): BuildingType {
        val amenity = way.amenity.lowercase()
        val shop = way.shop.lowercase()
        val building = way.tags["building"]?.lowercase() ?: ""
        val name = way.name.lowercase()

        // Landmark check
        if (name.contains("colosseo") || name.contains("basilica") || name.contains("pantheon") ||
            name.contains("castel") || name.contains("vatican") || name.contains("san pietro") ||
            name.contains("piazza") && (name.contains("navona") || name.contains("spagna") || name.contains("popolo"))) {
            return BuildingType.LANDMARK
        }

        // Amenity types
        when {
            amenity.contains("restaurant") || amenity.contains("cafe") || amenity.contains("bar") ||
            amenity.contains("pub") || amenity.contains("fast_food") || amenity.contains("food_court") -> {
                return BuildingType.RESTAURANT
            }
            amenity.contains("hospital") || amenity.contains("clinic") || amenity.contains("doctors") ||
            amenity.contains("pharmacy") -> {
                return BuildingType.HOSPITAL
            }
            amenity.contains("school") || amenity.contains("university") || amenity.contains("college") ||
            amenity.contains("kindergarten") -> {
                return BuildingType.SCHOOL
            }
            amenity.contains("police") || amenity.contains("fire_station") -> {
                return BuildingType.GOVERNMENT
            }
            amenity.contains("bank") || amenity.contains("atm") -> {
                return BuildingType.BANK
            }
            amenity.contains("cinema") || amenity.contains("theatre") || amenity.contains("arts_centre") ||
            amenity.contains("nightclub") || amenity.contains("casino") -> {
                return BuildingType.ENTERTAINMENT
            }
            amenity.contains("gym") || amenity.contains("fitness_centre") || amenity.contains("sports_centre") -> {
                return BuildingType.GYM
            }
            amenity.contains("hotel") || amenity.contains("hostel") || amenity.contains("guest_house") -> {
                return BuildingType.HOTEL
            }
            amenity.contains("place_of_worship") || amenity.contains("church") || amenity.contains("mosque") -> {
                return BuildingType.RELIGIOUS
            }
        }

        // Shop types
        when {
            shop.contains("supermarket") || shop.contains("convenience") || shop.contains("groceries") ||
            shop.contains("bakery") || shop.contains("butcher") || shop.contains("greengrocer") -> {
                return BuildingType.SUPERMARKET
            }
            shop.contains("mall") || shop.contains("department_store") -> {
                return BuildingType.MALL
            }
            shop.contains("clothes") || shop.contains("fashion") || shop.contains("shoes") ||
            shop.contains("boutique") || shop.contains("jewelry") -> {
                return BuildingType.CLOTHING
            }
            shop.contains("electronics") || shop.contains("computer") || shop.contains("mobile_phone") -> {
                return BuildingType.ELECTRONICS
            }
            shop.contains("book") || shop.contains("stationery") || shop.contains("library") -> {
                return BuildingType.BOOKSTORE
            }
            shop.contains("furniture") || shop.contains("hardware") || shop.contains("diy") -> {
                return BuildingType.HARDWARE
            }
            shop.contains("car") || shop.contains("bicycle") || shop.contains("motorcycle") -> {
                return BuildingType.VEHICLE
            }
        }

        // Building tag
        when {
            building.contains("residential") || building.contains("apartments") || building.contains("house") ||
            building.contains("detached") || building.contains("terrace") || building.contains("semidetached") -> {
                return BuildingType.RESIDENTIAL
            }
            building.contains("commercial") || building.contains("retail") || building.contains("office") -> {
                return BuildingType.COMMERCIAL
            }
            building.contains("industrial") || building.contains("warehouse") || building.contains("factory") -> {
                return BuildingType.INDUSTRIAL
            }
            building.contains("garage") || building.contains("parking") -> {
                return BuildingType.PARKING
            }
        }

        // Default: residenziale
        return BuildingType.RESIDENTIAL
    }

    /** Colori per tipo di edificio */
    private fun getTypeColors(type: BuildingType): Pair<Int, Int> {
        return when (type) {
            BuildingType.RESIDENTIAL -> 0xFFE8D4B9.toInt() to 0xFF8B4513.toInt() // crema + tegola
            BuildingType.COMMERCIAL -> 0xFFD4C4A8.toInt() to 0xFF6B5B45.toInt() // travertino + ardesia
            BuildingType.SUPERMARKET -> 0xFFFFF8DC.toInt() to 0xFFB8860B.toInt() // giallo chiaro + oro
            BuildingType.MALL -> 0xFFE8E8E8.toInt() to 0xFF808080.toInt() // grigio chiaro + grigio
            BuildingType.CLOTHING -> 0xFFF5E6CC.toInt() to 0xFF8B4513.toInt() // beige + marrone
            BuildingType.ELECTRONICS -> 0xFFE0E0E0.toInt() to 0xFF4A4A4A.toInt() // grigio tech
            BuildingType.BOOKSTORE -> 0xFFF0E6D2.toInt() to 0xFF8B7355.toInt() // carta + marrone
            BuildingType.HARDWARE -> 0xFFD0D0D0.toInt() to 0xFF696969.toInt() // grigio industriale
            BuildingType.VEHICLE -> 0xFFCCCCCC.toInt() to 0xFF555555.toInt() // acciaio
            BuildingType.RESTAURANT -> 0xFFFFE4B5.toInt() to 0xFF8B0000.toInt() // moccasin + rosso scuro
            BuildingType.HOSPITAL -> 0xFFFFFFFF.toInt() to 0xFF8B0000.toInt() // bianco + croce rossa
            BuildingType.SCHOOL -> 0xFFF5F5DC.toInt() to 0xFF228B22.toInt() // beige + verde
            BuildingType.GYM -> 0xFFE0E0E0.toInt() to 0xFF4169E1.toInt() // grigio + blu royal
            BuildingType.HOTEL -> 0xFFFFF0D5.toInt() to 0xFF8B4513.toInt() // cream + marrone
            BuildingType.BANK -> 0xFFF5F5F5.toInt() to 0xFF2F4F4F.toInt() // bianco + grigio scuro
            BuildingType.GOVERNMENT -> 0xFFE8E8E8.toInt() to 0xFF1C1C1C.toInt() // istituzionale
            BuildingType.ENTERTAINMENT -> 0xFFFFE4E1.toInt() to 0xFF8B008B.toInt() // rosa + magenta
            BuildingType.RELIGIOUS -> 0xFFFFF8DC.toInt() to 0xFFDAA520.toInt() // crema + oro
            BuildingType.PARKING -> 0xFFD0D0D0.toInt() to 0xFF696969.toInt() // grigio parcheggio
            BuildingType.INDUSTRIAL -> 0xFFB0B0B0.toInt() to 0xFF404040.toInt() // grigio industriale
            BuildingType.LANDMARK -> 0xFFD4C5A9.toInt() to 0xFFC4B599.toInt() // travertino speciale
        }
    }

    enum class BuildingType {
        RESIDENTIAL, COMMERCIAL, SUPERMARKET, MALL, CLOTHING, ELECTRONICS, BOOKSTORE, HARDWARE, VEHICLE,
        RESTAURANT, HOSPITAL, SCHOOL, GYM, HOTEL, BANK, GOVERNMENT, ENTERTAINMENT, RELIGIOUS, PARKING, INDUSTRIAL, LANDMARK
    }

    // ══════════════════════════════════════════════════════════════════════
    // FASE 3 — EDIFICI DA OSM
// ══════════════════════════════════════════════════════════════════════
// FASE 3 — EDIFICI DA OSM (dettagliati stile Roma)
// ══════════════════════════════════════════════════════════════════════

fun buildBuildings(osmData: OsmData) {
    val buildings = osmData.buildings
        .filter { it.nodes.size >= 3 }
        .sortedByDescending { it.height }
        .take(200)

    var buildingIndex = 0
    for (way in buildings) {
        if (currentNodeCount >= MAX_NODES - 50) break

        val fp = way.calculateFootprint() ?: continue
        val h = way.height.toFloat()
        val w = fp.width.coerceIn(3f, 40f)
        val d = fp.depth.coerceIn(3f, 40f)
        if (w < 3f || d < 3f || h < 3f) continue

        val isLandmark = way.name.isNotEmpty() && (way.name.lowercase().contains("colosseo") || way.name.lowercase().contains("basilica") || way.name.lowercase().contains("pantheon") || way.name.lowercase().contains("castel") || way.name.lowercase().contains("vatican") || way.name.lowercase().contains("san pietro") || way.name.lowercase().contains("piazza"))

        // Determine building type from OSM tags
        val buildingType = getBuildingType(way)
        val typeColors = getTypeColors(buildingType)
        val levels = maxOf(1, (h / 3.2f).toInt())
        val floorHeight = h / levels

        // Material instances based on building type
        val bodyColor = typeColors.first
        val roofColorVal = typeColors.second
        val bodyMatInst = ml.createColorInstance(color = bodyColor)
        val roofMatInst = ml.createColorInstance(color = roofColorVal)
        val darkBodyMatInst = ml.createColorInstance(color = (bodyColor and 0xFFFFFF.toInt()) or 0xCC000000.toInt())
        val windowGlassMatLit = ml.createColorInstance(color = 0xFFFFEEAA.toInt())
        val windowGlassMatDark = ml.createColorInstance(color = 0xFF223344.toInt())
        val windowGlassMat = ml.createColorInstance(color = 0xFF88CCEE.toInt())

        val windowFrameMat = ml.createColorInstance(color = 0xFF443322.toInt())
        val shutterMat = ml.createColorInstance(color = 0xFF553311.toInt())
        val corniceMat = ml.createColorInstance(color = 0xFFDDCCAA.toInt())
        val storefrontMat = ml.createColorInstance(color = 0xFF222233.toInt())
        val balconyMat = ml.createColorInstance(color = 0xFF775533.toInt())

        // ── CORPO EDIFICIO: per piano ──
        for (floor in 0 until levels) {
            val floorY = floor * floorHeight + floorHeight / 2f
            val floorH = floorHeight * 0.92f
            val floorW = w * 0.98f
            val floorD = d * 0.98f

            // Muro piano
            addNode(CubeNode(engine, Size(floorW, floorH, floorD), materialInstance = if (floor == 0) darkBodyMatInst else bodyMatInst).apply {
                position = Position(fp.centerX, floorY, fp.centerZ)
                rotation = Position(0f, -fp.rotation, 0f)
            })

            // Cornice tra i piani (sopra piano terra e ultimo)
            if (floor == 0 || floor == levels - 1) {
                addNode(CubeNode(engine, Size(floorW + 0.15f, 0.12f, floorD + 0.15f), materialInstance = corniceMat).apply {
                    position = Position(fp.centerX, (floor + 1) * floorHeight - 0.06f, fp.centerZ)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
            }

            // ── FINESTRE PER PIANO ──
            val windowsPerSide = ((w / 2.5f).toInt()).coerceIn(1, 4)
            val windowsPerDepth = ((d / 2.5f).toInt()).coerceIn(1, 3)
            val winW = 0.7f; val winH = floorHeight * 0.55f; val winD = 0.08f

            // Facciata frontale (Z+)
            for (i in 1..windowsPerSide) {
                val wx = fp.centerX - w / 2f + i * w / (windowsPerSide + 1).toFloat()
                val winY = floorY
                // Cornice finestra
                addNode(CubeNode(engine, Size(winW + 0.1f, winH + 0.1f, 0.1f), materialInstance = windowFrameMat).apply {
                    position = Position(wx, winY, fp.centerZ + d / 2f + 0.05f)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                // Vetro: vario (acceso/spento/riflettente)
                val glassChoice = (buildingIndex * 31 + floor * 17 + i * 7) % 5
                val glassMat = when (glassChoice) {
                    0 -> windowGlassMatLit    // accesa
                    1 -> windowGlassMatDark   // spenta
                    2 -> windowGlassMat       // riflettente giorno
                    3 -> windowGlassMatDark   // spenta
                    else -> windowGlassMat    // riflettente
                }
                addNode(CubeNode(engine, Size(winW, winH, 0.08f), materialInstance = glassMat).apply {
                    position = Position(wx, winY, fp.centerZ + d / 2f + 0.05f)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                // Persiane (a volte aperte)
                if ((buildingIndex + floor + i) % 3 == 0) {
                    addNode(CubeNode(engine, Size(winW * 0.5f, winH, 0.04f), materialInstance = shutterMat).apply {
                        position = Position(wx + winW * 0.35f, winY, fp.centerZ + d / 2f + 0.07f)
                        rotation = Position(0f, -fp.rotation, 0f)
                    })
                    addNode(CubeNode(engine, Size(winW * 0.5f, winH, 0.04f), materialInstance = shutterMat).apply {
                        position = Position(wx - winW * 0.35f, winY, fp.centerZ + d / 2f + 0.07f)
                        rotation = Position(0f, -fp.rotation, 0f)
                    })
                }
                // Balcone (piano nobile e ultimo)
                if (floor == 1 || floor == levels - 1) {
                    addNode(CubeNode(engine, Size(winW + 0.2f, 0.06f, 0.35f), materialInstance = balconyMat).apply {
                        position = Position(wx, winY - winH / 2f - 0.03f, fp.centerZ + d / 2f + 0.175f)
                        rotation = Position(0f, -fp.rotation, 0f)
                    })
                    // Ringhiera
                    addNode(CubeNode(engine, Size(winW + 0.2f, 0.4f, 0.02f), materialInstance = corniceMat).apply {
                        position = Position(wx, winY - winH / 2f + 0.15f, fp.centerZ + d / 2f + 0.36f)
                        rotation = Position(0f, -fp.rotation, 0f)
                    })
                }
            }

            // Facciata posteriore (Z-)
            for (i in 1..windowsPerSide) {
                val wx = fp.centerX - w / 2f + i * w / (windowsPerSide + 1).toFloat()
                val winY = floorY
                val glassMat = if ((buildingIndex + floor + i + 2) % 3 == 0) windowGlassMatLit
                               else if ((buildingIndex + floor + i + 2) % 3 == 1) windowGlassMatDark
                               else windowGlassMat
                addNode(CubeNode(engine, Size(winW + 0.1f, winH + 0.1f, 0.1f), materialInstance = windowFrameMat).apply {
                    position = Position(wx, winY, fp.centerZ - d / 2f - 0.05f)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                addNode(CubeNode(engine, Size(winW, winH, 0.08f), materialInstance = glassMat).apply {
                    position = Position(wx, winY, fp.centerZ - d / 2f - 0.05f)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
            }

            // Lati (X+ e X-)
            for (i in 1..windowsPerDepth) {
                val wz = fp.centerZ - d / 2f + i * d / (windowsPerDepth + 1).toFloat()
                val winY = floorY
                val glassMat = if ((buildingIndex + floor + i + 3) % 3 == 0) windowGlassMatLit
                               else if ((buildingIndex + floor + i + 3) % 3 == 1) windowGlassMatDark
                               else windowGlassMat
                // Lato destro (X+)
                addNode(CubeNode(engine, Size(0.1f, winH + 0.1f, winW + 0.1f), materialInstance = windowFrameMat).apply {
                    position = Position(fp.centerX + w / 2f + 0.05f, winY, wz)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                addNode(CubeNode(engine, Size(0.08f, winH, winW), materialInstance = glassMat).apply {
                    position = Position(fp.centerX + w / 2f + 0.05f, winY, wz)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                // Lato sinistro (X-)
                addNode(CubeNode(engine, Size(0.1f, winH + 0.1f, winW + 0.1f), materialInstance = windowFrameMat).apply {
                    position = Position(fp.centerX - w / 2f - 0.05f, winY, wz)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                addNode(CubeNode(engine, Size(0.08f, winH, winW), materialInstance = glassMat).apply {
                    position = Position(fp.centerX - w / 2f - 0.05f, winY, wz)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
            }

            // ── PIANO TERRA: VETRINE / PORTONI ──
            if (floor == 0) {
                val entranceW = (w * 0.6f).coerceIn(1.5f, 4f)
                // Portone centrale
                addNode(CubeNode(engine, Size(1.2f, 2.4f, 0.1f), materialInstance = doorMatInst).apply {
                    position = Position(fp.centerX, 1.2f, fp.centerZ + d / 2f + 0.05f)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                // Vetrine ai lati
                for (side in listOf(-1, 1)) {
                    val sx = fp.centerX + side * (entranceW / 2f + 1.5f)
                    addNode(CubeNode(engine, Size(2.5f, 2.4f, 0.08f), materialInstance = windowGlassMat).apply {
                        position = Position(sx, 1.2f, fp.centerZ + d / 2f + 0.05f)
                        rotation = Position(0f, -fp.rotation, 0f)
                    })
                    addNode(CubeNode(engine, Size(2.5f, 0.2f, 0.1f), materialInstance = windowFrameMat).apply {
                        position = Position(sx, 2.5f, fp.centerZ + d / 2f + 0.05f)
                        rotation = Position(0f, -fp.rotation, 0f)
                    })
                    // Tenda negozio
                    if ((buildingIndex + side + 1) % 4 == 0) {
                        val awningColor = awningColors[(buildingIndex + side + 1) % awningColors.size]
                        val awningMat = ml.createColorInstance(color = awningColor)
                        addNode(CubeNode(engine, Size(2.5f, 0.04f, 0.5f), materialInstance = awningMat).apply {
                            position = Position(sx, 2.6f, fp.centerZ + d / 2f + 0.25f)
                            rotation = Position(0f, -fp.rotation, 0f)
                        })
                    }
                }
            }
        }

        // ── TETTO ──
        val roofType = if (isLandmark) "dome" else when (way.roofShape) {
            "flat" -> "flat"
            "gabled", "hipped" -> "gabled"
            "domed" -> "dome"
            else -> if (h > 15f && (buildingIndex % 5 == 0)) "gabled" else "flat"
        }

        when (roofType) {
            "flat" -> {
                // Tetto piano con parapetto
                addNode(CubeNode(engine, Size(w + 0.4f, 0.25f, d + 0.4f), materialInstance = roofMatInst).apply {
                    position = Position(fp.centerX, h + 0.125f, fp.centerZ)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                // Parapetto
                addNode(CubeNode(engine, Size(w + 0.5f, 0.6f, 0.15f), materialInstance = corniceMat).apply {
                    position = Position(fp.centerX, h + 0.55f, fp.centerZ + d / 2f + 0.075f)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                addNode(CubeNode(engine, Size(w + 0.5f, 0.6f, 0.15f), materialInstance = corniceMat).apply {
                    position = Position(fp.centerX, h + 0.55f, fp.centerZ - d / 2f - 0.075f)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                addNode(CubeNode(engine, Size(0.15f, 0.6f, d + 0.5f), materialInstance = corniceMat).apply {
                    position = Position(fp.centerX + w / 2f + 0.075f, h + 0.55f, fp.centerZ)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                addNode(CubeNode(engine, Size(0.15f, 0.6f, d + 0.5f), materialInstance = corniceMat).apply {
                    position = Position(fp.centerX - w / 2f - 0.075f, h + 0.55f, fp.centerZ)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
                // Elementi tetto: comignoli, AC, torri acqua
                if (levels >= 3) {
                    addNode(CubeNode(engine, Size(0.6f, 1.5f, 0.6f), materialInstance = roofMatInst).apply {
                        position = Position(fp.centerX - w * 0.3f, h + 1.0f, fp.centerZ - d * 0.3f)
                        rotation = Position(0f, -fp.rotation, 0f)
                    })
                    addNode(CubeNode(engine, Size(1.2f, 0.8f, 1.2f), materialInstance = ml.createColorInstance(color = 0xFF888888.toInt())).apply {
                        position = Position(fp.centerX + w * 0.2f, h + 0.4f, fp.centerZ + d * 0.2f)
                        rotation = Position(0f, -fp.rotation, 0f)
                    })
                }
            }
            "gabled" -> {
                // Tetto a due falde
                val ridgeH = h + w * 0.35f
                val roofDepth = d + 0.6f
                val roofW = w + 0.6f
                // Falda 1
                addNode(CubeNode(engine, Size(roofW, w * 0.35f, roofDepth), materialInstance = roofMatInst).apply {
                    position = Position(fp.centerX, h + w * 0.35f / 2f, fp.centerZ)
                    rotation = Position(-0.6f, -fp.rotation, 0f) // inclinazione
                })
                // Falda 2 (speculare - semplificato come box inclinato opposto)
                addNode(CubeNode(engine, Size(roofW, w * 0.35f, roofDepth), materialInstance = roofMatInst).apply {
                    position = Position(fp.centerX, h + w * 0.35f / 2f, fp.centerZ)
                    rotation = Position(0.6f, -fp.rotation, 0f)
                })
                // Frontoni triangolari (semplificati)
                addNode(CubeNode(engine, Size(roofW, 0.1f, w * 0.35f), materialInstance = corniceMat).apply {
                    position = Position(fp.centerX, ridgeH, fp.centerZ + d / 2f + w * 0.175f)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
            }
            "dome" -> {
                // Cupola per landmark
                val domeR = minOf(w, d) * 0.45f
                val domeH = domeR * 0.7f
                addNode(SphereNode(engine, domeR, materialInstance = roofMatInst).apply {
                    position = Position(fp.centerX, h + domeH * 0.5f, fp.centerZ)
                })
                // Lanternino
                addNode(CubeNode(engine, Size(domeR * 0.3f, domeR * 0.4f, domeR * 0.3f), materialInstance = corniceMat).apply {
                    position = Position(fp.centerX, h + domeH + domeR * 0.2f, fp.centerZ)
                })
            }
        }

        // ── DETTAGLI AGGIUNTIVI ──
        // Pilastri angolari
        if (w > 10f && d > 10f) {
            val pilasterMat = ml.createColorInstance(color = 0xFFBBBBAA.toInt())
            for (cornerX in listOf(-1f, 1f)) {
                for (cornerZ in listOf(-1f, 1f)) {
                    addNode(CubeNode(engine, Size(0.3f, h, 0.3f), materialInstance = pilasterMat).apply {
                        position = Position(fp.centerX + cornerX * w / 2f, h / 2f, fp.centerZ + cornerZ * d / 2f)
                        rotation = Position(0f, -fp.rotation, 0f)
                    })
                }
            }
        }

        val halfW = w / 2f; val halfD = d / 2f
        if (fp.rotation == 0f) {
            buildingAABBs.add(AABB(fp.centerX - halfW, fp.centerX + halfW, fp.centerZ - halfD, fp.centerZ + halfD))
        } else {
            val maxDim = maxOf(halfW, halfD) * 1.5f
            buildingAABBs.add(AABB(fp.centerX - maxDim, fp.centerX + maxDim, fp.centerZ - maxDim, fp.centerZ + maxDim))
        }
        buildingIndex++
    }
}

    // ══════════════════════════════════════════════════════════════════════
    // FASE 4 — ALBERI DA OSM + PROCEDURALI
    // ══════════════════════════════════════════════════════════════════════

    fun buildTrees(osmData: OsmData, mapSize: Float = 1000f) {
        val half = mapSize / 2f
        for (tree in osmData.trees.take(30)) {
            if (currentNodeCount >= MAX_NODES - 5) break
            val x = tree.localX; val z = tree.localZ
            if (x < -half || x > half || z < -half || z > half) continue
            spawnTree(x, z, tree.id.toInt())
        }
        for (park in osmData.parks) {
            if (park.nodes.size < 3) continue
            val fp = park.calculateFootprint() ?: continue
            val treeCount = (fp.width * fp.depth / 100f).toInt().coerceIn(3, 8)
            for (t in 0 until treeCount) {
                if (currentNodeCount >= MAX_NODES - 5) break
                val seed = park.id.toInt() * 7 + t * 41
                val tx = fp.centerX + ((seed % 100).toFloat() / 100f - 0.5f) * fp.width * 0.8f
                val tz = fp.centerZ + (((seed / 10) % 100).toFloat() / 100f - 0.5f) * fp.depth * 0.8f
                spawnTree(tx, tz, seed)
            }
        }
    }

    private fun spawnTree(tx: Float, tz: Float, seed: Int) {
        val treeMat = when (seed % 3) { 0 -> leafMatInst; 1 -> leafLightMatInst; else -> leafDarkMatInst }
        val treeH = 1.6f + (seed % 5).toFloat() * 0.15f
        addNode(CubeNode(engine, Size(0.18f, treeH, 0.18f), materialInstance = trunkMatInst).apply {
            position = Position(tx, treeH / 2f, tz)
        })
        val canopyBase = treeH + 0.1f
        addNode(SphereNode(engine, 0.55f + (seed % 3).toFloat() * 0.08f, materialInstance = treeMat).apply {
            position = Position(tx, canopyBase + 0.3f, tz)
        })
        addNode(SphereNode(engine, 0.4f + (seed % 2).toFloat() * 0.1f, materialInstance = treeMat).apply {
            position = Position(tx + 0.2f, canopyBase + 0.15f, tz + 0.15f)
        })
        addNode(SphereNode(engine, 0.35f + (seed % 2).toFloat() * 0.05f, materialInstance = leafLightMatInst).apply {
            position = Position(tx - 0.15f, canopyBase + 0.4f, tz - 0.1f)
        })
        if (seed % 3 == 0) {
            addNode(CubeNode(engine, Size(0.3f, 0.06f, 0.06f), materialInstance = trunkMatInst).apply {
                position = Position(tx + 0.2f, treeH * 0.65f, tz)
            })
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // FASE 5 — CESPUGLI E FIORI NEI PARCHI
    // ══════════════════════════════════════════════════════════════════════

    fun buildVegetation(osmData: OsmData) {
        val flowerColors = intArrayOf(
            0xFFE91E63.toInt(), 0xFFFFEB3B.toInt(), 0xFFFF5722.toInt(),
            0xFF9C27B0.toInt(), 0xFFFF9800.toInt(), 0xFF2196F3.toInt()
        )
        for (park in osmData.parks) {
            if (park.nodes.size < 3) continue
            val fp = park.calculateFootprint() ?: continue

            val bushCount = (fp.width * fp.depth / 200f).toInt().coerceIn(2, 6)
            for (b in 0 until bushCount) {
                if (currentNodeCount >= MAX_NODES - 2) break
                val seed = park.id.toInt() * 13 + b * 29
                val bx = fp.centerX + ((seed % 100).toFloat() / 100f - 0.5f) * fp.width * 0.7f
                val bz = fp.centerZ + (((seed / 10) % 100).toFloat() / 100f - 0.5f) * fp.depth * 0.7f
                val bushSize = 0.22f + (seed % 20).toFloat() / 100f
                addNode(SphereNode(engine, bushSize, materialInstance = bushMatInst).apply {
                    position = Position(bx, bushSize, bz)
                })
                addNode(SphereNode(engine, bushSize * 0.7f, materialInstance = leafLightMatInst).apply {
                    position = Position(bx + bushSize * 0.4f, bushSize * 0.8f, bz + bushSize * 0.3f)
                })
            }

            val flowerCount = (fp.width * fp.depth / 300f).toInt().coerceIn(1, 4)
            for (f in 0 until flowerCount) {
                if (currentNodeCount >= MAX_NODES - 2) break
                val seed = park.id.toInt() * 11 + f * 37
                val fx = fp.centerX + ((seed % 100).toFloat() / 100f - 0.5f) * fp.width * 0.6f
                val fz = fp.centerZ + (((seed / 10) % 100).toFloat() / 100f - 0.5f) * fp.depth * 0.6f
                val fMat = ml.createColorInstance(color = flowerColors[seed % flowerColors.size])
                addNode(CubeNode(engine, Size(0.02f, 0.15f, 0.02f), materialInstance = grassDetailMatInst).apply {
                    position = Position(fx, 0.08f, fz)
                })
                addNode(SphereNode(engine, 0.06f + (seed % 5).toFloat() / 100f, materialInstance = fMat).apply {
                    position = Position(fx, 0.18f, fz)
                })
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // FASE 6 — ARREDO URBANO
    // ══════════════════════════════════════════════════════════════════════

    fun buildStreetFurniture(osmData: OsmData) {
        for (way in osmData.roads) {
            if (way.highway !in listOf("primary", "secondary")) continue
            for ((a, b) in way.segments()) {
                val dx = b.localX - a.localX; val dz = b.localZ - a.localZ
                val segLen = Math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
                if (segLen < 20f) continue
                val angle = Math.atan2(dz.toDouble(), dx.toDouble()).toFloat()
                val cosA = Math.cos(angle.toDouble()).toFloat()
                val sinA = Math.sin(angle.toDouble()).toFloat()
                val perpX = -sinA * 2f; val perpZ = cosA * 2f

                var d = 0f
                while (d < segLen) {
                    if (currentNodeCount >= MAX_NODES - 3) break
                    val t = d / segLen.coerceAtLeast(0.1f)
                    val lx = a.localX + dx * t; val lz = a.localZ + dz * t

                    addNode(CubeNode(engine, Size(0.08f, 2.8f, 0.08f), materialInstance = poleMatInst).apply {
                        position = Position(lx + perpX, 1.4f, lz + perpZ)
                    })
                    addNode(CubeNode(engine, Size(0.8f, 0.08f, 0.08f), materialInstance = poleMatInst).apply {
                        position = Position(lx + perpX - 0.3f * cosA, 2.7f, lz + perpZ - 0.3f * sinA)
                    })
                    addNode(SphereNode(engine, 0.12f, materialInstance = lightMatInst).apply {
                        position = Position(lx + perpX - 0.6f * cosA, 2.65f, lz + perpZ - 0.6f * sinA)
                    })
                    lampLightMaterial = lightMatInst
                    d += 30f
                }
            }
        }

        for (park in osmData.parks) {
            if (park.nodes.size < 3) continue
            val fp = park.calculateFootprint() ?: continue
            val benchCount = (fp.width * fp.depth / 500f).toInt().coerceIn(1, 3)
            for (b in 0 until benchCount) {
                if (currentNodeCount >= MAX_NODES - 2) break
                val seed = park.id.toInt() * 17 + b * 53
                val bx = fp.centerX + ((seed % 100).toFloat() / 100f - 0.5f) * fp.width * 0.6f
                val bz = fp.centerZ + (((seed / 10) % 100).toFloat() / 100f - 0.5f) * fp.depth * 0.6f
                addNode(CubeNode(engine, Size(0.9f, 0.06f, 0.35f), materialInstance = benchWoodMatInst).apply {
                    position = Position(bx, 0.35f, bz)
                })
                addNode(CubeNode(engine, Size(0.9f, 0.3f, 0.06f), materialInstance = benchWoodMatInst).apply {
                    position = Position(bx, 0.55f, bz - 0.15f)
                })
            }
        }

        var trashCount = 0
        for (way in osmData.roads) {
            if (way.highway !in listOf("primary", "secondary", "residential")) continue
            if (trashCount >= 8) break
            for ((a, b) in way.segments()) {
                if (trashCount >= 8 || currentNodeCount >= MAX_NODES - 1) break
                val dx = b.localX - a.localX; val dz = b.localZ - a.localZ
                val segLen = Math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
                if (segLen < 20f) continue
                val angle = Math.atan2(dz.toDouble(), dx.toDouble()).toFloat()
                val cx = (a.localX + b.localX) / 2f; val cz = (a.localZ + b.localZ) / 2f
                val perpX = -Math.sin(angle.toDouble()).toFloat() * 2.5f
                val perpZ = Math.cos(angle.toDouble()).toFloat() * 2.5f
                addNode(CubeNode(engine, Size(0.3f, 0.5f, 0.3f), materialInstance = trashCanMatInst).apply {
                    position = Position(cx + perpX, 0.25f, cz + perpZ)
                })
                trashCount++
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // FASE 7 — AUTO
    // ══════════════════════════════════════════════════════════════════════

    fun buildCars(osmData: OsmData) {
        val carColors = intArrayOf(
            0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFFECEDF1.toInt(),
            0xFFFFCA28.toInt(), 0xFF212121.toInt(), 0xFF4CAF50.toInt()
        )
        var carCount = 0
        for (way in osmData.roads) {
            if (way.highway !in listOf("primary", "secondary", "residential")) continue
            if (carCount >= 10) break
            for ((a, b) in way.segments()) {
                if (carCount >= 10 || currentNodeCount >= MAX_NODES - 8) break
                val dx = b.localX - a.localX; val dz = b.localZ - a.localZ
                val segLen = Math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
                if (segLen < 15f) continue
                val angle = Math.atan2(dz.toDouble(), dx.toDouble()).toFloat()
                val cosA = Math.cos(angle.toDouble()).toFloat()
                val sinA = Math.sin(angle.toDouble()).toFloat()
                val perpX = -sinA * 1.5f; val perpZ = cosA * 1.5f
                val cx = a.localX + dx * 0.5f + perpX; val cz = a.localZ + dz * 0.5f + perpZ
                val carMat = ml.createColorInstance(color = carColors[carCount % carColors.size])

                addNode(CubeNode(engine, Size(1.4f, 0.3f, 0.7f), materialInstance = carMat).apply {
                    position = Position(cx, 0.22f, cz); rotation = Position(0f, -angle, 0f)
                })
                addNode(CubeNode(engine, Size(0.6f, 0.25f, 0.6f), materialInstance = carWindshieldMatInst).apply {
                    position = Position(cx, 0.5f, cz); rotation = Position(0f, -angle, 0f)
                })
                for (wx in floatArrayOf(-0.45f, 0.45f)) {
                    for (wz in floatArrayOf(-0.3f, 0.3f)) {
                        addNode(SphereNode(engine, 0.1f, materialInstance = carWheelMatInst).apply {
                            position = Position(cx + wx * cosA - wz * sinA, 0.1f, cz + wx * sinA + wz * cosA)
                        })
                    }
                }
                addNode(SphereNode(engine, 0.06f, materialInstance = carHeadlightMatInst).apply {
                    position = Position(cx + 0.7f * cosA, 0.22f, cz + 0.7f * sinA)
                })
                carCount++
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // FASE 8 — COLOSSEO
    // ══════════════════════════════════════════════════════════════════════

    fun buildColosseum(osmData: OsmData) {
        val colosseo = osmData.buildings.firstOrNull {
            it.name.lowercase().let { n -> n.contains("colosseum") || n.contains("colosseo") || n.contains("amphitheatre") }
        }
        val (cx, cz) = if (colosseo != null) {
            val fp = colosseo.calculateFootprint()
            if (fp != null) fp.centerX to fp.centerZ else 0f to 0f
        } else 0f to 0f

        val w = 120f; val d = 100f; val h = 15f
        addNode(CubeNode(engine, Size(w, h, d), materialInstance = travertineMatInst).apply {
            position = Position(cx, h / 2f, cz)
        })
        addNode(CubeNode(engine, Size(w * 0.75f, 0.5f, d * 0.75f), materialInstance = arenaMatInst).apply {
            position = Position(cx, 0.25f, cz)
        })
        addNode(CubeNode(engine, Size(w + 5f, 2f, d + 5f), materialInstance = travertineDarkMatInst).apply {
            position = Position(cx, h + 1f, cz)
        })
    }

    // ══════════════════════════════════════════════════════════════════════
    // FASE 9 — POI SPECIALI
    // ══════════════════════════════════════════════════════════════════════

    fun buildPoiFeatures(osmData: OsmData) {
        for (fountain in osmData.pois.filter { it.amenity == "fountain" }.take(3)) {
            if (currentNodeCount >= MAX_NODES - 3) break
            addNode(CubeNode(engine, Size(1.5f, 0.5f, 1.5f), materialInstance = poleMatInst).apply {
                position = Position(fountain.localX, 0.25f, fountain.localZ)
            })
            addNode(SphereNode(engine, 0.6f, materialInstance = waterMatInst).apply {
                position = Position(fountain.localX, 0.8f, fountain.localZ)
            })
            addNode(CubeNode(engine, Size(0.08f, 1.2f, 0.08f), materialInstance = poleMatInst).apply {
                position = Position(fountain.localX, 1.0f, fountain.localZ)
            })
        }

        for (church in osmData.buildings.filter {
            it.amenity == "place_of_worship" || it.tags["building"] == "church"
        }.take(2)) {
            if (currentNodeCount >= MAX_NODES - 4) continue
            val fp = church.calculateFootprint() ?: continue
            val cw = fp.width.coerceIn(2f, 10f); val cd = fp.depth.coerceIn(2f, 10f)
            val ch = church.height.toFloat().coerceIn(5f, 20f)
            val churchMat = ml.createColorInstance(color = 0xFFECEDF1.toInt())
            addNode(CubeNode(engine, Size(cw, ch, cd), materialInstance = churchMat).apply {
                position = Position(fp.centerX, ch / 2f, fp.centerZ)
            })
            addNode(CubeNode(engine, Size(0.8f, ch * 1.5f, 0.8f), materialInstance = churchMat).apply {
                position = Position(fp.centerX + cw / 2f + 0.5f, ch * 0.75f, fp.centerZ)
            })
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // FASE 10 — CARTELLI STRADALI (nomi vie da OSM)
    // ══════════════════════════════════════════════════════════════════════

    private val signPostMatInst by lazy { ml.createColorInstance(color = 0xFF616161.toInt()) }
    private val signPlateMatInst by lazy { ml.createColorInstance(color = 0xFFE8EAF6.toInt()) }

    fun buildStreetSigns(osmData: OsmData) {
        var signCount = 0
        for (way in osmData.roads) {
            if (signCount >= 20) break
            if (currentNodeCount >= MAX_NODES - 2) break
            val name = way.streetName
            if (name.isEmpty()) continue
            if (way.highway !in listOf("primary", "secondary", "tertiary", "residential")) continue

            val firstSeg = way.segments().firstOrNull() ?: continue
            val (a, b) = firstSeg
            val dx = b.localX - a.localX; val dz = b.localZ - a.localZ
            val segLen = Math.sqrt((dx * dx + dz * dz).toDouble()).toFloat()
            if (segLen < 5f) continue
            val angle = Math.atan2(dz.toDouble(), dx.toDouble()).toFloat()
            val cosA = Math.cos(angle.toDouble()).toFloat()
            val sinA = Math.sin(angle.toDouble()).toFloat()
            val perpX = -sinA * 1.8f; val perpZ = cosA * 1.8f
            val sx = a.localX + dx * 0.3f + perpX
            val sz = a.localZ + dz * 0.3f + perpZ

            addNode(CubeNode(engine, Size(0.06f, 2.2f, 0.06f), materialInstance = signPostMatInst).apply {
                position = Position(sx, 1.1f, sz)
            })
            addNode(CubeNode(engine, Size(0.8f, 0.25f, 0.04f), materialInstance = signPlateMatInst).apply {
                position = Position(sx, 2.15f, sz)
                rotation = Position(0f, -angle, 0f)
            })
            signCount++
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // FASE 11 — RISTORANTI (insegna + tenda strisce + tavolini)
    // ══════════════════════════════════════════════════════════════════════

    private val restaurantSignMatInst by lazy { ml.createColorInstance(color = 0xFFE53935.toInt()) }
    private val restaurantStripeMatInst by lazy { ml.createColorInstance(color = 0xFFECEDF1.toInt()) }
    private val restaurantTableMatInst by lazy { ml.createColorInstance(color = 0xFF8D6E63.toInt()) }
    private val restaurantChairMatInst by lazy { ml.createColorInstance(color = 0xFF90A4AE.toInt()) }

    fun buildRestaurantFeatures(osmData: OsmData) {
        val restaurants = osmData.buildings.filter {
            it.amenity == "restaurant" || it.amenity == "cafe" || it.amenity == "bar"
        }.take(5)

        for (rest in restaurants) {
            if (currentNodeCount >= MAX_NODES - 6) break
            val fp = rest.calculateFootprint() ?: continue
            val w = fp.width.coerceIn(2f, 20f)
            val d = fp.depth.coerceIn(2f, 20f)
            val h = rest.height.toFloat().coerceIn(3f, 20f)
            val angle = fp.rotation
            val cosA = Math.cos(angle.toDouble()).toFloat()
            val sinA = Math.sin(angle.toDouble()).toFloat()

            addNode(CubeNode(engine, Size(w * 0.5f, 0.2f, 0.08f), materialInstance = restaurantSignMatInst).apply {
                position = Position(fp.centerX, h * 0.75f, fp.centerZ + d / 2f + 0.05f)
                rotation = Position(0f, -angle, 0f)
            })

            addNode(CubeNode(engine, Size(w * 0.7f, 0.06f, 0.35f), materialInstance = restaurantSignMatInst).apply {
                position = Position(fp.centerX, minOf(2.5f, h * 0.35f), fp.centerZ + d / 2f + 0.22f)
                rotation = Position(0f, -angle, 0f)
            })
            addNode(CubeNode(engine, Size(w * 0.7f, 0.06f, 0.35f), materialInstance = restaurantStripeMatInst).apply {
                val offset = 0.15f * cosA
                position = Position(fp.centerX + offset * sinA, minOf(2.44f, h * 0.35f - 0.06f), fp.centerZ + d / 2f + 0.22f - offset * cosA)
                rotation = Position(0f, -angle, 0f)
            })

            val tableCount = 2
            for (t in 0 until tableCount) {
                if (currentNodeCount >= MAX_NODES - 2) break
                val tOffset = (t - (tableCount - 1) / 2f) * 1.5f
                val tx = fp.centerX + tOffset * cosA + (d / 2f + 1.0f) * sinA
                val tz = fp.centerZ + tOffset * sinA - (d / 2f + 1.0f) * cosA
                addNode(CubeNode(engine, Size(0.6f, 0.04f, 0.6f), materialInstance = restaurantTableMatInst).apply {
                    position = Position(tx, 0.5f, tz)
                })
                addNode(CubeNode(engine, Size(0.06f, 0.45f, 0.06f), materialInstance = restaurantTableMatInst).apply {
                    position = Position(tx, 0.23f, tz)
                })
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // FASE 12 — NEGOZI (insegna + vetrina grande)
    // ══════════════════════════════════════════════════════════════════════

    private val shopSignMatInst by lazy { ml.createColorInstance(color = 0xFF1E88E5.toInt()) }
    private val shopWindowMatInst by lazy { ml.createColorInstance(color = 0xFFB3E5FC.toInt()) }

    fun buildShopFeatures(osmData: OsmData) {
        val shops = osmData.buildings.filter {
            it.shop.isNotEmpty()
        }.take(10)

        for (shop in shops) {
            if (currentNodeCount >= MAX_NODES - 3) break
            val fp = shop.calculateFootprint() ?: continue
            val w = fp.width.coerceIn(2f, 20f)
            val d = fp.depth.coerceIn(2f, 20f)
            val h = shop.height.toFloat().coerceIn(3f, 20f)
            val angle = fp.rotation

            addNode(CubeNode(engine, Size(w * 0.6f, 0.2f, 0.08f), materialInstance = shopSignMatInst).apply {
                position = Position(fp.centerX, h * 0.85f, fp.centerZ + d / 2f + 0.05f)
                rotation = Position(0f, -angle, 0f)
            })

            val winW = (w * 0.5f).coerceIn(0.8f, 2.5f)
            addNode(CubeNode(engine, Size(winW, 0.8f, 0.06f), materialInstance = shopWindowMatInst).apply {
                position = Position(fp.centerX, 0.6f, fp.centerZ + d / 2f + 0.04f)
                rotation = Position(0f, -angle, 0f)
            })
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // BUILD COMPLETO
    // ══════════════════════════════════════════════════════════════════════

    fun buildAll(osmData: OsmData, mapSize: Float = 1000f) {
        currentNodeCount = 0
        buildingAABBs.clear()
        windowMaterials.clear()

        buildTerrain(mapSize)
        buildRoads(osmData)
        buildColosseum(osmData)
        buildBuildings(osmData)
        buildTrees(osmData, mapSize)
        buildVegetation(osmData)
        buildStreetFurniture(osmData)
        buildCars(osmData)
        buildPoiFeatures(osmData)
        buildStreetSigns(osmData)
        buildRestaurantFeatures(osmData)
        buildShopFeatures(osmData)
    }

    // ── Build incrementale (per caricamento a fasi) ──────────────────

    fun buildPhase1_TerrainAndRoads(osmData: OsmData, mapSize: Float = 1000f) {
        currentNodeCount = 0
        buildingAABBs.clear()
        windowMaterials.clear()
        buildTerrain(mapSize)
        buildRoads(osmData)
    }

    fun buildPhase2_ColosseumAndBuildings(osmData: OsmData) {
        buildColosseum(osmData)
        buildBuildings(osmData)
    }

    fun buildPhase3_TreesAndVegetation(osmData: OsmData, mapSize: Float = 1000f) {
        buildTrees(osmData, mapSize)
        buildVegetation(osmData)
    }

    fun buildPhase4_FurnitureAndCars(osmData: OsmData) {
        buildStreetFurniture(osmData)
        buildCars(osmData)
    }

    fun buildPhase5_Details(osmData: OsmData) {
        buildPoiFeatures(osmData)
        buildStreetSigns(osmData)
        buildRestaurantFeatures(osmData)
        buildShopFeatures(osmData)
    }
}
