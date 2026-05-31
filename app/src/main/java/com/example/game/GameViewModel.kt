package com.example.game

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.GameDatabase
import com.example.data.GameRepository
import com.example.data.PlayerProgress
import com.example.data.VoyageHistory
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

enum class ScreenState {
    HANGAR,
    COMBAT,
    SUMMARY,
    HISTORY
}

class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: GameRepository
    val voyageHistory: StateFlow<List<VoyageHistory>>
    val playerProgress: StateFlow<PlayerProgress?>

    // Screen navigation state
    var currentScreen by mutableStateOf(ScreenState.HANGAR)
        private set

    // Active space-voyage interactive coordinates
    var screenWidth = 1080f
    var screenHeight = 1920f

    // Current spaceship gameplay properties
    var playerX by mutableStateOf(540f)
    var playerY by mutableStateOf(1500f)
    var targetPlayerX by mutableStateOf(540f)
    var targetPlayerY by mutableStateOf(1500f)

    // Current player combat stats
    var playerHp by mutableStateOf(100f)
    var playerMaxHp by mutableStateOf(100f)
    var playerShield by mutableStateOf(0f)
    var playerMaxShield by mutableStateOf(0f)
    var isInvincible by mutableStateOf(false)
    var invincibleTimeLeftMs = 0L

    var activeWeaponPowerType by mutableStateOf(1) // 1 = Single, 2 = Dual, 3 = Triple spreading
    var superLaserTimeLeftMs = 0L
    var chronoBoostTimeLeftMs = 0L

    var score by mutableStateOf(0)
    var shardsCollectedSession by mutableStateOf(0)
    var currentSector by mutableStateOf(1)
    var totalDistanceUnits by mutableStateOf(0f)
    var bossFightActive by mutableStateOf(false)
    var bossHp = 0f
    var bossMaxHp = 250f

    // List collections for UI Canvas drawing
    val stars = mutableListOf<AmbientStar>()
    val asteroids = mutableListOf<Asteroid>()
    val lasers = mutableListOf<CombatLaser>()
    val enemies = mutableListOf<SpaceEnemy>()
    val powerUps = mutableListOf<SpacePowerUp>()
    val shards = mutableListOf<CosmicShard>()
    val particles = mutableListOf<VisualParticle>()
    val floatingTexts = mutableListOf<FloatingText>()

    // Game loop control
    private var gameJob: Job? = null
    private val enemyIdCounter = AtomicLong(1)
    private var lastSpawnTime = 0L
    private var lastAsteroidSpawnTime = 0L
    private var combatStartTime = 0L

    // For Summary screen
    var lastVoyageScore = 0
    var lastVoyageShards = 0
    var lastVoyageSector = 1

    init {
        val database = GameDatabase.getDatabase(application)
        repository = GameRepository(database.gameDao())
        voyageHistory = repository.voyageHistory.stateInScope(emptyList())
        playerProgress = repository.playerProgress.stateInScope(null)

        // Generate static star field backdrop
        generateInitialStars()

        // Safely pre-populate the database with player profile if it doesn't exist
        viewModelScope.launch(Dispatchers.IO) {
            repository.getCurrentProgress()
        }
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.stateInScope(initialValue: T): StateFlow<T> {
        return this.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = initialValue
        )
    }

    private fun generateInitialStars() {
        stars.clear()
        val totalStars = 80
        for (i in 0 until totalStars) {
            stars.add(
                AmbientStar(
                    x = (Math.random() * 1200 - 60).toFloat(),
                    y = (Math.random() * 2100 - 100).toFloat(),
                    size = (1.5 + Math.random() * 3.5).toFloat(),
                    speed = (1.5 + Math.random() * 5.5).toFloat()
                )
            )
        }
    }

    // Navigation functions
    fun navigateToHangar() {
        stopGameLoop()
        currentScreen = ScreenState.HANGAR
    }

    fun navigateToHistory() {
        currentScreen = ScreenState.HISTORY
    }

    // Set interactive touch dimensions
    fun updateSurfaceDimensions(width: Float, height: Float) {
        screenWidth = width
        screenHeight = height
        playerY = height * 0.82f
        playerX = width / 2f
        targetPlayerX = playerX
        targetPlayerY = playerY
    }

    fun updatePlayerTarget(x: Float, y: Float) {
        targetPlayerX = x.coerceIn(40f, screenWidth - 40f)
        targetPlayerY = y.coerceIn(100f, screenHeight - 120f)
    }

    // Start a new space combat run
    fun startNewVoyage() {
        viewModelScope.launch(Dispatchers.Default) {
            val progress = repository.getCurrentProgress()
            val shipConfig = SpaceShipConfig.ALL_SHIPS.find { it.id == progress.selectedShip } ?: SpaceShipConfig.ALL_SHIPS[0]

            // Calculate state based on ship attributes and RPG permanent upgrades
            playerMaxHp = shipConfig.baseHp + (progress.maxHpLevel - 1) * 25f
            playerHp = playerMaxHp

            playerMaxShield = shipConfig.baseShield + (progress.shieldLevel) * 20f
            playerShield = playerMaxShield // Shield starts full

            // Setup weapon power type (base weapon)
            activeWeaponPowerType = if (progress.laserLevel >= 5) 2 else 1
            superLaserTimeLeftMs = 0L
            chronoBoostTimeLeftMs = 0L

            // Reset scores
            score = 0
            shardsCollectedSession = 0
            currentSector = 1
            totalDistanceUnits = 0f
            bossFightActive = false

            // Clear combat lists
            asteroids.clear()
            lasers.clear()
            enemies.clear()
            powerUps.clear()
            shards.clear()
            particles.clear()
            floatingTexts.clear()

            playerX = screenWidth / 2f
            playerY = screenHeight * 0.82f
            targetPlayerX = playerX
            targetPlayerY = playerY

            combatStartTime = System.currentTimeMillis()
            lastSpawnTime = combatStartTime
            lastAsteroidSpawnTime = combatStartTime

            // Start 60fps Loop
            currentScreen = ScreenState.COMBAT
            startGameLoop(progress)
        }
    }

    private fun startGameLoop(progress: PlayerProgress) {
        gameJob?.cancel()
        gameJob = viewModelScope.launch(Dispatchers.Default) {
            var frameCount = 0L
            val loopDelay = 16L // ~60fps Physics
            val shipConfig = SpaceShipConfig.ALL_SHIPS.find { it.id == progress.selectedShip } ?: SpaceShipConfig.ALL_SHIPS[0]

            // Movement and speed factors
            val speedFactor = 1f + (progress.thrusterLevel - 1) * 0.08f

            while (currentScreen == ScreenState.COMBAT) {
                val startLoopTime = System.currentTimeMillis()

                // Star scrolling
                val actualScrollSpeed = if (chronoBoostTimeLeftMs > 0) 1.5f else 5f
                for (star in stars) {
                    star.y += star.speed * (actualScrollSpeed / 5f)
                    if (star.y > screenHeight + 20) {
                        star.y = -20f
                        star.x = (Math.random() * screenWidth).toFloat()
                    }
                }

                // Smooth player position interpolation (Thruster leveling speed boost)
                val shipEaser = 0.12f * speedFactor
                playerX += (targetPlayerX - playerX) * shipEaser
                playerY += (targetPlayerY - playerY) * shipEaser

                // Trail emission
                if (frameCount % 4 == 0L) {
                    emitJetEngineParticles(playerX, playerY, shipConfig.color)
                }

                // Temporary buffs expiry ticking
                if (superLaserTimeLeftMs > 0) {
                    superLaserTimeLeftMs -= loopDelay
                    if (superLaserTimeLeftMs <= 0) {
                        activeWeaponPowerType = if (progress.laserLevel >= 5) 2 else 1
                        addFloatingNotification(playerX, playerY - 80, "Laser Buff Expired", Color.Gray)
                    }
                }
                if (chronoBoostTimeLeftMs > 0) {
                    chronoBoostTimeLeftMs -= loopDelay
                    if (chronoBoostTimeLeftMs <= 0) {
                        addFloatingNotification(playerX, playerY - 80, "Chronocells Depleted", Color.Gray)
                    }
                }
                if (isInvincible) {
                    invincibleTimeLeftMs -= loopDelay
                    if (invincibleTimeLeftMs <= 0) {
                        isInvincible = false
                    }
                }

                // Shield passive regeneration
                if (progress.shieldLevel > 0 && frameCount % 60 == 0L) {
                    if (playerShield < playerMaxShield) {
                        // Regular regeneration factor
                        playerShield = (playerShield + 1f + (progress.shieldLevel * 0.5f)).coerceAtMost(playerMaxShield)
                    }
                }

                // Distance calculation
                totalDistanceUnits += 0.1f * (if (chronoBoostTimeLeftMs > 0) 0.5f else 1f)

                // Periodic Shoot Laser automatic emitter
                val baseFireInterval = when {
                    progress.laserLevel >= 8 -> 240L
                    progress.laserLevel >= 3 -> 300L
                    else -> 380L
                }
                val laserInterval = if (superLaserTimeLeftMs > 0) (baseFireInterval * 0.6f).toLong() else baseFireInterval
                if (frameCount % (laserInterval / loopDelay).coerceAtLeast(1) == 0L) {
                    firePlayerLaser(progress)
                }

                // Spawning entities
                handleProceduralSpawning(progress)

                // Update particles, text, lasers, enemies, asteroids, shards
                updateGameEntities(progress)

                // Boundary/Collision check logic
                handlePhysicsCollisions()

                // High-performance screen bounds cleaner
                garbageCollectEntities()

                // Score ticking of distance
                if (frameCount % 60 == 0L) {
                    score += 5
                }

                // Sector check
                val newSec = (score / 1200) + 1
                if (newSec != currentSector) {
                    currentSector = newSec
                    addFloatingNotification(screenWidth / 2f, screenHeight / 3f, "SECTOR $currentSector READY", Color.Green)
                    AudioSynthesizer.playPowerupSound()
                }

                frameCount++
                val timeTaken = System.currentTimeMillis() - startLoopTime
                delay((loopDelay - timeTaken).coerceAtLeast(1L))
            }
        }
    }

    private fun firePlayerLaser(progress: PlayerProgress) {
        val baseDamage = 10f + (progress.laserLevel * 3.5f)
        val bulletColor = Color(0xFF00FFCC)

        // Weapon config structures
        when (activeWeaponPowerType) {
            1 -> {
                // Single central shot
                lasers.add(CombatLaser(playerX, playerY - 35f, 0f, -24f, true, baseDamage, bulletColor))
            }
            2 -> {
                // Dual twin shots
                lasers.add(CombatLaser(playerX - 25f, playerY - 15f, 0f, -24f, true, baseDamage * 0.9f, bulletColor))
                lasers.add(CombatLaser(playerX + 25f, playerY - 15f, 0f, -24f, true, baseDamage * 0.9f, bulletColor))
            }
            else -> {
                // Triple angled shots
                lasers.add(CombatLaser(playerX, playerY - 35f, 0f, -25f, true, baseDamage * 0.85f, bulletColor))
                lasers.add(CombatLaser(playerX - 30f, playerY - 10f, -4f, -22f, true, baseDamage * 0.75f, bulletColor))
                lasers.add(CombatLaser(playerX + 30f, playerY - 10f, 4f, -22f, true, baseDamage * 0.75f, bulletColor))
            }
        }
        AudioSynthesizer.playLaserSound()
    }

    private fun handleProceduralSpawning(progress: PlayerProgress) {
        val now = System.currentTimeMillis()

        // Spawning stars & small meteorites
        if (now - lastAsteroidSpawnTime > 2500 - (currentSector * 150).coerceAtMost(1200)) {
            val asteroidSize = 35f + (Math.random() * 60).toFloat()
            val vx = (-3 + Math.random() * 6).toFloat()
            val vy = (3 + Math.random() * 5).toFloat() + (currentSector * 0.5f)
            asteroids.add(
                Asteroid(
                    x = (Math.random() * screenWidth).toFloat(),
                    y = -80f,
                    radius = asteroidSize,
                    vx = vx,
                    vy = vy,
                    rotation = (Math.random() * 360).toFloat(),
                    rotSpeed = (-4 + Math.random() * 8).toFloat(),
                    hp = asteroidSize * 0.6f + (currentSector * 10f)
                )
            )
            lastAsteroidSpawnTime = now
        }

        // Standard procedural waves spawning
        if (bossFightActive) return // No normal spawns during dynamic Boss

        // Boss spawn checker
        if (score > 0 && score % 1500 in 0..150 && enemies.none { it.type == EnemyClass.BOSS }) {
            triggerBossFight()
            return
        }

        val spawnCooldown = (2800 - (currentSector * 200)).coerceAtLeast(1000).toLong()
        if (now - lastSpawnTime > spawnCooldown) {
            val enemyX = 60f + (Math.random() * (screenWidth - 120f)).toFloat()

            // Choose enemy type based on Sector level percentages
            val roll = Math.random()
            val density = currentSector
            val chosenType = when {
                roll < 0.25f && density >= 3 -> EnemyClass.KAMIKAZE
                roll < 0.50f && density >= 2 -> EnemyClass.GUNNER
                roll < 0.65f && density >= 4 -> EnemyClass.CRUISER
                else -> EnemyClass.SCOUT
            }

            // Create customized space enemy configurations
            val enemy = when (chosenType) {
                EnemyClass.SCOUT -> {
                    SpaceEnemy(
                        id = enemyIdCounter.incrementAndGet(),
                        type = EnemyClass.SCOUT,
                        name = "Rift Scout",
                        x = enemyX,
                        y = -60f,
                        width = 80f,
                        height = 80f,
                        hp = 25f + (currentSector * 8f),
                        maxHp = 25f + (currentSector * 8f),
                        vx = if (Math.random() > 0.5) 2f else -2f,
                        vy = 3.5f + (currentSector * 0.3f),
                        scoreReward = 20,
                        shardReward = 1 + (if (Math.random() > 0.7) 1 else 0),
                        shotIntervalMs = 1800L
                    )
                }
                EnemyClass.GUNNER -> {
                    SpaceEnemy(
                        id = enemyIdCounter.incrementAndGet(),
                        type = EnemyClass.GUNNER,
                        name = "Astra Fighter",
                        x = enemyX,
                        y = -70f,
                        width = 90f,
                        height = 90f,
                        hp = 45f + (currentSector * 12f),
                        maxHp = 45f + (currentSector * 12f),
                        vx = 4f, // sweeping horizontal path
                        vy = 1.8f + (currentSector * 0.1f),
                        scoreReward = 40,
                        shardReward = 2,
                        shotIntervalMs = 1400L
                    )
                }
                EnemyClass.KAMIKAZE -> {
                    SpaceEnemy(
                        id = enemyIdCounter.incrementAndGet(),
                        type = EnemyClass.KAMIKAZE,
                        name = "Seeker Droid",
                        x = enemyX,
                        y = -50f,
                        width = 65f,
                        height = 65f,
                        hp = 15f + (currentSector * 5f),
                        maxHp = 15f + (currentSector * 5f),
                        vx = 0f,
                        vy = 7.5f + (currentSector * 0.5f), // High speed dives
                        scoreReward = 30,
                        shardReward = 1,
                        shotIntervalMs = 999999L // Doesn't shoot, crashes
                    )
                }
                EnemyClass.CRUISER -> {
                    SpaceEnemy(
                        id = enemyIdCounter.incrementAndGet(),
                        type = EnemyClass.CRUISER,
                        name = "Heavy Frigate",
                        x = enemyX,
                        y = -100f,
                        width = 140f,
                        height = 140f,
                        hp = 110f + (currentSector * 30f),
                        maxHp = 110f + (currentSector * 30f),
                        vx = 1f,
                        vy = 1.0f + (currentSector * 0.05f),
                        scoreReward = 90,
                        shardReward = 4,
                        shotIntervalMs = 2200L
                    )
                }
                else -> { // Backup scout
                    SpaceEnemy(
                        id = enemyIdCounter.incrementAndGet(),
                        type = EnemyClass.SCOUT,
                        name = "Scout V2",
                        x = enemyX,
                        y = -60f,
                        width = 80f,
                        height = 80f,
                        hp = 30f,
                        maxHp = 30f,
                        vx = 0f,
                        vy = 4f,
                        scoreReward = 20,
                        shardReward = 1,
                        shotIntervalMs = 2000L
                    )
                }
            }

            enemies.add(enemy)
            lastSpawnTime = now
        }
    }

    private fun triggerBossFight() {
        bossFightActive = true
        bossMaxHp = 250f + (currentSector * 110f)
        bossHp = bossMaxHp

        // Flashing screen banner
        addFloatingNotification(screenWidth / 2f, screenHeight / 3f, "⚠️ WARNING: FLAGSHIP DETECTED ⚠️", Color.Red)
        AudioSynthesizer.playPowerupSound()

        val boss = SpaceEnemy(
            id = enemyIdCounter.incrementAndGet(),
            type = EnemyClass.BOSS,
            name = "Rift Leviathan-V${currentSector}",
            x = screenWidth / 2f - 180f,
            y = -220f,
            width = 360f,
            height = 200f,
            hp = bossHp,
            maxHp = bossMaxHp,
            vx = 2.5f,
            vy = 1.4f, // creep slowly down
            scoreReward = 500,
            shardReward = 30 + (currentSector * 10),
            shotIntervalMs = 1200L
        )
        enemies.add(boss)
    }

    private fun updateGameEntities(progress: PlayerProgress) {
        val now = System.currentTimeMillis()
        val chronoRatio = if (chronoBoostTimeLeftMs > 0) 0.45f else 1.0f

        // 1. Particle Systems Update
        val particleIterator = particles.iterator()
        while (particleIterator.hasNext()) {
            val p = particleIterator.next()
            p.x += p.vx * chronoRatio
            p.y += p.vy * chronoRatio
            p.alpha -= p.decayRate
            p.size -= p.sizeDecay
            if (p.alpha <= 0f || p.size <= 0f) {
                particleIterator.remove()
            }
        }

        // 2. Floating text ticking
        val txtIterator = floatingTexts.iterator()
        while (txtIterator.hasNext()) {
            val t = txtIterator.next()
            t.y += t.vy
            t.life--
            if (t.life <= 0) {
                txtIterator.remove()
            }
        }

        // 3. Movement Asteroid fields
        for (ast in asteroids) {
            ast.x += ast.vx * chronoRatio
            ast.y += ast.vy * chronoRatio
            ast.rotation += ast.rotSpeed * chronoRatio
        }

        // 4. Movement projectile lasers
        for (laser in lasers) {
            laser.x += laser.vx * (if (laser.isPlayerOwned) 1.0f else chronoRatio)
            laser.y += laser.vy * (if (laser.isPlayerOwned) 1.0f else chronoRatio)
        }

        // 5. Space Powerups downward scroll
        for (power in powerUps) {
            power.y += power.vy * chronoRatio
        }

        // 6. Cosmic magnetic Shards
        // Attracted radius depends directly on selected dynamic ship plus persistent upgrades!
        val baseMagnetRange = 120f
        val magnetRange = baseMagnetRange + (progress.magnetLevel * 45f)
        val selectedShip = progress.selectedShip
        val finalMagnetReach = if (selectedShip == "Hyperion-X1") magnetRange * 1.6f else magnetRange

        for (sh in shards) {
            val dx = playerX - sh.x
            val dy = playerY - sh.y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < finalMagnetReach) {
                // Suckle towards player with accelerating gravity speed
                val pullSpeed = (12f + (progress.magnetLevel * 1.5f)).coerceAtMost(30f)
                sh.vx = (dx / dist) * pullSpeed
                sh.vy = (dy / dist) * pullSpeed
            } else {
                sh.vx = 0f
                sh.vy = 4.5f * chronoRatio
            }
            sh.x += sh.vx
            sh.y += sh.vy
        }

        // 7. Active hostile spaceships update
        for (en in enemies) {
            // Apply unique class logic movements
            when (en.type) {
                EnemyClass.BOSS -> {
                    // Creep down to 300px then move side to side
                    if (en.y < 300f) {
                        en.y += en.vy * chronoRatio
                    } else {
                        en.patternState += 0.02f * chronoRatio
                        en.x += en.vx * chronoRatio
                        if (en.x < 30f || en.x > screenWidth - en.width - 30f) {
                            en.vx = -en.vx
                        }
                    }

                    // Boss weapon shooting attacks inside coroutine
                    if (now - en.lastShotTime > en.shotIntervalMs) {
                        fireBossHeavyRing(en)
                        en.lastShotTime = now
                    }
                }
                EnemyClass.SCOUT -> {
                    en.x += en.vx * chronoRatio
                    en.y += en.vy * chronoRatio
                    // horizontal bounce boundaries
                    if (en.x < 10f || en.x > screenWidth - en.width - 10f) {
                        en.vx = -en.vx
                    }

                    if (now - en.lastShotTime > en.shotIntervalMs) {
                        lasers.add(CombatLaser(en.x + en.width/2f, en.y + en.height, 0f, 11f, false, 12f, Color.Red))
                        en.lastShotTime = now
                    }
                }
                EnemyClass.GUNNER -> {
                    // sine wave movement sweep horizontally
                    en.patternState += 0.04f * chronoRatio
                    en.x = (screenWidth / 2f) + sin(en.patternState) * (screenWidth / 2f - 100f)
                    en.y += en.vy * chronoRatio

                    if (now - en.lastShotTime > en.shotIntervalMs) {
                        lasers.add(CombatLaser(en.x + 20f, en.y + en.height, -2f, 13f, false, 15f, Color(0xFFFFCC00)))
                        lasers.add(CombatLaser(en.x + en.width - 20f, en.y + en.height, 2f, 13f, false, 15f, Color(0xFFFFCC00)))
                        en.lastShotTime = now
                    }
                }
                EnemyClass.KAMIKAZE -> {
                    // Tracks horizontal coordinate of the player ship during dive
                    if (en.y < playerY - 300) {
                        val dx = playerX - (en.x + en.width / 2f)
                        if (kotlin.math.abs(dx) > 10) {
                            en.x += (if (dx > 0) 3.5f else -3.5f) * chronoRatio
                        }
                    }
                    en.y += en.vy * chronoRatio
                }
                EnemyClass.CRUISER -> {
                    en.x += en.vx * chronoRatio
                    en.y += en.vy * chronoRatio

                    if (now - en.lastShotTime > en.shotIntervalMs) {
                        // Heavy beam laser
                        lasers.add(CombatLaser(en.x + en.width/2f, en.y + en.height, 0f, 7f, false, 25f, Color(0xFFE040FB), isHeavyBeam = true))
                        en.lastShotTime = now
                    }
                }
            }
        }
    }

    private fun fireBossHeavyRing(boss: SpaceEnemy) {
        val centerX = boss.x + boss.width / 2f
        val centerY = boss.y + boss.height - 20f
        val numBullets = 8 + (currentSector * 2).coerceAtMost(8)
        val bulletSpeed = 7f + currentSector

        for (i in 0 until numBullets) {
            val angle = i * (2.0 * Math.PI / numBullets)
            val vx = (cos(angle) * bulletSpeed).toFloat()
            val vy = (sin(angle) * bulletSpeed).toFloat()
            lasers.add(CombatLaser(centerX, centerY, vx, vy, false, 15f, Color.Red))
        }
        AudioSynthesizer.playLaserSound()
    }

    private fun handlePhysicsCollisions() {
        // 1. Play projectile collisions with asteroid elements
        val laserIterator = lasers.iterator()
        while (laserIterator.hasNext()) {
            val laser = laserIterator.next()
            if (!laser.isPlayerOwned) continue

            // Check details against asteroids
            val astIterator = asteroids.iterator()
            var collided = false
            while (astIterator.hasNext()) {
                val ast = astIterator.next()
                val dx = laser.x - ast.x
                val dy = laser.y - ast.y
                val dist = sqrt(dx * dx + dy * dy)

                if (dist <= ast.radius) {
                    collided = true
                    ast.hp -= laser.damage
                    emitExplosionParticles(laser.x, laser.y, Color.LightGray, count = 5)

                    if (ast.hp <= 0) {
                        emitExplosionParticles(ast.x, ast.y, Color.DarkGray, count = 12)
                        // Drop shards dynamically based on size
                        spawnShardsAt(ast.x, ast.y, value = (ast.radius / 30f).toInt().coerceAtLeast(1))
                        score += (ast.radius * 0.4f).toInt()
                        astIterator.remove()
                        AudioSynthesizer.playExplosionSound()
                    }
                    break
                }
            }

            if (collided) {
                laserIterator.remove()
                continue
            }

            // Check details against enemy ships
            val enemyIterator = enemies.iterator()
            while (enemyIterator.hasNext()) {
                val en = enemyIterator.next()
                if (laser.x in en.x..(en.x + en.width) && laser.y in en.y..(en.y + en.height)) {
                    collided = true
                    en.hp -= laser.damage
                    emitExplosionParticles(laser.x, laser.y, Color(0xFFFF5252), count = 6)
                    addFloatingMessage(laser.x, laser.y - 15, "%.0f".format(laser.damage), Color.Yellow)

                    if (en.hp <= 0) {
                        emitExplosionParticles(en.x + en.width/2f, en.y + en.height/2f, Color(0xFFFF1744), count = 20)
                        addFloatingNotification(en.x + en.width/2f, en.y, "+${en.scoreReward} PTS", Color.White)
                        score += en.scoreReward
                        shardsCollectedSession += en.shardReward
                        spawnShardsAt(en.x + en.width/2f, en.y + en.height/2f, en.shardReward)

                        // Spawning powerups roll (12% chance if normal, 100% chance for Boss defeat!)
                        if (en.type == EnemyClass.BOSS) {
                            bossFightActive = false
                            dropGuaranteedPowerUp(en.x + en.width/2f, en.y + en.height/2f)
                        } else if (Math.random() < 0.12f) {
                            dropRandomPowerUp(en.x + en.width/2f, en.y + en.height/2f)
                        }

                        enemyIterator.remove()
                        AudioSynthesizer.playExplosionSound()
                    }
                    break
                }
            }
            if (collided) {
                laserIterator.remove()
            }
        }

        // 2. Play projectiles collisions against the player craft
        val enemyLaserIterator = lasers.iterator()
        while (enemyLaserIterator.hasNext()) {
            val laser = enemyLaserIterator.next()
            if (laser.isPlayerOwned) continue

            // AABB hit box check player ship
            val dx = laser.x - playerX
            val dy = laser.y - playerY
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < 45f) { // Collision bounds radius 45dp
                enemyLaserIterator.remove()
                damagePlayer(laser.damage)
            }
        }

        // 3. Collision player versus Asteroids or enemy crafts directly (Crash impacts)
        val astIterator = asteroids.iterator()
        while (astIterator.hasNext()) {
            val ast = astIterator.next()
            val dx = playerX - ast.x
            val dy = playerY - ast.y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < ast.radius + 35f) {
                // Take significant impact crash damage
                damagePlayer(ast.radius * 0.7f)
                emitExplosionParticles(ast.x, ast.y, Color.Gray, 15)
                astIterator.remove()
                AudioSynthesizer.playExplosionSound()
            }
        }

        val enemyShipIterator = enemies.iterator()
        while (enemyShipIterator.hasNext()) {
            val en = enemyShipIterator.next()
            val cX = en.x + en.width / 2f
            val cY = en.y + en.height / 2f
            val dx = playerX - cX
            val dy = playerY - cY
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < 50f + (en.width / 3f)) {
                if (en.type == EnemyClass.BOSS) {
                    damagePlayer(45f)
                    en.hp -= 80f // High crash damage to Boss
                } else {
                    damagePlayer(en.hp * 0.8f) // Deal crash damage based on actual ship size
                    emitExplosionParticles(cX, cY, Color.Red, 15)
                    enemyShipIterator.remove()
                }
                AudioSynthesizer.playExplosionSound()
            }
        }

        // 4. Shards picking vacuum collision
        val shardIterator = shards.iterator()
        while (shardIterator.hasNext()) {
            val sh = shardIterator.next()
            val dx = playerX - sh.x
            val dy = playerY - sh.y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < 50f) {
                shardsCollectedSession += sh.value
                addFloatingMessage(sh.x, sh.y, "+${sh.value} CR", Color(0xFFC107))
                shardIterator.remove()
                AudioSynthesizer.playPowerupSound()
            }
        }

        // 5. Powerups picking collision
        val powerIterator = powerUps.iterator()
        while (powerIterator.hasNext()) {
            val p = powerIterator.next()
            val dx = playerX - p.x
            val dy = playerY - p.y
            val dist = sqrt(dx * dx + dy * dy)

            if (dist < 55f) {
                applyPowerUp(p.type)
                powerIterator.remove()
                AudioSynthesizer.playPowerupSound()
            }
        }
    }

    private fun damagePlayer(amount: Float) {
        if (isInvincible) return

        var remainingDmg = amount
        // Apply shield absorption
        if (playerShield > 0f) {
            val absorbed = playerShield.coerceAtMost(remainingDmg)
            playerShield -= absorbed
            remainingDmg -= absorbed
            emitExplosionParticles(playerX, playerY, Color(0xFF00FFCC), 5)
            addFloatingMessage(playerX + 25, playerY - 30, "Aegis Absorb", Color(0xFF00FFCC))
        }

        if (remainingDmg > 0f) {
            playerHp -= remainingDmg
            addFloatingMessage(playerX, playerY - 35, "-%.0f HP".format(remainingDmg), Color.Red)
            emitExplosionParticles(playerX, playerY, Color.Red, 8)

            // Trigger temporary visual immunity frames
            isInvincible = true
            invincibleTimeLeftMs = 800L
        }

        if (playerHp <= 0f) {
            triggerShipDestruction()
        }
    }

    private fun applyPowerUp(type: PowerUpType) {
        when (type) {
            PowerUpType.HEAL_HULL -> {
                val restore = playerMaxHp * 0.45f
                playerHp = (playerHp + restore).coerceAtMost(playerMaxHp)
                addFloatingNotification(playerX, playerY - 90, "Hull Repaired (+45%)", Color.Green)
            }
            PowerUpType.SUPER_LASER -> {
                activeWeaponPowerType = 3 // Set to Triple laser spreading
                superLaserTimeLeftMs = 8000L // 8s
                addFloatingNotification(playerX, playerY - 90, "⚡ SUPER LASER ACTIVE ⚡", Color.Cyan)
            }
            PowerUpType.SHIELD_OVERCHARGE -> {
                playerShield = playerMaxShield
                isInvincible = true
                invincibleTimeLeftMs = 4000L // Immunity 4s
                addFloatingNotification(playerX, playerY - 90, "🛡️ SHIELDS MAX CAPACITATED 🛡️", Color(0xFFE040FB))
            }
            PowerUpType.CHRONO_BOOST -> {
                chronoBoostTimeLeftMs = 7000L // 7s temporal cell
                addFloatingNotification(playerX, playerY - 90, "⏳ TIME WARP: CHRONOCELLS ⏳", Color.Yellow)
            }
        }
    }

    private fun dropRandomPowerUp(x: Float, y: Float) {
        val types = PowerUpType.values()
        val randomType = types[(Math.random() * types.size).toInt()]
        powerUps.add(SpacePowerUp(x, y, randomType))
    }

    private fun dropGuaranteedPowerUp(x: Float, y: Float) {
        powerUps.add(SpacePowerUp(x, y, PowerUpType.SHIELD_OVERCHARGE))
    }

    private fun spawnShardsAt(x: Float, y: Float, value: Int) {
        for (i in 0 until value) {
            shards.add(
                CosmicShard(
                    x = x + (-20 + Math.random() * 40).toFloat(),
                    y = y + (-20 + Math.random() * 40).toFloat(),
                    vx = 0f,
                    vy = 3f,
                    value = 1
                )
            )
        }
    }

    private fun emitExplosionParticles(x: Float, y: Float, color: Color, count: Int) {
        for (i in 0 until count) {
            val angle = Math.random() * 2.0 * Math.PI
            val velocity = (2 + Math.random() * 9).toFloat()
            particles.add(
                VisualParticle(
                    x = x,
                    y = y,
                    vx = (cos(angle) * velocity).toFloat(),
                    vy = (sin(angle) * velocity).toFloat(),
                    color = color,
                    size = (6f + Math.random() * 12f).toFloat(),
                    decayRate = (0.015f + Math.random() * 0.02f).toFloat(),
                    sizeDecay = (0.2f + Math.random() * 0.35f).toFloat()
                )
            )
        }
    }

    private fun emitJetEngineParticles(x: Float, y: Float, shipColor: Color) {
        // Starship trailing flare particles
        val flareColor = if (Math.random() > 0.4) shipColor else Color(0xFFFF5722)
        particles.add(
            VisualParticle(
                x = x + (-8 + Math.random() * 16).toFloat(),
                y = y + 30f,
                vx = (-1.5f + Math.random() * 3.0f).toFloat(),
                vy = (4f + Math.random() * 5.0f).toFloat(),
                color = flareColor,
                size = (5f + Math.random() * 5).toFloat(),
                decayRate = (0.04f + Math.random() * 0.04f).toFloat(),
                sizeDecay = 0.2f
            )
        )
    }

    private fun addFloatingMessage(x: Float, y: Float, msg: String, color: Color) {
        floatingTexts.add(
            FloatingText(
                x = x,
                y = y,
                text = msg,
                color = color,
                vy = -2.5f,
                life = 35
            )
        )
    }

    private fun addFloatingNotification(x: Float, y: Float, msg: String, color: Color) {
        floatingTexts.add(
            FloatingText(
                x = x,
                y = y,
                text = msg,
                color = color,
                vy = -1.2f,
                life = 85
            )
        )
    }

    private fun triggerShipDestruction() {
        stopGameLoop()
        AudioSynthesizer.playExplosionSound()

        // Mass fiery explosion
        emitExplosionParticles(playerX, playerY, Color(0xFFFF3D00), count = 45)
        emitExplosionParticles(playerX, playerY, Color.Yellow, count = 25)

        // Capture metrics
        lastVoyageScore = score
        lastVoyageShards = shardsCollectedSession
        lastVoyageSector = currentSector

        // Persist run details to Room DB asynchronously
        viewModelScope.launch(Dispatchers.Default) {
            val progress = repository.getCurrentProgress()

            // 1. Save voyage run history
            val record = VoyageHistory(
                score = lastVoyageScore,
                shardsCollected = lastVoyageShards,
                sectorReached = lastVoyageSector,
                shipType = progress.selectedShip
            )
            repository.insertVoyage(record)

            // 2. Replenish credits permanently
            repository.addCredits(lastVoyageShards)

            // Switch to game summary review screen
            currentScreen = ScreenState.SUMMARY
        }
    }

    private fun garbageCollectEntities() {
        lasers.removeAll { it.y < -150f || it.y > screenHeight + 150f || it.x < -150f || it.x > screenWidth + 150f }
        enemies.removeAll { it.y > screenHeight + 150f }
        asteroids.removeAll { it.y > screenHeight + 150f || it.x < -150f || it.x > screenWidth + 150f }
        powerUps.removeAll { it.y > screenHeight + 100f }
        shards.removeAll { it.y > screenHeight + 100f }
    }

    private fun stopGameLoop() {
        gameJob?.cancel()
        gameJob = null
    }

    // RPG Shop Operations
    fun buyPermanentUpgrade(statType: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val progress = repository.getCurrentProgress()
            val currentLevel = when (statType) {
                "laser" -> progress.laserLevel
                "shield" -> progress.shieldLevel
                "hp" -> progress.maxHpLevel
                "thruster" -> progress.thrusterLevel
                "magnet" -> progress.magnetLevel
                else -> 0
            }

            if (currentLevel >= 10) return@launch // Maximum level achieved
            val upgradeCost = calculateUpgradeCost(statType, currentLevel)

            if (progress.credits >= upgradeCost) {
                val updated = progress.copy(credits = progress.credits - upgradeCost)
                val finalProgress = when (statType) {
                    "laser" -> updated.copy(laserLevel = updated.laserLevel + 1)
                    "shield" -> updated.copy(shieldLevel = updated.shieldLevel + 1)
                    "hp" -> updated.copy(maxHpLevel = updated.maxHpLevel + 1)
                    "thruster" -> updated.copy(thrusterLevel = updated.thrusterLevel + 1)
                    "magnet" -> updated.copy(magnetLevel = updated.magnetLevel + 1)
                    else -> updated
                }

                repository.saveProgress(finalProgress)
                AudioSynthesizer.playUpgradeSound()
            }
        }
    }

    fun selectShipType(shipId: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val progress = repository.getCurrentProgress()
            val list = progress.unlockedShips.split(",").toSet()
            if (shipId in list) {
                repository.saveProgress(progress.copy(selectedShip = shipId))
                AudioSynthesizer.playUpgradeSound()
            }
        }
    }

    fun unlockShipType(shipConfig: SpaceShipConfig) {
        viewModelScope.launch(Dispatchers.Default) {
            val progress = repository.getCurrentProgress()
            val list = progress.unlockedShips.split(",").toMutableSet()

            if (shipConfig.id !in list && progress.credits >= shipConfig.cost) {
                list.add(shipConfig.id)
                val updated = progress.copy(
                    credits = progress.credits - shipConfig.cost,
                    unlockedShips = list.joinToString(","),
                    selectedShip = shipConfig.id // Auto-equip locked ship
                )
                repository.saveProgress(updated)
                AudioSynthesizer.playUpgradeSound()
            }
        }
    }

    fun clearHistoricalRuns() {
        viewModelScope.launch(Dispatchers.Default) {
            repository.clearHistory()
        }
    }

    // RPG Level cost sizing helper
    fun calculateUpgradeCost(statType: String, currentLevel: Int): Int {
        if (currentLevel >= 10) return 0
        return when (statType) {
            "laser" -> (50 + currentLevel * 35)
            "shield" -> (40 + currentLevel * 30) // Shield level 0 is locked
            "hp" -> (35 + currentLevel * 25)
            "thruster" -> (30 + currentLevel * 20)
            "magnet" -> (25 + currentLevel * 15)
            else -> 999
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopGameLoop()
    }
}
