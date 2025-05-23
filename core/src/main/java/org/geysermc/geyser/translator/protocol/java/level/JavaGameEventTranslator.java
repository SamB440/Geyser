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

package org.geysermc.geyser.translator.protocol.java.level;

import org.geysermc.geyser.entity.EntityDefinitions;
import org.geysermc.geyser.session.cache.TeleportCache;
import org.geysermc.mcprotocollib.protocol.data.game.ClientCommand;
import org.geysermc.mcprotocollib.protocol.data.game.entity.player.GameMode;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.EnterCreditsValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RainStrengthValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.RespawnScreenValue;
import org.geysermc.mcprotocollib.protocol.data.game.level.notify.ThunderStrengthValue;
import org.geysermc.mcprotocollib.protocol.packet.ingame.clientbound.level.ClientboundGameEventPacket;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.ServerboundClientCommandPacket;
import org.cloudburstmc.protocol.bedrock.data.GameRuleData;
import org.cloudburstmc.protocol.bedrock.data.entity.EntityEventType;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.geysermc.geyser.entity.type.player.PlayerEntity;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.MinecraftLocale;
import org.geysermc.geyser.translator.inventory.PlayerInventoryTranslator;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.util.EntityUtils;

@Translator(packet = ClientboundGameEventPacket.class)
public class JavaGameEventTranslator extends PacketTranslator<ClientboundGameEventPacket> {
    @Override
    public void translate(GeyserSession session, ClientboundGameEventPacket packet) {
        PlayerEntity entity = session.getPlayerEntity();

        switch (packet.getNotification()) {
            // Yes, START_RAIN and STOP_RAIN are swapped in terms of what they cause the client to do.
            // This is how the Mojang mappings name them, so we go with it
            // It seems Mojang's intent was that START_RAIN would set the rain strength to 0 so that it can then be incremeneted on a gradient by the server
            // The inverse is true for STOP_RAIN
            // This is indeed the behavior of the vanilla server
            // However, it seems most server software (at least Spigot and Paper) did not go along with this
            // As a result many developers use these packets for the opposite of what their names implies
            // Behavior last verified with Java 1.19.4 and Bedrock 1.19.71
            case START_RAINING:
                session.updateRain(0);
                break;
            case STOP_RAINING:
                session.updateRain(1);
                break;
            case RAIN_LEVEL_CHANGE:
                // This is the rain strength on LevelEventType.START_RAINING, but can be any value on LevelEventType.STOP_RAINING
                float rainStrength = ((RainStrengthValue) packet.getValue()).getStrength();
                session.updateRain(rainStrength);
                break;
            case THUNDER_LEVEL_CHANGE:
                // See above, same process
                float thunderStrength = ((ThunderStrengthValue) packet.getValue()).getStrength();
                session.updateThunder(thunderStrength);
                break;
            case CHANGE_GAME_MODE:
                GameMode gameMode = (GameMode) packet.getValue();

                SetPlayerGameTypePacket playerGameTypePacket = new SetPlayerGameTypePacket();
                playerGameTypePacket.setGamemode(EntityUtils.toBedrockGamemode(gameMode).ordinal());
                session.sendUpstreamPacket(playerGameTypePacket);
                session.setGameMode(gameMode);

                session.sendAdventureSettings();

                if (session.getPlayerEntity().isOnGround() && gameMode == GameMode.SPECTATOR) {
                    // Fix a bug where the player has glitched movement and thinks they are still on the ground
                    MovePlayerPacket movePlayerPacket = new MovePlayerPacket();
                    movePlayerPacket.setRuntimeEntityId(entity.getGeyserId());
                    movePlayerPacket.setPosition(entity.getPosition());
                    movePlayerPacket.setRotation(entity.getBedrockRotation());
                    movePlayerPacket.setOnGround(false);
                    movePlayerPacket.setMode(MovePlayerPacket.Mode.TELEPORT);
                    movePlayerPacket.setTeleportationCause(MovePlayerPacket.TeleportationCause.UNKNOWN);
                    session.sendUpstreamPacket(movePlayerPacket);

                    session.getUnconfirmedTeleports().add(new TeleportCache(null, entity.position(), entity.getPitch(), entity.getYaw(), -1));
                }

                // Update the crafting grid to add/remove barriers for creative inventory
                PlayerInventoryTranslator.updateCraftingGrid(session, session.getPlayerInventory());
                break;
            case WIN_GAME:
                switch ((EnterCreditsValue) packet.getValue()) {
                    case SEEN_BEFORE -> {
                        ServerboundClientCommandPacket javaRespawnPacket = new ServerboundClientCommandPacket(ClientCommand.RESPAWN);
                        session.sendDownstreamGamePacket(javaRespawnPacket);
                    }
                    case FIRST_TIME -> {
                        ShowCreditsPacket showCreditsPacket = new ShowCreditsPacket();
                        showCreditsPacket.setStatus(ShowCreditsPacket.Status.START_CREDITS);
                        showCreditsPacket.setRuntimeEntityId(entity.getGeyserId());
                        session.sendUpstreamPacket(showCreditsPacket);
                    }
                }
                break;
            case GUARDIAN_ELDER_EFFECT:
                // note: There is a ElderGuardianEffectValue that determines if a sound should be made or not,
                // but that doesn't seem to be controllable on Bedrock Edition
                EntityEventPacket eventPacket = new EntityEventPacket();
                eventPacket.setType(EntityEventType.ELDER_GUARDIAN_CURSE);
                eventPacket.setData(0);
                eventPacket.setRuntimeEntityId(entity.getGeyserId());
                session.sendUpstreamPacket(eventPacket);
                break;
            case IMMEDIATE_RESPAWN:
                GameRulesChangedPacket gamerulePacket = new GameRulesChangedPacket();
                gamerulePacket.getGameRules().add(new GameRuleData<>("doimmediaterespawn",
                        packet.getValue() == RespawnScreenValue.IMMEDIATE_RESPAWN));
                session.sendUpstreamPacket(gamerulePacket);
                break;
            case NO_RESPAWN_BLOCK_AVAILABLE:
                // Not sent as a proper message? Odd.
                session.sendMessage(MinecraftLocale.getLocaleString("block.minecraft.spawn.not_valid",
                        session.locale()));
                break;
            case PLAY_ARROW_HIT_SOUND:
                PlaySoundPacket arrowSoundPacket = new PlaySoundPacket();
                arrowSoundPacket.setSound("random.orb");
                arrowSoundPacket.setPitch(0.5f);
                arrowSoundPacket.setVolume(0.5f);
                arrowSoundPacket.setPosition(entity.getPosition());
                session.sendUpstreamPacket(arrowSoundPacket);
                break;
            default:
                // DEMO_EVENT               - for JE game demo
                // LEVEL_CHUNKS_LOAD_START  - ???
                // PUFFER_FISH_STING        - doesn't exist on bedrock
                break;
        }
    }
}
