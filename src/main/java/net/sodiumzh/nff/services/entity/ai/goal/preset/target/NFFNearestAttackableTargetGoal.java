package net.sodiumzh.nff.services.entity.ai.goal.preset.target;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.sodiumzh.nff.services.entity.ai.goal.NFFTargetGoal;
import net.sodiumzh.nff.services.entity.taming.INFFTamed;
import net.sodiumzh.nff.services.entity.taming.NFFTamedStatics;

import javax.annotation.Nullable;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.function.Predicate;

public class NFFNearestAttackableTargetGoal<T extends LivingEntity> extends NFFTargetGoal {
  // private static final int DEFAULT_RANDOM_INTERVAL = 10;
   protected final Class<T> targetType;
   protected final int randomInterval;
   @Nullable
   protected LivingEntity target;
   /** This filter is applied to the Entity search. Only matching entities will be targeted. */
   protected TargetingConditions targetConditions;
    /** Attacker state filter. It will try targeting only when the attacker meets this condition. */
    protected Predicate<INFFTamed> stateConditions = m -> true;


    public NFFNearestAttackableTargetGoal(INFFTamed mob, Class<T> targetType, boolean mustSee) {
      this(mob, targetType, 10, mustSee, false, (Predicate<LivingEntity>)null);
   }

   public NFFNearestAttackableTargetGoal(INFFTamed mob, Class<T> targetType, boolean mustSee, Predicate<LivingEntity> targetPredicate) {
      this(mob, targetType, 10, mustSee, false, targetPredicate);
   }

   public NFFNearestAttackableTargetGoal(INFFTamed mob, Class<T> targetType, boolean mustSee, boolean mustReach) {
      this(mob, targetType, 10, mustSee, mustReach, (Predicate<LivingEntity>)null);
   }

   public NFFNearestAttackableTargetGoal(INFFTamed mob, Class<T> targetType, int pRandomInterval, boolean mustSee, boolean mustReach, @Nullable Predicate<LivingEntity> targetPredicate) {
      super(mob, mustSee, mustReach);
      this.targetType = targetType;
      this.randomInterval = reducedTickDelay(pRandomInterval);
      this.setFlags(EnumSet.of(Goal.Flag.TARGET));
      this.targetConditions = TargetingConditions.forCombat().range(this.getFollowDistance()).selector(targetPredicate);
      this.allowAllStatesExceptWait();
   }

   @Override
   public boolean checkCanUse() {
	  if (isDisabled())
		  return false;
       if (!stateConditions.test(mob))
           return false;
      if (this.randomInterval > 0 && this.mob.asMob().getRandom().nextInt(this.randomInterval) != 0) {
         return false;
      } else {
         this.findTarget();
		if (mob.isTamedAlliedTo(this.target) || !mob.wantsToAttack(this.target))
			return false;
         return this.target != null;
      }
   }

   protected AABB getTargetSearchArea(double targetDistance) {
      return this.mob.asMob().getBoundingBox().inflate(targetDistance, 4.0D, targetDistance);
   }

    /**
     * Set state conditions. The {@code INFFTamed} using this goal needs to pass this check to start.
     */
    public NFFNearestAttackableTargetGoal<T> stateConditions(Predicate<INFFTamed> condition)
    {
        stateConditions = condition;
        return this;
    }

   protected void findTarget() {
      double followDist = mob.asMob().getAttributeValue(Attributes.FOLLOW_RANGE);
      AABB searchArea = new AABB(mob.asMob().position().subtract(new Vec3(followDist, followDist, followDist)), mob.asMob().position().add(new Vec3(followDist, followDist, followDist)));
      mob.asMob().level().getEntities(mob.asMob(), searchArea, (Entity e) ->
              e instanceof Mob m
                  && this.targetType.isAssignableFrom(m.getClass())
                  && mob.asMob().distanceToSqr(m) <= followDist * followDist
                  && !mob.isTamedAlliedTo(m)
                  && mob.asMob().hasLineOfSight(m)
                  && this.targetConditions.test(mob.asMob(), m))
          .stream()
          .min(Comparator.comparingDouble(e -> mob.asMob().distanceToSqr(e)))
          .ifPresentOrElse(e -> this.target = (Mob)e, () -> this.target = null);
   }

   /**
    * Execute a one shot task or start executing a continuous task
    */
   @Override
    public void onStart() {
      this.mob.asMob().setTarget(this.target);
      super.onStart();
   }

   public void setTarget(@Nullable LivingEntity target) {
      this.target = target;
   }
}