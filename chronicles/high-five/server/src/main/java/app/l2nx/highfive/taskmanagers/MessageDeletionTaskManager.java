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
package app.l2nx.highfive.taskmanagers;

import app.l2nx.highfive.clock.ClockBridge;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.managers.MailManager;
import app.l2nx.highfive.network.SystemMessageId;
import app.l2nx.highfive.network.holders.MailMessage;
import app.l2nx.highfive.network.protocol.p267.serverpackets.SystemMessage;
import app.l2nx.highfive.partition.WorldPartition;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author Mobius
 */
public class MessageDeletionTaskManager implements Runnable {
    private static final Map<Integer, Long> PENDING_MESSAGES = new ConcurrentHashMap<>();
    private static boolean working = false;

    protected MessageDeletionTaskManager() {
        WorldPartition.loop().scheduleAtFixedRate(this, 10000, 10000, TimeUnit.MILLISECONDS);
    }

    @Override
    public void run() {
        if (working) {
            return;
        }

        working = true;

        if (!PENDING_MESSAGES.isEmpty()) {
            final long currentTime = ClockBridge.wallMillis();
            final Iterator<Entry<Integer, Long>> iterator =
                    PENDING_MESSAGES.entrySet().iterator();
            Entry<Integer, Long> entry;
            Integer messageId;
            MailMessage message;

            while (iterator.hasNext()) {
                entry = iterator.next();
                if (currentTime > entry.getValue()) {
                    messageId = entry.getKey();
                    message = MailManager.getInstance().getMessage(messageId);
                    if (message == null) {
                        iterator.remove();
                        continue;
                    }

                    if (message.hasAttachments()) {
                        final Player sender = World.getPlayer(message.getSenderId());
                        final Player receiver = World.getPlayer(message.getReceiverId());
                        MailManager.getInstance().returnAttachments(message);
                        if (sender != null) {
                            sender.sendPacket(SystemMessageId.THE_MAIL_WAS_RETURNED_DUE_TO_THE_EXCEEDED_WAITING_TIME);
                        }

                        if (receiver != null) {
                            receiver.sendPacket(new SystemMessage(
                                    SystemMessageId.THE_MAIL_WAS_RETURNED_DUE_TO_THE_EXCEEDED_WAITING_TIME));
                        }
                    }

                    MailManager.getInstance().deleteMessageInDb(messageId);
                    iterator.remove();
                }
            }
        }

        working = false;
    }

    public void add(int msgId, long deletionTime) {
        PENDING_MESSAGES.put(msgId, deletionTime);
    }

    public static MessageDeletionTaskManager getInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static class SingletonHolder {
        protected static final MessageDeletionTaskManager INSTANCE = new MessageDeletionTaskManager();
    }
}
