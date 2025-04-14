package com.tylermackj.hardcoremp.types;

import java.util.Optional;
import java.util.UUID;

import org.ladysnake.cca.api.v3.component.sync.AutoSyncedComponent;

import com.tylermackj.hardcoremp.ComponentRegisterer;
import com.tylermackj.hardcoremp.HardcoreMP;
import com.tylermackj.hardcoremp.Utils;
import com.tylermackj.hardcoremp.interfaces.ITeamData;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper.WrapperLookup;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.scoreboard.Team;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;

public class TeamData implements ITeamData, AutoSyncedComponent {
    private final Team provider;

    private static final int RADIUS = 1000000;
    private static final int MAX_SPAWN_HIGHT = 1024;

    private static final String NBT_ATTEMPT = "attempt";
    private static final String NBT_SPAWN_POS = "spawnPos";
    private static final String NBT_X = "x";
    private static final String NBT_Y = "y";
    private static final String NBT_Z = "z";
    private static final String NBT_UUID = "uuid";
    private static final String NBT_COUNT = "count";
    private static final String NBT_START = "start";

    private class AttemptData {
        public BlockPos spawnPos = BlockPos.ORIGIN;
        public UUID attemptUuid = UUID.randomUUID();
        public int attemptCount = 0;
        public long attemptStart = 0;

        public AttemptData() {}
        public AttemptData(
            BlockPos spawnPos,
            UUID attemptUuid,
            int attemptCount,
            long attemptStart
        ) {
            this.spawnPos = spawnPos;
            this.attemptUuid = attemptUuid;
            this.attemptCount = attemptCount;
            this.attemptStart = attemptStart;
        }
    }

    private AttemptData attempt = new AttemptData();

    public TeamData(Team provider) { this.provider = provider; }

    private void setAttempt(World world, int attemptCount) {
        this.attempt = new AttemptData(
            this.randomizeSpawnPos(world),
            UUID.randomUUID(),
            attemptCount,
            world.getTime()
        );

        ComponentRegisterer.TEAM_DATA.sync(this.provider);
    }   

    public BlockPos randomizeSpawnPos(World world) {
        Optional<BlockPos> spawnPos = Optional.ofNullable(null);

        do {
            HardcoreMP.LOGGER.info("Generating spawnpoint for team " + this.provider.getName() + " (" + world.asString() + ")");
            BlockPos spawnPoint = Utils.randomBlockPos(RADIUS, MAX_SPAWN_HIGHT);

            TagKey<Biome> oceanTag = TagKey.of(RegistryKeys.BIOME, Identifier.of("c:is_ocean"));

            while (world.getBiome(spawnPoint).isIn(oceanTag)) {
                HardcoreMP.LOGGER.info("Avoiding ocean");
                spawnPoint = Utils.randomBlockPos(RADIUS, MAX_SPAWN_HIGHT);
            }

            HardcoreMP.LOGGER.info("Finding ground");
            spawnPos = BlockPos.findClosest(spawnPoint, 0, MAX_SPAWN_HIGHT, (blockPos) -> {
                return !world.isAir(blockPos.down());
            });
        } while (spawnPos.isEmpty());

        return spawnPos.orElseThrow();
    } 

    @Override
    public boolean isRequiredOnClient() {
        return false;
    }

    @Override
    public void readFromNbt(NbtCompound tag, WrapperLookup registryLookup) {
        Optional<NbtCompound> attemptNbt = tag.getCompound(NBT_ATTEMPT);

        if (attemptNbt.isPresent()) {
            NbtCompound spawnPosNbt = attemptNbt.get().getCompound(NBT_SPAWN_POS).orElseThrow();

            this.attempt = new AttemptData(
                new BlockPos(
                    spawnPosNbt.getInt(NBT_X).orElseThrow(),
                    spawnPosNbt.getInt(NBT_Y).orElseThrow(),
                    spawnPosNbt.getInt(NBT_Z).orElseThrow()
                ),
                UUID.fromString(attemptNbt.get().getString(NBT_UUID).orElseThrow()),
                attemptNbt.get().getInt(NBT_COUNT).orElseThrow(),
                attemptNbt.get().getLong(NBT_START).orElseThrow()
            );
        }
    }

    @Override
    public void writeToNbt(NbtCompound tag, WrapperLookup registryLookup) {

        NbtCompound spawnPosNbt = new NbtCompound();
        spawnPosNbt.putInt(NBT_X, attempt.spawnPos.getX());
        spawnPosNbt.putInt(NBT_Y, attempt.spawnPos.getY());
        spawnPosNbt.putInt(NBT_Z, attempt.spawnPos.getZ());

        NbtCompound attemptNbt = new NbtCompound();
        attemptNbt.put(NBT_SPAWN_POS, spawnPosNbt);
        attemptNbt.putString(NBT_UUID, attempt.attemptUuid.toString());
        attemptNbt.putInt(NBT_COUNT, attempt.attemptCount);
        attemptNbt.putLong(NBT_START, attempt.attemptStart);

        tag.put(NBT_ATTEMPT, attemptNbt);
    }

    @Override
    public BlockPos getSpawnPos() {
        return this.attempt.spawnPos;
    }

    @Override
    public UUID getAttemptUuid() {
        return this.attempt.attemptUuid;
    }

    @Override
    public int getAttemptCount() {
        return this.attempt.attemptCount;
    }

    @Override
    public long getAttemptStart() {
        return this.attempt.attemptStart;
    }

    @Override
    public void nextAttempt(World world) {
        this.setAttempt(world, this.attempt.attemptCount + 1);
    }

    @Override
    public void resetAttempts(World world) {
        this.setAttempt(world, 0);
    }
}
