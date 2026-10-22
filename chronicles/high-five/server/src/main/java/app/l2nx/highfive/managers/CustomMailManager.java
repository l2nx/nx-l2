/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: MIT
 */
/*
 * SPDX-License-Identifier: MIT
 *
 * Copyright (c) 2013 L2jMobius
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR
 * IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package app.l2nx.highfive.managers;

import app.l2nx.highfive.config.custom.CustomMailManagerConfig;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.ItemEnchantHolder;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Mail;
import app.l2nx.highfive.network.enums.MessageSenderType;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.highfive.partition.BackgroundTasks;
import app.l2nx.highfive.partition.WorldPartition;
import app.l2nx.highfive.repository.mail.CustomMailRepository;
import app.l2nx.highfive.repository.mail.model.CustomMail;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

/**
 * The next read is armed only after the world pass that sends the rows, or the same rows would be sent twice.
 *
 * @author Mobius
 */
@Slf4j
@Component
public class CustomMailManager {
    private static volatile CustomMailManager instance;

    private final CustomMailRepository customMailRepository;
    private final MailManager mailManager;

    public CustomMailManager(CustomMailRepository customMailRepository, MailManager mailManager) {
        this.customMailRepository = customMailRepository;
        this.mailManager = mailManager;
        instance = this;
    }

    public void load() {
        pollLater();

        log.info("Enabled.");
    }

    private void pollLater() {
        try {
            BackgroundTasks.schedule(
                    this::poll, CustomMailManagerConfig.CUSTOM_MAIL_MANAGER_DELAY, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("Not polling custom mail any more: the server is stopping.");
        }
    }

    private void poll() {
        final List<CustomMail> rows;
        try {
            rows = customMailRepository.findAll();
        } catch (DataAccessException e) {
            log.warn("Error reading from database: ", e);
            pollLater();
            return;
        }

        if (rows.isEmpty()) {
            pollLater();
            return;
        }

        try {
            WorldPartition.offer(() -> deliver(rows));
        } catch (RejectedExecutionException e) {
            log.debug("Not delivering custom mail: the world has stopped.");
        }
    }

    private void deliver(List<CustomMail> rows) {
        try {
            for (CustomMail row : rows) {
                final int playerId = row.receiverId();
                final Player player = World.getPlayer(playerId);
                if ((player != null) && player.isOnline()) {
                    // Delete before sending: a crash in between loses one mail instead of duplicating its items.
                    // A row that could not be deleted waits for the next poll, or every poll would send it again.
                    try {
                        customMailRepository.delete(row.id());
                    } catch (DataAccessException e) {
                        log.warn("Error deleting entry from database: ", e);
                        continue;
                    }

                    // Create message.
                    final MailMessage msg =
                            new MailMessage(playerId, row.subject(), row.content(), MessageSenderType.PLAYER);
                    final List<ItemEnchantHolder> itemHolders = parseItems(row.items());
                    if (!itemHolders.isEmpty()) {
                        final Mail attachments = msg.createAttachments();
                        for (ItemEnchantHolder itemHolder : itemHolders) {
                            final Item item = attachments.addItem(
                                    ItemProcessType.TRANSFER, itemHolder.getId(), itemHolder.getCount(), null, null);
                            if (itemHolder.getEnchantLevel() > 0) {
                                item.setEnchantLevel(itemHolder.getEnchantLevel());
                            }
                        }
                    }

                    // Send message.
                    mailManager.sendMessage(msg);
                    log.info("Message sent to {}.", player.getName());
                }
            }
        } finally {
            pollLater();
        }
    }

    private static List<ItemEnchantHolder> parseItems(String items) {
        final List<ItemEnchantHolder> itemHolders = new ArrayList<>();
        if (items == null) {
            return itemHolders;
        }

        for (String str : items.split(";")) {
            if (str.contains(" ")) {
                final String[] split = str.split(" ");
                final String itemId = split[0];
                final String itemCount = split[1];
                final String enchant = split.length > 2 ? split[2] : "0";
                if (StringUtils.isNumeric(itemId) && StringUtils.isNumeric(itemCount)) {
                    itemHolders.add(new ItemEnchantHolder(
                            Integer.parseInt(itemId), Long.parseLong(itemCount), Integer.parseInt(enchant)));
                }
            } else if (StringUtils.isNumeric(str)) {
                itemHolders.add(new ItemEnchantHolder(Integer.parseInt(str), 1));
            }
        }

        return itemHolders;
    }

    // TODO: callers receive the bean through their constructor (#41).
    public static CustomMailManager getInstance() {
        final CustomMailManager current = instance;
        if (current == null) {
            throw new IllegalStateException("CustomMailManager is used before the Spring context created it.");
        }
        return current;
    }
}
