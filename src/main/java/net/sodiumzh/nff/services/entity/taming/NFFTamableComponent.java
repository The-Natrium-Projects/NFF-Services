package net.sodiumzh.nff.services.entity.taming;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.MinecraftForge;
import net.sodiumzh.nff.services.eventlistener.NFFEntityEventListeners;
import net.sodiumzh.nff.services.registry.NFFEntityComponents;
import net.sodiumzh.nfu.container.ITable2D;
import net.sodiumzh.nfu.entity.component.EntityComponentAPI;
import net.sodiumzh.nfu.entity.component.EntityComponentBase;
import net.sodiumzh.nfu.entity.component.EntityComponentType;
import net.sodiumzh.nfu.entity.component.preset.EntityTimerComponent;
import net.sodiumzh.nfu.object.HierarchyPath;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Main handler component of NFF tamable mobs.
 * <p>Note: this component ONLY works under path {@code "/nff/tamable"}, or the features will not be auto handled.
 */
public class NFFTamableComponent extends EntityComponentBase<Mob> {

    protected @Nonnull NFFTamingProcess tamingProcess;
    protected @Nullable UUID alwaysHostileTo = null;
    protected boolean forcePersistent = false;

    public NFFTamableComponent(Mob entity) {
        super(entity);
        this.tamingProcess = Optional.ofNullable(NFFTamingMapping.getProcess(entity)).orElseThrow(() -> new IllegalStateException("Missing taming process."));
    }

    @Override
    public Map<HierarchyPath, EntityComponentType<?, ?>> getRequiredSubcomponents() {
        return Map.of(
            HierarchyPath.byNameArray("data"), NFFEntityComponents.TAMABLE_DATA.get(),
            HierarchyPath.byNameArray("timer"), NFFEntityComponents.MOB_TIMER.get(),
            HierarchyPath.byNameArray("anger_handler"), NFFEntityComponents.TAMABLE_ANGER_HANDLER.get());
    }

    @Override
    public List<HierarchyPath> getAllowedPaths() {
        return List.of(HierarchyPath.byNameArray("nff", "tamable"));
    }

    public NFFTamableDataComponent getDataComponent() {
        return this.getSubComponent("data", NFFEntityComponents.TAMABLE_DATA.get())
            .orElseGet(() -> NFFEntityComponents.TAMABLE_DATA.get().create(this.getEntity()));
    }

    public EntityTimerComponent<Mob> getTimerComponent() {
        return this.getSubComponent("timer", NFFEntityComponents.MOB_TIMER.get())
            .orElseGet(() -> NFFEntityComponents.MOB_TIMER.get().create(this.getEntity()));
    }

    public NFFTamableAngerHandlerComponent getAngerHandler() {
        return this.getSubComponent("anger_handler", NFFEntityComponents.TAMABLE_ANGER_HANDLER.get())
            .orElseGet(() -> NFFEntityComponents.TAMABLE_ANGER_HANDLER.get().create(this.getEntity()));
    }

    /**
     * Get the general nbt data, i.e. non-player-specific data. One mob has only one general nbt.
     */
    @Nonnull
    public CompoundTag getGeneralNBT() {
        return this.getDataComponent().getNBT();
    }

    /**
     * Get the nbt for a specific player. This nbt is separated from general nbt, and has one nbt for each player.
     */
    @Nonnull
    public Optional<CompoundTag> getPlayerSpecificNBT(Player player) {
        return this.getDataComponent().getPlayerSpecificNBT(player);
    }

    @Nonnull
    public Optional<CompoundTag> getPlayerSpecificNBT(UUID uuid) {
        return this.getDataComponent().getPlayerSpecificNBT(uuid);
    }

    /**
     * Get the nbt for a specific player if it's present, and create if absent.
     * This nbt is separated from general nbt, and has one nbt for each player.
     */
    public CompoundTag getOrCreatePlayerSpecificNBT(Player player) {
        return this.getDataComponent().getOrCreatePlayerSpecificNBT(player);
    }

    public CompoundTag getOrCreatePlayerSpecificNBT(UUID uuid) {
        return this.getDataComponent().getOrCreatePlayerSpecificNBT(uuid);
    }

    public boolean hasPlayerSpecificNBT(Player player) {
        return this.getDataComponent().hasPlayerSpecificNBT(player);
    }

    public boolean hasPlayerSpecificNBT(UUID uuid) {
        return this.getDataComponent().hasPlayerSpecificNBT(uuid);
    }

    /**
     * Get all players which have a <i>non-empty</i> nbt in this mob.
     * <p>Note: It will return all UUIDs of which the nbt is present. There's no guarantee that the player is present
     * in the level.
     * <p>Note: This is not the timer.
     */
    public Set<UUID> getAllPlayersWithNBT() {
        return this.getDataComponent().getAllPlayersWithNBT();
    }

    public int getPlayerTimerRemainingTime(@Nonnull Player player, String key) {
        return this.getTimerComponent().getUUIDSpecificTimer(player.getUUID(), key).map(EntityTimerComponent.Timer::getTicksRemaining).orElse(0);
    }

    public int getPlayerTimerRemainingTime(@Nonnull UUID uuid, String key) {
        return this.getTimerComponent().getUUIDSpecificTimer(uuid, key).map(EntityTimerComponent.Timer::getTicksRemaining).orElse(0);
    }

    public boolean hasPlayerTimer(Player player, String key) {
        return this.getPlayerTimerRemainingTime(player, key) != 0;
    }

    public boolean hasPlayerTimer(UUID uuid, String key) {
        return this.getPlayerTimerRemainingTime(uuid, key) != 0;
    }

    public void putPlayerTimer(Player player, String key, int ticks) {
        this.getTimerComponent().addUUIDSpecificTimer(player.getUUID(), key, ticks, 1, true);
    }

    public void putPlayerTimer(UUID uuid, String key, int ticks) {
        this.getTimerComponent().addUUIDSpecificTimer(uuid, key, ticks, 1, true);
    }

    public void removePlayerTimer(Player player, String key, boolean postEvent) {
        this.getTimerComponent().removeUUIDSpecificTimer(player.getUUID(), key);
        if (postEvent)
            MinecraftForge.EVENT_BUS.post(new EntityTimerComponent.ExpireEvent(this.getEntity(), this.getTimerComponent(), key, true, player.getUUID()));
    }

    public void removePlayerTimer(UUID uuid, String key, boolean postEvent) {
        this.getTimerComponent().removeUUIDSpecificTimer(uuid, key);
        if (postEvent)
            MinecraftForge.EVENT_BUS.post(new EntityTimerComponent.ExpireEvent(this.getEntity(), this.getTimerComponent(), key, true, uuid));
    }

    public List<String> getAllTimerKeys(Player player) {
        return this.getTimerComponent().getAllUUIDSpecificTimerNames(player.getUUID());
    }

    /**
     * Get all players which have a <i>non-empty</i> timer in this mob. Note that this is not the nbt.
     */
    public List<UUID> getAllTimingPlayers() {
        return this.getTimerComponent().getAllUUIDSpecificTimerNames().stream().map(ITable2D.KeyPair::row).distinct().toList();
    }

    /**
     * Get all player UUIDs and remaining timers of the same key. (The players are not necessarily present in the level)
     */
    public List<Tuple<UUID, Integer>> getAllPlayerTimersOfKey(String key) {
        return this.getTimerComponent().getAllUUIDSpecificTimerNames().stream().filter(p -> p.column().equals(key))
            .map(p -> new Tuple<>(p.row(), this.getTimerComponent().getUUIDSpecificTimer(p.row(), key).map(EntityTimerComponent.Timer::getTicksRemaining).orElse(0)))
            .filter(p -> p.getB() != 0)
            .toList();
    }

    /**
     * Set the mob is always hostile to a specified target once it's in the follow range, ignoring target goals.
     * If input is null, the previous always-hostile-to target will be removed and the mob will perform normally.
     * <p>Always Hostile feature is handled in {@link NFFEntityEventListeners#onLivingChangeTarget_Low}
     * and {@link NFFEntityEventListeners#onLivingSetAttackTarget_Lowest}.
     */
    public void setAlwaysHostileTo(@Nullable LivingEntity target) {
        this.alwaysHostileTo = Optional.ofNullable(target).map(LivingEntity::getUUID).orElse(null);
    }

    /**
     * get the target the mob is always hostile to.
     * If the mob isn't set always hostile to anything, it will return null, no matter if the mob has a target.
     * <p>Always Hostile feature is handled in {@link NFFEntityEventListeners#onLivingChangeTarget_Low}
     * and {@link NFFEntityEventListeners#onLivingSetAttackTarget_Lowest}.
     */
    @Nullable
    public Optional<UUID> getAlwaysHostileTo() {
        return Optional.ofNullable(this.alwaysHostileTo);
    }

    public Optional<LivingEntity> getAlwaysHostileToLiving() {
        if (this.alwaysHostileTo == null) return Optional.empty();
        Player player = this.getEntity().level().getPlayerByUUID(this.alwaysHostileTo);
        if (player != null) return Optional.of(player);
        if (this.getEntity().level() instanceof ServerLevel sl)
            return Optional.ofNullable(sl.getEntities().get(this.alwaysHostileTo))
                .filter(e -> e instanceof LivingEntity).map(e -> (LivingEntity) e);
        else return this.getEntity().level()
            .getEntitiesOfClass(LivingEntity.class, this.getEntity().getBoundingBox().inflate(16d))
            .stream().filter(e -> e.getUUID().equals(this.alwaysHostileTo)).findAny();
    }

    /**
     * Set if the mob is forced no despawn despite the return of {@link Mob#isPersistenceRequired}.
     * <p>Force Persistent feature is handled in {@link NFFEntityEventListeners#onDespawn}.
     * <p>Note: it doesn't prevent despawn in the peace mode.
     * @param value True to keep it persistent. False to perform {@link Mob#isPersistenceRequired} check.
     */
    public void setForcePersistent(boolean value) {
        this.forcePersistent = value;
    }

    /**
     * Get if the mob is forced no despawn despite the return of {@link Mob#isPersistenceRequired}
     * <p>Force Persistent feature is handled in {@link NFFEntityEventListeners#onDespawn}.
     * <p>Note: it doesn't prevent despawn in the peace mode.
     * @return True if the mob is forced no despawn in {@link NFFTamableDataComponent}. False to perform {@link Mob#isPersistenceRequired} check.
     */
    public boolean isForcePersistent() {
        return this.forcePersistent;
    }

    @Nonnull
    public NFFTamingProcess getTamingProcess() {
        return this.tamingProcess;
    }

    public NFFTamableComponent setTamingProcess(NFFTamingProcess tamingProcess) {
        this.tamingProcess = tamingProcess;
        return this;
    }

    // Static shortcuts

    /**
     * Get the tamable component, or create a transient instance if absent (to prevent nullity).
     * <p>Not recommended, use {@code getOptional} if possible.
     */
    public static NFFTamableComponent getOrDefault(Mob m) {
        return getOptional(m).orElse(NFFEntityComponents.TAMABLE.get().create(m));
    }

    /**
     * Get the tamable capability of a mob if present, or empty if not.
     */
    public static Optional<NFFTamableComponent> getOptional(Entity e) {
        if (e instanceof Mob mob)
            return EntityComponentAPI.getComponentByPath(mob, NFFEntityComponents.PATH_TAMABLE, NFFEntityComponents.TAMABLE.get());
        else return Optional.empty();
    }

    public static Optional<NFFTamableDataComponent> getDataComponent(Entity e) {
        return getOptional(e).map(NFFTamableComponent::getDataComponent);
    }

    public static Optional<EntityTimerComponent<Mob>> getTimerComponent(Entity e) {
        return getOptional(e).map(NFFTamableComponent::getTimerComponent);
    }

    public static Optional<NFFTamableAngerHandlerComponent> getAngerHandler(Entity e) {
        return getOptional(e).map(NFFTamableComponent::getAngerHandler);
    }

    @Override
    public void tick() {
        if (!this.isClientSide())  {
            this.getAlwaysHostileToLiving().filter(this.getEntity()::hasLineOfSight)
                .ifPresent(this.getEntity()::setTarget);
            this.getTamingProcess().doServerTick(this.getEntity());
        }
    }

    @Override
    public CompoundTag serializeNBT() {
        CompoundTag nbt = new CompoundTag();
        if (alwaysHostileTo != null)
            nbt.putUUID("alwaysHostileTo", alwaysHostileTo);
        nbt.putBoolean("forcePersistent", forcePersistent);
        return nbt;
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        if (nbt.hasUUID("alwaysHostileTo"))
            this.alwaysHostileTo = nbt.getUUID("alwaysHostileTo");
        this.forcePersistent = nbt.getBoolean("forcePersistent");
    }

    @Deprecated
    public final Map<String, Integer> getTimerMap() {
        return this.getTimerComponent().getAllGeneralTimerNames().stream()
                .map(key -> new AbstractMap.SimpleEntry<>(key, this.getTimerComponent().getGeneralTimer(key).map(EntityTimerComponent.Timer::getTicksRemaining).orElse(0)))
                .filter(entry -> entry.getValue() != 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }


}
