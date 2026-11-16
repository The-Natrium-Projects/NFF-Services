package net.sodiumzh.nff.services.entity.taming;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.registries.ForgeRegistries;
import net.sodiumzh.nfu.container.ITable2D;
import net.sodiumzh.nfu.container.Table2D;
import net.sodiumzh.nfu.container.Tuple2;
import net.sodiumzh.nfu.exception.MissingRegistryEntryException;
import org.spongepowered.asm.mixin.injection.struct.InjectorGroupInfo;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Defines entity types before and after taming. Mobs which are registered to be a tamable type (type before
 * taming) will be automatically attached a {@link NFFTamableComponent}.
 */
public class NFFTamingMapping {


	private static final ITable2D<EntityType<?>, EntityType<?>, Supplier<NFFTamingProcess>> TABLE = new Table2D<>();

	/* Register */
	
	public static void register(@Nonnull ResourceLocation from, @Nonnull ResourceLocation convertTo, @Nonnull Supplier<NFFTamingProcess> processAccessor, boolean override)
	{
		if (!ForgeRegistries.ENTITY_TYPES.containsKey(from))
			throw new MissingRegistryEntryException("NFFTamingMapping registering illegal key " + from);
		if (!ForgeRegistries.ENTITY_TYPES.containsKey(convertTo))
			throw new MissingRegistryEntryException("NFFTamingMapping registering illegal key " + convertTo);
		EntityType<?> fromType = ForgeRegistries.ENTITY_TYPES.getValue(from);
		EntityType<?> toType = ForgeRegistries.ENTITY_TYPES.getValue(convertTo);
		if (TABLE.containsRow(ForgeRegistries.ENTITY_TYPES.getValue(from))) {
			if (override) {
				TABLE.removeRow(fromType);
			} else {
				return;
			}
		}
		TABLE.put(fromType, toType, processAccessor);
	}
	
	public static void register(@Nonnull ResourceLocation fromType, @Nonnull ResourceLocation convertToType, @Nonnull Supplier<NFFTamingProcess> process)
	{
		register(fromType, convertToType, process, false);
	}

	/* Search */

	// Get which type this mob should convert to (from type)
	@SuppressWarnings("unchecked")
	@Nullable
	public static EntityType<? extends Mob> getConvertTo(EntityType<?> fromType) {
		return (EntityType<? extends Mob>) TABLE.getRow(fromType).keySet().stream().findAny().orElse(null);
	}
	
	// Get which type this mob should convert to (from mob)
	@Nullable
	public static EntityType<? extends Mob> getConvertTo(Mob fromMob)
	{
		return getConvertTo(fromMob.getType());
	}

	/**
	 * // Get which taming process this mob should use (from type) as instance.
	 * @param fromType Type of the "wild" mob.
	 * @return Process.
	 */
	@Nullable
	public static NFFTamingProcess getProcess(EntityType<?> fromType) {
		EntityType<? extends Mob> after = getConvertTo(fromType);
		if (after == null) return null;
		return TABLE.get(fromType, after).map(Supplier::get).orElse(null);
	}

	/**
	 * // Get which taming process this mob ("wild") should use as instance.
	 * @param fromMob The "wild" mob.
	 * @return Process.
	 */
	@SuppressWarnings("unchecked")
	public static NFFTamingProcess getProcess(Mob fromMob)
	{
		return getProcess(fromMob.getType());
	}
	
	/** Get if the type ("wild" type) is tamable. */
	public static boolean contains(EntityType<?> fromType) {
		return !TABLE.getRow(fromType).isEmpty();
	}
	
	/** Get if the mob is tamable. */
	@SuppressWarnings("unchecked")
	public static boolean contains(Mob fromMob)
	{
		return contains(fromMob.getType());
	}

	/** Get if the type could be (not necessarily) a tamed mob. */
	public static boolean containsAfter(EntityType<?> toType)
	{
		return !TABLE.getColumn(toType).isEmpty();
	}

	/** Get if the mob could be (not necessarily) a tamed mob. */
	public static boolean containsAfter(Mob fromMob) {
		return containsAfter(fromMob.getType());
	}

	@Nullable
	public static EntityType<? extends Mob> getTypeBefore(EntityType<?> afterType) {
		return (EntityType<? extends Mob>) TABLE.getColumn(afterType).keySet().stream().findAny().orElse(null);
	}
	
	@Nullable
	public static EntityType<? extends Mob> getTypeBefore(Mob tamed)
	{
		return getTypeBefore(tamed.getType());
	}	
	
	public static Set<EntityType<? extends Mob>> getAllTamableTypes() {
		return TABLE.keyPairs().stream().map(ITable2D.KeyPair::row)
			.map(et -> (EntityType<? extends Mob>)et)
			.collect(Collectors.toSet());
	}
	
	public static Set<EntityType<? extends Mob>> getAllTamedTypes()
	{
		return TABLE.keyPairs().stream().map(ITable2D.KeyPair::column)
			.map(et -> (EntityType<? extends Mob>)et)
			.collect(Collectors.toSet());
	}




}
