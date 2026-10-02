package net.sodiumzh.nff.services.entity.ai.goal.preset.target;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.sodiumzh.nff.services.entity.ai.goal.NFFTargetGoal;
import net.sodiumzh.nff.services.entity.taming.INFFTamed;
import net.sodiumzh.nff.services.entity.taming.NFFTamedStatics;

import java.util.EnumSet;

public class NFFOwnerHurtByTargetGoal extends NFFTargetGoal {

	private LivingEntity ownerLastHurtBy;
	private int timestamp;

	public NFFOwnerHurtByTargetGoal(INFFTamed inMob) {
		super(inMob, false);
		this.setFlags(EnumSet.of(Goal.Flag.TARGET));
		allowAllStatesExceptWait();
		this.setNoExpireCondition(() -> (this.mob.isOwnerPresent() && this.mob.asMob().getLastHurtByMob() == this.ownerLastHurtBy));
		this.setExpireTicks(30 * 20);	// Expires after 30s by default
	}

	/**
	 * Returns whether execution should begin. You can also read and cache any state
	 * necessary for execution in this handler as well.
	 */
	@Override
	public boolean checkCanUse() {
		if (isDisabled())
			return false;
		LivingEntity owner = mob.getOwner();
		if (owner == null)
			return false;
		else 
		{
			this.ownerLastHurtBy = owner.getLastHurtByMob();
			if (mob.isTamedAlliedTo(this.ownerLastHurtBy) || !mob.wantsToAttack(this.ownerLastHurtBy))
				return false;
			int i = owner.getLastHurtByMobTimestamp();
			if (i == this.timestamp)
				return false;
			else if (!this.canAttack(this.ownerLastHurtBy, TargetingConditions.DEFAULT))
				return false;
			else if (!mob.wantsToAttack(this.ownerLastHurtBy))
				return false;
			else return true;
		}
	}

	@Override
	public boolean checkCanContinueToUse()
	{
		return super.checkCanContinueToUse();
	}
	
	/**
	 * Execute a one shot task or start executing a continuous task
	 */
	@Override
	public void onStart() {
		mob.asMob().setTarget(this.ownerLastHurtBy);
		LivingEntity livingentity = mob.getOwner();
		if (livingentity != null) {
			this.timestamp = livingentity.getLastHurtByMobTimestamp();
		}
		super.onStart();
	}
	
	@Override
	public void onStop()
	{
		this.mob.asMob().setTarget(null);
		this.ownerLastHurtBy = null;
	}
	
}
