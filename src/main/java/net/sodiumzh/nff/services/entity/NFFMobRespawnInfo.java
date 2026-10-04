package net.sodiumzh.nff.services.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.sodiumzh.nff.services.entity.taming.INFFTamed;
import net.sodiumzh.nfu.entity.MobRespawnInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class NFFMobRespawnInfo extends MobRespawnInfo {

    protected static final String OWNER_UUID_KEY = "ownerUUID";

    @Nullable
    protected UUID ownerUUID = null;

    @Override
    public void writeNBT(CompoundTag writeInto) {
        super.writeNBT(writeInto);
        Optional.ofNullable(ownerUUID).ifPresent(id -> writeInto.putUUID(OWNER_UUID_KEY, id));
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        super.deserializeNBT(nbt);
        this.ownerUUID = nbt.hasUUID(OWNER_UUID_KEY) ? nbt.getUUID(OWNER_UUID_KEY) : null;
    }

    @Override
    protected void afterRespawn(Mob mob, Level level, @Nullable Player player) {
        mob.clearFire();
        mob.removeAllEffects();
        INFFTamed.get(mob).ifPresent(b -> {
            b.updateAnchor();
        });
    }

    public Optional<UUID> getOwnerUUID() {
        return Optional.ofNullable(this.ownerUUID);
    }

}
