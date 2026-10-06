package net.sodiumzh.nff.services.eventlistener;

import com.mojang.logging.LogUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.sodiumzh.nff.services.NFFServices;
import net.sodiumzh.nff.services.entity.taming.*;
import net.sodiumzh.nff.services.inventory.NFFTamedMobInventory;
import net.sodiumzh.nff.services.registry.NFFDataSerializers;
import net.sodiumzh.nff.services.registry.NFFEntityComponents;
import net.sodiumzh.nfu.entity.component.EntityComponentAPI;
import net.sodiumzh.nfu.mixin.event.entity.EntityFinalizeLoadingEvent;
import net.sodiumzh.nfu.mixin.event.entity.EntityLoadEvent;
import net.sodiumzh.nfu.network.NFUDataSerializer;
import net.sodiumzh.nfu.network.NFUDataSerializers;
import net.sodiumzh.nfu.util.NFUDebugStatics;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

/**
 * Event listeners for porting 0.x.32 data to 0.x.33 which involves a large code clear-up.
 * Will be removed in 0.x.34, and 0.x.34 data will not be compatible with 0.x.32 or lower.
 */
@Deprecated(forRemoval = true, since = "0.x.34")
@Mod.EventBusSubscriber(modid = NFFServices.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class DataPort33 {

    @SubscribeEvent
    public static void onEntityLoad(EntityFinalizeLoadingEvent event) throws Exception {
        if (INFFTamed.get(event.getEntity()).isPresent()) {
            // Port common data
            INFFTamed tamed = INFFTamed.get(event.getEntity()).get();
            CompoundTag caps = event.getNBT().getCompound("ForgeCaps");
            CompoundTag legacyCommonData = caps.getCompound("nffservices:nff_mob_common_data");
            if (!legacyCommonData.isEmpty()) {
                try {
                    // When porting, CNFFTamedCommonData.Values#deserializeNBT will finally call getInventory() so we must create it in advance
                    INFFTamed.get(event.getEntity()).ifPresent(t -> t.getDataAccessor().getInventoryComponent().createInventoryIfAbsent());
                    // Try loading data from old format
                    CNFFTamedCommonData.Values temp = new CNFFTamedCommonData.Values(tamed);
                    temp.deserializeNBT(legacyCommonData);
                    NFFTamedSyncherComponent syncher = EntityComponentAPI.getComponentByPath(event.getEntity(), "/nff/tamed/syncher", NFFEntityComponents.TAMED_SYNCHER.get()).orElseThrow();
                    NFFTamedDataComponent data = EntityComponentAPI.getComponentByPath(event.getEntity(), "/nff/tamed/data", NFFEntityComponents.TAMED_DATA.get()).orElseThrow();
                    NFFTamedDataAccessor accessor = tamed.getDataAccessor();
                    // Setup debug output
                    Player player = Optional.of(event.getEntity().level().players())
                        .map(p -> p.isEmpty() ? null : p.get(0))
                        .orElse(null);
                    BiConsumer<String, String> msgPrinter = (fieldName, msg) ->
                        LogUtils.getLogger().info("[0.x.33 Data Porting] Mob: Loaded data \"" + fieldName + "\"= \"" + msg + "\". Mob: " + event.getEntity().getName().getString(), player);
                    // Port nbt
                    temp.getAdditionalNBT().getAllKeys().forEach(k -> data.getNBT().put(k, temp.getAdditionalNBT().get(k).copy()));
                    msgPrinter.accept("additionalNBT", data.getNBT().toString());
                    // Port synched values
                    syncher.setSynchedData("identifier", UUID.class, accessor.getIdentifier());
                    msgPrinter.accept("identifier", accessor.getIdentifier().toString());
                    accessor.setEncounteredDate(temp.getEncounteredDate());
                    msgPrinter.accept("encounteredDate", Arrays.toString(accessor.getEncounteredDate()));
                    accessor.setOwnerUUID(temp.getOwnerUUID());
                    msgPrinter.accept("ownerUUID", accessor.getOwnerUUID().toString());
                    accessor.setOwnerName(temp.getOwnerName());
                    msgPrinter.accept("ownerName", accessor.getOwnerName());
                    accessor.setAIState(temp.getAIState());
                    msgPrinter.accept("aiState", accessor.getAIState().toString());
                    // Port other values
                    data.putPermanentVariable("initialType", ForgeRegistries.ENTITY_TYPES.getKey(temp.getInitialEntityType()), NFUDataSerializers.RESOURCE_LOCATION.get());
                    msgPrinter.accept("initialType", accessor.getInitialEntityType().getDescriptionId());
                    data.putPermanentVariable("randomStrollAnchor", temp.getAnchor(), NFUDataSerializers.VEC3.get());
                    msgPrinter.accept("randomStrollAnchor", accessor.getAnchor().toString());
                    // Port inventory
                    ListTag inventoryNBT = Optional.ofNullable(temp.getAdditionalInventory()).map(NFFTamedMobInventory::toTag)
                            .orElseGet(ListTag::new);
                    accessor.getInventoryComponent().createInventoryIfAbsent();
                    accessor.getAdditionalInventory()
                        .ifPresent(inventory -> inventory.readFromTag(inventoryNBT));
                    msgPrinter.accept("inventory", accessor.getAdditionalInventory().toString());
                    NFUDebugStatics.debugPrintToScreen("Mob \"" + event.getEntity().getName().getString() + "\" porting finished.", player);
                } catch (RuntimeException e) {
                    throw new RuntimeException("Porting 0.x.32 data failed. Mob: " + event.getEntity().getName().getString() + "\n", e);
                }
            }
        }
    }

}
