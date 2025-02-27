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

package org.geysermc.geyser.translator.protocol.bedrock;

import org.cloudburstmc.protocol.bedrock.packet.BookEditPacket;
import org.geysermc.geyser.GeyserLogger;
import org.geysermc.geyser.inventory.GeyserItemStack;
import org.geysermc.geyser.item.type.WrittenBookItem;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.translator.protocol.PacketTranslator;
import org.geysermc.geyser.translator.protocol.Translator;
import org.geysermc.geyser.translator.text.MessageTranslator;
import org.geysermc.mcprotocollib.protocol.data.game.item.ItemStack;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponentTypes;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.DataComponents;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.Filterable;
import org.geysermc.mcprotocollib.protocol.data.game.item.component.WritableBookContent;
import org.geysermc.mcprotocollib.protocol.packet.ingame.serverbound.inventory.ServerboundEditBookPacket;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

@Translator(packet = BookEditPacket.class)
public class BedrockBookEditTranslator extends PacketTranslator<BookEditPacket> {

    @Override
    public void translate(GeyserSession session, BookEditPacket packet) {
        if (packet.getText() != null && !packet.getText().isEmpty() && packet.getText().length() > WrittenBookItem.MAXIMUM_PAGE_EDIT_LENGTH) {
            GeyserLogger.get().warning("Page length greater than server allowed!");
            return;
        }

        GeyserItemStack itemStack = session.getPlayerInventory().getItemInHand();
        if (itemStack != null) {
            DataComponents components = itemStack.getOrCreateComponents();
            ItemStack bookItem = new ItemStack(itemStack.getJavaId(), itemStack.getAmount(), components);
            List<String> pages = new LinkedList<>();

            WritableBookContent writableBookContent = components.get(DataComponentTypes.WRITABLE_BOOK_CONTENT);
            if (writableBookContent != null) {
                for (Filterable<String> page : writableBookContent.getPages()) {
                    pages.add(page.getRaw());
                }
            }

            int page = packet.getPageNumber();
            if (page < 0 || WrittenBookItem.MAXIMUM_PAGE_COUNT <= page) {
                GeyserLogger.get().warning("Edited page is out of acceptable bounds!");
                return;
            }
            switch (packet.getAction()) {
                case ADD_PAGE: {
                    // Add empty pages in between
                    for (int i = pages.size(); i < page; i++) {
                        pages.add(i, "");
                    }
                    pages.add(page, MessageTranslator.convertToPlainText(packet.getText()));
                    break;
                }
                // Called whenever a page is modified
                case REPLACE_PAGE: {
                    if (page < pages.size()) {
                        pages.set(page, MessageTranslator.convertToPlainText(packet.getText()));
                    } else {
                        // Add empty pages in between
                        for (int i = pages.size(); i < page; i++) {
                            pages.add(i, "");
                        }
                        pages.add(page, MessageTranslator.convertToPlainText(packet.getText()));
                    }
                    break;
                }
                case DELETE_PAGE: {
                    if (page < pages.size()) {
                        pages.remove(page);
                    }
                    break;
                }
                case SWAP_PAGES: {
                    int page2 = packet.getSecondaryPageNumber();
                    if (page < pages.size() && page2 < pages.size()) {
                        Collections.swap(pages, page, page2);
                    }
                    break;
                }
                case SIGN_BOOK: {
                    // As of JE 1.20.5, client no longer adds title and author on its own
                    break;
                }
                default:
                    return;
            }
            // Remove empty pages at the end
            while (!pages.isEmpty()) {
                String currentPage = pages.get(pages.size() - 1);
                if (currentPage.isEmpty()) {
                    pages.remove(pages.size() - 1);
                } else {
                    break;
                }
            }

            List<Filterable<String>> filterablePages = new ArrayList<>(pages.size());
            for (String raw : pages) {
                filterablePages.add(new Filterable<>(raw, null));
            }
            components.put(DataComponentTypes.WRITABLE_BOOK_CONTENT, new WritableBookContent(filterablePages));

            // Update local copy
            session.getPlayerInventory().setItem(36 + session.getPlayerInventory().getHeldItemSlot(), GeyserItemStack.from(bookItem), session);
            session.getInventoryTranslator().updateInventory(session, session.getPlayerInventory());

            String title;
            if (packet.getAction() == BookEditPacket.Action.SIGN_BOOK) {
                // Add title to packet so the server knows we're signing
                title = MessageTranslator.convertToPlainText(packet.getTitle());
                if (title.length() > WrittenBookItem.MAXIMUM_TITLE_LENGTH) {
                    GeyserLogger.get().warning("Book title larger than server allows!");
                    return;
                }
            } else {
                title = null;
            }

            session.getBookEditCache().setPacket(new ServerboundEditBookPacket(session.getPlayerInventory().getHeldItemSlot(), pages, title));
            // There won't be any more book updates after this, so we can try sending the edit packet immediately
            if (packet.getAction() == BookEditPacket.Action.SIGN_BOOK) {
                session.getBookEditCache().checkForSend();
            }
        }
    }
}
