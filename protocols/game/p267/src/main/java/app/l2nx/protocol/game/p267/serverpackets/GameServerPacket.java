/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.protocol.game.p267.serverpackets;

import io.netty.buffer.ByteBuf;

/** Writes its opcode and body; the frame header in front of it is the caller's. */
public sealed interface GameServerPacket
        permits ActionFailed,
                Attack,
                CharCreateFail,
                CharCreateOk,
                CharDeleteFail,
                CharDeleteSuccess,
                CharSelected,
                CharSelectionInfo,
                CreatureSay,
                DeleteObject,
                Die,
                ExChangePostState,
                ExNoticePostArrived,
                ExNoticePostSent,
                ExReplyPostItemList,
                ExReplyReceivedPost,
                ExReplySentPost,
                ExShowReceivedPostList,
                ExShowSentPostList,
                KeyPacket,
                LeaveWorld,
                LoginFail,
                MagicSkillUse,
                MoveToLocation,
                MoveToPawn,
                MyTargetSelected,
                RestartResponse,
                Revive,
                ServerClose,
                StatusUpdate,
                StopMove,
                SystemMessage,
                TeleportToLocation,
                ValidateLocation {
    void write(ByteBuf out);
}
