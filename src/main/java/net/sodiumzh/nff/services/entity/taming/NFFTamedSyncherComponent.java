package net.sodiumzh.nff.services.entity.taming;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraftforge.common.MinecraftForge;
import net.sodiumzh.nff.services.entity.ai.NFFTamedMobAIState;
import net.sodiumzh.nff.services.event.entity.NFFTamedSyncherConstructEvent;
import net.sodiumzh.nff.services.inventory.NFFTamedMobInventory;
import net.sodiumzh.nff.services.registry.NFFDataSerializers;
import net.sodiumzh.nfu.entity.component.preset.EntitySyncherComponent;
import net.sodiumzh.nfu.network.NFUDataSerializer;
import net.sodiumzh.nfu.network.NFUDataSerializers;
import org.jetbrains.annotations.ApiStatus;

import javax.annotation.Nullable;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

public class NFFTamedSyncherComponent extends EntitySyncherComponent<Mob> {

    private static final String IDENTIFIER_SYNCHED_KEY = "identifier";
    private static final String OWNER_UUID_SYNCHED_KEY = "ownerUUID";
    private static final String OWNER_NAME_SYNCHED_KEY = "ownerName";
    private static final String ENCOUNTERED_DATE_SYNCHED_KEY = "encounteredDate";
    private static final String ATTACK_TARGET_SYNCHED_KEY = "attackTarget";
    private static final String AI_STATE_SYNCHED_KEY = "aiState";
    private static final String ADDITIONAL_INVENTORY_KEY = "additionalInventory";
    private static final UUID EMPTY_UUID = new UUID(0L, 0L);

    public NFFTamedSyncherComponent(Mob entity) {
        super(entity);
        this.createSynchedData(IDENTIFIER_SYNCHED_KEY, NFUDataSerializers.UUID.get(), EMPTY_UUID, true);
        this.createSynchedData(OWNER_UUID_SYNCHED_KEY, NFUDataSerializers.UUID.get(), EMPTY_UUID, true);
        this.createSynchedData(OWNER_NAME_SYNCHED_KEY, NFUDataSerializers.STRING.get(), "", true);
        this.createSynchedData(ENCOUNTERED_DATE_SYNCHED_KEY, NFUDataSerializers.INT_ARRAY.get(), new int[] {2023, 1, 1}, true);
        this.createSynchedData(AI_STATE_SYNCHED_KEY, NFUDataSerializers.STRING.get(), NFFTamedMobAIState.WAIT.getId().toString(), true);
        this.createSynchedData(ADDITIONAL_INVENTORY_KEY, NFFDataSerializers.TAMED_MOB_INVENTORY.get(), NFFTamedMobInventory.createEmpty(null), true);
        this.createSynchedGetter(ATTACK_TARGET_SYNCHED_KEY, NFUDataSerializers.INT.get(), -1,
            mob -> Optional.ofNullable(mob.getTarget()).map(LivingEntity::getId).orElse(-1));	// -1 means no target
        MinecraftForge.EVENT_BUS.post(new NFFTamedSyncherConstructEvent(entity, this));
    }

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

    private void setIdentifier(UUID identifier) {
        if (identifier.equals(new UUID(0L, 0L))) throw new IllegalArgumentException();
        this.setSynchedData(IDENTIFIER_SYNCHED_KEY, UUID.class, identifier);
    }

    public void generateIdentifier()
    {
        UUID id = this.getSynchedData(IDENTIFIER_SYNCHED_KEY, UUID.class).orElse(null);
        if (id == null || id.equals(EMPTY_UUID))
        {
            if (!this.isClientSide())
            {
                this.setSynchedData(IDENTIFIER_SYNCHED_KEY, UUID.class, UUID.randomUUID());
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

    public String getOwnerName() {
        String str = this.getSynchedData(OWNER_NAME_SYNCHED_KEY, String.class).orElse(null);
        if (str != null && !str.isEmpty()) return str;
        else {
            LogUtils.getLogger().error(String.format("CNFFTamedCommonData: mob %s missing owner name. Return \"(Unknown)\". It will be updated once the owner entered the level", this.getEntity().getName().getString()));
            return "(Unknown)";
        }
    }

    public void setOwnerName(String val) {
        this.setSynchedData(OWNER_NAME_SYNCHED_KEY, String.class, val);
    }

    public int[] getEncounteredDate() {
        return this.getSynchedData(ENCOUNTERED_DATE_SYNCHED_KEY, int[].class).orElse(new int[]{2023, 1, 1});
    }

    public void setEncounteredDate(int[] val)
    {
        this.setSynchedData(ENCOUNTERED_DATE_SYNCHED_KEY, int[].class, val);
    }

    /**
     * Non-null, but empty-able.
     */
    public UUID getOwnerUUID()
    {
        return this.getSynchedData(OWNER_UUID_SYNCHED_KEY, UUID.class).orElse(EMPTY_UUID);
    }

    public void setOwnerUUID(UUID value)
    {
        if (value == null) this.setOwnerUUID(EMPTY_UUID);
        this.setSynchedData(OWNER_UUID_SYNCHED_KEY, UUID.class, value);
    }

    public NFFTamedMobAIState getAIState()
    {
        return NFFTamedMobAIState.fromID(new ResourceLocation(
            this.getSynchedData(AI_STATE_SYNCHED_KEY, String.class).orElseThrow()));
    }

    public void setAIState(NFFTamedMobAIState state)
    {
        this.setSynchedData(AI_STATE_SYNCHED_KEY, String.class, state.getId().toString());
    }

    @Nullable
    public LivingEntity getAttackTarget() {
        int id = this.getSynchedGetter(ATTACK_TARGET_SYNCHED_KEY, Integer.class).orElse(-1);
        if (id == -1) return null;
        return Stream.of(this.getEntity().level().getEntity(id)).filter(e -> e instanceof LivingEntity)
            .map(e -> (LivingEntity)e).findAny().orElse(null);
    }

}
