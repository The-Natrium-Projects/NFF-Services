package net.sodiumzh.nff.services.entity.taming;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.sodiumzh.nff.services.inventory.NFFTamedMobInventory;
import net.sodiumzh.nff.services.registry.NFFDataSerializers;
import net.sodiumzh.nff.services.registry.NFFTagRegistry;
import net.sodiumzh.nfu.entity.component.preset.EntityDataComponent;
import net.sodiumzh.nfu.function.MutablePredicate;
import net.sodiumzh.nfu.network.NFUDataSerializers;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Optional;

public class NFFTamedDataComponent extends EntityDataComponent<Mob> {

    public NFFTamedDataComponent(Mob entity) {
        super(entity);
        this.putTransientVariable("sunImmunity", new MutablePredicate<Mob>());
    }

    private EntityType<? extends Mob> getInitialEntityTypeRaw() {
        return this.getVariable("initialType", ResourceLocation.class)
            .map(loc -> (EntityType<? extends Mob>)ForgeRegistries.ENTITY_TYPES.getValue(loc)).orElse(null);
    }

    public INFFTamed getTamed() {
        return INFFTamed.get(this.getEntity()).orElseThrow(() -> new IllegalStateException("Missing INFFTamed interface."));
    }

    @SuppressWarnings("unchecked")
    public EntityType<? extends Mob> getInitialEntityType()
    {
			/* If missing type, it may be saved as "minecraft:pig" and bypass fixing. Generally pig should not be a
			 valid initial type since it's not INFFTamed. However, in the future INFFTamed may be available as a capability
			 and somehow attached to pig, so there's a tag to prevent this (in most cases it shouldn't happen)
			 */
        EntityType<? extends Mob> initialType = this.getInitialEntityTypeRaw();
        if (initialType != null &&
            !(initialType.equals(EntityType.PIG) && !this.getEntity().getType().is(NFFTagRegistry.COULD_BE_FROM_PIG)))
            return initialType;
        else {
            LogUtils.getLogger().error(String.format("NFF Tamed Data: mob %s missing initial type. Reset to current type.", this.getEntity().getName().getString()));
            this.recordEntityType();
            return this.getInitialEntityTypeRaw();
        }
    }

    @SuppressWarnings("unchecked")
    public void recordEntityType()
    {
        this.putPermanentVariable("initialType", ForgeRegistries.ENTITY_TYPES.getKey(this.getEntity().getType()), NFUDataSerializers.RESOURCE_LOCATION.get());
    }

    @SuppressWarnings("unchecked")
    private void setInitialEntityType(@Nonnull EntityType<?> entityType)
    {
        this.putPermanentVariable("initialType", ForgeRegistries.ENTITY_TYPES.getKey(entityType), NFUDataSerializers.RESOURCE_LOCATION.get());
    }

    @SuppressWarnings("unchecked")
    public MutablePredicate<INFFTamed> getSunImmunity() {
        return this.getOrPutTransient("sunImmunity", MutablePredicate.class, MutablePredicate::new).orElseThrow();
    }

    public Vec3 getRandomStrollAnchor() {
        return this.getOrPutPermanent("randomStrollAnchor", Vec3.class, NFUDataSerializers.VEC3.get(), () -> this.getEntity().position()).orElseThrow();
    }

    public void setRandomStrollAnchor(Vec3 anchor) {
        this.putPermanentVariable("randomStrollAnchor", anchor, NFUDataSerializers.VEC3.get());
    }

    @ApiStatus.Internal
    @Nullable
    public LivingEntity getPreviousTarget() {
        return this.getVariable("previousTarget", LivingEntity.class).orElse(null);
    }

    @ApiStatus.Internal
    public void setPreviousTarget(@Nullable LivingEntity l) {
        this.putTransientVariable("previousTarget", l);
    }

}
