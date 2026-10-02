package net.sodiumzh.nff.services.entity.taming;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.sodiumzh.nff.services.inventory.NFFTamedMobInventory;
import net.sodiumzh.nff.services.network.ClientboundNFFGUIOpenPacket;
import net.sodiumzh.nff.services.network.NFFChannels;
import net.sodiumzh.nfu.util.NFUEntityStatics;
import net.sodiumzh.nfu.util.NFUNetworkStatics;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A function library for befriended mobs
 */

public class NFFTamedStatics
{

	/* AI */

	/**
	 *  Default settings of the rule about what the mob can attack.
	 */
	public static boolean wantsToAttackDefault(INFFTamed mob, LivingEntity target) {
		if (target instanceof Creeper && !mob.canAttackCreeper())
			return false;
		else if (target instanceof Ghast && !mob.canAttackGhast())
			return false;
		else return !isLivingAlliedToBM(mob, target);
	}

	/**
	 * Convert a befriended mob to other type. This action will keep its data.
	 * @param target The mob to convert.
	 * @param newType The type converting to, must implement {@code INFFTamed} interface.
	 * @return The new mob reference.
	 */
	public static INFFTamed convertToOtherBefriendedType(INFFTamed target, EntityType<? extends Mob> newType)
	{
		// Additional inventory will be invalidated upon convertion, so backup as a tag
		CompoundTag mobTag = new CompoundTag();
		target.asMob().saveWithoutId(mobTag);
		// Do convertion
		
		Mob newMob = NFUEntityStatics.replaceMob(newType, target.asMob());
		if (INFFTamed.get(newMob).isEmpty())
			throw new UnsupportedOperationException("NFFTamedStatics::convertToOtherBefriendedType supports mobs implementing INFFTamed.");
		newMob.load(mobTag);
		// Write the inventory back
		/*if(inventoryTag.getInt("size") != newMob.getAdditionalInventory().getContainerSize())
			throw new UnsupportedOperationException("NFFTamedStatics::convertToOtherBefriendedType additional inventory must have same size before and after conversion.");
		newMob.getAdditionalInventory().readFromTag(inventoryTag);
		// Do other settings
		newMob.setAIState(target.getAIState(), false);
		newMob.init(target.getOwnerUUID(), target.asMob());
		newMob.updateFromInventory();*/
		// setInit() needs to call manually
		
		return (INFFTamed)newMob;
	}
	
	/* Inventory */

	/**
	 * Open the inventory GUI of the mob.
	 * <p>Warning: DO NOT call this if {@link INFFTamed#makeMenu} method returns null, otherwise it will crash the game.
	 */
	@SuppressWarnings("resource")
	public static void openBefriendedInventory(Player player, INFFTamed mob) {
		LivingEntity living = (LivingEntity) mob;
		if (!player.level().isClientSide && player instanceof ServerPlayer sp
				&& (!living.isVehicle() || living.hasPassenger(player)))
		{
			
			if (player.containerMenu != player.inventoryMenu)
			{
				player.closeContainer();
			}

			sp.nextContainerCounter();
			ClientboundNFFGUIOpenPacket packet = new ClientboundNFFGUIOpenPacket(sp.containerCounter,
					mob.getDataAccessor().getInventorySize(), living.getId());
			NFUNetworkStatics.sendToPlayer(NFFChannels.BM_CHANNEL, packet, sp);
			var menu = mob.makeMenu(sp.containerCounter, sp.getInventory(), mob.getAdditionalInventory()
				.orElseGet(() -> new NFFTamedMobInventory(0, mob)));
			if (menu == null) return;
			sp.containerMenu = menu;
			sp.initMenu(sp.containerMenu);
			MinecraftForge.EVENT_BUS.post(new PlayerContainerEvent.Open(player, player.containerMenu));
		}
	}

	/**
	 * Get owner if the owner is closer than the given distance of the mob. Otherwise return {@link Optional#empty}.
	 * @param mob Mob (implements {@link INFFTamed}) to test. No need to do {@link INFFTamed#isOwnerInDimension} check.
	 * @param radius Search area
	 * @param sphericalArea If true, it will search in a sphere with given radius. Otherwise search in a box with given radius.
	 * @return Owner if the owner is present and in the given area. Otherwise {@link Optional#empty}.
	 */
	public static Optional<Player> getOwnerInArea(INFFTamed mob, double radius, boolean sphericalArea)
	{
		if (!mob.isOwnerInDimension())
			return Optional.empty();
		List<Entity> list = mob.asMob().level().getEntities(mob.asMob(), NFUEntityStatics.getNeighboringArea(mob.asMob(), radius), e -> e == mob.getOwner());
		if (list.isEmpty())
			return Optional.empty();
		else if (!sphericalArea)
			return Optional.of((Player)(list.get(0)));
		else if (mob.asMob().distanceToSqr(list.get(0)) <= radius * radius)
			return Optional.of((Player)(list.get(0)));
		else return Optional.empty();
	}

	public static List<Mob> getOwningMobsInArea(Player player, EntityType<? extends Mob> type, double radius, boolean sphericalArea)
	{
		Stream<Entity> stream = player.level().getEntities(player, player.getBoundingBox().inflate(radius, radius, radius),
				e -> (e.getType() == type && INFFTamed.get(e).filter(bm -> bm.getOwner() == player).isPresent())).stream();
		if (sphericalArea)
			stream = stream.filter(e -> e.distanceToSqr(player) <= radius * radius);
		return stream.map(e -> (Mob)e).collect(Collectors.toList());
	}

    /**
     * Get self, direct and indirect owners' UUID. The list's order is {self, owner, owner's owner, ...}
     */
    private static List<UUID> getSelfAndOwnersUUID(LivingEntity e) {
        if (e == null) return List.of();
        List<UUID> res = new ArrayList<>();
        LivingEntity current = e;
        res.add(e.getUUID());
        while (e != null) {
            UUID emptyUUID = new UUID(0L, 0L);
            // For vanilla ownables (directly implemented in the mob class)
            if (e instanceof OwnableEntity ownable) {
                if (ownable.getOwnerUUID() != null && ownable.getOwnerUUID().equals(emptyUUID)) {
                    res.add(ownable.getOwnerUUID());
                    e = ownable.getOwner();
                }
            }
            // For an NFF implementation that's not directly implemented in the mob class
            else if (INFFTamed.get(e).isPresent()) {
                INFFTamed tamed = INFFTamed.get(e).orElseThrow();
                if (tamed.getOwnerUUID() != null && !tamed.getOwnerUUID().equals(emptyUUID)) {
                    res.add(tamed.getOwnerUUID());
                    e = tamed.getOwnerInDimension();
                }
            }
            else e = null;
        }
        return res;
    }

	/**
	 * Check if a living entity ({@code test}) should be considered as ally by an {@code OwnableEntity} ({@code entity}) under BMF rule.
	 * <p>This method is private because it doesn't involve NFF mobs, so directly calling this may cause unexpected
	 * behavior changes on vanilla mobs. Call {@link INFFTamed#isTamedAlliedTo(LivingEntity)} instead.
	 * <p>On server only. On client always {@code false}.
	 */
	static boolean isLivingAlliedToOwnableUnsafe(OwnableEntity ownable, LivingEntity target, boolean allowsPVP)
	{
		if (ownable == null || target == null) return false;
        Level level = target.level();

		if (level.isClientSide) return false;

        // Get the ownership chains of self and target
        LivingEntity ownableEntity = ownable instanceof LivingEntity le ? le : (ownable instanceof INFFTamed tamed ? tamed.asMob() : null);
        if (ownableEntity == null) return false;
        List<UUID> selfAndOwners = getSelfAndOwnersUUID(ownableEntity);
        List<UUID> targetAndOwners = getSelfAndOwnersUUID(target);
        if (selfAndOwners.isEmpty() || targetAndOwners.isEmpty()) return false;
        // Case when the ownable and target's ownership chains involve the same entity
        if (Stream.concat(selfAndOwners.stream(), targetAndOwners.stream()).collect(Collectors.toSet()).size() < selfAndOwners.size() + targetAndOwners.size())
            return true;
        // If not allowing PVP, if self is player-owned, don't attack any player or player-owned mob
        if (!allowsPVP) {
            if (level.getServer() != null
                && NFUEntityStatics.getAllKnownPlayers().contains(selfAndOwners.get(selfAndOwners.size() - 1))
                && NFUEntityStatics.getAllKnownPlayers().contains(targetAndOwners.get(targetAndOwners.size() - 1)))
            {
                return true;
            }
        }
        return false;

	}

	/**
	 * Check if a BM is considered as ally by an {@code OwnableEntity}.
	 * <p>On server only. On client always {@code false}.
	 * @deprecated Use {@link INFFTamed#isAllyTo} instead.
	 */
	@Deprecated
	public static boolean isBMAlliedToOwnable(OwnableEntity entity, INFFTamed test)
	{
		if (entity instanceof INFFTamed i)
			return test.isAllyTo(i.asMob());
		else if (entity instanceof LivingEntity le)
			return test.isAllyTo(le);
		else return false;
	}

	/**
	 * Check if a {@code LivingEntity} is considered as ally by a BM.
	 * <p>On server only. On client always {@code false}.
	 * @deprecated Use {@link INFFTamed#isAllyTo} instead.
	 */
	@Deprecated
	public static boolean isLivingAlliedToBM(INFFTamed bm, LivingEntity test)
	{
		return bm.isAllyTo(test);
	}

	/**
	 * Get the LivingEntity instance from {@link OwnableEntity} interface. It handles both
	 * {@link INFFTamed} cases and Livings directly implementing {@link OwnableEntity}.
	 * <p>Generally it shouldn't return {@link Optional#empty}, but as we cannot guarantee
	 * other mods don't attach OwnableEntity to non-living classes, we still use optional here
	 */
	@Nullable
	public static Optional<LivingEntity> livingFromOwnableInterface(OwnableEntity ownable) {
		if (ownable instanceof LivingEntity l) return Optional.of(l);
		else if (ownable instanceof INFFTamed t)
			return Optional.ofNullable(t.asMob());
		else return Optional.empty();
	}

	/**
	 * Get the {@link OwnableEntity} interface from LivingEntity instance. It handles both
	 * {@link INFFTamed} cases and Livings directly implementing {@link OwnableEntity}.
	 * <p>Empty if the mob doesn't use {@link INFFTamed} or directly implement {@link OwnableEntity}
	 */
	public static Optional<OwnableEntity> ownableFromLiving(LivingEntity living) {
		return Optional.ofNullable(INFFTamed.get(living).map(i -> (OwnableEntity)i)
			.orElseGet(() -> living instanceof OwnableEntity o ? o : null));
	}
}
