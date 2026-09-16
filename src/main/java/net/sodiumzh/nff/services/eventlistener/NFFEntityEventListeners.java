package net.sodiumzh.nff.services.eventlistener;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.item.ItemExpireEvent;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.entity.living.ZombieEvent.SummonAidEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.EntityInteract;
import net.minecraftforge.eventbus.api.Event.Result;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.LogicalSide;
import net.minecraftforge.fml.common.Mod;
import net.sodiumzh.nff.services.NFFServices;
import net.sodiumzh.nff.services.entity.ai.NFFTamedMobAIState;
import net.sodiumzh.nff.services.entity.taming.*;
import net.sodiumzh.nff.services.entity.taming.INFFTamed.DeathRespawnerGenerationType;
import net.sodiumzh.nff.services.event.entity.NFFTamedDeathEvent;
import net.sodiumzh.nff.services.event.entity.NFFTamedDropRespawnerOnDyingEvent;
import net.sodiumzh.nff.services.event.entity.ai.NFFTamedChangeAiStateEvent;
import net.sodiumzh.nff.services.item.NFFMobRespawnerInstance;
import net.sodiumzh.nff.services.item.NFFMobRespawnerItem;
import net.sodiumzh.nff.services.registry.NFFCapRegistry;
import net.sodiumzh.nff.services.registry.NFFItemRegistry;
import net.sodiumzh.nff.services.registry.NFFTagRegistry;
import net.sodiumzh.nfu.entity.anger.MobAngerReason;
import net.sodiumzh.nfu.entity.component.CEntityComponentManager;
import net.sodiumzh.nfu.entity.component.EntityComponentFinalizeSetupEvent;
import net.sodiumzh.nfu.entity.taming.TamingInteractionResult;
import net.sodiumzh.nfu.mixin.event.entity.EntityDiscardEvent;
import net.sodiumzh.nfu.mixin.event.entity.EntityFinishConstructionEvent;
import net.sodiumzh.nfu.mixin.event.entity.LivingStartDeathEvent;
import net.sodiumzh.nfu.mixin.event.entity.MobSunBurnTickEvent;
import net.sodiumzh.nfu.util.NFUContainerStatics;
import net.sodiumzh.nfu.util.NFUEntityStatics;
import org.apache.commons.lang3.mutable.MutableObject;

import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("removal")
@Mod.EventBusSubscriber(modid = NFFServices.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class NFFEntityEventListeners
{

	@SubscribeEvent
	public static void onEntityInteract(EntityInteract event) {
		if (event.isCanceled())
			return;
		Entity target = event.getTarget();
		Player player = event.getPlayer();
		MutableObject<InteractionResult> result = new MutableObject<InteractionResult>(InteractionResult.PASS);
		boolean isClientSide = event.getSide() == LogicalSide.CLIENT;
		boolean isMainHand = event.getHand() == InteractionHand.MAIN_HAND;
		MutableObject<Boolean> shouldPostInteractEvent = new MutableObject<Boolean>(Boolean.FALSE);

		// Mob interaction start //
		if (target != null && target instanceof Mob) 
		{
	
			Mob mob = (Mob)target;
			@SuppressWarnings("unchecked")
			EntityType<Mob> type = (EntityType<Mob>) mob.getType();

			// Handle befriendable mob start //
			NFFTamableComponent.getOptional(mob).ifPresent((l) ->
				{
					TamingInteractionResult res = NFFTamingMapping.getProcess(type)
							.handleInteract(player, mob, event.getHand());
					if (res.getTamedMob().isPresent()) // Directly exit if befriended, as this mob is no longer valid
					{
						event.setCanceled(true);
						event.setCancellationResult(res.getResult());
						return;
					} else if (res.getResult().consumesAction())
					{
						event.setCanceled(true);
						result.setValue(res.getResult());
						shouldPostInteractEvent.setValue(true);
					}

				});

			// Handle befriendable mob end //
			// Handle befriended mob start //
			INFFTamed.get(mob).ifPresent(t -> {
				if (player.isShiftKeyDown() && player.getMainHandItem().getItem() == NFFItemRegistry.DEBUG_BEFRIENDER.get()) {
					result.setValue(InteractionResult.sidedSuccess(isClientSide));
				}
			});
			// Handle befriended mob end //
		}
		// Mob interaction end //

		// Server events end //
		// Client events start //
		else {
		}
		// Client events end //
		event.setCanceled(result.getValue().equals(InteractionResult.sidedSuccess(isClientSide)));
		event.setCancellationResult(result.getValue());
	}

	@SuppressWarnings("resource")
	@SubscribeEvent(priority = EventPriority.LOW)

	public static void onLivingChangeTarget_Low(LivingChangeTargetEvent event)
	{
		if (!event.getEntity().getLevel().isClientSide) {
			// Handle tamable always-hostile
			if (!event.getEntity().getLevel().isClientSide && event.getEntity() instanceof Mob mob) {
				NFFTamableComponent.getOptional(mob)
					.flatMap(c -> c.getAlwaysHostileToLiving()        // when has an always-hostile target
						.filter(e -> !e.equals(event.getNewTarget()))    // and trying to set to another target (or remove target)
						.filter(mob::hasLineOfSight))    // and can see the target
					.ifPresent(e -> {
						event.setNewTarget(e);
						event.setCanceled(false);
					});    // Then turn to the always-hostile target
			}
		}
	}

	@SubscribeEvent
	public static void onLivingChangeTarget(LivingChangeTargetEvent event)
	{
		
        // Handle Golems //
        // TODO: Port LivingChangeTargetEvent to 1.18.2 and move this inside
        if (event.getEntity() instanceof AbstractGolem g)
        {
        	if (event.getNewTarget() instanceof INFFTamed bm)
        	{
        		
        		switch (bm.golemAttitude())
        		{
        		case DEFAULT:
        		{
        			// No change
        			break;
        		}
        		case NEUTRAL:
        		{
        			// Golems keep neutral to befriended mobs, but if it's attacked it will still attack back
	        		if (g.getLastHurtByMob() == null || !g.getLastHurtByMob().equals(event.getNewTarget()))
	        		{
	        			event.setCanceled(true);
	        		}
	        		break;
        		}
        		case PASSIVE:
        		{
        			// Always cancel
        			event.setCanceled(true);
        			break;
        		}
        		case CUSTOM:
        		{
        			// Use custom config
        			if (!bm.shouldGolemAttack(g))
        			{
        				event.setCanceled(true);
        			}
        			break;
        		}
        		default:
        		{
        			throw new RuntimeException();
        		}
        		}
        	}
        }
        // Handle Golems End
	}
	
	@SubscribeEvent
	public static void onLivingSetTarget(LivingChangeTargetEvent event)
	{
		@SuppressWarnings("deprecation")
		LivingEntity target = event.getNewTarget();
		// Handle mobs //
		if (target != null && event.getEntity() instanceof Mob mob)
		{
			if (INFFTamed.get(mob).isPresent()) {
				if (INFFTamed.get(mob).filter(i -> i.isAllyTo(target)).isPresent())
					event.setNewTarget(INFFTamed.get(mob).orElseThrow().getPreviousTarget());
				else INFFTamed.get(mob).orElseThrow().setPreviousTarget(target);
			}
			else if (INFFTamed.get(target).filter(i -> i.isAllyTo(mob)).isPresent()) {
				event.setCanceled(true);
		}
	        // Handle befriended mobs end //
	        // Handle TamableAnimal //	
	        if (mob instanceof OwnableEntity oe
				&& INFFTamed.get(target).filter(i -> i.isAllyTo(NFFTamedStatics.livingFromOwnableInterface(oe).orElse(null))).isPresent())
	        {
				event.setCanceled(true);
	        }
	        // Handle TamableAnimal end //

	        // Handle Golems //
	        if (mob instanceof AbstractGolem g && !mob.getType().is(NFFTagRegistry.IGNORES_GOLEM_ATTITUDE))
	        {
				INFFTamed.get(target).ifPresent(bm -> {
	        		switch (bm.golemAttitude()) {
	        		case DEFAULT: {
	        			// No change
	        			break;
	        		}
	        		case NEUTRAL: {
	        			// Golems keep neutral to befriended mobs, but if it's attacked it will still attack back
		        		if (g.getLastHurtByMob() == null || !g.getLastHurtByMob().equals(target))
		        		{
		        			event.setCanceled(true);
		        		}
		        		break;
	        		}
	        		case PASSIVE: {
	        			// Always cancel
	        			event.setCanceled(true);
	        			break;
	        		}
	        		case CUSTOM: {
	        			// Use custom config
	        			if (!bm.shouldGolemAttack(g))
	        			{
	        				event.setCanceled(true);
	        			}
	        			break;
	        		}
	        		default: {
	        			throw new RuntimeException();
	        		}
	        		}
	        	});
	        }
	        // Handle Golems End
	        // Handle hostility tags
			if (mob.getType().is(NFFTagRegistry.PASSIVE_TO_NFF_MOBS) && INFFTamed.get(target).isPresent())
				event.setCanceled(true);
	        if (mob.getType().is(NFFTagRegistry.NEUTRAL_TO_NFF_MOBS) && INFFTamed.get(target).filter(t -> !t.asMob().equals(mob.getLastHurtByMob())).isPresent())
	        	event.setCanceled(true);
		}
		// Handle mobs end //
	}	
	
	/** Here it's still necessary because other mods may reset target here overriding the AlwaysHostile feature*/
	
	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onLivingSetAttackTarget_Lowest(LivingSetAttackTargetEvent event)
	{
		if (!event.getEntity().getLevel().isClientSide)
		{
			// Handle tamable always-hostile
			if (!event.getEntity().getLevel().isClientSide && event.getEntity() instanceof Mob mob)
			{
				NFFTamableComponent.getOptional(mob)
					.flatMap(c -> c.getAlwaysHostileToLiving()		// when has an always-hostile target
						.filter(e -> !e.equals(event.getTarget()))	// and trying to set to another target (or remove target)
						.filter(mob::hasLineOfSight))	// and can see the target
					.ifPresent(mob::setTarget);	// Then turn to the always-hostile target
			}
		}
		
	}

	@SubscribeEvent
	public static void onLivingDeath(LivingDeathEvent event) {
		if (event.isCanceled())
			return;
		if (!event.getEntity().getLevel().isClientSide) {
			INFFTamed.get(event.getEntity()).ifPresent(t -> {
				if (MinecraftForge.EVENT_BUS.post(new NFFTamedDeathEvent(t, event.getSource()))) {
					event.setCanceled(true);
					if (event.getEntityLiving().getHealth() < 0.00001f)
						event.getEntityLiving().setHealth(1f);
					return;
				}
				// Befriended mobs should not kill each other with same owner, or get killed by
				// owner-tamed animals
				else if (INFFTamed.get(event.getSource().getEntity()).isPresent()) {
					INFFTamed srcBef = INFFTamed.get(event.getSource().getEntity()).get();
					if (srcBef.getOwner() != null && t.getOwner() != null && srcBef.getOwner() == t.getOwner()) {
						t.asMob().setHealth(1.0f);
						t.asMob().invulnerableTime += 20;
						event.setCanceled(true);
						return;
					}
				} else if (event.getSource().getEntity() instanceof TamableAnimal ta) {
					if (ta.getOwner() != null && t.getOwner() != null && ta.getOwner() == t.getOwner()) {
						t.asMob().setHealth(1.0f);
						t.asMob().invulnerableTime += 20;
						event.setCanceled(true);
						return;
					}
				}
				if (!event.getEntity().level.isClientSide) {
					// Drop all items in inventory if no vanishing curse
					if (/*bef.dropInventoryOnDeath()*//**TODO: Fix item loss if not dropping */true) {
						t.getAdditionalInventory().ifPresent(container -> {
							;
							for (int i = 0; i < container.getContainerSize(); ++i) {
								if (container.getItem(i) != ItemStack.EMPTY) {
									if (!EnchantmentHelper.hasVanishingCurse(container.getItem(i))) {
										event.getEntity().spawnAtLocation(container.getItem(i).copy());
									}
									container.getItem(i).setCount(0);
									t.getAdditionalInventory().ifPresent(inv -> inv.syncToMob(t.asMob()));
								}
							}
						});
					}
					// If drop respawner, drop and initialize
					if (t.getRespawnerType() != null) {
						// Set necessary properties to default before creating the respawner
						t.asMob().setDeltaMovement(Vec3.ZERO);
						t.asMob().getActiveEffects().stream().toList().forEach(ei -> t.asMob().removeEffect(ei.getEffect()));
						t.asMob().setRemainingFireTicks(0);
						// Create the respawner
						NFFMobRespawnerInstance ins = NFFMobRespawnerInstance.createAndInitItem(NFFMobRespawnerItem.fromMob(t.getRespawnerType(), t.asMob()));
						if (ins.isNFFRespawnerItem()) {
							if (t.getDeathRespawnerGenerationType() == DeathRespawnerGenerationType.GIVE) {
								if (t.isOwnerInDimension() && t.getOwner().getInventory().getFreeSlot() != -1 && t.getOwner().addItem(ins.get()))
								{}
								else
								{
									if (!t.asMob().getLevel().getCapability(NFFCapRegistry.CAP_LEVEL).isPresent()) {
										throw new IllegalStateException(
												"NFF: Server level missing CNFFLevelModule capability");
									}
									t.asMob().getLevel().getCapability(NFFCapRegistry.CAP_LEVEL).ifPresent(cap ->
									{
										cap.addSuspendedRespawner(ins);
									});
								}
							}

							else if (t.getDeathRespawnerGenerationType() == DeathRespawnerGenerationType.DROP)
							{
								ItemEntity resp = new ItemEntity(event.getEntity().level, event.getEntity().getX(),
										event.getEntity().getY(), event.getEntity().getZ(), ins.get());
								if (t.isRespawnerInvulnerable()) {
									ins.setInvulnerable(true);
									resp.setInvulnerable(true);
								}
								ins.setRecoverInVoid(t.shouldRespawnerRecoverOnDropInVoid());
								ins.setNoExpire(t.respawnerNoExpire());
								if (!MinecraftForge.EVENT_BUS.post(new NFFTamedDropRespawnerOnDyingEvent(t, ins)))
									event.getEntity().level.addFreshEntity(resp);
							}
						}
					}
				}

				else if (event.getEntity() instanceof TamableAnimal ta) 
				{
					INFFTamed.get(event.getSource().getEntity()).ifPresent(srcBef -> {
						if (srcBef.getOwner() != null && ta.getOwner() != null && srcBef.getOwner() == ta.getOwner()) {
							ta.setHealth(1.0f);
							ta.invulnerableTime += 20;
							event.setCanceled(true);
							return;
						}
					});
				}
				else if (event.getEntity() instanceof Player player && player.getLevel() instanceof ServerLevel sl) {
					// Notify taming interruption on player death
					NFUContainerStatics.iterableToList(sl.getServer().getAllLevels()).stream()	// Get all levels
						.flatMap(sl1 -> NFUContainerStatics.iterableToList(sl1.getEntities().getAll()).stream())	// Get all entities of all levels
						.filter(e -> e instanceof Mob)	// Cast to mobs
						.map(e -> NFFTamableComponent.getOptional(e).orElse(null))	// Try getting tamable component
						.filter(Objects::nonNull)	// Clear non-tamable
						.filter(c -> !c.getTamingProcess().dontInterruptOnPlayerDie() && c.getTamingProcess().isInProcess(player, c.getEntity()))	// When should interrupt on player death, and player is in process
						.forEach(c -> c.getTamingProcess().interrupt(player, c.getEntity(), true));		// Interrupt player's process
				}
			});
		}
	}
	
	// Don't allow befriended zombies to summon
	@SubscribeEvent
	public static void onZombieSummon(SummonAidEvent event)
	{
		INFFTamed.get(event.getEntity()).ifPresent(t -> event.setResult(Result.DENY));
	}

	@SubscribeEvent
	public static void onLivingHurt(LivingHurtEvent event)
	{
		if (event.isCanceled())
			return;
		LivingEntity living = event.getEntityLiving();
		LivingEntity source = (event.getSource().getEntity() != null && event.getSource().getEntity() instanceof LivingEntity) ?
				(LivingEntity)(event.getSource().getEntity()) : null;
		if (!living.level.isClientSide)
		{
			// Handle befriending process events on mob attacked
			// The events are handled in handler classes, not a forge event
			// On befriendable mob attacked by player
			if (living instanceof Mob mob
				&& source instanceof Player player)
			{
				NFFTamableComponent.getOptional(mob).ifPresent(c -> {
					if (c.getTamingProcess().isInProcess(player, mob)) {
						c.getTamingProcess().onAttackedByProcessingPlayer(mob, player, event.getAmount());
					}
				});
			}
			// On player attacked by befriendable mob
			else if (living instanceof Player player
				&& source instanceof Mob mob)
			{
				NFFTamableComponent.getOptional(mob).ifPresent(c -> {
					if (c.getTamingProcess().isInProcess(player, mob)) {
						c.getTamingProcess().onAttackProcessingPlayer(mob, player, event.getAmount());
					}
				});
			}
		}
	}


	@SuppressWarnings("unchecked")
	@SubscribeEvent
	public static void onLivingUpdate(LivingEvent.LivingUpdateEvent event)
	{
		if (!event.getEntity().level.isClientSide)
		{
			if (event.getEntity() instanceof Mob mob)
			{
				INFFTamed.get(mob).ifPresent(bm -> {
					// update befriended mob anchor position
					if (bm.getAnchorPos() != null)
					{
						// Stop update when wandering
						if (bm.getAIState() != NFFTamedMobAIState.WANDER)
							bm.updateAnchor();
					}
					// Sometimes it may happen that the mobs still attack allies, reset here
		        	// Generally the code below shouldn't be invoked, so print an error to log
		        	if (NFFTamedStatics.isLivingAlliedToBM(bm, bm.asMob().getTarget()))
		        	{
                        LogUtils.getLogger().error("NFF Services: NFF tamed mob [{}] attempting to attack ally [{}]. Target reset.", bm.asMob().getName().getString(), bm.asMob().getTarget().getName().getString());
		        		bm.asMob().setTarget(null);
		        		if (bm.asMob().getTarget() != null)
		        			// Maybe sometimes setTarget can be cancelled
		        			NFUEntityStatics.forceSetTarget(bm.asMob(), null);
		        		bm.setPreviousTarget(null);
		        	}

					bm.recordLocationToOwner();
				});
			}
		}
	}

	@SubscribeEvent
	public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
		// Remove suspicious tamed location stored in player that the mob may no longer exist
		// Do once each 10s as it could be a bit costly
		// A suspicious location entry is defined as the entry in which the pos is loaded
		// but the mob isn't found in level
		if (event.phase.equals(TickEvent.Phase.START)
				&& event.side.equals(LogicalSide.SERVER)
				&& event.player.tickCount % 200 == 199) {
			INFFTamed.removeSuspiciousMobLocations(event.player);
		}
	}

	@SubscribeEvent
	public static void onBefriendedChangeAiState(NFFTamedChangeAiStateEvent event)
	{
		// When switching from wait, clear mob target and owner last hurt target
		// or it may unexpectedly start to attack right on switching
		// But if the owner was just hurt by a mob, this befriended mob will still
		// start to attack it.
		if (event.getStateBefore().equals(NFFTamedMobAIState.WAIT))
		{
			event.getMob().asMob().setTarget(null);
			if (event.getMob().isOwnerInDimension())
				event.getMob().getOwnerInDimension().setLastHurtMob(null);
		}
	}
	
	@SubscribeEvent
	public static void onItemExpire(ItemExpireEvent event)
	{
		NFFMobRespawnerInstance ins = NFFMobRespawnerInstance.createIfValid(event.getEntityItem().getItem());
		if (ins != null && ins.isNoExpire())
		{
			event.setCanceled(true);	
		}
	}
	
	@SubscribeEvent
	public static void onEntityJoinWorld(EntityJoinWorldEvent event)
	{
		if (!event.getEntity().getLevel().isClientSide()) {
			if (event.getEntity() instanceof LivingEntity living) {
				NFFTamableComponent.getOptional(living).ifPresent(c -> c.getTamingProcess().tamableInit(c));
			}
			INFFTamed.get(event.getEntity())
				.filter(INFFTamed::enableSunSensitivity)
				.ifPresent(INFFTamed::setupSunImmunityRules);
		}

	}

	@SubscribeEvent
	public static void onDespawn(LivingSpawnEvent.AllowDespawn event)
	{
		if (NFFTamableComponent.getOptional(event.getEntity()).filter(NFFTamableComponent::isForcePersistent).isPresent())
			event.setResult(Result.DENY);
	}

	// NFU MIXIN EVENTS

	@SubscribeEvent
	public static void onEntityFinishConstruction(EntityFinishConstructionEvent event) {
		INFFTamed.get(event.getEntity()).ifPresent(t -> {
			// Initialize inventory here.
			//t.getDataAccessor().getSyncherComponent().setInventory(Optional.ofNullable(t.createAdditionalInventory()).orElseGet(() -> NFFTamedMobInventory.createEmpty(t)));
			t.onInitialize();
		});
	}

	@SubscribeEvent
	public static void onMobSunBurnTick(MobSunBurnTickEvent event)
	{
		if (INFFTamed.get(event.getEntity()).filter(t -> t.enableSunSensitivity() && t.isSunImmune()).isPresent())
			event.setCanceled(true);
	}

	@SubscribeEvent
	public static void onDiscard(EntityDiscardEvent event) {
		INFFTamed.get(event.getEntity()).ifPresent(INFFTamed::removeLocationOnOwner);
	}

	@SubscribeEvent
	public static void onStartDeath(LivingStartDeathEvent event) {
		INFFTamed.get(event.getEntity()).ifPresent(INFFTamed::removeLocationOnOwner);
	}
}
