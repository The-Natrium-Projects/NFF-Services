package net.sodiumzh.nff.services.entity.taming;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerListener;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.registries.ForgeRegistries;
import net.sodiumzh.nff.services.entity.ai.NFFTamedMobAIState;
import net.sodiumzh.nff.services.event.entity.ai.NFFTamedChangeAiStateEvent;
import net.sodiumzh.nff.services.eventlistener.NFFEntityEventListeners;
import net.sodiumzh.nff.services.inventory.NFFTamedInventoryMenu;
import net.sodiumzh.nff.services.inventory.NFFTamedMobInventory;
import net.sodiumzh.nff.services.item.NFFMobRespawnerItem;
import net.sodiumzh.nfu.annotation.DontCallManually;
import net.sodiumzh.nfu.annotation.DontOverride;
import net.sodiumzh.nfu.annotation.NotYetImplemented;
import net.sodiumzh.nfu.container.CyclicSwitch;
import net.sodiumzh.nfu.entity.MobApplicableItemTable;
import net.sodiumzh.nfu.entity.component.EntityComponentAPI;
import net.sodiumzh.nfu.entity.component.EntityComponentTypes;
import net.sodiumzh.nfu.entity.component.preset.HealingHandlerComponent;
import net.sodiumzh.nfu.function.MutablePredicate;
import net.sodiumzh.nfu.util.NFUEntityStatics;
import net.sodiumzh.nfu.util.NFUNBTStatics;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central interface for NFF tamed mobs.
 * <p>Any mob using NFF tamed mob features should be bound an implementation of this interface. The access of this interface
 * from the mob is defined in {@link NFFTamedTypeRegistry}.
 * <p><b>WARNING: </b> DO NOT perform {@code instanceof} check or cast to this interface directly from the mob reference!!!
 * Always use {@link INFFTamed#get} or {@link NFFTamedTypeRegistry#asTamed} instead.
 */
public interface INFFTamed extends ContainerListener, OwnableEntity {

	public static final CyclicSwitch<NFFTamedMobAIState> DEFAULT_AI_SWITCH = new CyclicSwitch<>
		(NFFTamedMobAIState.WAIT, NFFTamedMobAIState.FOLLOW, NFFTamedMobAIState.WANDER);

	/**
	 * Get the corresponding INFFTamed interface
	 */
	public static Optional<INFFTamed> get(Entity o) {
		return o instanceof Mob m ? NFFTamedTypeRegistry.asTamed(m) : Optional.empty();
	}

	// Initialization //

	/**
	 * Invoked on this mob's construction.
	 * <p>Called by {@link NFFEntityEventListeners#onEntityFinishConstruction}.
	 */
	public default void onInitialize() {}

	/**
	 * Invoked on this mob is tamed.
	 * <p>Called by {@link NFFTamingProcess#doTaming}.
	 * @param player Tamer player.
	 * @param tamedFrom The "wild" mob it's tamed from. If it's an in-place taming i.e.
	 *                  using the "wild" mob entity for tamed mob implementation, it's null.
	 */
	@ApiStatus.OverrideOnly
	public default void onTamed(@Nonnull Player player, @Nullable Mob tamedFrom) {}

	// Ownership //
	
	/** 
	 * Get owner as player entity.
	 * @deprecated Only for {@link OwnableEntity} implementation.
	 */
	@Deprecated
	@ApiStatus.Internal
	@Override
	@Nullable
	public default Player getOwner() 
	{
		return getOwnerInDimension();
	}
	
	/**
	 * Get owner if the owner is in the same dimension. Otherwise return {@code null}.
	 */
	@DontOverride
	@Nullable
	public default Player getOwnerInDimension()
	{
		if (getOwnerUUID() != null)
		{
			return this.asMob().level().getPlayerByUUID(getOwnerUUID());
		}
		else return null;
	}
	
	/**
	 * Get owner if the owner is in any dimension. Otherwise return {@code null}.
	 * <p> In client it will only check the loaded dimension.
	 */
	@DontOverride
	@Nullable
	public default Player getOwnerInWorld()
	{
		if (getOwnerUUID() != null)
		{
			if (this.asMob().level().isClientSide)
			{
				return getOwnerInDimension();
			}
			else
			{
				MinecraftServer sv = this.asMob().level().getServer();
				Player owner = null;
				for (Level level: sv.getAllLevels())
				{
					owner = level.getPlayerByUUID(getOwnerUUID());
					if (owner != null)
						return owner;
				}
				return null;
			}
		}
		return null;
	}
	
	/** 
	 * Get owner as UUID.
	*/
	@Override
	@DontOverride
	@Nullable
	public default UUID getOwnerUUID()
	{
		return this.getDataAccessor().getOwnerUUID();
	}
	
	/** Set owner from player entity.
	 */
	@DontOverride
	public default void setOwner(@Nonnull Player owner)
	{
		setOwnerUUID(owner.getUUID());
	}
	
	/**
	* Set owner from player UUID.
	*/
	@DontOverride
	public default void setOwnerUUID(@Nonnull UUID ownerUUID)
	{
		if (!this.asMob().level().isClientSide)
			this.getDataAccessor().setOwnerUUID(ownerUUID);
	}

	/**
	 * Check if owner is in the level.
	 * @deprecated Use {@code isOwnerInDimension} or {@code isOwnerInWorld} instead.
	 */
	@DontOverride
	@Deprecated
	public default boolean isOwnerPresent()
	{
		return getOwner() != null;
	}
	
	/**
	 * Check if the owner is in the same dimension as the mob.
	 */
	@DontOverride
	public default boolean isOwnerInDimension()
	{
		return this.getOwnerInDimension() != null;
	}
	
	/**
	 * Check if the owner is in the server in any dimension.
	*/
	@DontOverride
	public default boolean isOwnerInWorld()
	{
		return this.getOwnerInWorld() != null;
	}

	public default boolean isOwnedBy(Entity test) {
		return this.getOwnerUUID() != null && test != null && test.getUUID().equals(this.getOwnerUUID());
	}

	public default boolean isOwnedBy(UUID test) {
		return this.getOwnerUUID() != null && test != null && Objects.equals(this.getOwnerUUID(), test);
	}

	@Deprecated
	public default boolean isOwner(Entity test) {
		return isOwnedBy(test);
	}

	/* -------------------------------------------------------- */
	/* AI configs */

	/** 
	 * Get current AI state as enum.
	 * <p>以枚举类的形式获取当前AI状态。
	 */
	@DontOverride
	public default NFFTamedMobAIState getAIState()
	{
		return this.getDataAccessor().getAIState();
	}
	
	/** A preset action when switching AI e.g. on right click.
	 * By default it cycles among Wait, Follow and Wander.
	 * <p>DO NOT override this. Override {@code getNextAIState()} instead.
	 * @return The new AI state.
	 */
	@DontOverride
	public default NFFTamedMobAIState switchAIState()
	{		
		NFFTamedMobAIState nextState = getNextAIState();
		if (MinecraftForge.EVENT_BUS.post(new NFFTamedChangeAiStateEvent(this, getAIState(), nextState)))
			return getAIState();
		setAIState(nextState, false);
		return nextState;
	}
	
	/**
	 * Get the next AI State after a switching action e.g. right click.
	 * <p>Called in {@code switchAIState()} above.
	 */
	@DontCallManually
	public default NFFTamedMobAIState getNextAIState()
	{
		NFFTamedMobAIState res = DEFAULT_AI_SWITCH.next(getAIState());
		return res != null ? res : NFFTamedMobAIState.WAIT;
	}
	
	/**
	 * Set the AI state.
	 * @param postEvent Whether it should post a {@link NFFTamedChangeAiStateEvent}.
	 */
	@DontOverride
	public default void setAIState(NFFTamedMobAIState state, boolean postEvent)
	{
		if (state == this.getAIState())
			return;
		if (postEvent && MinecraftForge.EVENT_BUS.post(new NFFTamedChangeAiStateEvent(this, getAIState(), state)))
			return;
		this.getDataAccessor().setAIState(state);
	}
	
	/** Get if a target mob can be attacked by this mob.
	 * Called in target goals.
	*/
	public default boolean wantsToAttack(LivingEntity pTarget)
	{
		return NFFTamedStatics.wantsToAttackDefault(this, pTarget);
	}
	
	/** 
	 * <b> Don't call manually! </b> This method is only called in {@link NFFEntityEventListeners#onLivingSetAttackTarget}.
	 * Get the previous target before updating target.
	 * This function is only called on setting target. DO NOT CALL ANYWHERE ELSE!
	 */
	@DontOverride
	@DontCallManually
	public default LivingEntity getPreviousTarget()
	{
		return this.getDataAccessor().getPreviousTarget();
	}
	
	/** 
	* <b> Don't call manually! </b> This method is only called in {@link NFFEntityEventListeners#onLivingSetAttackTarget}.
	* Get the previous target after updating target.
	* This function is only called on setting target. DO NOT CALL ANYWHERE ELSE!
	*/
	@DontOverride
	@DontCallManually
	public default void setPreviousTarget(LivingEntity target)
	{
		this.getDataAccessor().setPreviousTarget(target);
	}
	
	/** Get the anchor pos that the mob won't stroll too far from it
	* If you want to disable anchor, just override this method and return null
	*/
	@Nullable
	public default Vec3 getAnchorPos() 
	{
		return this.getDataAccessor().getAnchor();
	}
	
	@DontOverride
	public default void setAnchorPos(Vec3 pos) 
	{
		this.getDataAccessor().setAnchor(pos);
	}
	
	public default double getAnchoredStrollRadius()  
	{
		return 16.0d;
	}
	
	/**
	 * Check if a position is further than the stroll radius to the anchor point.
	 * Called in random stroll goals.
	 */
	@DontOverride
	public default boolean isTooFarFromAnchor(Vec3 v)
	{
		Vec3 a = getAnchorPos();
		if (a == null)
			return false;
		double dx = v.x - a.x;
		double dz = v.z - a.z;
		return dx * dx + dz * dz > getAnchoredStrollRadius() * getAnchoredStrollRadius();		
	}

	/**
	 * Check if another mob should be accounted as ally of self, and should not attack each other.
     * @deprecated This method name is confusing with vanilla isAlliedTo(). Use {@code isTamedAlliedTo} instead.
	 */
	@Deprecated(forRemoval = true)
    public default boolean isAllyTo(LivingEntity other) {
		return isTamedAlliedTo(other);
	}

    public default boolean isTamedAlliedTo(LivingEntity other) {
        return NFFTamedStatics.isLivingAlliedToOwnableUnsafe(this, other);
    }

    public

	/**
	 * Check if another mob should be accounted as ally of the tamed mob, and should not attack each other.
	 */
	public static boolean isAlly(INFFTamed tamed, LivingEntity other) {
		return tamed.isAllyTo(other);
	}


	/**
	 * Check if a position is further than the stroll radius to the anchor point.
	 * Called in random stroll goals.
	 */
	@DontOverride
	public default boolean isTooFarFromAnchor(BlockPos pos)
	{
		return 	isTooFarFromAnchor(new Vec3(pos.getX(), pos.getY(), pos.getZ()));
	}
	
	/**
	 * Update anchor point on tick. When the mob isn't waiting, the anchor will follow it;
	 * when the mob enters waiting state, the anchor will stop and the mob gets anchored.
	 * Called on world tick only. Don't call anywhere else.
	 */
	@DontCallManually
	@DontOverride
	public default void updateAnchor()
	{
		if (getAnchorPos() != null)
			setAnchorPos(asMob().position());
	}
	
	/* Inventory */

	public default Optional<NFFTamedMobInventory> getAdditionalInventory() {
		return this.getDataAccessor().getAdditionalInventory();
	}
	
	/**
	 * @deprecated Use {@code getDataAccessor.getInventorySize()} instead.
	 */
	@Deprecated
	public default int getInventorySize() {
		return this.getDataAccessor().getInventorySize();
	}
	
	/**
	 * Method to create additional inventory. Invoked on befriended or loaded.
	 */
	@Nullable
	public NFFTamedMobInventory createAdditionalInventory();

	@Nullable
	public NFFTamedInventoryMenu makeMenu(int containerId, Inventory playerInventory, Container container);

	/* ContainerListener interface */

	/** Actions on additional inventory changed.
	 * <p>DO NOT override this. Override onInventoryChanged instead.
	 */
	@ApiStatus.NonExtendable
	@Override
	public default void containerChanged(Container pContainer) 
	{
		if (!(pContainer instanceof NFFTamedMobInventory))
			throw new UnsupportedOperationException("INFFTamed container only receives NFFTamedMobInventory.");
		if (!this.asMob().level().isClientSide())
			this.getAdditionalInventory().ifPresent(i -> i.syncToMob(this.asMob()));
		this.onInventoryChanged();
	}

	/**
	 * Additional actions on inventory changed.
	 */
	@ApiStatus.OverrideOnly
	public default void onInventoryChanged() 
	{
	}

	/**
	 * @deprecated Not implemented
	 */
	@Deprecated
	@NotYetImplemented
	public default boolean dropInventoryOnDeath()
	{
		return true;
	}
	
	/* Healing related */	

	/**
	 * Get the implementation type of healing handler.
	 */
	@Deprecated
	public default Class<? extends HealingHandlerComponent> healingHandlerClass()
	{
		return HealingHandlerComponent.class;
	}

	public default HealingHandlerComponent getHealingHandler() {
		return EntityComponentAPI.getComponentByPathOrFallback(this.asMob(), "/nff/tamed/healing_handler", EntityComponentTypes.HEALING_HANDLER.get());
	}

	@DontOverride
	public default boolean applyHealingItem(ItemStack stack, float value, boolean consume, int cooldown, Player player)
	{
		return this.getHealingHandler().applyHealingItem(stack, value, consume, cooldown, player);
	}
	
	/** Add all usable items here, including non-consuming items. Value is HP it can heal. */
	@Nullable
	public default MobApplicableItemTable getHealingItems()
	{
		return null;
	}

	@ApiStatus.NonExtendable
	public default InteractionResult tryApplyHealingItems(ItemStack stack, Player player)
	{
		if (stack.isEmpty())
			return InteractionResult.PASS;
		MobApplicableItemTable table = getHealingItems();
		if (table == null) 
		{
			return InteractionResult.PASS;
		}
		MobApplicableItemTable.Outcome output = table.getOutcome(this.asMob(), stack).orElse(null);
		if (output != null)
		{
			return applyHealingItem(stack, (float)(output.amount()), !output.noConsume(), output.cooldown(), player) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
		}
		return InteractionResult.PASS;
	}
	
	/* Respawn */
	
	public static enum DeathRespawnerGenerationType
	{
		GIVE,	// Directly give the repawner to the player
		DROP,	// Drop the respawner on the ground
		NONE	// Dont generate respawner
	}
	
	/**
	 * Defines how the respawner should be generated after mob dies.
	 */
	public default DeathRespawnerGenerationType getDeathRespawnerGenerationType()
	{
		return DeathRespawnerGenerationType.DROP;
	}

	/**
	 * (DROP only) 
	 * Get the type (subclass) of respawner it will drop.
	 * If it's null, it will be handled as if generation type is NONE.
	 */
	@Nullable
	public NFFMobRespawnerItem getRespawnerType();
	
	/**
	 * (DROP only)
	 * If true, the respawner will be invulnerable (except creative players, /kill commands and the void)
	 * <p> This works only when Generation Type is {@code DROP}. 
	 */
	public default boolean isRespawnerInvulnerable()
	{
		return true;
	}
	
	/**
	 * (DROP only)
	 * If true, the respawner will be lifted up on drop into the void
	 */
	public default boolean shouldRespawnerRecoverOnDropInVoid()
	{
		return true;
	}
	
	/**
	 * (DROP only)
	 * If true, the respawner will never expire 
	 */
	public default boolean respawnerNoExpire()
	{
		return true;
	}
	
	/* Misc */

	/**
	 * Get this as Mob.
	 */
	@DontOverride
	public default Mob asMob()
	{
		return (Mob)this;
	}
	
	/**
	 * Get this as INFFTamed.
	 */
	@DontOverride
	public default INFFTamed getBM()
	{
		return this;
	}

	/**
	 * Specify the mod ID this mob belongs to.
	 */
	@Deprecated
	public default String getModId()
	{
		return ForgeRegistries.ENTITY_TYPES.getKey(asMob().getType()).getNamespace();
	}

	/**
	 * Get a utility accessor for tamed data.
	 * <p>If your sub-interface have its own data, override this to your data accessor utility extending {@link NFFTamedDataAccessor}.
	 */
	public default NFFTamedDataAccessor getDataAccessor() {
		return NFFTamedDataAccessor.get(this);
	}

	/**
	 * Get the UUID identifier of this mob. (Not the entity UUID. This is for identifying a mob even if it respawned with a new UUID).
	 * Returns empty uuid (0, 0) if the data cap is lost (may occasionally happen).
	 */
	@DontOverride
	@Nonnull
	public default UUID getIdentifier()
	{
		return this.getDataAccessor().getIdentifier();
	}
	
	/* Behaviors */
	
	public static enum GolemAttitude
	{
		/**
		 * Golems will not proactively attack the mob, but will attack for other reasons
		 */
		NEUTRAL, 
		/**
		 * Golems will keep default attitude, usually hostile to mobs under Monster class.
		 */
		DEFAULT, 
		/**
		 * Golems will be totally passive and never attacks the mob
		 */
		PASSIVE,
		/**
		 * Custom, defined in {@link INFFTamed#shouldGolemAttack}.
		 */
		CUSTOM
	}
	
	/**
	 * Defines how golems should handle hostility towards this mob.
	 */
	public default GolemAttitude golemAttitude()
	{
		return GolemAttitude.NEUTRAL;
	}
	
	/**
	 * Only when {@link INFFTamed#golemAttitude} is {@link GolemAttitude#CUSTOM}, check if a golem should attack
	 * when it attempts to set target to this mob.
	 */
	public default boolean shouldGolemAttack(AbstractGolem golem)
	{
		return true;
	}
	
	/**
	 * If true, it can attack creepers (mobs under {@link Creeper} class).
	 */
	public default boolean canAttackCreeper()
	{
		return false;
	}
	
	/**
	 * If true, it can attack ghasts (mobs under {@link Ghast} class).
	 */
	public default boolean canAttackGhast()
	{
		return false;
	}
	
	/**
	 * If true, the mob can prevent other player's sleep. It never prevents the owner's sleep.
	 */
	public default boolean canPreventOtherPlayersSleep(ServerPlayer player)
	{
		return false;
	}

    /**
     * Return if the mob should use sun-sensitivity features. Override this to true
     * for mobs that should react to sun.
     */
    public default boolean enableSunSensitivity() {return false;}

    /**
     * Check if the mob is immune to sun from rules.
     * Implemented in {@link NFFEntityEventListeners#onMobSunBurnTick} via {@link net.sodiumzh.nfu.mixin.event.entity.MobSunBurnTickEvent}
     */
    @DontOverride
    @ApiStatus.NonExtendable
    public default boolean isSunImmune()
    {
		// Mobs not using sun sensitivity should always be accounted as sun-immune (i.e. not sun-sensitive)
        return !this.enableSunSensitivity() || getSunImmunity().test(this);
    }

    /**
     * Setup rules for sun immunity. Use {@code getSunImmunity()} to access rules.
     * Called in EntityJoinWorldEvent only
     */
    @DontCallManually
    @ApiStatus.OverrideOnly
    public default void setupSunImmunityRules() {};

    @DontOverride
    @ApiStatus.NonExtendable
    public default MutablePredicate<INFFTamed> getSunImmunity()
    {
        return this.getDataAccessor().getSunImmunity();
    }

	// ===== Mob Search === //

	/**
	 *  Only on server, record the current location to the owner's data.
	 *  Called in {@link NFFEntityEventListeners#onLivingUpdate}.
	 */
	public default void recordLocationToOwner() {
		Player player = this.getOwnerInWorld();
		if (player == null) return;
		CompoundTag nbt = EntityComponentAPI.getDataComponent(player).getNBT();
		if (!nbt.contains("tamedMobLocations", Tag.TAG_COMPOUND))
			nbt.put("tamedMobLocations", new CompoundTag());
		MobLocationInfo info = MobLocationInfo.fromMob(this);
		nbt.getCompound("tamedMobLocations").put(info.identifier().toString(), info.save());
	}

	/**
	 * Remove the location when the entity is removed.
	 */
	public default void removeLocationOnOwner() {
		Player player = this.getOwnerInWorld();
		if (player == null) return;
		CompoundTag nbt = EntityComponentAPI.getDataComponent(player).getNBT();
		nbt.getCompound("tamedMobLocations").remove(this.getIdentifier().toString());
	}

	/** Only on server, get all NFF mob's locations. The keys are Tamed Identifiers, not mob uuid!! */
	public static Map<UUID, MobLocationInfo> getAllMobLocations(Player player) {
		if (!(player.level() instanceof ServerLevel sl)) return new HashMap<>();

		CompoundTag nbt = EntityComponentAPI.getDataComponent(player).getNBT();
		if (!nbt.contains("tamedMobLocations", Tag.TAG_COMPOUND)) return Map.of();
		Map<UUID, Optional<MobLocationInfo>> res = NFUNBTStatics.mapFromCompoundTag(nbt.getCompound("tamedMobLocations"),
			UUID::fromString, tag -> Optional.ofNullable(MobLocationInfo.load((CompoundTag) tag, sl)));
		return res.values().stream()
				.filter(Optional::isPresent)
				.map(Optional::get)
				.filter(MobLocationInfo::isValid)
			.collect(Collectors.toMap(MobLocationInfo::identifier, info -> info));
	}

	/**
	 * Find tamed mob by its tamed mob identifier (not entity uuid). Will search in all loaded dimensions.
	 * <p>WARNING: this method is now very costly because there is no direct map from the identifier to the mob references, and
	 * it have to search on all mobs loaded on the server.</p>
	 * TODO Make a direct map and fix the issue above.
	 * @param identifier Tamed mob identifier. (Not the entity UUID!)
	 * @param context Any server level that can provide a context to the server.
	 * @return Find result.
	 */
	public static Optional<Mob> byIdentifier(UUID identifier, ServerLevel context) {
		for (ServerLevel sl: context.getServer().getAllLevels()) {
			var list = sl.getEntities(EntityTypeTest.forClass(Mob.class), mob ->
					INFFTamed.get(mob).filter(t -> t.getIdentifier().equals(identifier)).isPresent());
			if (!list.isEmpty()) return Optional.of(list.get(0));
		}
		return Optional.empty();
	}

	/**
	 * Remove suspicious tamed location stored in player that the mob may no longer exist。
	 * A suspicious location entry is defined as the entry in which the pos is loaded
	 * but the mob isn't found in level。
	 * */
	public static void removeSuspiciousMobLocations(Player player) {
		if (!(player.level() instanceof ServerLevel sl)) return;

		CompoundTag nbt = EntityComponentAPI.getDataComponent(player).getNBT();
		List<UUID> levelLoadedIdentifiers = NFUEntityStatics.getEntitiesOnServer(sl, EntityTypeTest.forClass(Mob.class),
				e -> INFFTamed.get(e).filter(tamed -> Objects.equals(tamed.getOwner(), player)).isPresent())
			.stream().map(e -> INFFTamed.get(e).orElse(null)).filter(Objects::nonNull)
			.map(INFFTamed::getIdentifier).toList();
		List<INFFTamed.MobLocationInfo> savedLocations =
			nbt.getCompound("tamedMobLocations").getAllKeys()
				.stream().map(k -> INFFTamed.MobLocationInfo.load(nbt.getCompound("tamedMobLocations").getCompound(k), sl))
				.filter(Objects::nonNull).toList();
		List<UUID> suspiciousIdentifiers = new ArrayList<>();
		for (INFFTamed.MobLocationInfo loc: savedLocations) {
			ServerLevel dim = sl.getServer().getLevel(loc.dimension());
			if (dim == null || dim.isLoaded(loc.pos()) && !levelLoadedIdentifiers.contains(loc.identifier()))
				suspiciousIdentifiers.add(loc.identifier());
		}
		suspiciousIdentifiers.forEach(id -> nbt.getCompound("tamedMobLocations").remove(id.toString()));
	}

	public static record MobLocationInfo(UUID identifier, Component mobName, ResourceKey<Level> dimension, BlockPos pos) {

		public static MobLocationInfo fromMob(INFFTamed mob) {
			return new MobLocationInfo(mob.getIdentifier(), mob.asMob().getName(),
					mob.asMob().level().dimension(), mob.asMob().getOnPos());
		}

		public CompoundTag save() {
			CompoundTag nbt = new CompoundTag();
			nbt.putUUID("identifier", this.identifier());
			nbt.putString("name", Component.Serializer.toJson(mobName()));
			nbt.putString("dimension", dimension().location().toString());
			nbt.putIntArray("pos", new int[] {pos.getX(), pos.getY(), pos.getZ()});
			return nbt;
		}

		@Nullable
		public static MobLocationInfo load(CompoundTag nbt, ServerLevel context) {

			if (!nbt.hasUUID("identifier")) return null;
			UUID id = nbt.getUUID("identifier");
			if (id.equals(new UUID(0L, 0L))) return null;

			Component name = Component.Serializer.fromJson(nbt.getString("name"));

			ResourceLocation dimKey = new ResourceLocation(nbt.getString("dimension"));
			List<ResourceKey<Level>> dim = context.getServer().levelKeys().stream()
					.filter(key -> key.location().equals(dimKey)).toList();
			if (dim.isEmpty()) return null;

			int[] posArray = nbt.getIntArray("pos");

			return new MobLocationInfo(id, name, dim.get(0), new BlockPos(posArray[0], posArray[1], posArray[2]));
		}

		public boolean isValid() {
			return !Objects.equals(identifier(), new UUID(0L, 0L))
					&& mobName() != null && dimension() != null && pos() != null;
		}
	}

}
