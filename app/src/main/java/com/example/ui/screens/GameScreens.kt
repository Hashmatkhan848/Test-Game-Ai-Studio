package com.example.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.PlayerProgress
import com.example.data.VoyageHistory
import com.example.game.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sin
import kotlin.math.cos
import androidx.compose.ui.graphics.vector.ImageVector

// Theme helper: Universal Galactic Space Dark Base Background modifier
fun Modifier.galacticSpaceBackground(): Modifier = this.drawBehind {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                Color(0xFF090614), // Deep violet cosmic
                Color(0xFF04060E)  // Starry black void
            )
        )
    )
}

@Composable
fun HangarScreen(
    viewModel: GameViewModel,
    progress: PlayerProgress,
    onStartVoyage: () -> Unit,
    onNavigateToHistory: () -> Unit
) {
    var selectedShipIndex by remember { mutableStateOf(0) }
    val shipConfigs = SpaceShipConfig.ALL_SHIPS
    val activeShipConfig = shipConfigs[selectedShipIndex]

    val unlockedShipList = remember(progress.unlockedShips) {
        progress.unlockedShips.split(",").toSet()
    }
    val isShipUnlocked = activeShipConfig.id in unlockedShipList
    val isShipEquipped = progress.selectedShip == activeShipConfig.id

    // Twinkling background stars logic for depth
    val starColorPulse = rememberInfiniteTransition(label = "").animateFloat(
        initialValue = 0.3f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .galacticSpaceBackground()
            .statusBarsPadding()
            .navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Star Hangar Twinkle canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Color.Cyan.copy(alpha = starColorPulse.value))
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Title Header
        Text(
            text = "GALACTIC RIFT",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF00FFCC),
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            text = "ROGUE VOYAGER WORKSHOP",
            fontSize = 11.sp,
            color = Color.LightGray.copy(alpha = 0.72f),
            letterSpacing = 2.sp,
            fontFamily = FontFamily.SansSerif,
            textAlign = TextAlign.Center
        )

        // Shards Ledger / Cosmic Currency Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF16152B)),
            border = BorderStroke(1.2.dp, Color(0xFFFFC107).copy(alpha = 0.7f)),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .padding(16.dp)
                .widthIn(max = 450.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Cosmic Currency Symbol",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "COSMIC SHARDS",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${progress.credits}",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFFFFEE55),
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "RECORD HIGH SCORE",
                        fontSize = 10.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${progress.highScore}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00FFCC),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Sub Scrollable Shop details
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Ship Carousel selector section
            item {
                Text(
                    text = "SELECT ACTIVE SPACESHIP",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    textAlign = TextAlign.Start
                )

                // Carousel tab layout
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shipConfigs.forEachIndexed { idx, config ->
                        val active = idx == selectedShipIndex
                        val isEquipped = progress.selectedShip == config.id
                        val isUnlocked = config.id in unlockedShipList

                        Box(
                            modifier = Modifier
                                .width(125.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (active) Color(0xFF221F45) else Color(0xFF131121))
                                .border(
                                    1.5.dp,
                                    if (active) config.color else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedShipIndex = idx }
                                .padding(8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Draw Ship Icon Vector directly on Canvas
                                Canvas(modifier = Modifier.size(50.dp)) {
                                    val w = size.width
                                    val h = size.height
                                    val cX = w / 2f
                                    val cY = h / 2f

                                    // Simple custom ship shapes representing interstellar fighter
                                    val pWidth = w * 0.5f
                                    val pHeight = h * 0.7f

                                    val drawPath = Path().apply {
                                        moveTo(cX, cY - pHeight / 2)
                                        lineTo(cX + pWidth / 2, cY + pHeight / 2)
                                        lineTo(cX + pWidth * 0.2f, cY + pHeight * 0.2f)
                                        lineTo(cX, cY + pHeight * 0.35f)
                                        lineTo(cX - pWidth * 0.2f, cY + pHeight * 0.2f)
                                        lineTo(cX - pWidth / 2, cY + pHeight / 2)
                                        close()
                                    }
                                    drawPath(drawPath, color = config.color)
                                    // Engine flare circle
                                    drawCircle(
                                        Color(0xFFE040FB),
                                        radius = 6f,
                                        center = Offset(cX, cY + pHeight / 2 - 5f)
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = config.name,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.White else Color.Gray,
                                    fontFamily = FontFamily.SansSerif,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(modifier = Modifier.height(2.dp))
                                if (isEquipped) {
                                    Text(
                                        text = "EQUIPPED",
                                        fontSize = 8.sp,
                                        color = Color(0xFF00FFCC),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                } else if (!isUnlocked) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Locked",
                                            tint = Color(0xFFFFC107),
                                            modifier = Modifier.size(8.dp)
                                        )
                                        Spacer(modifier = Modifier.width(1.dp))
                                        Text(
                                            text = "${config.cost}",
                                            fontSize = 9.sp,
                                            color = Color(0xFFFFC107)
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "OWNED",
                                        fontSize = 8.sp,
                                        color = Color.LightGray
                                    )
                                }
                            }
                        }
                    }
                }

                // Active ship specifications details card
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16152B)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, activeShipConfig.color.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .widthIn(max = 450.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = activeShipConfig.name,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = activeShipConfig.color,
                                fontFamily = FontFamily.Monospace
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(activeShipConfig.color.copy(alpha = 0.2f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (isShipEquipped) "EQUIPPED" else if (isShipUnlocked) "READY" else "LOCKED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = activeShipConfig.color
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = activeShipConfig.description,
                            fontSize = 11.sp,
                            color = Color.LightGray,
                            lineHeight = 15.sp,
                            fontFamily = FontFamily.SansSerif
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Progress attribute meters
                        AttributeBar(name = "Shield Armor Rating", valRatio = activeShipConfig.baseShield / 100f, color = Color(0xFF00FFCC))
                        AttributeBar(name = "Hull Metal Integrity", valRatio = activeShipConfig.baseHp / 180f, color = Color(0xFFFF1744))
                        AttributeBar(name = "Propulsion Acceleration", valRatio = activeShipConfig.baseSpeed / 16f, color = Color(0xFFFFD600))

                        Spacer(modifier = Modifier.height(12.dp))

                        // Equip or Buy action button
                        Button(
                            onClick = {
                                if (isShipUnlocked) {
                                    viewModel.selectShipType(activeShipConfig.id)
                                } else {
                                    viewModel.unlockShipType(activeShipConfig)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isShipEquipped) Color.Green.copy(alpha = 0.24f) else activeShipConfig.color,
                                disabledContainerColor = Color.DarkGray
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(44.dp)
                                .testTag("ship_buy_equip_btn"),
                            enabled = isShipEquipped || isShipUnlocked || progress.credits >= activeShipConfig.cost
                        ) {
                            if (isShipEquipped) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Check, "Active ship config equipped", tint = Color.Green, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("EQUIPMENT ACTIVE", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                            } else if (isShipUnlocked) {
                                Text("ACTIVATE SPACESHIP", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            } else {
                                Icon(Icons.Default.Lock, "Buy lock", modifier = Modifier.size(14.dp), tint = Color.White)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PURCHASE SHIP (${activeShipConfig.cost} SHARDS)", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            // Permanent Upgrades buying ledger list section
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PERMANENT SHIP UPGRADES",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Specific Upgrade Items Lists
            val upgradeList = listOf(
                UpgradeItemSpecs("Laser Cannons", "laser", progress.laserLevel, Icons.Default.PlayArrow, Color(0xFF00FFCC)),
                UpgradeItemSpecs("Aegis Forcefield", "shield", progress.shieldLevel, Icons.Default.Shield, Color(0xFFE040FB)),
                UpgradeItemSpecs("Hull Nano-Plates", "hp", progress.maxHpLevel, Icons.Default.Favorite, Color(0xFFFF1744)),
                UpgradeItemSpecs("Thruster Overdrives", "thruster", progress.thrusterLevel, Icons.Default.Speed, Color(0xFFFFD600)),
                UpgradeItemSpecs("Gravity Shard Magnet", "magnet", progress.magnetLevel, Icons.Default.Star, Color(0xFFFF9100))
            )

            items(upgradeList) { upgrade ->
                HangarUpgradeRow(
                    specs = upgrade,
                    currentCredits = progress.credits,
                    calculateCost = { curLvl -> viewModel.calculateUpgradeCost(upgrade.key, curLvl) },
                    onUpgradeTriggered = { viewModel.buyPermanentUpgrade(upgrade.key) }
                )
            }

            // Bottom space buffer
            item {
                Spacer(modifier = Modifier.height(110.dp))
            }
        }
    }

    // Centered Fixed bottom navigation controls container
    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xDF070114)
                        )
                    )
                )
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 450.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main History ledger shortcut Button
                Button(
                    onClick = onNavigateToHistory,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1C1A30)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(0.35f)
                        .height(52.dp)
                        .testTag("historical_record_btn"),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Navigate Past Runs History",
                        tint = Color.LightGray,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Dynamic large floating launch into battle active run button
                Button(
                    onClick = {
                        AudioSynthesizer.playPowerupSound()
                        onStartVoyage()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(0.65f)
                        .height(52.dp)
                        .testTag("launch_combat_btn"),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Action Launch Ship into Galaxy",
                            tint = Color.Black,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "LAUNCH SHIP",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

// Visual Attributes stats bars
@Composable
fun AttributeBar(name: String, valRatio: Float, color: Color) {
    Column(modifier = Modifier.padding(vertical = 3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = name, fontSize = 9.sp, color = Color.Gray)
            Text(text = "${(valRatio * 100f).toInt()}%", fontSize = 9.sp, color = color, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.08f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(valRatio.coerceIn(0.01f, 1f))
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

data class UpgradeItemSpecs(
    val title: String,
    val key: String,
    val level: Int,
    val icon: ImageVector,
    val tintColor: Color
)

@Composable
fun HangarUpgradeRow(
    specs: UpgradeItemSpecs,
    currentCredits: Int,
    calculateCost: (Int) -> Int,
    onUpgradeTriggered: () -> Unit
) {
    val maxed = specs.level >= 10
    val cost = calculateCost(specs.level)
    val canAfford = currentCredits >= cost && !maxed

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF131121)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (maxed) specs.tintColor.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f)),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 450.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(0.58f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(specs.tintColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = specs.icon,
                        contentDescription = specs.title,
                        tint = specs.tintColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = specs.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Level tick box display (10 ticks)
                        for (i in 1..10) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 1.dp)
                                    .size(width = 6.dp, height = 8.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(
                                        if (i <= specs.level) specs.tintColor else Color.White.copy(
                                            alpha = 0.1f
                                        )
                                    )
                            )
                        }
                    }
                }
            }

            // Upgrade clickable button component
            Button(
                onClick = onUpgradeTriggered,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF282544),
                    disabledContainerColor = Color(0x3B282544)
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                enabled = canAfford,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(0.42f)
                    .height(38.dp)
                    .testTag("upgrade_${specs.key}_btn")
            ) {
                if (maxed) {
                    Text(
                        text = "MAX LEVEL",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Spend Shard icon spacer",
                            tint = if (canAfford) Color(0xFFFFC107) else Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "UPGRADE ($cost)",
                            color = if (canAfford) Color.White else Color.Gray,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun SpaceCombatScreen(
    viewModel: GameViewModel,
    progress: PlayerProgress,
    onGameTerminated: () -> Unit
) {
    val density = LocalDensity.current

    // Observe gameplay configurations
    val stars_ = viewModel.stars
    val asteroids_ = viewModel.asteroids
    val lasers_ = viewModel.lasers
    val enemies_ = viewModel.enemies
    val powerUps_ = viewModel.powerUps
    val shards_ = viewModel.shards
    val particles_ = viewModel.particles
    val floatingTexts_ = viewModel.floatingTexts

    // Starship configuration
    val selectedShip = progress.selectedShip

    Box(
        modifier = Modifier
            .fillMaxSize()
            .galacticSpaceBackground()
            .pointerInput(Unit) {
                // Touch to Drag starship logic
                detectDragGestures(
                    onDragStart = { offset ->
                        viewModel.updatePlayerTarget(offset.x, offset.y)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        viewModel.updatePlayerTarget(
                            viewModel.targetPlayerX + dragAmount.x,
                            viewModel.targetPlayerY + dragAmount.y
                        )
                    }
                )
            }
            .onSizeChanged { size ->
                // Feed Surface resolution dimensions up to physics engine state
                viewModel.updateSurfaceDimensions(size.width.toFloat(), size.height.toFloat())
            }
            .testTag("combat_space_interaction_area")
    ) {

        // Canvas element drawing dynamic structures in high-fidelity
        Canvas(modifier = Modifier.fillMaxSize()) {
            // 1. STARS PARALLAX BACKPLANE
            for (star in stars_) {
                drawCircle(
                    color = Color.White.copy(alpha = star.alpha),
                    radius = star.size,
                    center = Offset(star.x, star.y)
                )
            }

            // 2. POWER UPS SPHERES DRAWING
            for (power in powerUps_) {
                val pillColor = when (power.type) {
                    PowerUpType.HEAL_HULL -> Color(0xFF4CAF50)
                    PowerUpType.SUPER_LASER -> Color(0xFF00E5FF)
                    PowerUpType.SHIELD_OVERCHARGE -> Color(0xFFE040FB)
                    PowerUpType.CHRONO_BOOST -> Color(0xFFFFD600)
                }

                // Shimmer glow pulse
                drawCircle(
                    color = pillColor.copy(alpha = 0.2f),
                    radius = power.size + 12f * sin(System.currentTimeMillis() / 150f).toFloat(),
                    center = Offset(power.x, power.y)
                )
                // Main capsule sphere
                drawCircle(
                    color = pillColor,
                    radius = power.size * 0.72f,
                    center = Offset(power.x, power.y)
                )
                // Draw inner white core symbol (cross check for representation)
                drawLine(
                    color = Color.White,
                    start = Offset(power.x - 8f, power.y),
                    end = Offset(power.x + 8f, power.y),
                    strokeWidth = 3f
                )
                drawLine(
                    color = Color.White,
                    start = Offset(power.x, power.y - 8f),
                    end = Offset(power.x, power.y + 8f),
                    strokeWidth = 3f
                )
            }

            // 3. COSMIC MAGNETIC SHARDS
            for (shard in shards_) {
                val p = Path().apply {
                    moveTo(shard.x, shard.y - shard.size * 0.8f)
                    lineTo(shard.x + shard.size * 0.6f, shard.y)
                    lineTo(shard.x, shard.y + shard.size * 0.8f)
                    lineTo(shard.x - shard.size * 0.6f, shard.y)
                    close()
                }
                drawPath(p, color = Color(0xFFFFC107))
                drawCircle(Color.White, radius = 4f, center = Offset(shard.x, shard.y))
            }

            // 4. ASTEROID FLOATING FIELDS
            for (ast in asteroids_) {
                drawCircle(
                    color = Color(0xFF535561),
                    radius = ast.radius,
                    center = Offset(ast.x, ast.y)
                )
                // Draw rotating inner crater rings
                drawCircle(
                    color = Color(0xFF33353F),
                    radius = ast.radius * 0.28f,
                    center = Offset(
                        ast.x + ast.radius * 0.35f * sin(ast.rotation / 57f).toFloat(),
                        ast.y + ast.radius * 0.35f * cos(ast.rotation / 57f).toFloat()
                    )
                )
                drawCircle(
                    color = Color(0xFF2E303A),
                    radius = ast.radius,
                    center = Offset(ast.x, ast.y),
                    style = Stroke(width = 2.5f)
                )
            }

            // 5. PROJECTILE LASERS
            for (laser in lasers_) {
                val stW = if (laser.isHeavyBeam) 16f else 7f
                val len = if (laser.isHeavyBeam) 60f else 32f
                val orientation = if (laser.vy < 0) -1 else 1

                drawLine(
                    color = laser.color,
                    start = Offset(laser.x, laser.y),
                    end = Offset(laser.x, laser.y + (orientation * len)),
                    strokeWidth = stW,
                    cap = StrokeCap.Round
                )
                // core white heat
                drawLine(
                    color = Color.White,
                    start = Offset(laser.x, laser.y),
                    end = Offset(laser.x, laser.y + (orientation * len * 0.65f)),
                    strokeWidth = stW * 0.42f,
                    cap = StrokeCap.Round
                )
            }

            // 6. ADVERSARY HOSTILE SHIPS
            for (en in enemies_) {
                val enemyColor = when (en.type) {
                    EnemyClass.SCOUT -> Color(0xFFFF2E54)
                    EnemyClass.GUNNER -> Color(0xFFFF6900)
                    EnemyClass.KAMIKAZE -> Color(0xFFFFCC00)
                    EnemyClass.CRUISER -> Color(0xFFA200FF)
                    EnemyClass.BOSS -> Color(0xFFFF0066)
                }

                when (en.type) {
                    EnemyClass.SCOUT -> {
                        val path = Path().apply {
                            moveTo(en.x + en.width * 0.5f, en.y + en.height) // Tip
                            lineTo(en.x + en.width, en.y + en.height * 0.1f)
                            lineTo(en.x + en.width * 0.8f, en.y)
                            lineTo(en.x + en.width * 0.5f, en.y + en.height * 0.25f)
                            lineTo(en.x + en.width * 0.2f, en.y)
                            lineTo(en.x, en.y + en.height * 0.1f)
                            close()
                        }
                        drawPath(path, color = enemyColor)
                    }
                    EnemyClass.GUNNER -> {
                        val path = Path().apply {
                            moveTo(en.x, en.y)
                            lineTo(en.x + en.width * 0.2f, en.y + en.height * 0.5f)
                            lineTo(en.x + en.width * 0.5f, en.y + en.height)
                            lineTo(en.x + en.width * 0.8f, en.y + en.height * 0.5f)
                            lineTo(en.x + en.width, en.y)
                            lineTo(en.x + en.width * 0.5f, en.y + en.height * 0.35f)
                            close()
                        }
                        drawPath(path, color = enemyColor)
                        // Glowing laser barrels left right
                        drawRect(Color.Yellow, Offset(en.x + 5f, en.y), androidx.compose.ui.geometry.Size(10f, 15f))
                        drawRect(Color.Yellow, Offset(en.x + en.width - 15f, en.y), androidx.compose.ui.geometry.Size(10f, 15f))
                    }
                    EnemyClass.KAMIKAZE -> {
                        val path = Path().apply {
                            moveTo(en.x + en.width / 2f, en.y + en.height)
                            lineTo(en.x + en.width, en.y + en.height * 0.2f)
                            lineTo(en.x + en.width * 0.5f, en.y)
                            lineTo(en.x, en.y + en.height * 0.2f)
                            close()
                        }
                        drawPath(path, color = enemyColor)
                        // Fusion engine sparks
                        drawCircle(Color.Red, radius = 5f, center = Offset(en.x + en.width / 2f, en.y + 4f))
                    }
                    EnemyClass.CRUISER -> {
                        val path = Path().apply {
                            moveTo(en.x + en.width * 0.5f, en.y + en.height) // main cannon beak
                            lineTo(en.x + en.width, en.y + en.height * 0.72f) // wings fold back
                            lineTo(en.x + en.width * 0.75f, en.y) // base
                            lineTo(en.x + en.width * 0.25f, en.y)
                            lineTo(en.x, en.y + en.height * 0.72f)
                            close()
                        }
                        drawPath(path, color = enemyColor)
                        drawCircle(Color.White.copy(alpha = 0.6f), radius = 10f, center = Offset(en.x + en.width / 2f, en.y + en.height * 0.45f))
                    }
                    EnemyClass.BOSS -> {
                        // Flagship design draw
                        val path = Path().apply {
                            moveTo(en.x + en.width * 0.5f, en.y + en.height) // central nose
                            lineTo(en.x + en.width * 0.85f, en.y + en.height * 0.78f)
                            lineTo(en.x + en.width, en.y + en.height * 0.22f) // broad right wing flange
                            lineTo(en.x + en.width * 0.7f, en.y) // base engines
                            lineTo(en.x + en.width * 0.5f, en.y + en.height * 0.25f) // spinal trench
                            lineTo(en.x + en.width * 0.3f, en.y) // base engine left
                            lineTo(en.x, en.y + en.height * 0.22f)
                            lineTo(en.x + en.width * 0.15f, en.y + en.height * 0.78f)
                            close()
                        }
                        drawPath(path, color = enemyColor)

                        // Central core glowing reactor
                        drawCircle(
                            color = Color(0xFF00FFCC),
                            radius = 15f + 4f * sin(System.currentTimeMillis() / 100f).toFloat(),
                            center = Offset(en.x + en.width / 2f, en.y + en.height * 0.5f)
                        )
                    }
                }

                // Show enemy HP bar layout
                if (en.hp < en.maxHp) {
                    val progressRatio = en.hp / en.maxHp
                    val barWidth = en.width * 0.75f
                    val sX = en.x + (en.width - barWidth) / 2f
                    val sY = en.y - 12f

                    drawLine(
                        color = Color.DarkGray,
                        start = Offset(sX, sY),
                        end = Offset(sX + barWidth, sY),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = if (en.type == EnemyClass.BOSS) Color(0xFF00E5FF) else Color(0xFFFF1744),
                        start = Offset(sX, sY),
                        end = Offset(sX + barWidth * progressRatio, sY),
                        strokeWidth = 4f,
                        cap = StrokeCap.Round
                    )
                }
            }

            // 7. MAIN INTERACTIVE PLAYER SPACESHIP
            val shipConf = SpaceShipConfig.ALL_SHIPS.find { it.id == selectedShip } ?: SpaceShipConfig.ALL_SHIPS[0]
            val shipColor = shipConf.color

            val shipPath = Path().apply {
                moveTo(viewModel.playerX, viewModel.playerY - 45f) // Nose
                lineTo(viewModel.playerX + 36f, viewModel.playerY + 28f) // Right wing
                lineTo(viewModel.playerX + 14f, viewModel.playerY + 10f) // Spinal ridge
                lineTo(viewModel.playerX, viewModel.playerY + 20f) // center base
                lineTo(viewModel.playerX - 14f, viewModel.playerY + 10f) // Spinal ridge left
                lineTo(viewModel.playerX - 36f, viewModel.playerY + 28f) // Left wing
                close()
            }
            drawPath(shipPath, color = shipColor)

            // Cabin glass bubble glow
            drawCircle(
                color = Color.White.copy(alpha = 0.88f),
                radius = 11f,
                center = Offset(viewModel.playerX, viewModel.playerY - 10f)
            )

            // Immunity glowing outline
            if (viewModel.isInvincible) {
                drawPath(
                    shipPath,
                    color = Color.White.copy(alpha = 0.5f + (0.4f * sin(System.currentTimeMillis() / 60f).toFloat())),
                    style = Stroke(width = 6f)
                )
            }

            // Translucent glowing shield circle
            if (viewModel.playerShield > 0f) {
                val shieldRatio = viewModel.playerShield / viewModel.playerMaxShield
                val pulsatingAlpha = (0.22f + 0.28f * shieldRatio + 0.1f * sin(System.currentTimeMillis() / 90f).toFloat()).coerceIn(0f, 1f)
                drawCircle(
                    color = Color(0xFF00FFF8).copy(alpha = pulsatingAlpha),
                    radius = 68f,
                    center = Offset(viewModel.playerX, viewModel.playerY - 10f),
                    style = Stroke(width = 4f + (shieldRatio * 3.5f))
                )
            }

            // 8. EXPLOSION DRIFTING PARTICLES DRAWING
            for (p in particles_) {
                drawCircle(
                    color = p.color.copy(alpha = p.alpha),
                    radius = p.size,
                    center = Offset(p.x, p.y)
                )
            }
        }

        // Floating overlay message alerts directly over the Canvas (HP damage, dynamic triggers)
        floatingTexts_.forEach { t ->
            Text(
                text = t.text,
                color = t.color.copy(alpha = t.alpha),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.offset(
                    x = with(density) { t.x.toDp() } - 30.dp,
                    y = with(density) { t.y.toDp() }
                )
            )
        }

        // Full Screen Top/Bottom Heads Up Displays overlay
        HUDOverlay(viewModel = viewModel)
    }
}

// Full top-bottom overlay metrics displays
@Composable
fun HUDOverlay(viewModel: GameViewModel) {
    val healthPercent = (viewModel.playerHp / viewModel.playerMaxHp).coerceIn(0f, 1f)
    val shieldPercent = if (viewModel.playerMaxShield > 0f) (viewModel.playerShield / viewModel.playerMaxShield).coerceIn(0f, 1f) else 0f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(14.dp)
    ) {
        // TOP HALF DISPLAY HUD
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sector & Core Score Indicator Column
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "SECTOR ${viewModel.currentSector}",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00FFCC),
                    fontFamily = FontFamily.Monospace
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "SCORE: ",
                        fontSize = 11.sp,
                        color = Color.LightGray,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "${viewModel.score}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Session coins collected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.06f))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Cosmic Currency icon",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${viewModel.shardsCollectedSession}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFFFFEE55),
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // MIDDLE NOTIFICATION BANNERS (Boss Warnings)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (viewModel.bossFightActive) {
                // Flashing red warning system
                val scaleFactor = rememberInfiniteTransition(label = "").animateFloat(
                    initialValue = 0.9f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(400, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ), label = ""
                )

                Box(
                    modifier = Modifier
                        .scale(scaleFactor.value)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.Red.copy(alpha = 0.85f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "⚠️ ADVERSARY FLAGSHIP ENGAGED ⚠️",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // BOTTOM HUD PANEL (HP/Shield Gauges, active buffs)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Show Active Weapon Buff indicators
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (viewModel.superLaserTimeLeftMs > 0) {
                    StatusPillBadge(text = "🔥 SUPER PULSE", color = Color(0xFF00FFCC))
                }
                if (viewModel.chronoBoostTimeLeftMs > 0) {
                    StatusPillBadge(text = "⏳ CHRONOWARP", color = Color(0xFFFFD600))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 450.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Starship health armor bar segment
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Favorite, "Hull points HP icon", tint = Color(0xFFFF1744), modifier = Modifier.size(11.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(text = "HULL INTEGRITY", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Text(text = "${(healthPercent * 100f).toInt()}%", fontSize = 9.sp, color = Color(0xFFFF1744), fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(healthPercent)
                                .clip(RoundedCornerShape(4.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        colors = listOf(Color(0xFFCC1100), Color(0xFFFF1744))
                                    )
                                )
                        )
                    }
                }

                // Aegis Shield overlay segment
                if (viewModel.playerMaxShield > 0f) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, "Forcefield Shield icon", tint = Color(0xFF00FFCC), modifier = Modifier.size(11.dp))
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(text = "AEGIS ENERGY", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            Text(text = "${(shieldPercent * 100f).toInt()}%", fontSize = 9.sp, color = Color(0xFF00FFCC), fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(10.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(shieldPercent)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            colors = listOf(Color(0xFF00B0FF), Color(0xFF00FFCC))
                                        )
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}

// Extension to scale components easily
fun Modifier.scale(scale: Float) = this.then(
    Modifier.graphicsLayer(scaleX = scale, scaleY = scale)
)

@Composable
fun StatusPillBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.22f))
            .border(1.dp, color.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            fontFamily = FontFamily.Monospace
        )
    }
}


@Composable
fun GameSummaryScreen(
    viewModel: GameViewModel,
    onReturnToHangar: () -> Unit,
    onLaunchAgain: () -> Unit
) {
    val scaleAnim = remember { Animatable(0.7f) }
    LaunchedEffect(Unit) {
        scaleAnim.animateTo(1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .galacticSpaceBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Column(
            modifier = Modifier
                .scale(scaleAnim.value)
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF16152B))
                .border(1.5.dp, Color(0xFFFF1744).copy(alpha = 0.6f), RoundedCornerShape(24.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF1744).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = "Voyage end warning cap status icon",
                    tint = Color(0xFFFF1744),
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "VOYAGE TERMINATED",
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = "Your spaceship was destroyed in hyper-void combat",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )

            HorizontalDivider(
                color = Color.White.copy(alpha = 0.08f),
                modifier = Modifier.padding(vertical = 18.dp)
            )

            // Results Table Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                SummaryDataBox(lbl = "FINAL SCORE", value = "${viewModel.lastVoyageScore}", highlight = Color(0xFF00FFCC))
                SummaryDataBox(lbl = "SHARDS RETRIEVED", value = "+${viewModel.lastVoyageShards}", highlight = Color(0xFFFFC107))
                SummaryDataBox(lbl = "SECTOR CLEARED", value = "SEC ${viewModel.lastVoyageSector}", highlight = Color(0xFFE040FB))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Cosmic shards collected during voyages are credited to your persistent storage. Return to the Workshop Hangar to acquire permanent spacecraft improvements and unlock dynamic fleet ships.",
                    fontSize = 10.sp,
                    color = Color.LightGray.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 14.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onReturnToHangar,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.15f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("summary_back_hangar_btn")
                ) {
                    Text("WORKSHOP", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Button(
                    onClick = {
                        AudioSynthesizer.playPowerupSound()
                        onLaunchAgain()
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FFCC)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("summary_relaunch_btn")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Refresh, "Launch spacecraft again into combat", tint = Color.Black, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(3.dp))
                        Text("RELAUNCH", fontSize = 11.sp, color = Color.Black, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryDataBox(lbl: String, value: String, highlight: Color) {
    Column(
        modifier = Modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = lbl, fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Black, color = highlight, fontFamily = FontFamily.Monospace)
    }
}


@Composable
fun HistoryScreen(
    voyages: List<VoyageHistory>,
    onBack: () -> Unit,
    onClearHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .galacticSpaceBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onBack,
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = Color.White.copy(alpha = 0.08f)),
                modifier = Modifier.size(40.dp).testTag("history_back_btn")
            ) {
                Icon(Icons.Default.ArrowBack, "Navigate back screen from history", tint = Color.White)
            }

            Text(
                text = "VOYAGE ARCHIVE",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontFamily = FontFamily.Monospace
            )

            // Clear history button if not empty
            if (voyages.isNotEmpty()) {
                IconButton(
                    onClick = onClearHistory,
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Icon(Icons.Default.Delete, "Delete History Record", tint = Color(0xFFFF1744))
                }
            } else {
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // History summary overview
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131121)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .widthIn(max = 450.dp)
                .align(Alignment.CenterHorizontally),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                VerticalStatMetric(label = "TOTAL RUNS", value = "${voyages.size}")
                VerticalStatMetric(label = "RECORD SCORE", value = "${voyages.maxOfOrNull { it.score } ?: 0}")
                VerticalStatMetric(label = "TOTAL SHARDS", value = "${voyages.sumOf { it.shardsCollected }}")
            }
        }

        if (voyages.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.04f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Star, "Celestial Placeholder Star decoration", tint = Color.DarkGray, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ARCHIVE SECURED",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "No recorded runs yet deep in sector files.",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .widthIn(max = 450.dp)
                    .align(Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(voyages) { run ->
                    HistoryLogCard(run = run)
                }
            }
        }
    }
}

@Composable
fun VerticalStatMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, fontSize = 8.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun HistoryLogCard(run: VoyageHistory) {
    val dateString = remember(run.timestamp) {
        val formatter = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        formatter.format(Date(run.timestamp))
    }

    val shipConfig = remember(run.shipType) {
        SpaceShipConfig.ALL_SHIPS.find { it.id == run.shipType } ?: SpaceShipConfig.ALL_SHIPS[0]
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF16152B)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Colored beacon spacer representing spacecraft type utilized
                Box(
                    modifier = Modifier
                        .size(4.dp, 34.dp)
                        .clip(CircleShape)
                        .background(shipConfig.color)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "Cleared SEC ${run.sectorReached}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$dateString • ${shipConfig.name}",
                        fontSize = 9.sp,
                        color = Color.Gray
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Historical score",
                        tint = Color(0xFF00FFCC),
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "${run.score}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Cosmic Currency shards extracted spacer",
                        tint = Color(0xFFFFC107),
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "+${run.shardsCollected} Cr",
                        fontSize = 10.sp,
                        color = Color(0xFFFFC107),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
