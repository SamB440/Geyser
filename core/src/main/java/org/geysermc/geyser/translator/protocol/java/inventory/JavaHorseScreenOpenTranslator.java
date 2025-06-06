/*
 * Copyright (c) 2019-2022 GeyserMC. http://geysermc.org
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 * @author GeyserMC
 * @link https://github.com/GeyserMC/Geyser
 */

package org.geysermc.geyser.translator.protocol.java.inventory;

import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.cloudburstmc.nbt.NbtType;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityFlag;
import org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType;
import org.cloudburstmc.protocol.bedrock.packet.UpdateEquipPacket;
import org.geysermc.geyser.entity.type.Entity;
import org.geysermc.geyser.entity.type.living.animal.horse.CamelEntity;
import org.geysermc.geyser.entity.type.living.animal.horse.ChestedHorseEntity;
import org.geysermc.geyser.entity.type.living.animal.horse.LlamaEntity;
import org.geysermc.geyser.entity.type.living.animal.horse.SkeletonHorseEntity;
import org.geysermc.geyser.entity.type.living.animal.horse.ZombieHorseEntity;
import org.geysermc.geyser.inventory.Container;
import org.geysermc.geyser.inventory.InventoryHolder;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.InventoryTranslator;
import org.geysermc.geyser.translator.inventory.horse.DonkeyInventoryTranslator;
import org.geysermc.geyser.translator.inventory.horse.HorseInventoryTranslator;
import org.geysermc.geyser.translator.inventory.horse.LlamaInventoryTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.InventoryUtils;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.inventory.ClientboundHorseScreenOpenPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Translator(packet = ClientboundHorseScreenOpenPacket.class)
public class JavaHorseScreenOpenTranslator extends PacketTranslator<ClientboundHorseScreenOpenPacket> {

    private static final NbtMap ARMOR_SLOT;
    private static final NbtMap CARPET_SLOT;
    private static final NbtMap SADDLE_SLOT;

    static {
        // Build the NBT mappings that Bedrock wants to lay out the GUI
        String[] acceptedHorseArmorIdentifiers = new String[] {"minecraft:horsearmorleather", "minecraft:horsearmoriron",
                "minecraft:horsearmorgold", "minecraft:horsearmordiamond"};
        NbtMapBuilder armorBuilder = NbtMap.builder();
        List<NbtMap> acceptedArmors = new ArrayList<>(4);
        for (String identifier : acceptedHorseArmorIdentifiers) {
            NbtMapBuilder acceptedItemBuilder = NbtMap.builder()
                    .putShort("Aux", Short.MAX_VALUE)
                    .putString("Name", identifier);
            acceptedArmors.add(NbtMap.builder().putCompound("slotItem", acceptedItemBuilder.build()).build());
        }
        armorBuilder.putList("acceptedItems", NbtType.COMPOUND, acceptedArmors);
        NbtMapBuilder armorItem = NbtMap.builder()
                .putShort("Aux", Short.MAX_VALUE)
                .putString("Name", "minecraft:horsearmoriron");
        armorBuilder.putCompound("item", armorItem.build());
        armorBuilder.putInt("slotNumber", 1);
        ARMOR_SLOT = armorBuilder.build();

        NbtMapBuilder carpetBuilder = NbtMap.builder();
        NbtMapBuilder carpetItem = NbtMap.builder()
                .putShort("Aux", Short.MAX_VALUE)
                .putString("Name", "minecraft:carpet");
        List<NbtMap> acceptedCarpet = Collections.singletonList(NbtMap.builder().putCompound("slotItem", carpetItem.build()).build());
        carpetBuilder.putList("acceptedItems", NbtType.COMPOUND, acceptedCarpet);
        carpetBuilder.putCompound("item", carpetItem.build());
        carpetBuilder.putInt("slotNumber", 1);
        CARPET_SLOT = carpetBuilder.build();

        NbtMapBuilder saddleBuilder = NbtMap.builder();
        NbtMapBuilder acceptedSaddle = NbtMap.builder()
                .putShort("Aux", Short.MAX_VALUE)
                .putString("Name", "minecraft:saddle");
        List<NbtMap> acceptedItem = Collections.singletonList(NbtMap.builder().putCompound("slotItem", acceptedSaddle.build()).build());
        saddleBuilder.putList("acceptedItems", NbtType.COMPOUND, acceptedItem);
        saddleBuilder.putCompound("item", acceptedSaddle.build());
        saddleBuilder.putInt("slotNumber", 0);
        SADDLE_SLOT = saddleBuilder.build();
    }

    @Override
    public void translate(GeyserSession session, ClientboundHorseScreenOpenPacket packet) {
        Entity entity = session.getEntityCache().getEntityByJavaId(packet.getEntityId());
        if (entity == null) {
            return;
        }

        UpdateEquipPacket updateEquipPacket = new UpdateEquipPacket();
        updateEquipPacket.setWindowId((short) packet.getContainerId());
        updateEquipPacket.setWindowType((short) ContainerType.HORSE.getId());
        updateEquipPacket.setUniqueEntityId(entity.getGeyserId());

        NbtMapBuilder builder = NbtMap.builder();
        List<NbtMap> slots = new ArrayList<>();

        // Since 1.20.5, the armor slot is not included in the container size,
        // but everything is still indexed the same.
        int slotCount = 2; // Don't depend on slot count sent from server

        InventoryTranslator<Container> inventoryTranslator;
        if (entity instanceof LlamaEntity llamaEntity) {
            if (entity.getFlag(EntityFlag.CHESTED)) {
                slotCount += llamaEntity.getStrength() * 3;
            }
            inventoryTranslator = new LlamaInventoryTranslator(slotCount);
            slots.add(CARPET_SLOT);
        } else if (entity instanceof ChestedHorseEntity) {
            if (entity.getFlag(EntityFlag.CHESTED)) {
                slotCount += 15;
            }
            inventoryTranslator = new DonkeyInventoryTranslator(slotCount);
            slots.add(SADDLE_SLOT);
        } else if (entity instanceof CamelEntity) {
            if (entity.getFlag(EntityFlag.CHESTED)) {
                slotCount += 15;
            }
            // The camel has an invisible armor slot and needs special handling, same as the donkey
            inventoryTranslator = new DonkeyInventoryTranslator(slotCount);
            slots.add(SADDLE_SLOT);
        } else {
            inventoryTranslator = new HorseInventoryTranslator(slotCount);
            slots.add(SADDLE_SLOT);
            if (!(entity instanceof SkeletonHorseEntity || entity instanceof ZombieHorseEntity)) {
                slots.add(ARMOR_SLOT);
            }
        }

        // Build the NbtMap that sets the icons for Bedrock (e.g. sets the saddle outline on the saddle slot)
        builder.putList("slots", NbtType.COMPOUND, slots);

        updateEquipPacket.setTag(builder.build());
        session.sendUpstreamPacket(updateEquipPacket);

        Container container = new Container(session, entity.getNametag(), packet.getContainerId(), slotCount, null);
        InventoryUtils.openInventory(new InventoryHolder<>(session, container, inventoryTranslator));
    }
}
