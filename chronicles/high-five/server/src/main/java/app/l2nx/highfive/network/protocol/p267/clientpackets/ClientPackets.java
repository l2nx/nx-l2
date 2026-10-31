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
package app.l2nx.highfive.network.protocol.p267.clientpackets;

import app.l2nx.highfive.config.DevelopmentConfig;
import app.l2nx.highfive.session.GameContext;
import java.util.function.Supplier;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * @author Mobius
 */
@Slf4j
public enum ClientPackets {
    LOGOUT(0x00, Logout::new, GameContext.ACCOUNT),
    ATTACK(0x01, AttackRequest::new),
    REQUEST_START_PLEDGE_WAR(0x03, RequestStartPledgeWar::new),
    REQUEST_REPLY_START_PLEDGE(0x04, RequestReplyStartPledgeWar::new),
    REQUEST_STOP_PLEDGE_WAR(0x05, RequestStopPledgeWar::new),
    REQUEST_REPLY_STOP_PLEDGE_WAR(0x06, RequestReplyStopPledgeWar::new),
    REQUEST_SURRENDER_PLEDGE_WAR(0x07, RequestSurrenderPledgeWar::new),
    REQUEST_REPLY_SURRENDER_PLEDGE_WAR(0x08, RequestReplySurrenderPledgeWar::new),
    REQUEST_SET_PLEDGE_CREST(0x09, RequestSetPledgeCrest::new),
    REQUEST_GIVE_NICK_NAME(0x0B, RequestGiveNickName::new),
    CHARACTER_CREATE(0x0C, CharacterCreate::new, GameContext.LOBBY),
    CHARACTER_DELETE(0x0D, CharacterDelete::new, GameContext.LOBBY),
    MOVE_TO_LOCATION(0x0F, MoveToLocation::new),
    ENTER_WORLD(0x11, EnterWorld::new, GameContext.CHARACTER),
    CHARACTER_SELECT(0x12, CharacterSelect::new, GameContext.LOBBY),
    NEW_CHARACTER(0x13, NewCharacter::new, GameContext.LOBBY),
    REQUEST_ITEM_LIST(0x14, RequestItemList::new),
    REQUEST_UN_EQUIP_ITEM(0x16, RequestUnEquipItem::new),
    REQUEST_DROP_ITEM(0x17, RequestDropItem::new),
    USE_ITEM(0x19, UseItem::new),
    TRADE_REQUEST(0x1A, TradeRequest::new),
    ADD_TRADE_ITEM(0x1B, AddTradeItem::new),
    TRADE_DONE(0x1C, TradeDone::new),
    ACTION(0x1F, Action::new),
    REQUEST_LINK_HTML(0x22, RequestLinkHtml::new),
    REQUEST_BYPASS_TO_SERVER(0x23, RequestBypassToServer::new),
    REQUEST_BBS_WRITE(0x24, RequestBBSwrite::new),
    REQUEST_JOIN_PLEDGE(0x26, RequestJoinPledge::new),
    REQUEST_ANSWER_JOIN_PLEDGE(0x27, RequestAnswerJoinPledge::new),
    REQUEST_WITHDRAWAL_PLEDGE(0x28, RequestWithdrawalPledge::new),
    REQUEST_OUST_PLEDGE_MEMBER(0x29, RequestOustPledgeMember::new),
    REQUEST_GET_ITEM_FROM_PET(0x2C, RequestGetItemFromPet::new),
    REQUEST_ALLY_INFO(0x2E, RequestAllyInfo::new),
    REQUEST_CRYSTALLIZE_ITEM(0x2F, RequestCrystallizeItem::new),
    REQUEST_PRIVATE_STORE_MANAGE_SELL(0x30, RequestPrivateStoreManageSell::new),
    SET_PRIVATE_STORE_LIST_SELL(0x31, SetPrivateStoreListSell::new),
    ATTACK_REQUEST(0x32, AttackRequest::new),
    REQUEST_TELEPORT(0x33, null),
    SOCIAL_ACTION(0x34, null),
    CHANGE_MOVE_TYPE(0x35, null),
    CHANGE_WAIT_TYPE(0x36, null),
    REQUEST_SELL_ITEM(0x37, RequestSellItem::new),
    REQUEST_MAGIC_SKILL_LIST(0x38, null),
    REQUEST_MAGIC_SKILL_USE(0x39, RequestMagicSkillUse::new),
    APPEARING(0x3A, Appearing::new),
    SEND_WARE_HOUSE_DEPOSIT_LIST(0x3B, SendWareHouseDepositList::new),
    SEND_WARE_HOUSE_WITH_DRAW_LIST(0x3C, SendWareHouseWithDrawList::new),
    REQUEST_SHORT_CUT_REG(0x3D, RequestShortcutReg::new),
    REQUEST_SHORT_CUT_DEL(0x3F, RequestShortcutDel::new),
    REQUEST_BUY_ITEM(0x40, RequestBuyItem::new),
    REQUEST_JOIN_PARTY(0x42, RequestJoinParty::new),
    REQUEST_ANSWER_JOIN_PARTY(0x43, RequestAnswerJoinParty::new),
    REQUEST_WITH_DRAWAL_PARTY(0x44, RequestWithDrawalParty::new),
    REQUEST_OUST_PARTY_MEMBER(0x45, RequestOustPartyMember::new),
    CANNOT_MOVE_ANYMORE(0x47, CannotMoveAnymore::new),
    REQUEST_TARGET_CANCELD(0x48, RequestTargetCanceld::new),
    SAY2(0x49, Say2::new),
    REQUEST_PLEDGE_MEMBER_LIST(0x4D, RequestPledgeMemberList::new),
    REQUEST_MAGIC_LIST(0x4F, null),
    REQUEST_SKILL_LIST(0x50, RequestSkillList::new),
    MOVE_WITH_DELTA(0x52, MoveWithDelta::new),
    REQUEST_GET_ON_VEHICLE(0x53, RequestGetOnVehicle::new),
    REQUEST_GET_OFF_VEHICLE(0x54, RequestGetOffVehicle::new),
    ANSWER_TRADE_REQUEST(0x55, AnswerTradeRequest::new),
    REQUEST_ACTION_USE(0x56, RequestActionUse::new),
    REQUEST_RESTART(0x57, RequestRestart::new),
    VALIDATE_POSITION(0x59, ValidatePosition::new),
    START_ROTATING(0x5B, StartRotating::new),
    FINISH_ROTATING(0x5C, FinishRotating::new),
    REQUEST_SHOW_BOARD(0x5E, RequestShowBoard::new),
    REQUEST_ENCHANT_ITEM(0x5F, RequestEnchantItem::new),
    REQUEST_DESTROY_ITEM(0x60, RequestDestroyItem::new),
    REQUEST_QUEST_LIST(0x62, RequestQuestList::new),
    REQUEST_QUEST_ABORT(0x63, RequestQuestAbort::new),
    REQUEST_PLEDGE_INFO(0x65, RequestPledgeInfo::new),
    REQUEST_PLEDGE_EXTENDED_INFO(0x66, RequestPledgeExtendedInfo::new),
    REQUEST_PLEDGE_CREST(0x67, RequestPledgeCrest::new),
    REQUEST_SEND_FRIEND_MSG(0x6B, RequestSendFriendMsg::new),
    REQUEST_SHOW_MINI_MAP(0x6C, RequestShowMiniMap::new),
    REQUEST_RECORD_INFO(0x6E, RequestRecordInfo::new),
    REQUEST_HENNA_EQUIP(0x6F, RequestHennaEquip::new),
    REQUEST_HENNA_REMOVE_LIST(0x70, RequestHennaRemoveList::new),
    REQUEST_HENNA_ITEM_REMOVE_INFO(0x71, RequestHennaItemRemoveInfo::new),
    REQUEST_HENNA_REMOVE(0x72, RequestHennaRemove::new),
    REQUEST_ACQUIRE_SKILL_INFO(0x73, RequestAcquireSkillInfo::new),
    SEND_BYPASS_BUILD_CMD(0x74, SendBypassBuildCmd::new),
    REQUEST_MOVE_TO_LOCATION_IN_VEHICLE(0x75, RequestMoveToLocationInVehicle::new),
    CANNOT_MOVE_ANYMORE_IN_VEHICLE(0x76, CannotMoveAnymoreInVehicle::new),
    REQUEST_FRIEND_INVITE(0x77, RequestFriendInvite::new),
    REQUEST_ANSWER_FRIEND_INVITE(0x78, RequestAnswerFriendInvite::new),
    REQUEST_FRIEND_LIST(0x79, RequestFriendList::new),
    REQUEST_FRIEND_DEL(0x7A, RequestFriendDel::new),
    CHARACTER_RESTORE(0x7B, CharacterRestore::new, GameContext.LOBBY),
    REQUEST_ACQUIRE_SKILL(0x7C, RequestAcquireSkill::new),
    REQUEST_RESTART_POINT(0x7D, RequestRestartPoint::new),
    REQUEST_GM_COMMAND(0x7E, RequestGMCommand::new),
    REQUEST_PARTY_MATCH_CONFIG(0x7F, RequestPartyMatchConfig::new),
    REQUEST_PARTY_MATCH_LIST(0x80, RequestPartyMatchList::new),
    REQUEST_PARTY_MATCH_DETAIL(0x81, RequestPartyMatchDetail::new),
    REQUEST_PRIVATE_STORE_BUY(0x83, RequestPrivateStoreBuy::new),
    REQUEST_TUTORIAL_LINK_HTML(0x85, RequestTutorialLinkHtml::new),
    REQUEST_TUTORIAL_PASS_CMD_TO_SERVER(0x86, RequestTutorialPassCmdToServer::new),
    REQUEST_TUTORIAL_QUESTION_MARK(0x87, RequestTutorialQuestionMark::new),
    REQUEST_TUTORIAL_CLIENT_EVENT(0x88, RequestTutorialClientEvent::new),
    REQUEST_PETITION(0x89, RequestPetition::new),
    REQUEST_PETITION_CANCEL(0x8A, RequestPetitionCancel::new),
    REQUEST_GM_LIST(0x8B, RequestGmList::new),
    REQUEST_JOIN_ALLY(0x8C, RequestJoinAlly::new),
    REQUEST_ANSWER_JOIN_ALLY(0x8D, RequestAnswerJoinAlly::new),
    ALLY_LEAVE(0x8E, AllyLeave::new),
    ALLY_DISMISS(0x8F, AllyDismiss::new),
    REQUEST_DISMISS_ALLY(0x90, RequestDismissAlly::new),
    REQUEST_SET_ALLY_CREST(0x91, RequestSetAllyCrest::new),
    REQUEST_ALLY_CREST(0x92, RequestAllyCrest::new),
    REQUEST_CHANGE_PET_NAME(0x93, RequestChangePetName::new),
    REQUEST_PET_USE_ITEM(0x94, RequestPetUseItem::new),
    REQUEST_GIVE_ITEM_TO_PET(0x95, RequestGiveItemToPet::new),
    REQUEST_PRIVATE_STORE_QUIT_SELL(0x96, RequestPrivateStoreQuitSell::new),
    SET_PRIVATE_STORE_MSG_SELL(0x97, SetPrivateStoreMsgSell::new),
    REQUEST_PET_GET_ITEM(0x98, RequestPetGetItem::new),
    REQUEST_PRIVATE_STORE_MANAGE_BUY(0x99, RequestPrivateStoreManageBuy::new),
    SET_PRIVATE_STORE_LIST_BUY(0x9A, SetPrivateStoreListBuy::new),
    REQUEST_PRIVATE_STORE_QUIT_BUY(0x9C, RequestPrivateStoreQuitBuy::new),
    SET_PRIVATE_STORE_MSG_BUY(0x9D, SetPrivateStoreMsgBuy::new),
    REQUEST_PRIVATE_STORE_SELL(0x9F, RequestPrivateStoreSell::new),
    SEND_TIME_CHECK_PACKET(0xA0, null),
    REQUEST_SKILL_COOL_TIME(0xA6, RequestSkillCoolTime::new, GameContext.CHARACTER),
    REQUEST_PACKAGE_SENDABLE_ITEM_LIST(0xA7, RequestPackageSendableItemList::new),
    REQUEST_PACKAGE_SEND(0xA8, RequestPackageSend::new),
    REQUEST_BLOCK(0xA9, RequestBlock::new),
    REQUEST_SIEGE_INFO(0xAA, RequestSiegeInfo::new),
    REQUEST_SIEGE_ATTACKER_LIST(0xAB, RequestSiegeAttackerList::new),
    REQUEST_SIEGE_DEFENDER_LIST(0xAC, RequestSiegeDefenderList::new),
    REQUEST_JOIN_SIEGE(0xAD, RequestJoinSiege::new),
    REQUEST_CONFIRM_SIEGE_WAITING_LIST(0xAE, RequestConfirmSiegeWaitingList::new),
    REQUEST_SET_CASTLE_SIEGE_TIME(0xAF, RequestSetCastleSiegeTime::new),
    MULTI_SELL_CHOOSE(0xB0, MultiSellChoose::new),
    REQUEST_REMAIN_TIME(0xB2, null),
    BYPASS_USER_CMD(0xB3, BypassUserCmd::new),
    SNOOP_QUIT(0xB4, SnoopQuit::new),
    REQUEST_RECIPE_BOOK_OPEN(0xB5, RequestRecipeBookOpen::new),
    REQUEST_RECIPE_BOOK_DESTROY(0xB6, RequestRecipeBookDestroy::new),
    REQUEST_RECIPE_ITEM_MAKE_INFO(0xB7, RequestRecipeItemMakeInfo::new),
    REQUEST_RECIPE_ITEM_MAKE_SELF(0xB8, RequestRecipeItemMakeSelf::new),
    REQUEST_RECIPE_SHOP_MANAGE_LIST(0xB9, null),
    REQUEST_RECIPE_SHOP_MESSAGE_SET(0xBA, RequestRecipeShopMessageSet::new),
    REQUEST_RECIPE_SHOP_LIST_SET(0xBB, RequestRecipeShopListSet::new),
    REQUEST_RECIPE_SHOP_MANAGE_QUIT(0xBC, RequestRecipeShopManageQuit::new),
    REQUEST_RECIPE_SHOP_MANAGE_CANCEL(0xBD, null),
    REQUEST_RECIPE_SHOP_MAKE_INFO(0xBE, RequestRecipeShopMakeInfo::new),
    REQUEST_RECIPE_SHOP_MAKE_ITEM(0xBF, RequestRecipeShopMakeItem::new),
    REQUEST_RECIPE_SHOP_MANAGE_PREV(0xC0, RequestRecipeShopManagePrev::new),
    OBSERVER_RETURN(0xC1, ObserverReturn::new),
    REQUEST_EVALUATE(0xC2, null),
    REQUEST_HENNA_ITEM_LIST(0xC3, RequestHennaItemList::new),
    REQUEST_HENNA_ITEM_INFO(0xC4, RequestHennaItemInfo::new),
    REQUEST_BUY_SEED(0xC5, RequestBuySeed::new),
    DLG_ANSWER(0xC6, DlgAnswer::new),
    REQUEST_PREVIEW_ITEM(0xC7, RequestPreviewItem::new),
    REQUEST_SSQ_STATUS(0xC8, RequestSSQStatus::new),
    REQUEST_PETITION_FEEDBACK(0xC9, RequestPetitionFeedback::new),
    GAME_GUARD_REPLY(0xCB, GameGuardReply::new),
    REQUEST_PLEDGE_POWER(0xCC, RequestPledgePower::new),
    REQUEST_MAKE_MACRO(0xCD, RequestMakeMacro::new),
    REQUEST_DELETE_MACRO(0xCE, RequestDeleteMacro::new),
    REQUEST_BUY_PROCURE(0xCF, null);

    @Getter
    private final int packetId;

    private final Supplier<ClientPacket> packetSupplier;
    private final boolean implemented;

    @Getter
    private final GameContext context;

    ClientPackets(int packetId, Supplier<ClientPacket> packetSupplier) {
        this(packetId, packetSupplier, GameContext.WORLD);
    }

    ClientPackets(int packetId, Supplier<ClientPacket> packetSupplier, GameContext context) {
        // Packet id is an unsigned byte.
        if (packetId > 0xFF) {
            throw new IllegalArgumentException("Packet id must not be bigger than 0xFF");
        }

        this.packetId = packetId;
        this.packetSupplier = packetSupplier;
        implemented = packetSupplier != null;
        this.context = context;
    }

    public boolean implemented() {
        return implemented;
    }

    /** Only for an {@link #implemented()} entry. */
    public ClientPacket newPacket() {
        final ClientPacket packet = packetSupplier.get();
        packet.requireContext(context);
        if (DevelopmentConfig.DEBUG_CLIENT_PACKETS) {
            final String name = packet.getClass().getSimpleName();
            if (!DevelopmentConfig.EXCLUDED_DEBUG_PACKETS.contains(name)) {
                log.info("[C] {}", name);
            }
        }
        return packet;
    }
}
