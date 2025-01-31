/*
 * Copyright (c) 2020-2021  lokka30. Use of this source code is governed by the GNU AGPL v3.0 license that can be found in the LICENSE.md file.
 */

package me.lokka30.levelledmobs.rules;

/**
 * A duplicate of the built-in SpawnReason from spigot
 *
 * @author stumper66
 * @since 3.1.2
 */
public enum LevelledMobSpawnReason {
    NATURAL,
    JOCKEY,
    /**
     * @deprecated
     */
    @Deprecated
    CHUNK_GEN,
    LM_SPAWNER,
    SPAWNER,
    EGG,
    SPAWNER_EGG,
    LIGHTNING,
    BUILD_SNOWMAN,
    BUILD_IRONGOLEM,
    BUILD_WITHER,
    VILLAGE_DEFENSE,
    VILLAGE_INVASION,
    BREEDING,
    SLIME_SPLIT,
    REINFORCEMENTS,
    NETHER_PORTAL,
    DISPENSE_EGG,
    INFECTION,
    CURED,
    OCELOT_BABY,
    SILVERFISH_BLOCK,
    MOUNT,
    TRAP,
    ENDER_PEARL,
    SHOULDER_ENTITY,
    DROWNED,
    SHEARED,
    EXPLOSION,
    RAID,
    PATROL,
    BEEHIVE,
    PIGLIN_ZOMBIFIED,
    FROZEN,
    COMMAND,
    CUSTOM,
    DEFAULT
}
