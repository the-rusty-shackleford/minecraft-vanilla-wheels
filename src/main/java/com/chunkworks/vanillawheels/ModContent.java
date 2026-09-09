/*
 * Vanilla Wheels - a vehicle protocol.
 * Copyright (C) 2026 Rusty Shackleford and nfx
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.chunkworks.vanillawheels;

import com.chunkworks.vanillawheels.api.VanillaWheels;
import com.chunkworks.vanillawheels.api.VehicleProfile;
import com.chunkworks.vanillawheels.lift.LiftBlockEntity;
import com.chunkworks.vanillawheels.lift.LiftControllerBlock;
import com.chunkworks.vanillawheels.lift.LiftMenu;
import com.chunkworks.vanillawheels.lift.LiftPartBlock;
import com.chunkworks.vanillawheels.lift.MechanicLiftItem;
import com.mojang.serialization.Codec;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Every game object the protocol registers: one entity type for every
 * vehicle (the profile is data on the entity), the items, the components
 * a vehicle carries as an item, the sounds. The entity type's own size is
 * a placeholder; a vehicle sizes itself from its profile.
 */
public final class ModContent {
    private ModContent() {}

    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, VanillaWheelsMod.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(VanillaWheelsMod.MOD_ID);
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, VanillaWheelsMod.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, VanillaWheelsMod.MOD_ID);
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(VanillaWheelsMod.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, VanillaWheelsMod.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, VanillaWheelsMod.MOD_ID);

    /** The one entity type: which vehicle it is, is the profile it carries. */
    public static final DeferredHolder<EntityType<?>, EntityType<Vehicle>> VEHICLE_ENTITY = ENTITIES.register("vehicle",
            () -> EntityType.Builder.<Vehicle>of(Vehicle::new, MobCategory.MISC)
                    .sized(1.5f, 1.0f)
                    .clientTrackingRange(10)
                    .updateInterval(2)
                    .build("vehicle"));

    /** Which vehicle a vehicle item or a chassis item stands for. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ResourceLocation>> VEHICLE = COMPONENTS.register("vehicle",
            () -> DataComponentType.<ResourceLocation>builder().persistent(ResourceLocation.CODEC).networkSynchronized(ResourceLocation.STREAM_CODEC).build());
    /** A picked-up vehicle's paint. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<DyeColor>> PAINT = COMPONENTS.register("paint",
            () -> DataComponentType.<DyeColor>builder().persistent(DyeColor.CODEC).networkSynchronized(DyeColor.STREAM_CODEC).build());
    /** A picked-up vehicle's fuel, in burn ticks. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FUEL = COMPONENTS.register("fuel",
            () -> DataComponentType.<Integer>builder().persistent(Codec.intRange(0, Integer.MAX_VALUE)).networkSynchronized(ByteBufCodecs.VAR_INT).build());
    /** The disc in a picked-up vehicle's radio. */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<ItemStack>> DISC = COMPONENTS.register("disc",
            () -> DataComponentType.<ItemStack>builder().persistent(ItemStack.OPTIONAL_CODEC).networkSynchronized(ItemStack.OPTIONAL_STREAM_CODEC).build());

    /** A vehicle in the hand: placed like a boat. */
    public static final DeferredItem<VehicleItem> VEHICLE_ITEM = ITEMS.registerItem("vehicle", VehicleItem::new, new Item.Properties().stacksTo(1));
    /** A vehicle's body, before its wheels and engine: what the lift takes. */
    public static final DeferredItem<Item> CHASSIS = ITEMS.registerItem("chassis", ChassisItem::new, new Item.Properties().stacksTo(1));
    /** A wheel. */
    public static final DeferredItem<Item> WHEEL = ITEMS.registerSimpleItem("wheel");
    /** An engine. */
    public static final DeferredItem<Item> ENGINE = ITEMS.registerSimpleItem("engine");
    /** Takes a vehicle back into the hand. */
    public static final DeferredItem<Item> WRENCH = ITEMS.registerItem("wrench", WrenchItem::new, new Item.Properties().stacksTo(1));

    /** The Mechanic Lift's front-centre block, with the block entity; the item places the whole lift through it. */
    public static final DeferredBlock<LiftControllerBlock> LIFT_CONTROLLER = BLOCKS.registerBlock("mechanic_lift", LiftControllerBlock::new, liftProperties());
    /** Every other cell of a lift. */
    public static final DeferredBlock<LiftPartBlock> LIFT_PART = BLOCKS.registerBlock("mechanic_lift_part", LiftPartBlock::new, liftProperties());
    /** The lift in the hand. */
    public static final DeferredItem<MechanicLiftItem> LIFT_ITEM = ITEMS.registerItem("mechanic_lift", props -> new MechanicLiftItem(LIFT_CONTROLLER.get(), props), new Item.Properties().stacksTo(1));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<LiftBlockEntity>> LIFT_BE = BLOCK_ENTITIES.register("mechanic_lift",
            () -> BlockEntityType.Builder.of(LiftBlockEntity::new, LIFT_CONTROLLER.get()).build(null));
    /** The client's copy needs no position: it reads the server's verdicts from the data slots. */
    public static final DeferredHolder<MenuType<?>, MenuType<LiftMenu>> LIFT_MENU = MENUS.register("mechanic_lift",
            () -> new MenuType<>((id, inventory) -> new LiftMenu(id, inventory, net.minecraft.world.inventory.ContainerLevelAccess.NULL), net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));

    private static BlockBehaviour.Properties liftProperties() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.0f, 6.0f).sound(SoundType.METAL).noOcclusion().noLootTable().pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK);
    }

    public static final DeferredHolder<SoundEvent, SoundEvent> HORN_TRUCK = sound("horn.truck");
    public static final DeferredHolder<SoundEvent, SoundEvent> ENGINE_PETROL = sound("engine.petrol");
    public static final DeferredHolder<SoundEvent, SoundEvent> SKID = sound("skid");
    public static final DeferredHolder<SoundEvent, SoundEvent> THUD = sound("thud");
    public static final DeferredHolder<SoundEvent, SoundEvent> WRENCH_CLANK = sound("wrench");
    public static final DeferredHolder<SoundEvent, SoundEvent> FUEL_POUR = sound("fuel");

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String path) {
        return SOUNDS.register(path, () -> SoundEvent.createVariableRangeEvent(VanillaWheels.id(path)));
    }

    /** effects: registers everything on {@code modBus} */
    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        COMPONENTS.register(modBus);
        SOUNDS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        MENUS.register(modBus);
    }

    /** effects: returns a vehicle item for {@code vehicle} */
    public static ItemStack vehicleStack(ResourceLocation vehicle) {
        ItemStack stack = new ItemStack(VEHICLE_ITEM.get());
        stack.set(VEHICLE.get(), vehicle);
        return stack;
    }

    /** effects: returns a chassis item for {@code vehicle} */
    public static ItemStack chassisStack(ResourceLocation vehicle) {
        ItemStack stack = new ItemStack(CHASSIS.get());
        stack.set(VEHICLE.get(), vehicle);
        return stack;
    }

    /**
     * effects: puts the parts in Ingredients, the wrench in Tools, the lift in Functional Blocks, and one vehicle
     * item and one chassis per registered profile in Transportation, so a
     * vehicle mod's creative presence is automatic
     */
    static void buildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(WHEEL);
            event.accept(ENGINE);
        } else if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WRENCH);
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(LIFT_ITEM);
        }
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            for (Holder.Reference<VehicleProfile> profile : event.getParameters().holders().lookupOrThrow(VanillaWheels.VEHICLES).listElements().toList()) {
                event.accept(vehicleStack(profile.key().location()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                event.accept(chassisStack(profile.key().location()), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
            }
        }
    }
}
