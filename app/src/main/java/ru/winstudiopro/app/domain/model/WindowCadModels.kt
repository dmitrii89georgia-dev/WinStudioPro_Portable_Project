package ru.winstudiopro.app.domain.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

enum class ImpostDirection(val titleRu: String) {
    VERTICAL("Вертикальный импост"),
    HORIZONTAL("Горизонтальный импост")
}

/**
 * Три режима выравнивания импостов (по запросу пользователя):
 * 1. По чертежу (по осям) — равные расстояния от края рамы до оси импоста.
 * 2. По проёмам — равная ширина/высота внутренних проемов рамы в чистоте.
 * 3. По свету (равный стеклопакет) — световой проем глухарей равен световому проему створок/дверей.
 */
enum class ImpostAlignmentMode(
    val titleRu: String,
    val descriptionRu: String
) {
    BY_AXIS(
        "По чертежу (по осям)",
        "От края рамы — до центра импоста — до края рамы равные расстояния"
    ),
    BY_OPENING(
        "По проёмам (в чистоте рамы)",
        "Проёмы внутри рамы одинаковые (с учётом разницы толщины рамы и импоста)"
    ),
    BY_DAYLIGHT(
        "По свету (равное стекло)",
        "Световой проём (видимое стекло) глухарей равен световому проёму створок и дверей"
    )
}

/**
 * Все типы открываний в точности по сетке модального окна «открывание» из видео.
 */
enum class SashOpeningType(
    val titleRu: String,
    val shortCode: String,
    val hasSashProfile: Boolean
) {
    // Ряд 1 в диалоге (3 колонки):
    SLIDING_RIGHT("Раздвижная вправо (→)", "Раздв.→", true),
    TRANSOM_TOP("Верхнеподвесная (петли сверху \\/)", "Фрам.В", true),
    SLIDING_LEFT("Раздвижная влево (←)", "Раздв.←", true),

    // Ряд 2 в диалоге (3 колонки):
    TURN_LEFT("Поворотная левая (|>)", "Пов.Л", true),
    FIXED_GLASS("Глухое остекление (+)", "Глух.", false),
    TURN_RIGHT("Поворотная правая (<|)", "Пов.П", true),

    // Ряд 3 в диалоге (3 колонки):
    TILT_TURN_LEFT("Поворотно-откидная левая", "П/О Л", true),
    TRANSOM_BOTTOM("Фрамуга откидная (петли снизу /\\)", "Фрам.Н", true),
    TILT_TURN_RIGHT("Поворотно-откидная правая", "П/О П", true),

    // Ряд 4 (глухая створка по центру):
    BLIND_SASH("Глухая створка", "Ств.Гл", true),

    // Штульповые двухстворчатые (4 явные комбинации + legacy):
    SHTULP_TURN_L_PASSIVE_R("Штульп: Пов.Л + Пасс.П", "Шт.ПЛ+ПасП", true),
    SHTULP_TILTTURN_L_PASSIVE_R("Штульп: П/О Л + Пасс.П", "Шт.ПОЛ+ПасП", true),
    SHTULP_PASSIVE_L_TURN_R("Штульп: Пасс.Л + Пов.П", "Шт.ПасЛ+ПП", true),
    SHTULP_PASSIVE_L_TILTTURN_R("Штульп: Пасс.Л + П/О П", "Шт.ПасЛ+ПОП", true),
    SHTULP_TURN_TURN("Штульп: Пов.Л + Пов.П", "Шт.П+П", true),
    SHTULP_TILTTURN_L_TURN_R("Штульп: П/О Л + Пов.П", "Шт.ПОЛ+ПП", true),
    SHTULP_TURN_L_TILTTURN_R("Штульп: Пов.Л + П/О П", "Шт.ПЛ+ПОП", true),

    // Вертикально-подъемные (английские / гильотинные):
    VERT_SLIDE_UP("Подъемная вверх (↑)", "Подъем.↑", true),
    VERT_SLIDE_BOTH("Раздвижная верт. (↓↑)", "Раздв.↕", true);

    val isShtulp: Boolean
        get() = this == SHTULP_TURN_L_PASSIVE_R ||
            this == SHTULP_TILTTURN_L_PASSIVE_R ||
            this == SHTULP_PASSIVE_L_TURN_R ||
            this == SHTULP_PASSIVE_L_TILTTURN_R ||
            this == SHTULP_TURN_TURN ||
            this == SHTULP_TILTTURN_L_TURN_R ||
            this == SHTULP_TURN_L_TILTTURN_R

    val isShtulpLeftPassive: Boolean
        get() = this == SHTULP_PASSIVE_L_TURN_R || this == SHTULP_PASSIVE_L_TILTTURN_R

    val isShtulpRightPassive: Boolean
        get() = this == SHTULP_TURN_L_PASSIVE_R ||
            this == SHTULP_TILTTURN_L_PASSIVE_R ||
            this == SHTULP_TURN_TURN ||
            this == SHTULP_TILTTURN_L_TURN_R

    val isShtulpLeftTiltTurn: Boolean
        get() = this == SHTULP_TILTTURN_L_PASSIVE_R || this == SHTULP_TILTTURN_L_TURN_R

    val isShtulpRightTiltTurn: Boolean
        get() = this == SHTULP_PASSIVE_L_TILTTURN_R || this == SHTULP_TURN_L_TILTTURN_R
}

enum class SashImpostMode(val titleRu: String, val paneCount: Int) {
    NONE("Пустой проём", 1),
    V1("1 вертикальный импост", 2),
    H1("1 горизонтальный импост", 2),
    V2("2 вертикальных импоста", 3),
    H2("2 горизонтальных импоста", 3)
}

enum class GlazingBarsPattern(val titleRu: String, val horizontalRows: Int, val verticalCols: Int) {
    NONE("Без шпросов", 0, 0),
    GRID_3x3("Шпросы (решетка)", 2, 2),
    CROSS_1x1("Крест (1×1)", 1, 1)
}

data class OpeningProperties(
    val openingType: SashOpeningType = SashOpeningType.FIXED_GLASS,
    val isDoorSash: Boolean = false,
    val isReverseDoorSash: Boolean = false,
    val isSandwichFilling: Boolean = false,
    val hasMosquitoNet: Boolean = false,
    val mosquitoNetType: MosquitoNetType? = null,
    val hasHorizontalSashImpost: Boolean = false,
    val sashImpostHeightFromBottomMm: Float = 800f,
    val bottomPartIsSandwich: Boolean = false,
    val glazingBars: GlazingBarsPattern = GlazingBarsPattern.NONE,
    val sashImpostMode: SashImpostMode = SashImpostMode.NONE,
    val paneSandwichFlags: List<Boolean> = emptyList(),
    val shtulpSplitRatio: Float = 0.5f
) {
    fun effectiveMosquitoNetType(): MosquitoNetType? =
        mosquitoNetType ?: if (hasMosquitoNet) MosquitoNetType.WINDOW else null

    fun effectiveSashImpostMode(): SashImpostMode = when {
        sashImpostMode != SashImpostMode.NONE -> sashImpostMode
        hasHorizontalSashImpost -> SashImpostMode.H1
        else -> SashImpostMode.NONE
    }

    fun isPaneSandwich(index: Int): Boolean {
        if (paneSandwichFlags.isNotEmpty()) {
            return paneSandwichFlags.getOrElse(index) { false }
        }
        return if (effectiveSashImpostMode() == SashImpostMode.H1 && index == 1) {
            bottomPartIsSandwich || isSandwichFilling
        } else {
            isSandwichFilling
        }
    }
}

/**
 * Световая ячейка (проем) внутри рамы.
 * Координаты задаются в миллиметрах относительно левого верхнего угла конструкции (0, 0).
 */
data class OpeningCellNode(
    val id: String,
    val frameId: String,
    val leftMm: Float,
    val topMm: Float,
    val rightMm: Float,
    val bottomMm: Float,
    val properties: OpeningProperties = OpeningProperties()
) {
    val widthMm: Float get() = (rightMm - leftMm).coerceAtLeast(0f)
    val heightMm: Float get() = (bottomMm - topMm).coerceAtLeast(0f)
    val areaM2: Float get() = (widthMm * heightMm) / 1_000_000f
}

/**
 * Сегмент импоста внутри рамы.
 */
data class ImpostSegmentNode(
    val id: String,
    val frameId: String,
    val direction: ImpostDirection,
    val startX: Float,
    val startY: Float,
    val endX: Float,
    val endY: Float
) {
    val lengthMm: Float
        get() = if (direction == ImpostDirection.VERTICAL) abs(endY - startY) else abs(endX - startX)
}

/**
 * Отдельная рама внутри конструкции.
 */
data class FrameBlockNode(
    val id: String,
    val leftMm: Float,
    val topMm: Float,
    val rightMm: Float,
    val bottomMm: Float,
    val openings: List<OpeningCellNode>,
    val imposts: List<ImpostSegmentNode> = emptyList()
) {
    val widthMm: Float get() = (rightMm - leftMm).coerceAtLeast(0f)
    val heightMm: Float get() = (bottomMm - topMm).coerceAtLeast(0f)
}

/**
 * Дополнительные профили по периметру окна (подставочный, порог, доборы).
 */
data class WindowPerimeterAddons(
    val hasSeatProfile: Boolean = false,
    val hasDoorsill: Boolean = false,
    val hasCornerJamb: Boolean = false,
    val hasCornerProfile: Boolean = false,
    val hasExtenderProfile90: Boolean = false,
    val extenderTopMm: Int = 0,
    val extenderBottomMm: Int = 0,
    val extenderLeftMm: Int = 0,
    val extenderRightMm: Int = 0
)

/**
 * Топологическая модель окна / витража / балконного блока.
 */
data class WindowBlockTopology(
    val frames: List<FrameBlockNode>
) {
    val totalWidthMm: Float
        get() = (frames.maxOfOrNull { it.rightMm } ?: 1500f) - (frames.minOfOrNull { it.leftMm } ?: 0f)

    val totalHeightMm: Float
        get() = (frames.maxOfOrNull { it.bottomMm } ?: 1500f) - (frames.minOfOrNull { it.topMm } ?: 0f)

    val allOpenings: List<OpeningCellNode>
        get() = frames.flatMap { it.openings }

    val allImposts: List<ImpostSegmentNode>
        get() = frames.flatMap { it.imposts }

    val xGridCoords: List<Float>
        get() {
            val raw = mutableListOf<Float>()
            frames.forEach { f ->
                raw.add(f.leftMm)
                raw.add(f.rightMm)
                f.openings.forEach { o ->
                    raw.add(o.leftMm)
                    raw.add(o.rightMm)
                }
            }
            return deduplicateSortedCoords(raw)
        }

    val yGridCoords: List<Float>
        get() {
            val raw = mutableListOf<Float>()
            frames.forEach { f ->
                raw.add(f.topMm)
                raw.add(f.bottomMm)
                f.openings.forEach { o ->
                    raw.add(o.topMm)
                    raw.add(o.bottomMm)
                }
            }
            return deduplicateSortedCoords(raw)
        }

    private fun deduplicateSortedCoords(coords: List<Float>): List<Float> {
        val sorted = coords.sorted()
        val result = mutableListOf<Float>()
        for (c in sorted) {
            if (result.isEmpty() || abs(c - result.last()) > 1.0f) {
                result.add(c.roundToInt().toFloat())
            }
        }
        return result
    }
}

/**
 * Геометрические операции над 2D-топологией окна (деление проемов, слияние, выравнивание по осям/проемам/свету).
 */
object WindowCadOperations {
    private const val MIN_SEGMENT_MM = 200f
    private const val EPS = 2.0f

    fun single1500Topology(): WindowBlockTopology = WindowBlockTopology(
        frames = listOf(
            FrameBlockNode(
                id = "f1",
                leftMm = 0f,
                topMm = 0f,
                rightMm = 1500f,
                bottomMm = 1500f,
                openings = listOf(
                    OpeningCellNode("op1", "f1", 0f, 0f, 1500f, 1500f)
                )
            )
        )
    )

    /**
     * Ищет высоту соседнего горизонтального импоста от низа проема (для авто-привязки импоста двери/створки).
     * Если рядом с дверью есть глухарь, разделенный горизонтальным импостом (например на 800 или 900 мм снизу),
     * импост внутри двери автоматически встанет в одну линию с ним!
     */
    fun findNeighborHorizontalImpostHeightFromBottom(
        topology: WindowBlockTopology,
        openingId: String
    ): Float? {
        for (frame in topology.frames) {
            val cell = frame.openings.find { it.id == openingId } ?: continue
            // Ищем горизонтальные импосты в этой же раме, которые находятся в пределах высоты нашей ячейки
            val candidateImposts = frame.imposts.filter { imp ->
                imp.direction == ImpostDirection.HORIZONTAL &&
                    imp.startY > cell.topMm + 120f &&
                    imp.startY < cell.bottomMm - 120f
            }
            // Сначала проверяем непосредственно примыкающие слева или справа горизонтальные импосты
            val touching = candidateImposts.firstOrNull { imp ->
                abs(imp.startX - cell.rightMm) <= EPS || abs(imp.endX - cell.leftMm) <= EPS
            } ?: candidateImposts.firstOrNull()

            if (touching != null) {
                return (cell.bottomMm - touching.startY).roundToInt().toFloat()
            }
        }
        return null
    }

    fun updateSashImpostHeightFromBottom(
        topology: WindowBlockTopology,
        openingId: String,
        newHeightFromBottomMm: Float
    ): WindowBlockTopology {
        return topology.copy(
            frames = topology.frames.map { frame ->
                frame.copy(
                    openings = frame.openings.map { op ->
                        if (op.id == openingId) {
                            val clamped = newHeightFromBottomMm
                                .roundToInt()
                                .toFloat()
                                .coerceIn(180f, (op.heightMm - 180f).coerceAtLeast(200f))
                            op.copy(
                                properties = op.properties.copy(
                                    hasHorizontalSashImpost = true,
                                    sashImpostMode = if (op.properties.sashImpostMode == SashImpostMode.NONE) SashImpostMode.H1 else op.properties.sashImpostMode,
                                    sashImpostHeightFromBottomMm = clamped
                                )
                            )
                        } else op
                    }
                )
            }
        )
    }

    fun toggleDoorBottomSandwich(
        topology: WindowBlockTopology,
        openingId: String
    ): WindowBlockTopology {
        return topology.copy(
            frames = topology.frames.map { frame ->
                frame.copy(
                    openings = frame.openings.map { op ->
                        if (op.id == openingId) {
                            val nextFlags = if (op.properties.paneSandwichFlags.size >= 2) {
                                op.properties.paneSandwichFlags.mapIndexed { idx, v ->
                                    if (idx == 1) !v else v
                                }
                            } else {
                                op.properties.paneSandwichFlags
                            }
                            op.copy(
                                properties = op.properties.copy(
                                    bottomPartIsSandwich = !op.properties.bottomPartIsSandwich,
                                    paneSandwichFlags = nextFlags
                                )
                            )
                        } else op
                    }
                )
            }
        )
    }

    data class ShtulpGeometryMm(
        val leftDedMm: Float,
        val rightDedMm: Float,
        val lightWMm: Float,
        val lightWLMm: Float,
        val lightWRMm: Float,
        val axisXMm: Float
    )

    fun computeShtulpGeometry(
        frame: FrameBlockNode,
        op: OpeningCellNode,
        profileSystem: ProfileSystemSpec
    ): ShtulpGeometryMm {
        val atFrameLeft = abs(op.leftMm - frame.leftMm) <= EPS
        val atFrameRight = abs(op.rightMm - frame.rightMm) <= EPS
        val leftDed = if (atFrameLeft) profileSystem.frameWidthMm else profileSystem.impostWidthMm / 2f
        val rightDed = if (atFrameRight) profileSystem.frameWidthMm else profileSystem.impostWidthMm / 2f
        val lightW = (op.widthMm - leftDed - rightDed).coerceAtLeast(100f)
        val ratio = op.properties.shtulpSplitRatio.coerceIn(0.15f, 0.85f)
        val lightWL = lightW * ratio
        val lightWR = lightW - lightWL
        val axisX = op.leftMm + leftDed + lightWL
        return ShtulpGeometryMm(
            leftDedMm = leftDed,
            rightDedMm = rightDed,
            lightWMm = lightW,
            lightWLMm = lightWL,
            lightWRMm = lightWR,
            axisXMm = axisX
        )
    }

    fun updateShtulpSplitBySegment(
        topology: WindowBlockTopology,
        frameId: String,
        openingId: String,
        isLeftLeaf: Boolean,
        enteredWidthMm: Float,
        profileSystem: ProfileSystemSpec
    ): WindowBlockTopology {
        return topology.copy(
            frames = topology.frames.map { frame ->
                if (frame.id != frameId) return@map frame
                frame.copy(
                    openings = frame.openings.map { op ->
                        if (op.id != openingId) return@map op
                        val geom = computeShtulpGeometry(frame, op, profileSystem)
                        val lightW = geom.lightWMm
                        val minLight = minOf(MIN_SEGMENT_MM, lightW * 0.4f)
                        val rawLightWL = if (isLeftLeaf) {
                            enteredWidthMm - geom.leftDedMm
                        } else {
                            lightW - (enteredWidthMm - geom.rightDedMm)
                        }
                        val clampedLightWL = rawLightWL.coerceIn(minLight, (lightW - minLight).coerceAtLeast(minLight))
                        val newRatio = (clampedLightWL / lightW).coerceIn(0.15f, 0.85f)
                        op.copy(properties = op.properties.copy(shtulpSplitRatio = newRatio))
                    }
                )
            }
        )
    }

    fun splitOpening(
        topology: WindowBlockTopology,
        openingId: String,
        direction: ImpostDirection,
        parts: Int
    ): WindowBlockTopology {
        if (parts < 2 || parts > 4) return topology
        val updatedFrames = topology.frames.map { frame ->
            val target = frame.openings.find { it.id == openingId } ?: return@map frame
            val newOpenings = mutableListOf<OpeningCellNode>()
            val newImposts = frame.imposts.toMutableList()

            for (op in frame.openings) {
                if (op.id != openingId) {
                    newOpenings.add(op)
                } else {
                    if (direction == ImpostDirection.VERTICAL) {
                        val step = (target.widthMm / parts)
                        if (step < MIN_SEGMENT_MM) return@map frame
                        for (i in 0 until parts) {
                            val l = (target.leftMm + i * step).roundToInt().toFloat()
                            val r = if (i == parts - 1) target.rightMm else (target.leftMm + (i + 1) * step).roundToInt().toFloat()
                            newOpenings.add(
                                OpeningCellNode(
                                    id = "${target.id}_v$i",
                                    frameId = frame.id,
                                    leftMm = l,
                                    topMm = target.topMm,
                                    rightMm = r,
                                    bottomMm = target.bottomMm,
                                    properties = target.properties
                                )
                            )
                            if (i < parts - 1) {
                                newImposts.add(
                                    ImpostSegmentNode(
                                        id = "imp_${target.id}_v$i",
                                        frameId = frame.id,
                                        direction = ImpostDirection.VERTICAL,
                                        startX = r,
                                        startY = target.topMm,
                                        endX = r,
                                        endY = target.bottomMm
                                    )
                                )
                            }
                        }
                    } else {
                        val step = (target.heightMm / parts)
                        if (step < MIN_SEGMENT_MM) return@map frame
                        for (i in 0 until parts) {
                            val t = (target.topMm + i * step).roundToInt().toFloat()
                            val b = if (i == parts - 1) target.bottomMm else (target.topMm + (i + 1) * step).roundToInt().toFloat()
                            newOpenings.add(
                                OpeningCellNode(
                                    id = "${target.id}_h$i",
                                    frameId = frame.id,
                                    leftMm = target.leftMm,
                                    topMm = t,
                                    rightMm = target.rightMm,
                                    bottomMm = b,
                                    properties = target.properties
                                )
                            )
                            if (i < parts - 1) {
                                newImposts.add(
                                    ImpostSegmentNode(
                                        id = "imp_${target.id}_h$i",
                                        frameId = frame.id,
                                        direction = ImpostDirection.HORIZONTAL,
                                        startX = target.leftMm,
                                        startY = b,
                                        endX = target.rightMm,
                                        endY = b
                                    )
                                )
                            }
                        }
                    }
                }
            }
            frame.copy(openings = newOpenings, imposts = newImposts)
        }
        return topology.copy(frames = updatedFrames)
    }

    fun canRemoveImpost(frame: FrameBlockNode, impost: ImpostSegmentNode): Boolean {
        return findMergeablePair(frame, impost) != null
    }

    private fun findMergeablePair(
        frame: FrameBlockNode,
        impost: ImpostSegmentNode
    ): Pair<OpeningCellNode, OpeningCellNode>? {
        if (impost.direction == ImpostDirection.VERTICAL) {
            val leftOp = frame.openings.find {
                abs(it.rightMm - impost.startX) <= EPS &&
                    abs(it.topMm - impost.startY) <= EPS &&
                    abs(it.bottomMm - impost.endY) <= EPS
            }
            val rightOp = frame.openings.find {
                abs(it.leftMm - impost.startX) <= EPS &&
                    abs(it.topMm - impost.startY) <= EPS &&
                    abs(it.bottomMm - impost.endY) <= EPS
            }
            if (leftOp != null && rightOp != null) return leftOp to rightOp
        } else {
            val topOp = frame.openings.find {
                abs(it.bottomMm - impost.startY) <= EPS &&
                    abs(it.leftMm - impost.startX) <= EPS &&
                    abs(it.rightMm - impost.endX) <= EPS
            }
            val bottomOp = frame.openings.find {
                abs(it.topMm - impost.startY) <= EPS &&
                    abs(it.leftMm - impost.startX) <= EPS &&
                    abs(it.rightMm - impost.endX) <= EPS
            }
            if (topOp != null && bottomOp != null) return topOp to bottomOp
        }
        return null
    }

    fun removeImpost(topology: WindowBlockTopology, impostId: String): WindowBlockTopology {
        val updatedFrames = topology.frames.map { frame ->
            val impost = frame.imposts.find { it.id == impostId } ?: return@map frame
            val pair = findMergeablePair(frame, impost) ?: return@map frame
            val (first, second) = pair
            val merged = OpeningCellNode(
                id = first.id,
                frameId = frame.id,
                leftMm = minOf(first.leftMm, second.leftMm),
                topMm = minOf(first.topMm, second.topMm),
                rightMm = maxOf(first.rightMm, second.rightMm),
                bottomMm = maxOf(first.bottomMm, second.bottomMm),
                properties = first.properties
            )
            val remainingOpenings = frame.openings
                .filterNot { it.id == first.id || it.id == second.id } + merged
            val remainingImposts = frame.imposts.filterNot { it.id == impostId }
            frame.copy(openings = remainingOpenings, imposts = remainingImposts)
        }
        return topology.copy(frames = updatedFrames)
    }

    fun updateOpeningProperties(
        topology: WindowBlockTopology,
        openingId: String,
        newProps: OpeningProperties
    ): WindowBlockTopology {
        return topology.copy(
            frames = topology.frames.map { frame ->
                frame.copy(
                    openings = frame.openings.map { op ->
                        if (op.id == openingId) op.copy(properties = newProps) else op
                    }
                )
            }
        )
    }

    /**
     * Выравнивание сегментов по выбранному режиму:
     * - BY_AXIS: равные расстояния по осям (от края рамы до центра импоста).
     * - BY_OPENING: равные внутренние проёмы между рамой и импостами.
     * - BY_DAYLIGHT: равный световой проём (стекло в глухаре == стекло в створке/двери).
     */
    fun equalizeAxisSegments(
        topology: WindowBlockTopology,
        isHorizontalAxis: Boolean,
        mode: ImpostAlignmentMode = ImpostAlignmentMode.BY_AXIS,
        profileSystem: ProfileSystemSpec? = null
    ): WindowBlockTopology {
        val oldCoords = if (isHorizontalAxis) topology.xGridCoords else topology.yGridCoords
        if (oldCoords.size < 3) return topology
        val minVal = oldCoords.first()
        val maxVal = oldCoords.last()
        val totalSpan = maxVal - minVal
        val count = oldCoords.size - 1

        if (mode == ImpostAlignmentMode.BY_AXIS || profileSystem == null) {
            val step = totalSpan / count
            val newCoords = (0..count).map { i ->
                if (i == count) maxVal else (minVal + i * step).roundToInt().toFloat()
            }
            return remapAxisCoordinates(topology, isHorizontalAxis, oldCoords, newCoords)
        }

        val frameInnerA1 = profileSystem.frameWidthMm
        val halfImpInnerB1 = profileSystem.impostWidthMm / 2f
        val frameFullA3 = profileSystem.frameFullWidthA3Mm.takeIf { it > 0f } ?: (frameInnerA1 + profileSystem.frameRebateHeightA2Mm)
        val halfImpFullB3 = (profileSystem.impostFullWidthB3Mm.takeIf { it > 0f } ?: (profileSystem.impostWidthMm + 2f * profileSystem.impostRebateHeightB2Mm)) / 2f

        // Вычисляем потерю ширины/высоты в каждом сегменте i = 0 until count
        val segmentLosses = FloatArray(count) { i ->
            val segStart = oldCoords[i]
            val segEnd = oldCoords[i + 1]
            val startInnerDed = if (i == 0) frameInnerA1 else halfImpInnerB1
            val endInnerDed = if (i == count - 1) frameInnerA1 else halfImpInnerB1
            val baseInnerLoss = startInnerDed + endInnerDed

            if (mode == ImpostAlignmentMode.BY_OPENING) {
                baseInnerLoss
            } else {
                // BY_DAYLIGHT: для глухаря свет режется полными ширинами A3 и B3/2,
                // а для створки/двери — внутренним проёмом (A1, B1/2) плюс 2 * (полная ширина створки C2/D2 - нахлёст)
                val startFullDed = if (i == 0) frameFullA3 else halfImpFullB3
                val endFullDed = if (i == count - 1) frameFullA3 else halfImpFullB3
                val fixedDaylightLoss = startFullDed + endFullDed

                val matchingOpenings = topology.allOpenings.filter { op ->
                    if (isHorizontalAxis) {
                        abs(op.leftMm - segStart) <= EPS && abs(op.rightMm - segEnd) <= EPS
                    } else {
                        abs(op.topMm - segStart) <= EPS && abs(op.bottomMm - segEnd) <= EPS
                    }
                }
                matchingOpenings.maxOfOrNull { op ->
                    val p = op.properties
                    if (!p.openingType.hasSashProfile) {
                        fixedDaylightLoss
                    } else {
                        val sashFullW = if (p.isDoorSash) {
                            profileSystem.doorFullWidthD2Mm.takeIf { it > 0f } ?: profileSystem.doorSashWidthMm
                        } else {
                            profileSystem.sashFullWidthC2Mm.takeIf { it > 0f } ?: profileSystem.sashWidthMm
                        }
                        val overlap = if (p.isDoorSash) profileSystem.doorOverlapMm else profileSystem.sashOverlapMm
                        val isDoubleShtulp = p.openingType.isShtulp
                        val leaves = if (isHorizontalAxis && isDoubleShtulp) 2 else 1
                        baseInnerLoss + leaves * 2f * (sashFullW - overlap).coerceAtLeast(0f)
                    }
                } ?: fixedDaylightLoss
            }
        }

        val totalLosses = segmentLosses.sum()
        val equalClearLight = ((totalSpan - totalLosses) / count).coerceAtLeast(80f)

        val newCoords = mutableListOf(minVal)
        var acc = minVal
        for (i in 0 until count) {
            val segAxialLen = (equalClearLight + segmentLosses[i]).coerceAtLeast(MIN_SEGMENT_MM)
            acc += segAxialLen
            newCoords.add(if (i == count - 1) maxVal else acc.roundToInt().toFloat())
        }
        return remapAxisCoordinates(topology, isHorizontalAxis, oldCoords, newCoords)
    }

    fun updateChainSegmentLength(
        topology: WindowBlockTopology,
        isHorizontalAxis: Boolean,
        segmentIndex: Int,
        newSegmentLengthMm: Float
    ): WindowBlockTopology {
        val oldCoords = if (isHorizontalAxis) topology.xGridCoords else topology.yGridCoords
        val segCount = oldCoords.size - 1
        if (segmentIndex !in 0 until segCount) return topology
        if (segCount == 1) {
            return if (isHorizontalAxis) {
                resizeTotalDimensions(topology, max(MIN_SEGMENT_MM, newSegmentLengthMm), topology.totalHeightMm)
            } else {
                resizeTotalDimensions(topology, topology.totalWidthMm, max(MIN_SEGMENT_MM, newSegmentLengthMm))
            }
        }

        val lengths = FloatArray(segCount) { i -> oldCoords[i + 1] - oldCoords[i] }
        val totalLen = oldCoords.last() - oldCoords.first()
        val maxAllowed = totalLen - (segCount - 1) * MIN_SEGMENT_MM
        val clampedNew = newSegmentLengthMm.coerceIn(MIN_SEGMENT_MM, max(MIN_SEGMENT_MM, maxAllowed))
        val delta = clampedNew - lengths[segmentIndex]
        lengths[segmentIndex] = clampedNew

        var remainingDelta = delta
        val neighborOrder = ((segmentIndex + 1) until segCount).toList() + ((segmentIndex - 1) downTo 0).toList()
        for (idx in neighborOrder) {
            if (abs(remainingDelta) < 0.5f) break
            val availableReduction = lengths[idx] - MIN_SEGMENT_MM
            if (remainingDelta > 0f) {
                val take = minOf(remainingDelta, availableReduction)
                lengths[idx] -= take
                remainingDelta -= take
            } else {
                lengths[idx] -= remainingDelta
                remainingDelta = 0f
            }
        }

        val newCoords = mutableListOf(oldCoords.first())
        var acc = oldCoords.first()
        for (i in 0 until segCount) {
            acc += lengths[i]
            newCoords.add(if (i == segCount - 1) oldCoords.last() else acc.roundToInt().toFloat())
        }
        return remapAxisCoordinates(topology, isHorizontalAxis, oldCoords, newCoords)
    }

    fun resizeTotalDimensions(
        topology: WindowBlockTopology,
        newWidthMm: Float,
        newHeightMm: Float
    ): WindowBlockTopology {
        val safeW = max(300f, newWidthMm.roundToInt().toFloat())
        val safeH = max(300f, newHeightMm.roundToInt().toFloat())
        val oldX = topology.xGridCoords
        val oldY = topology.yGridCoords
        val oldW = (oldX.lastOrNull() ?: safeW).coerceAtLeast(1f)
        val oldH = (oldY.lastOrNull() ?: safeH).coerceAtLeast(1f)

        val newX = oldX.mapIndexed { idx, x ->
            when (idx) {
                0 -> 0f
                oldX.lastIndex -> safeW
                else -> (x * safeW / oldW).roundToInt().toFloat()
            }
        }
        val newY = oldY.mapIndexed { idx, y ->
            when (idx) {
                0 -> 0f
                oldY.lastIndex -> safeH
                else -> (y * safeH / oldH).roundToInt().toFloat()
            }
        }
        val step1 = remapAxisCoordinates(topology, true, oldX, newX)
        return remapAxisCoordinates(step1, false, oldY, newY)
    }

    private fun remapAxisCoordinates(
        topology: WindowBlockTopology,
        isHorizontalAxis: Boolean,
        oldCoords: List<Float>,
        newCoords: List<Float>
    ): WindowBlockTopology {
        fun mapCoord(v: Float): Float {
            val idx = oldCoords.indexOfFirst { abs(it - v) <= EPS }
            return if (idx >= 0) newCoords[idx] else v
        }

        val updatedFrames = topology.frames.map { frame ->
            val fl = if (isHorizontalAxis) mapCoord(frame.leftMm) else frame.leftMm
            val fr = if (isHorizontalAxis) mapCoord(frame.rightMm) else frame.rightMm
            val ft = if (!isHorizontalAxis) mapCoord(frame.topMm) else frame.topMm
            val fb = if (!isHorizontalAxis) mapCoord(frame.bottomMm) else frame.bottomMm

            val openings = frame.openings.map { op ->
                val nl = if (isHorizontalAxis) mapCoord(op.leftMm) else op.leftMm
                val nr = if (isHorizontalAxis) mapCoord(op.rightMm) else op.rightMm
                val nt = if (!isHorizontalAxis) mapCoord(op.topMm) else op.topMm
                val nb = if (!isHorizontalAxis) mapCoord(op.bottomMm) else op.bottomMm
                val newH = (nb - nt).coerceAtLeast(200f)
                val clampedDoorImp = op.properties.sashImpostHeightFromBottomMm.coerceIn(150f, (newH - 150f).coerceAtLeast(180f))
                op.copy(
                    leftMm = nl,
                    rightMm = nr,
                    topMm = nt,
                    bottomMm = nb,
                    properties = op.properties.copy(sashImpostHeightFromBottomMm = clampedDoorImp)
                )
            }
            val imposts = frame.imposts.map { imp ->
                imp.copy(
                    startX = if (isHorizontalAxis) mapCoord(imp.startX) else imp.startX,
                    endX = if (isHorizontalAxis) mapCoord(imp.endX) else imp.endX,
                    startY = if (!isHorizontalAxis) mapCoord(imp.startY) else imp.startY,
                    endY = if (!isHorizontalAxis) mapCoord(imp.endY) else imp.endY
                )
            }
            frame.copy(
                leftMm = fl,
                rightMm = fr,
                topMm = ft,
                bottomMm = fb,
                openings = openings,
                imposts = imposts
            )
        }
        return topology.copy(frames = updatedFrames)
    }
}
