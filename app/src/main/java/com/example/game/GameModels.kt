package com.example.game

import androidx.compose.ui.graphics.Color

// Spaceship designs
data class SpaceShipConfig(
    val id: String,
    val name: String,
    val description: String,
    val baseHp: Float,
    val baseSpeed: Float,
    val baseShield: Float,
    val color: Color,
    val cost: Int // Shards needed to unlock
) {
    companion object {
        val ALL_SHIPS = listOf(
            SpaceShipConfig(
                id = "Stinger-X",
                name = "Stinger-X",
                description = "Nimble Recon Scout. Rapid-fire dual pulse laser canons with excellent handling.",
                baseHp = 100f,
                baseSpeed = 16f,
                baseShield = 30f,
                color = Color(0xFF00E5FF),
                cost = 0
            ),
            SpaceShipConfig(
                id = "Vanguard-III",
                name = "Vanguard-III",
                description = "Heavy Guard Sentinel. Enveloped in high-capacity Aegis energy shielding. Heavy direct kinetic plasma canon.",
                baseHp = 140f,
                baseSpeed = 11f,
                baseShield = 100f,
                color = Color(0xFFFF9100),
                cost = 250
            ),
            SpaceShipConfig(
                id = "Hyperion-X1",
                name = "Hyperion-X1",
                description = "Elite Siege Dreadnought. Fights with quantum tri-burst lasers. Expansive raw gravity magnet.",
                baseHp = 180f,
                baseSpeed = 13f,
                baseShield = 60f,
                color = Color(0xFFE040FB),
                cost = 600
            )
        )
    }
}

// Background space objects
data class AmbientStar(
    var x: Float,
    var y: Float,
    val size: Float,
    val speed: Float,
    val alpha: Float = 0.4f + (Math.random() * 0.6).toFloat()
)

data class Asteroid(
    var x: Float,
    var y: Float,
    val radius: Float,
    var vx: Float,
    var vy: Float,
    var rotation: Float,
    val rotSpeed: Float,
    var hp: Float
) {
    val maxHp = hp
}

// Bullets/Projectiles
data class CombatLaser(
    var x: Float,
    var y: Float,
    val vx: Float,
    val vy: Float,
    val isPlayerOwned: Boolean,
    val damage: Float,
    val color: Color,
    val isHeavyBeam: Boolean = false,
    val isMissile: Boolean = false,
    var targetX: Float = 0f,
    var targetY: Float = 0f
)

// Enemy types & models
enum class EnemyClass {
    SCOUT,     // Fast, fires thin laser downward
    GUNNER,    // Stays at distance, fires double circular lasers
    KAMIKAZE,  // Aggressively charges towards player, explodes
    CRUISER,   // Heavy bullet sponge, slow heavy shots,
    BOSS       // Massively styled flagship, spawns minions and multi-direction rings
}

data class SpaceEnemy(
    val id: Long,
    val type: EnemyClass,
    val name: String,
    var x: Float,
    var y: Float,
    val width: Float,
    val height: Float,
    var hp: Float,
    val maxHp: Float,
    var vx: Float,
    var vy: Float,
    val scoreReward: Int,
    val shardReward: Int,
    var lastShotTime: Long = 0,
    val shotIntervalMs: Long,
    var patternState: Float = 0f // For boss wave movements
)

// Loot drops
enum class PowerUpType {
    HEAL_HULL,       // Completely or partially restores integrity
    SUPER_LASER,     // Multiplies bullet streams (Double/Triple) for short time
    SHIELD_OVERCHARGE, // Buffs shield and provides brief invincibility
    CHRONO_BOOST     // Slows down enemies for short period
}

data class SpacePowerUp(
    var x: Float,
    var y: Float,
    val type: PowerUpType,
    var vy: Float = 4f,
    val size: Float = 40f
)

data class CosmicShard(
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 5f,
    val value: Int = 1,
    val size: Float = 30f
)

// High-fidelity Particle Systems
data class VisualParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val color: Color,
    var size: Float,
    var alpha: Float = 1f,
    val decayRate: Float = 0.02f,
    val sizeDecay: Float = 0.2f
)

// Flying Floating texts (Damage indicators, healing indicators)
data class FloatingText(
    var x: Float,
    var y: Float,
    val text: String,
    val color: Color,
    var alpha: Float = 1f,
    var vy: Float = -2.5f,
    var life: Int = 40 // frames
)
