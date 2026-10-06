package net.sodiumzh.nff.services.entity.taming;

import com.mojang.logging.LogUtils;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Tuple;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.util.INBTSerializable;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;
import net.sodiumzh.nff.services.NFFServices;
import net.sodiumzh.nff.services.entity.ai.NFFTamedMobAIState;
import net.sodiumzh.nff.services.inventory.NFFTamedMobInventory;
import net.sodiumzh.nff.services.network.NFFChannels;
import net.sodiumzh.nff.services.network.NFFClientGamePacketHandler;
import net.sodiumzh.nff.services.registry.NFFCapRegistry;
import net.sodiumzh.nff.services.registry.NFFCapabilityAttachments;
import net.sodiumzh.nff.services.registry.NFFTagRegistry;
import net.sodiumzh.nfu.annotation.DontCallManually;
import net.sodiumzh.nfu.capability.CEntityTickingCapability;
import net.sodiumzh.nfu.container.Tuple2;
import net.sodiumzh.nfu.function.MutablePredicate;
import net.sodiumzh.nfu.network.AvailableSide;
import net.sodiumzh.nfu.network.NFUDataSerializer;
import net.sodiumzh.nfu.network.NFUDataSerializers;
import net.sodiumzh.nfu.util.NFUDebugStatics;
import net.sodiumzh.nfu.util.NFUNBTStatics;
import net.sodiumzh.nfu.util.NFUReflectionStatics;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.time.LocalDate;
import java.time.temporal.ChronoField;
import java.util.*;
import java.util.function.Supplier;

/**
 * No longer used, left only for porting old data
 * TODO remove in 0.x.34
 */
@Deprecated(forRemoval = true)
public interface CNFFTamedCommonData extends INBTSerializable<CompoundTag>, CEntityTickingCapability<Mob> {
	
	// General //
	
	/** Get the befriended mob owning this data. */
	public INFFTamed getBM();

	/** Get the whole additional NBT as {@link CompoundTag}.
	 * <p>Use this if the additional data needs to be saved but not synched. If needs to be synched, use the synched data system.
	 */
	public CompoundTag getAdditionalNBT();
	
	/** Get sun immunity. It only works when the mob is an {@link INFFTamedSunSensitiveMob}, otherwise throws exception. */
	public MutablePredicate<INFFTamed> getSunImmunity();
	
	/** Get temporary object from a key from table. Temporary object table is a non-serialized object table to store any objects, 
	 * not directly accessible but only with {@code getTempObject}, {@code addTempObject} and {@code removeTempObject}.
	 * @return Object if present, or null if not.
	 */
	@Nullable
	public Object getTempObject(String key);
	
	/** Add a temporary object to table. Temporary object table is a non-serialized object table to store any objects.*/
	public void addTempObject(String key, Object obj);
	
	/** Remove a temporary object from table. Temporary object table is a non-serialized object table to store any objects.
	 * If the key is absent, it will not do anything.
	 */
	public void removeTempObject(String key);
	
	/**
	 * Force access a value with casted class.
	 * If it's absent, return null. If class mismatches, log error and return null.
	 * @return Value with casted class, or null if absent or class mismatching.
	 */
	@SuppressWarnings("unchecked")
	@Nullable
	public default <T> T getTempObjectCast(String key)
	{
		Object obj = getTempObject(key);
		if (obj == null) return null;
		try {
			return (T) obj;
		}
		catch (ClassCastException e) {
			LogUtils.getLogger().error("CNFFTamedCommonData#getTempObjectCasted: class cast mismatch found.");
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Get the UUID identifier of this mob. (Independent to the entity UUID. 
	 * This is generated on befriended for identifying a mob even if it has respawned with a new UUID.)
	 */
	public UUID getIdentifier();
	
	/**
	 * Generate UUID identifier of this mob. (Independent to the entity UUID. 
	 * This is generated on befriended for identifying a mob even if it has respawned with a new UUID.)
	 */
	@DontCallManually
	public void generateIdentifier();

	/**
	 * Get the registry key of the entity type of this mob on befriended.
	 * <p>
	 * This allows to read the mob's "initial" type after it converts to other
	 * types somehow.
	 */
	public EntityType<? extends Mob> getInitialEntityType();
	
	/**
	 * Record the registry key of the entity type of this mob on befriended.
	 * <p>
	 * This allows to read the mob's "initial" type after it converts to other
	 * types somehow.
	 */
	public void recordEntityType();
	
	// Owner info related //
	
	/**
	 * Get the owner's display name, or "(Unknown)" if not found.
	 */
	public String getOwnerName();
	
	/**
	 * Set the owner's display name stored.
	 */
	public void setOwnerName(String val);
	
	/**
	 * Get the date player encountered it, including befriended, or took ownership from another player.
	 * @since 0.x.20
	 * @return An int[3] indicating year, month and day, or (0,0,0) if not recorded (legacy).
	 */
	@Nullable
	public int[] getEncounteredDate();
	
	/**
	 * Record info about befriending time and location. Invoked in {@link NFFTamingProcess#doTaming}.
	 */
	@DontCallManually
	public void setEncounteredDate(int[] val);
	
	public default void recordEncounteredDate()
	{
		LocalDate now = LocalDate.now();
		int[] date = new int[] {now.get(ChronoField.YEAR), now.get(ChronoField.MONTH_OF_YEAR), now.get(ChronoField.DAY_OF_MONTH)};
		this.setEncounteredDate(date);
	}
	
	public UUID getOwnerUUID();
	
	public void setOwnerUUID(UUID value);
	
	// Behavior related //
	
	/**
	 * Get random stroll anchor point as vector.
	 */
	public Vec3 getAnchor();
	
	/**
	* Set random stroll anchor point.
	*/ 
	public void setAnchor(Vec3 anchor);

	public NFFTamedMobAIState getAIState();
	
	public void setAIState(NFFTamedMobAIState state);

	/**
	 * Get the attack target. Same to {@link Mob#getTarget()} on server. On client,
	 * it will be read from the synched getter cache.
	 */
	@Nullable
	public LivingEntity getAttackTarget();

	/**
	 * <b> Don't call manually! </b> This method is only called in {@link INFFTamed#getPreviousTarget}.
	 */
	@DontCallManually
	@Nullable
	public LivingEntity getPreviousTarget();
	
	/**
	 * <b> Don't call manually! </b> This method is only called in {@link INFFTamed#setPreviousTarget}.
	 */
	@DontCallManually
	@Nullable
	public void setPreviousTarget(LivingEntity target);
	
	// Inventory related //
	
	public NFFTamedMobInventory getAdditionalInventory();
	
	// Synched Data related //
	
	/**
	 * Create a synched data. Synched data must be created before other any operations.
	 * @param <T>Data class. Exactly same to the data class in {@code dataSerialzier}.
	 * @param key Data key as string.
	 * @param dataSerialzier Data serializer applied.
	 * @param initValue Default value if not set.
	 */
	public <T> void createSynchedData(String key, NFUDataSerializer<T> dataSerialzier, T initValue);

	public <T> boolean hasSynchedData(String key, Class<T> dataType);

	public <T> Optional<T> getSynchedData(String key, Class<T> dataClass);
	
	public <T> Optional<T> getSynchedDataUnchecked(String key);
	
	/**
	 * Set synched data value. Only on server.
	 * @param key Synched data key
	 * @param dataClass Expected data class. <i>This is a salt to ensure you know what class this field expects to receive.</i>
	 * @param value New value.
	 */
	public <T> void setSynchedData(String key, Class<T> dataClass, T value);
	
	public void setSynchedDataClient(String key, NFUDataSerializer<?> serializer, Object value);

	/**
	 * Define a synched getter. Synched getters get from a {@link Supplier} every tick from server and store it on client.
	 * They are not saved into data.
	 * <p>When a field is accessed on the client, it will read the cache value synched from server (if no synching happened,
	 * it's the default value).
	 */
	public <T> void createSynchedGetter(String key, NFUDataSerializer<T> serializer, @Nonnull T defaultValue, Supplier<T> accessorOnServer);

	/**
	 * Get synched getter from key and type.
	 * <p>Safe to call on both sides. On server, it will be directly accessed by the supplier,
	 * and on client it will be read from the cached field which is updated on synching.
	 * <p>Note: this includes an unsafe casting. Double-check the type before using.
	 * Return {@code null} if not present.
	 */
	public <T> T getSynchedGetter(String key, Class<T> type);

	/**
	 * Get a synched getter as a raw {@link Object}.
	 * <p>Safe to call on both sides. On server, it will be directly accessed by the supplier,
	 * and on client it will be read from the cached field which is updated on synching.
	 * <p> Null if the key doesn't exist.
	 */
	@Nullable
	public Object getSynchedGetter(String key);

	/**
	 * Set the key-value pair in the synched getter cache on client.
	 * <p>This is only used in synching process and should not be called elsewhere.
	 * Safe to call on server as the cache on server will not be read.
	 */
	@DontCallManually
	public void setSynchedGetterClient(String key, @Nonnull Object o);

	/**
	 * Set how many ticks to do a sync (data and fields)/
	 * @deprecated use {@code setSyncInterval} instead. This method name is confusing.
	 */
	@Deprecated
	public default void setDataSyncInterval(int ticks) {
		setSyncInterval(ticks);
	}

	/**
	 * Set how many ticks to do a sync (data and fields)
	 */
	public void setSyncInterval(int ticks);


	// Misc //
	
	/**
	 * <b> Don't call manually! </b> Use {@link INFFTamed#hasInit()} instead.
	 * Get whether this mob has finished initialization.
	 * <p>After finishing initialization the mob will start updating from its inventory.
	 */
	@DontCallManually
	public boolean hasInit();
	
	/**
	 * <b> Don't call manually! </b> Use {@link INFFTamed#setInit()} or {@link INFFTamed#setNotInit()} instead.
	 * Set the label for if this mob has finished initialization.
	 */
	@DontCallManually
	public void setInitState(boolean value);

	// ********************************************************************************* //
	// ********************************************************************************* //
	
	// Values of mob data, also as implementation of interface methods.
	public static class Values implements CNFFTamedCommonData
	{
		private static final UUID EMPTY_UUID = new UUID(0l, 0l);
		private INFFTamed mob;

		// General
		private static final String IDENTIFIER_SYNCHED_KEY = "identifier";
		private EntityType<? extends Mob> initialType = null;
		private CompoundTag nbt = new CompoundTag();
		// Owner
		private static final String OWNER_UUID_SYNCHED_KEY = "ownerUUID";
		private static final String OWNER_NAME_SYNCHED_KEY = "ownerName";
		private static final String ENCOUNTERED_DATE_SYNCHED_KEY = "encounteredDate";
		private static final String ATTACK_TARGET_SYNCHED_KEY = "attackTarget";
		// Behavior
		private LivingEntity previousTarget = null;
		private Vec3 anchor;
		private static final String AI_STATE_SYNCHED_KEY = "aiState";
		// Inventory
		private NFFTamedMobInventory inventory;
		// Misc
		private boolean hasInit = false;
		// Syncher
		private Map<String, Tuple<NFUDataSerializer<?>, Object>> synchedData = new HashMap<>();
			// Suppliers are only called on server. On client, this table is present for keeping a serializer instance
			// to decode, but the suppliers will never be called.
		private Map<String, SynchedGetter> synchedGetters = new HashMap<>();
		private int syncInterval = 1;
		// BefriendedUndeadMob data
		private MutablePredicate<INFFTamed> sunImmunity = new MutablePredicate<>();
		// Temp
		private Map<String, Object> tempObjects = new HashMap<>();
		
		public Values(INFFTamed mob)
		{
			this.mob = mob;
			this.anchor = mob.asMob().position();
			this.nbt = new CompoundTag();
			this.inventory = this.getBM().createAdditionalInventory();
			this.createSynchedData(IDENTIFIER_SYNCHED_KEY, NFUDataSerializers.UUID.get(), EMPTY_UUID);
			this.createSynchedData(OWNER_UUID_SYNCHED_KEY, NFUDataSerializers.UUID.get(), EMPTY_UUID);
			this.createSynchedData(OWNER_NAME_SYNCHED_KEY, NFUDataSerializers.STRING.get(), "");
			this.createSynchedData(ENCOUNTERED_DATE_SYNCHED_KEY, NFUDataSerializers.INT_ARRAY.get(), new int[] {2023, 1, 1});
			this.createSynchedData(AI_STATE_SYNCHED_KEY, NFUDataSerializers.STRING.get(), NFFTamedMobAIState.WAIT.getId().toString());
			this.createSynchedGetter(ATTACK_TARGET_SYNCHED_KEY, NFUDataSerializers.INT.get(), -1,
					() -> Optional.ofNullable(this.getEntity().getTarget())
							.flatMap(living -> Optional.of(living.getId())).orElse(-1));	// -1 means no target
			
			//this.getBM().onDataInit(this);
			//MinecraftForge.EVENT_BUS.post(new NFFTamedCommonDataConstructEvent(this));
			//this.sync();
		}
	
		private Level getLevel()
		{
			return this.getEntity().level();
		}

		@SuppressWarnings({ "resource", "unused" })
		private boolean isClientSide()
		{
			return this.getLevel().isClientSide;
		}
		
		private boolean isEntityFirstTick()
		{
			return NFUReflectionStatics.forceGet(this.getBM().asMob(), Entity.class, "f_19803_").cast();
		}
		
		@Override
		public CompoundTag serializeNBT() {
			CompoundTag save = new CompoundTag();
			// General
			save.putBoolean("25+", true);	// This is a label that the data format is 0.x.25+. Remove this after 0.x.30.
			save.put("additionalNBT", this.getAdditionalNBT());
			save.put("synchedData", this.saveSynchedData());
			//save.putUUID("identifier", this.getIdentifier());
			save.putString("initialEntityType", ForgeRegistries.ENTITY_TYPES.getKey(this.getInitialEntityType()).toString());
			save.putString("modId", ForgeRegistries.ENTITY_TYPES.getKey(this.getEntity().getType()).getNamespace());	// This field is not directly accessible, but only for reading mod id from entity saved data NBT.
			// Owner
			//save.putString("ownerName", this.getOwnerName());	// TODO: Remove after 0.x.30
			//save.putUUID("ownerUUID", this.getOwnerUUID());
			//if (this.getEncounteredDate() != null) save.putIntArray("encounteredDate", getEncounteredDate()); 
			//else save.putIntArray("encounteredDate", new int[] {0, 0, 0});	// TODO: remove after 0.x.30
			// Behavior
			NFUNBTStatics.putVec3(save, "randomStrollAnchor", this.getAnchor());
			//save.putString("aiState", this.getAIState().getId().toString());
			// Inventory
			this.getAdditionalInventory().saveToTag(save, "additionalInventory");
			return save;
		}

		@Override
		public void deserializeNBT(CompoundTag nbt) {
			this.nbt = nbt.getCompound("additionalNBT").copy();
			this.readSynchedData(nbt.getCompound("synchedData"));
			//this.setIdentifier(nbt.getUUID("identifier"));
			this.setInitialEntityType(ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation(nbt.getString("initialEntityType"))));
			//this.setOwnerName(nbt.getString("ownerName"));
			//this.setOwnerUUID(nbt.getUUID("ownerUUID"));
			//this.setEncounteredDate(nbt.getIntArray("encounteredDate"));
			this.setAnchor(NFUNBTStatics.getVec3(nbt, "randomStrollAnchor"));
			//this.setAIState(NFFTamedMobAIState.fromID(new ResourceLocation(nbt.getString("aiState"))));
			this.inventory.readFromTag(nbt.getCompound("additionalInventory"));
			if (this.getEncounteredDate()[0] == 0)
				this.recordEncounteredDate();
		}

		private CompoundTag saveSynchedData()
		{
			CompoundTag nbt = new CompoundTag();
			for (var entry: this.synchedData.entrySet())
			{
				CompoundTag entryNBT = new CompoundTag();
				entryNBT.putString("serializer", entry.getValue().getA().getKey().toString());
				entryNBT.put("value", NFUDataSerializer.toTagUnchecked(entry.getValue().getA(), entry.getValue().getB()));
				nbt.put(entry.getKey(), entryNBT);
			}
			return nbt;
		}
		
		private void readSynchedData(CompoundTag nbt)
		{
			for (String key: nbt.getAllKeys())
			{
				CompoundTag entryNBT = nbt.getCompound(key);
				NFUDataSerializer<?> s = NFUDataSerializer.fromId(new ResourceLocation(entryNBT.getString("serializer")));
				this.synchedData.put(key, new Tuple<>(s, s.fromTag(entryNBT.get("value"))));
			}
		}
		
		@Override
		public CompoundTag getAdditionalNBT() {
			return nbt;
		}

		@Override
		public INFFTamed getBM() {
			return mob;
		}

		@Override
		public Mob getEntity()
		{
			return getBM().asMob();
		}
		
		@Override
		public MutablePredicate<INFFTamed> getSunImmunity() {
				return sunImmunity;
		}



		@Override
		public Object getTempObject(String key) {
			return tempObjects.get(key);
		}

		@Override
		public void addTempObject(String key, Object obj) {
			tempObjects.put(key, obj);
		}

		@Override
		public void removeTempObject(String key) {
			tempObjects.remove(key);
		}
		
		@Override
		public UUID getIdentifier()
		{
			UUID id = this.getSynchedData(IDENTIFIER_SYNCHED_KEY, UUID.class).orElse(null);
			if (id != null && !id.equals(EMPTY_UUID))
				return id;
			else {
				LogUtils.getLogger().error(String.format("CNFFTamedCommonData: mob %s missing identifier. Regenerated.", this.getEntity().getName().getString()));
				this.generateIdentifier();
				return this.getSynchedData(IDENTIFIER_SYNCHED_KEY, UUID.class).orElseThrow();
			}
		}

		@Override
		public void generateIdentifier()
		{
			UUID id = this.getSynchedData(IDENTIFIER_SYNCHED_KEY, UUID.class).orElse(null);
			if (id == null || id.equals(EMPTY_UUID))
			{
				if (!this.isClientSide())
				{
					this.setSynchedData(IDENTIFIER_SYNCHED_KEY, UUID.class, UUID.randomUUID());
					this.sync();
					// In a rare case, this method appears to be called twice on running NFFTamingProcess#doTaming(). Output some verbose log
					String callingChain = StackWalker.getInstance().walk(stackFrameStream -> stackFrameStream.map(StackWalker.StackFrame::getMethodName)
						.reduce("Stack Trace:", (str, name) -> str + " -> " + name));
					LogUtils.getLogger().debug("NFF tamed mob identifier generated for mob \"" + this.getEntity().getName().getString()
					+ "\": " + this.getSynchedData(IDENTIFIER_SYNCHED_KEY, UUID.class).orElse(EMPTY_UUID));
					LogUtils.getLogger().debug("Method calling chain: " + callingChain);
				}
			}
			else {
				LogUtils.getLogger().error("Attempting to generate NFF tamed mob identifier twice. Skipped. Mob: \"" + this.getEntity().getName() + "\"");
			}
		}
		
		// Directly set, not safe
		private void setIdentifier(UUID val)
		{
			this.setSynchedData(IDENTIFIER_SYNCHED_KEY, UUID.class, val);
		}
		
		@Override
		public EntityType<? extends Mob> getInitialEntityType()
		{
			/* If missing type, it may be saved as "minecraft:pig" and bypass fixing. Generally pig should not be a
			 valid initial type since it's not INFFTamed. However, in the future INFFTamed may be available as a capability
			 and somehow attached to pig, so there's a tag to prevent this (in most cases it shouldn't happen)
			 */
			if (this.initialType != null &&
				!(this.initialType.equals(EntityType.PIG) && !this.getEntity().getType().is(NFFTagRegistry.COULD_BE_FROM_PIG)))
				return this.initialType;
			else {
				LogUtils.getLogger().error(String.format("CNFFTamedCommonData: mob %s missing initial type. Reset to current type.", this.getEntity().getName().getString()));
				this.recordEntityType();
				return this.initialType;
			}
		}
		
		@SuppressWarnings("unchecked")
		@Override
		public void recordEntityType()
		{
			this.initialType = (EntityType<? extends Mob>) this.getEntity().getType();
		}
		
		@SuppressWarnings("unchecked")
		private void setInitialEntityType(@Nonnull EntityType<?> entityType)
		{
			this.initialType = (EntityType<? extends Mob>) entityType;
		}
		
		@Override
		public String getOwnerName() {
			String str = this.getSynchedData(OWNER_NAME_SYNCHED_KEY, String.class).orElse(null);
			if (str != null && !str.isEmpty()) return str;
			else {
				LogUtils.getLogger().error(String.format("CNFFTamedCommonData: mob %s missing owner name. Return \"(Unknown)\". It will be updated once the owner entered the level", this.getEntity().getName().getString()));
				return "(Unknown)";
			}
		}

		@Override
		public void setOwnerName(String val) {
			
			this.setSynchedData(OWNER_NAME_SYNCHED_KEY, String.class, val);
		}		
		
		@Override
		public int[] getEncounteredDate() {
			return this.getSynchedData(ENCOUNTERED_DATE_SYNCHED_KEY, int[].class).orElse(new int[]{2023, 1, 1});
		}

		@Override
		public void setEncounteredDate(int[] val)
		{
			this.setSynchedData(ENCOUNTERED_DATE_SYNCHED_KEY, int[].class, val);
		}

		@Nonnull
		@Override
		public UUID getOwnerUUID()
		{
			UUID uuid = this.getSynchedData(OWNER_UUID_SYNCHED_KEY, UUID.class).orElse(EMPTY_UUID);
			if (uuid == null || uuid.equals(EMPTY_UUID)) 
				if (this.isClientSide())
				{
					LogUtils.getLogger().error("CNFFTamedCommonData: mob %s missing owner on client. Not initialized?");
					return EMPTY_UUID;
				}
				//else throw new RuntimeException(String.format("CNFFTamedCommonData: mob %s missing owner.", this.getEntity().getName().getString()));
				else {
					// TODO Change it to exception after 0.x.30
					LogUtils.getLogger().error(String.format("CNFFTamedCommonData: mob %s missing owner.", this.getEntity().getName().getString()));
					return EMPTY_UUID;
				}
			return uuid;
		}
		
		@Override
		public void setOwnerUUID(@Nonnull UUID value)
		{
			if (value == null || value.equals(EMPTY_UUID))
				throw new IllegalArgumentException(String.format("CNFFTamedCommonData#setOwnerUUID: requires non-null and non-zero.", this.getEntity().getName().getString()));
			this.setSynchedData(OWNER_UUID_SYNCHED_KEY, UUID.class, value);
		}
		
		@Override
		public NFFTamedMobAIState getAIState()
		{
			return NFFTamedMobAIState.fromID(new ResourceLocation(
					this.getSynchedData(AI_STATE_SYNCHED_KEY, String.class).orElseThrow()));
		}
		
		@Override
		public void setAIState(NFFTamedMobAIState state)
		{
			this.setSynchedData(AI_STATE_SYNCHED_KEY, String.class, state.getId().toString());
		}

		@Override
		public Vec3 getAnchor() {
			return anchor;
		}

		@Override
		public void setAnchor(Vec3 anchor) {
			this.anchor = anchor;
		}

		@Override
		public boolean hasInit() {
			return this.hasInit;
		}

		@Override
		public void setInitState(boolean value) {
			this.hasInit = value;
		}

		@Override
		public LivingEntity getAttackTarget() {
			if (this.getEntity().level().isClientSide) {
				int id = Optional.ofNullable(this.getSynchedGetter(ATTACK_TARGET_SYNCHED_KEY, Integer.class)).orElse(-1);
				if (id < 0) return null;
				Entity e = this.getEntity().level().getEntity(id);
				return e instanceof LivingEntity living ? living : null;
			}
			else return this.getEntity().getTarget();
		}

		@Override
		public LivingEntity getPreviousTarget() {
			return this.previousTarget;
		}

		@Override
		public void setPreviousTarget(LivingEntity target) {
			this.previousTarget = target;
		}
		
		@Override
		public <T> void createSynchedData(String key, NFUDataSerializer<T> dataSerializer, T defaultValue)
		{
			this.synchedData.put(key, new Tuple<>(dataSerializer, defaultValue));
		}

		@Override
		public <T> boolean hasSynchedData(String key, Class<T> dataType)
		{
			return this.synchedData.containsKey(key) && dataType.isAssignableFrom(this.synchedData.get(key).getClass());
		}
		
		@SuppressWarnings("resource")
		@Override
		public <T> void setSynchedData(String key, Class<T> dataType, T value)
		{
			if (this.isClientSide())
				throw new IllegalStateException("CNFFTamedCommonData synched data: set data only on server. "
						+ "On client it's auto-synched. Use setSynchedDataClient to set only on client.");
			if (!this.synchedData.containsKey(key))
				throw new IllegalArgumentException(String.format("CNFFTamedCommonData synched data: missing field key \"%s\". "
						+ "Use createSynchedData to define fields first.", key));
			if (!this.synchedData.get(key).getA().getObjectClass().isAssignableFrom(value.getClass()))
				throw new IllegalArgumentException(String.format("CNFFTamedCommonData#setSynchedData: putting data type %s to key \"%s\"."
						+ "Requires type %s.", value.getClass().getSimpleName(), key, this.synchedData.get(key).getA().getObjectClass()));
			this.synchedData.get(key).setB(value);
		}

		@Override
		@SuppressWarnings("unchecked")
		public <T> Optional<T> getSynchedData(String key, Class<T> dataClass)
		{
			if (!this.synchedData.containsKey(key))
			{
				LogUtils.getLogger().error(String.format("CNFFTamedCommonData synched data: Key \"%s\" not found.", key));
				return null;
			}
			if (!dataClass.isAssignableFrom(this.synchedData.get(key).getB().getClass()))
				throw new IllegalArgumentException("CNFFTamedCommonData synched data: data type and serializer don't match.");
			return Optional.of((T) this.synchedData.get(key).getB());
		}
		
		@SuppressWarnings("resource")
		@Override
		public void setSynchedDataClient(String key, NFUDataSerializer<?> serializer, Object value)
		{
			if (!this.isClientSide())
				throw new IllegalStateException("CNFFTamedCommonData synched data: setSynchedDataClient only on client. On server use setSynchedData() instead.");
			if (!serializer.getObjectClass().isAssignableFrom(value.getClass()))
				throw new IllegalArgumentException("CNFFTamedCommonData synched data#setSynchedDataClient: data type and serializer don't match.");
			this.synchedData.put(key, new Tuple<>(serializer, value));
		}

		@SuppressWarnings("unchecked")
		@Override
		public <T> Optional<T> getSynchedDataUnchecked(String key) {
			if (!this.synchedData.containsKey(key))
				throw new IllegalArgumentException(String.format("CNFFTamedCommonData#getSynchedDataUnchecked: Field \"%s\" not defined.", key));
			return Optional.of((T) this.synchedData.get(key).getB());
		}

		@Override
		public <T> void createSynchedGetter(String key, NFUDataSerializer<T> serializer, @Nonnull T defaultValue, Supplier<T> accessorOnServer) {
			this.synchedGetters.put(key,
				new SynchedGetter(key, serializer, accessorOnServer, defaultValue, defaultValue));
		}

		/**
		 * Get value from a synched getter.
		 * @param key Synched getter key.
		 * @param type Object type to get.
		 * @return Gotten value. Null if the getter doesn't exist, or accessing encountered an error.
		 */
		@Nullable
		@Override
		@SuppressWarnings("unchecked")
		public <T> T getSynchedGetter(String key, Class<T> type) {
			if (!this.synchedGetters.containsKey(key)) return null;
			Object raw = null;
			try {
				if (this.getEntity().level().isClientSide())
					raw = this.synchedGetters.get(key).cache;
				else
					raw = this.synchedGetters.getOrDefault(key, SynchedGetter.dummy()).accessor.get();
			} catch (RuntimeException e) {
				// Exceptions thrown by accessor calling
				NFUDebugStatics.errorOnce(CNFFTamedCommonData.class, e.getMessage());
				raw = null;
			}
			try {
				return (T)raw;
			} catch (ClassCastException e) {
				// Exception thrown when accessor calling succeeded but type mismatched
				throw new IllegalArgumentException(String.format("CNFFTamedCommonData#getSynchedGetter: illegal getting class %s. Found object class: %s",
					type.getName(), raw.getClass().getName()), e);
			}

		}

		@Override
		@Nullable
		public Object getSynchedGetter(String key) {
			// Raw casting to Object is safe
			return this.getSynchedGetter(key, Object.class);
		}

		@Override
		public void setSynchedGetterClient(String key, @Nonnull Object o) {
			this.synchedGetters.get(key).setCacheValue(o);
		}

		@Override
		public void setSyncInterval(int ticks)
		{
			if (ticks <= 0)
				throw new IllegalArgumentException();
			this.syncInterval = ticks;
		}
		
		@SuppressWarnings("resource")
		@Override
		public void tick()
		{
			if (!this.isClientSide() && this.getEntity().tickCount % this.syncInterval == 0 && !this.isEntityFirstTick())	// Don't update on the first tick to prevent some unexpected uninitialized problems
			{
				// Sync data
				if (!this.synchedData.isEmpty())
				{
					this.sync();
				}
				// TODO remove this. It's for porting legacy format.
				if (this.getOwnerName() == "" && this.getBM().isOwnerInDimension())
					this.setOwnerName(this.getBM().getOwnerInDimension().getName().getString());
			}
			// Sync inventory. This is to prevent cases when inventory is not correctly synched to mob.
			this.getAdditionalInventory().syncToMob(this.getEntity());
		}

		@Override
		public AvailableSide getTickingSide()
		{
			return AvailableSide.BOTH;
		}

		@Override
		public NFFTamedMobInventory getAdditionalInventory() {
			return this.inventory;
		}
		
		private void sync()
		{
			ClientboundDataSyncPacket packet = new ClientboundDataSyncPacket(this);
			List<? extends Player> players = this.getLevel().players();
			for (Player player: players)
			{
				if (player instanceof ServerPlayer sp)
				{
					NFFChannels.BM_CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), packet);
				}
			}
		}
	}
	
	public static class Prvd implements ICapabilitySerializable<CompoundTag>
	{
		
		public CNFFTamedCommonData values;
		
		private Prvd(INFFTamed mob)
		{
			values = new Values(mob);
			throw new RuntimeException("No longer used as a capability.");
		}
		
		@Override
		public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
			throw new RuntimeException("No longer used as a capability.");
		}

		@Override
		public CompoundTag serializeNBT() {
			return values.serializeNBT();
		}

		@Override
		public void deserializeNBT(CompoundTag nbt) {
			values.deserializeNBT(nbt);
		}
	}
	
	public static class ClientboundDataSyncPacket implements Packet<ClientGamePacketListener> 
	{
		public final CNFFTamedCommonData.Values dataCap;	// Only on server
		public final int entityId;
		public final Map<String, Tuple<NFUDataSerializer<?>, Object>> objects; // Synched data, only on client
		public final Map<String, Object> getters; // Synched getters, only on client
		public final List<ItemStack> inventory;

		public ClientboundDataSyncPacket(CNFFTamedCommonData.Values v)
		{
			this.dataCap = v;
			this.entityId = v.getBM().asMob().getId();
			this.objects = null;
			this.getters = null;
			this.inventory = v.getAdditionalInventory().toList();
		}
		
		public ClientboundDataSyncPacket(FriendlyByteBuf buf)
		{
			this.dataCap = null;
			this.objects = new HashMap<>();
			this.getters = new HashMap<>();
			// Start reading
			this.entityId = buf.readInt();
			// Read data
			int dataSize = buf.readInt();
			for (int i = 0; i < dataSize; ++i)
			{
				String key = buf.readUtf();
				NFUDataSerializer<?> type = NFUDataSerializer.fromId(new ResourceLocation(buf.readUtf()));
				Object obj = type.read(buf);
				objects.put(key, new Tuple<>(type, obj));
			}
			// Read getters
			int fieldSize = buf.readInt();
			for (int i = 0; i < fieldSize; ++i) {
				String key = buf.readUtf();
				NFUDataSerializer<?> type = NFUDataSerializer.fromId(new ResourceLocation(buf.readUtf()));
				Object obj = type.read(buf);
				getters.put(key, obj);
			}
			// Read inventory
			this.inventory = new ArrayList<>();
			int inventorySize = buf.readInt();
			for (int i = 0; i < inventorySize; ++i) {
				inventory.add(buf.readItem());
			}
		}
		
		@Override
		public void write(FriendlyByteBuf buf) 
		{
			buf.writeInt(entityId);
			// Write synched data
			buf.writeInt(dataCap.synchedData.size());
			for (var entry: dataCap.synchedData.entrySet()) {
				buf.writeUtf(entry.getKey());
				buf.writeUtf(entry.getValue().getA().getKey().toString());
				NFUDataSerializer.writeUnchecked(entry.getValue().getA(), buf, entry.getValue().getB());
			}
			// Write synched getters
			/* Getters may throw exception on getting, and at this time this getter should be skipped.
			 So the values should be gathered first and the amount will depend on valid entry amount instead
			 of overall getter amount. */
			List<Tuple2<String, Object>> getters = new ArrayList<>(20);
			for (var entry: dataCap.synchedGetters.entrySet()) {
				Optional.ofNullable(dataCap.getSynchedGetter(entry.getKey()))
					.ifPresent(o -> getters.add(Tuple2.of(entry.getKey(), o)));	// Get value and collect if valid
			}
			buf.writeInt(getters.size());
			for (Tuple2<String, Object> entry: getters) {
				var serializer = dataCap.synchedGetters.get(entry.getA()).serializer;
				buf.writeUtf(entry.getA());	// Write key
				buf.writeUtf(serializer.getKey().toString());	// Write serializer key
				NFUDataSerializer.writeUnchecked(serializer, buf, entry.getB());	// Write value
			}
			// Write inventory
			buf.writeInt(this.inventory.size());
            for (ItemStack itemStack : this.inventory) {
                buf.writeItemStack(itemStack, true);
            }
		}

		@Override
		public void handle(ClientGamePacketListener pHandler) {
			throw new RuntimeException();
			//NFFClientGamePacketHandler.handleBefriendedDataSync(this, pHandler);
		}
	}

	// Utils //
	
	/**
	 * Get this capability's save data from the whole entity save data.
	 * @param mobTag Entity nbt saved by {@link Entity#save}
	 * @return This capability's save data, saved by {@code serializeNBT}
	 */
	public static CompoundTag getCapFromMobTag(CompoundTag mobTag)
	{
		CompoundTag forgecaps = mobTag.getCompound("ForgeCaps");
		CompoundTag data = forgecaps.getCompound(new ResourceLocation(NFFServices.MOD_ID, NFFCapabilityAttachments.KEY_NFF_MOB_COMMON_DATA).toString());
		if (!data.isEmpty())
			return data;
		else return forgecaps.getCompound("befriendmobs:cap_befriended_mob_data");	// TODO Legacy format: remove later
	}
	
	@SuppressWarnings("unchecked")
	public static <T> T getSynchedDataFromMobTag(CompoundTag mobTag, String key, Class<T> dataClass)
	{
		try {
			CompoundTag synched = getCapFromMobTag(mobTag).getCompound("synchedData");
			NFUDataSerializer<?> serializer = NFUDataSerializer.fromId(new ResourceLocation(synched.getCompound(key).getString("serializer")));
			Object res = serializer.fromTag(synched.getCompound(key).get("value"));
			return (T) res;
		} catch (RuntimeException e)
		{
			LogUtils.getLogger().error("Error in CNFFTamedCommonData#getSynchedDataFromMobTag. Return null. Cause:");
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Get the owner UUID stored in this cap's save data from the whole entity save data.
	 * @param mobTag Entity nbt saved by {@link Entity#save}
	 * @return Owner UUID saved in this cap
	 */
	public static UUID getOwnerUUIDFromMobTag(CompoundTag mobTag)
	{
		return getSynchedDataFromMobTag(mobTag, "ownerUUID", UUID.class);
	}
	
	/**
	 * Get the owner name stored in this cap's save data from the whole entity save data.
	 * @param mobTag Entity nbt saved by {@link Entity#save}
	 * @return Owner name saved in this cap
	 */
	public static String getOwnerNameFromMobTag(CompoundTag mobTag)
	{
		String res = getSynchedDataFromMobTag(mobTag, "ownerName", String.class);
		return res != null ? res : "(Unknown)";
	}
	
	/**
	 * Get the owner name stored in this cap's save data from the whole entity save data.
	 * @param mobTag Entity nbt saved by {@link Entity#save}
	 * @return Mod id saved in this cap
	 */
	public static String getModIdFromMobTag(CompoundTag mobTag)
	{
		CompoundTag capTag = getCapFromMobTag(mobTag);
		if (capTag == null) return null;
		return capTag.getString("modId");
	}

	static class SynchedGetter  {
		private String key;
		private NFUDataSerializer<?> serializer;
		private Supplier<?> accessor;
		private Object cache;
		private Object defaultValue;

		public <T> SynchedGetter(String key, NFUDataSerializer<T> serializer, Supplier<? extends T> accessor, T cache, T defaultValue) {
			this.key = key;
			this.serializer = serializer;
			this.accessor = accessor;
			this.cache = cache;
			this.defaultValue = defaultValue;
		}

		public static SynchedGetter dummy() {
			return new SynchedGetter(null, null, null, null, null);
		}

		public void setCacheValue(Object value) {
			this.cache = value;
		}
	}
}
