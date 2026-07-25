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

    // ── Palette colori Brookhaven ──────────────────────────────────────
    private val bodyColors = intArrayOf(
        0xFFB3D9FF.toInt(), 0xFFFFCDD2.toInt(), 0xFFC8E6C9.toInt(),
        0xFFFFF9C4.toInt(), 0xFFD1C4E9.toInt(), 0xFFFFE0B2.toInt(),
        0xFFB2DFDB.toInt(), 0xFFF0F4C3.toInt(), 0xFFDCEDC8.toInt(),
        0xFFFFAB91.toInt(), 0xFFB3E5FC.toInt(), 0xFFE1BEE7.toInt()
    )
    private val roofColorsArr = intArrayOf(
        0xFF8D6E63.toInt(), 0xFF78909C.toInt(), 0xFFA1887F.toInt(),
        0xFF90A4AE.toInt(), 0xFFBCAAA4.toInt(), 0xFFB0BEC5.toInt(),
        0xFF80CBC4.toInt(), 0xFFC5E1A5.toInt(), 0xFFAED581.toInt(),
        0xFFD4937A.toInt(), 0xFF81D4FA.toInt(), 0xFFCE93D8.toInt()
    )
    private val awningColors = intArrayOf(
        0xFFE53935.toInt(), 0xFF1E88E5.toInt(), 0xFFFFCA28.toInt(), 0xFF4CAF50.toInt()
    )

    // ── Materiali riutilizzabili (creati una volta sola) ───────────────
    private val grassDarkMat by lazy { ml.createColorInstance(color = 0xFF3D7A33.toInt()) }
    private val grassLightMat by lazy { ml.createColorInstance(color = 0xFF5C9E4A.toInt()) }
    private val asphaltMat by lazy { ml.createColorInstance(color = 0xFF555565.toInt()) }
    private val roadMat by lazy { ml.createColorInstance(color = 0xFF484858.toInt()) }
    private val yellowLineMat by lazy { ml.createColorInstance(color = 0xFFFFC107.toInt()) }
    private val sidewalkMat by lazy { ml.createColorInstance(color = 0xFFBDBDBD.toInt()) }
    private val windowMatInst by lazy { ml.createColorInstance(color = 0xFF90CAF9.toInt()) }
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

    // ── Stato ──────────────────────────────────────────────────────────
    val windowMaterials = mutableListOf<com.google.android.filament.MaterialInstance>()
    var lampLightMaterial: com.google.android.filament.MaterialInstance? = null
        private set
    val buildingAABBs = mutableListOf<AABB>()

    private var currentNodeCount = 0
    private val MAX_NODES = 1400

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

    // ══════════════════════════════════════════════════════════════════════
    // FASE 3 — EDIFICI DA OSM
    // ══════════════════════════════════════════════════════════════════════

    fun buildBuildings(osmData: OsmData) {
        val buildings = osmData.buildings
            .filter { it.nodes.size >= 3 }
            .sortedByDescending { it.height }
            .take(100)

        var buildingIndex = 0
        for (way in buildings) {
            if (currentNodeCount >= MAX_NODES - 10) break

            val fp = way.calculateFootprint() ?: continue
            val h = way.height.toFloat()
            val w = fp.width.coerceIn(1f, 30f)
            val d = fp.depth.coerceIn(1f, 30f)
            if (w < 1f || d < 1f || h < 2f) continue

            val ci = buildingIndex % bodyColors.size
            val bodyColor = way.facadeColor ?: bodyColors[ci]
            val roofColorVal = way.roofColor ?: roofColorsArr[ci]
            val bodyMatInst = ml.createColorInstance(color = bodyColor)
            val roofMatInst = ml.createColorInstance(color = roofColorVal)

            addNode(CubeNode(engine, Size(w, h, d), materialInstance = bodyMatInst).apply {
                position = Position(fp.centerX, h / 2f, fp.centerZ)
                rotation = Position(0f, -fp.rotation, 0f)
            })
            addNode(CubeNode(engine, Size(w + 0.3f, 0.2f, d + 0.3f), materialInstance = roofMatInst).apply {
                position = Position(fp.centerX, h + 0.1f, fp.centerZ)
                rotation = Position(0f, -fp.rotation, 0f)
            })
            addNode(CubeNode(engine, Size(w + 0.5f, 0.08f, d + 0.5f), materialInstance = grassLightMat).apply {
                position = Position(fp.centerX, h + 0.24f, fp.centerZ)
                rotation = Position(0f, -fp.rotation, 0f)
            })
            addNode(CubeNode(engine, Size(0.6f, 1.2f, 0.08f), materialInstance = doorMatInst).apply {
                position = Position(fp.centerX, 0.6f, fp.centerZ + d / 2f + 0.04f)
                rotation = Position(0f, -fp.rotation, 0f)
            })

            if (w >= 2f && h >= 3f) {
                windowMaterials.add(windowMatInst)
                val nWinFront = ((w / 2f).toInt()).coerceIn(1, 3)
                val stepW = w / (nWinFront + 1).toFloat()
                val winY = h * 0.55f
                for (i in 1..nWinFront) {
                    val wx = fp.centerX - w / 2f + i * stepW
                    addNode(CubeNode(engine, Size(0.3f, 0.3f, 0.08f), materialInstance = windowMatInst).apply {
                        position = Position(wx, winY, fp.centerZ + d / 2f + 0.04f)
                        rotation = Position(0f, -fp.rotation, 0f)
                    })
                }
                if (d >= 3f) {
                    val nWinSide = ((d / 3f).toInt()).coerceIn(1, 2)
                    val stepD = d / (nWinSide + 1).toFloat()
                    for (i in 1..nWinSide) {
                        val wz = fp.centerZ - d / 2f + i * stepD
                        addNode(CubeNode(engine, Size(0.08f, 0.3f, 0.3f), materialInstance = windowMatInst).apply {
                            position = Position(fp.centerX - w / 2f - 0.04f, winY, wz)
                            rotation = Position(0f, -fp.rotation, 0f)
                        })
                    }
                }
            }

            if (h > 3f) {
                val awningMatInst = ml.createColorInstance(color = awningColors[buildingIndex % awningColors.size])
                addNode(CubeNode(engine, Size(w * 0.6f, 0.05f, 0.35f), materialInstance = awningMatInst).apply {
                    position = Position(fp.centerX, minOf(2.5f, h * 0.3f), fp.centerZ + d / 2f + 0.2f)
                    rotation = Position(0f, -fp.rotation, 0f)
                })
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
        for (tree in osmData.trees.take(50)) {
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
