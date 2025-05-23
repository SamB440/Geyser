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

package org.geysermc.geyser.inventory;

import lombok.Getter;
import org.geysermc.geyser.level.block.Blocks;
import org.geysermc.geyser.level.block.type.Block;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.inventory.Generic3X3InventoryTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.inventory.ContainerType;

@Getter
public class Generic3X3Container extends Container {
    /**
     * Whether we need to set the container type as {@link org.cloudburstmc.protocol.bedrock.data.inventory.ContainerType#DROPPER}.
     * <p>
     * Used at {@link Generic3X3InventoryTranslator#openInventory(GeyserSession, Generic3X3Container)}
     */
    private boolean isDropper = false;

    public Generic3X3Container(GeyserSession session, String title, int id, int size, ContainerType containerType) {
        super(session, title, id, size, containerType);
    }

    @Override
    public void setUsingRealBlock(boolean usingRealBlock, Block block) {
        super.setUsingRealBlock(usingRealBlock, block);
        if (usingRealBlock) {
            isDropper = block == Blocks.DROPPER;
        }
    }
}
