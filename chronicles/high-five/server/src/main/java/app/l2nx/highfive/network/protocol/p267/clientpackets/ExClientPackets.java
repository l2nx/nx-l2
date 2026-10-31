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
public enum ExClientPackets {
    REQUEST_GOTO_LOBBY(0x36, RequestGotoLobby::new, GameContext.LOBBY),
    REQUEST_EX_2ND_PASSWORD_CHECK(0x93, RequestEx2ndPasswordCheck::new, GameContext.LOBBY),
    REQUEST_EX_2ND_PASSWORD_VERIFY(0x94, RequestEx2ndPasswordVerify::new, GameContext.LOBBY),
    REQUEST_EX_2ND_PASSWORD_REQ(0x95, RequestEx2ndPasswordReq::new, GameContext.LOBBY),
    REQUEST_MANOR_LIST(0x01, RequestManorList::new, GameContext.CHARACTER),
    REQUEST_PROCEDURE_CROP_LIST(0x02, RequestProcureCropList::new),
    REQUEST_SET_SEED(0x03, RequestSetSeed::new),
    REQUEST_SET_CROP(0x04, RequestSetCrop::new),
    REQUEST_WRITE_HERO_WORDS(0x05, RequestWriteHeroWords::new),
    REQUEST_EX_ASK_JOIN_MPCC(0x06, RequestExAskJoinMPCC::new),
    REQUEST_EX_ACCEPT_JOIN_MPCC(0x07, RequestExAcceptJoinMPCC::new),
    REQUEST_EX_OUST_FROM_MPCC(0x08, RequestExOustFromMPCC::new),
    REQUEST_OUST_FROM_PARTY_ROOM(0x09, RequestOustFromPartyRoom::new),
    REQUEST_DISMISS_PARTY_ROOM(0x0A, RequestDismissPartyRoom::new),
    REQUEST_WITHDRAW_PARTY_ROOM(0x0B, RequestWithdrawPartyRoom::new),
    REQUEST_CHANGE_PARTY_LEADER(0x0C, RequestChangePartyLeader::new),
    REQUEST_AUTO_SOULSHOT(0x0D, RequestAutoSoulShot::new),
    REQUEST_EX_ENCHANT_SKILL_INFO(0x0E, RequestExEnchantSkillInfo::new),
    REQUEST_EX_ENCHANT_SKILL(0x0F, RequestExEnchantSkill::new),
    REQUEST_EX_PLEDGE_CREST_LARGE(0x10, RequestExPledgeCrestLarge::new),
    REQUEST_EX_SET_PLEDGE_CREST_LARGE(0x11, RequestExSetPledgeCrestLarge::new),
    REQUEST_PLEDGE_SET_ACADEMY_MASTER(0x12, RequestPledgeSetAcademyMaster::new),
    REQUEST_PLEDGE_POWER_GRADE_LIST(0x13, RequestPledgePowerGradeList::new),
    REQUEST_PLEDGE_MEMBER_POWER_INFO(0x14, RequestPledgeMemberPowerInfo::new),
    REQUEST_PLEDGE_SET_MEMBER_POWER_GRADE(0x15, RequestPledgeSetMemberPowerGrade::new),
    REQUEST_PLEDGE_MEMBER_INFO(0x16, RequestPledgeMemberInfo::new),
    REQUEST_PLEDGE_WAR_LIST(0x17, RequestPledgeWarList::new),
    REQUEST_EX_FISH_RANKING(0x18, RequestExFishRanking::new),
    REQUEST_PCCAFE_COUPON_USE(0x19, RequestPCCafeCouponUse::new),
    REQUEST_SERVER_LOGIN(0x1A, null),
    REQUEST_DUEL_START(0x1B, RequestDuelStart::new),
    REQUEST_DUAL_ANSWER_START(0x1C, RequestDuelAnswerStart::new),
    REQUEST_EX_SET_TUTORIAL(0x1D, null),
    REQUEST_EX_RQ_ITEM_LINK(0x1E, RequestExRqItemLink::new),
    CANNOT_MOVE_ANYMORE_AIR_SHIP(0x1F, null),
    MOVE_TO_LOCATION_IN_AIR_SHIP(0x20, MoveToLocationInAirShip::new),
    REQUEST_KEY_MAPPING(0x21, RequestKeyMapping::new, GameContext.CHARACTER),
    REQUEST_SAVE_KEY_MAPPING(0x22, RequestSaveKeyMapping::new),
    REQUEST_EX_REMOVE_ITEM_ATTRIBUTE(0x23, RequestExRemoveItemAttribute::new),
    REQUEST_SAVE_INVENTORY_ORDER(0x24, RequestSaveInventoryOrder::new),
    REQUEST_EXIT_PARTY_MATCHING_WAITING_ROOM(0x25, RequestExitPartyMatchingWaitingRoom::new),
    REQUEST_CONFIRM_TARGET_ITEM(0x26, RequestConfirmTargetItem::new),
    REQUEST_CONFIRM_REFINER_ITEM(0x27, RequestConfirmRefinerItem::new),
    REQUEST_CONFIRM_GEMSTONE(0x28, RequestConfirmGemStone::new),
    REQUEST_OLYMPIAD_OBSERVER_END(0x29, RequestOlympiadObserverEnd::new),
    REQUEST_CURSED_WEAPON_LIST(0x2A, RequestCursedWeaponList::new),
    REQUEST_CURSED_WEAPON_LOCATION(0x2B, RequestCursedWeaponLocation::new),
    REQUEST_PLEDGE_REORGANIZE_MEMBER(0x2C, RequestPledgeReorganizeMember::new),
    REQUEST_EX_MPCC_SHOW_PARTY_MEMBERS_INFO(0x2D, RequestExMPCCShowPartyMembersInfo::new),
    REQUEST_OLYMPIAD_MATCH_LIST(0x2E, RequestOlympiadMatchList::new),
    REQUEST_ASK_JOIN_PARTY_ROOM(0x2F, RequestAskJoinPartyRoom::new),
    ANSWER_JOIN_PARTY_ROOM(0x30, AnswerJoinPartyRoom::new),
    REQUEST_LIST_PARTY_MATCHING_WAITING_ROOM(0x31, RequestListPartyMatchingWaitingRoom::new),
    REQUEST_EX_ENCHANT_SKILL_SAFE(0x32, RequestExEnchantSkillSafe::new),
    REQUEST_EX_ENCHANT_SKILL_UNTRAIN(0x33, RequestExEnchantSkillUntrain::new),
    REQUEST_EX_ENCHANT_SKILL_ROUTE_CHANGE(0x34, RequestExEnchantSkillRouteChange::new),
    REQUEST_EX_ENCHANT_ITEM_ATTRIBUTE(0x35, RequestExEnchantItemAttribute::new),
    MOVE_TO_LOCATION_AIR_SHIP(0x38, MoveToLocationAirShip::new),
    REQUEST_BID_ITEM_AUCTION(0x39, RequestBidItemAuction::new),
    REQUEST_INFO_ITEM_AUCTION(0x3A, RequestInfoItemAuction::new),
    REQUEST_EX_CHANGE_NAME(0x3B, RequestExChangeName::new),
    REQUEST_ALL_CASTLE_INFO(0x3C, RequestAllCastleInfo::new),
    REQUEST_ALL_FORTRESS_INFO(0x3D, RequestAllFortressInfo::new, GameContext.CHARACTER),
    REQUEST_ALL_AGIT_INGO(0x3e, RequestAllAgitInfo::new),
    REQUEST_FORTRESS_SIEGE_INFO(0x3F, RequestFortressSiegeInfo::new),
    REQUEST_GET_BOSS_RECORD(0x40, RequestGetBossRecord::new),
    REQUEST_REFINE(0x41, RequestRefine::new),
    REQUEST_CONFIRM_CANCEL_ITEM(0x42, RequestConfirmCancelItem::new),
    REQUEST_REFINE_CANCEL(0x43, RequestRefineCancel::new),
    REQUEST_EX_MAGIC_SKILL_USE_GROUND(0x44, RequestExMagicSkillUseGround::new),
    REQUEST_DUEL_SURRENDER(0x45, RequestDuelSurrender::new),
    REQUEST_EX_ENCHANT_SKILL_INFO_DETAIL(0x46, RequestExEnchantSkillInfoDetail::new),
    REQUEST_FORTRESS_MAP_INFO(0x48, RequestFortressMapInfo::new),
    REQUEST_PVP_MATCH_RECORD(0x49, null),
    SET_PRIVATE_STORE_WHOLE_MSG(0x4A, SetPrivateStoreWholeMsg::new),
    REQUEST_DISPEL(0x4B, RequestDispel::new),
    REQUEST_EX_TRY_TO_PUT_ENCHANT_TARGET_ITEM(0x4C, RequestExTryToPutEnchantTargetItem::new),
    REQUEST_EX_TRY_TO_PUT_ENCHANT_SUPPORT_ITEM(0x4D, RequestExTryToPutEnchantSupportItem::new),
    REQUEST_EX_CANCEL_ENCHANT_ITEM(0x4E, RequestExCancelEnchantItem::new),
    REQUEST_CHANGE_NICKNAME_COLOR(0x4F, RequestChangeNicknameColor::new),
    REQUEST_RESET_NICKNAME(0x50, RequestResetNickname::new),
    EX_BOOKMARK_PACKET(0x51, null),
    REQUEST_WITHDRAW_PREMIUM_ITEM(0x52, RequestWithDrawPremiumItem::new),
    REQUEST_EX_JUMP(0x53, null),
    REQUEST_EX_START_SHOW_CRATAE_CUBE_RANK(0x54, null),
    REQUEST_EX_STOP_SHOW_CRATAE_CUBE_RANK(0x55, null),
    NOTIFY_START_MINI_GAME(0x56, null),
    REQUEST_EX_JOIN_DOMINION_WAR(0x57, RequestJoinDominionWar::new),
    REQUEST_EX_DOMINION_INFO(0x58, RequestDominionInfo::new),
    REQUEST_EX_CLEFT_ENTER(0x59, null),
    REQUEST_EX_CUBE_GAME_CHANGE_TEAM(0x5A, RequestExCubeGameChangeTeam::new),
    END_SCENE_PLAYER(0x5B, EndScenePlayer::new),
    REQUEST_EX_CUBE_GAME_READY_ANSWER(0x5C, RequestExCubeGameReadyAnswer::new),
    REQUEST_EX_LIST_MPCC_WAITING(0x5D, null),
    REQUEST_EX_MANAGE_MPCC_ROOM(0x5E, null),
    REQUEST_EX_JOIN_MPCC_ROOM(0x5F, null),
    REQUEST_EX_OUST_FROM_MPCC_ROOM(0x60, null),
    REQUEST_EX_DISMISS_MPCC_ROOM(0x61, null),
    REQUEST_EX_WITHDRAW_MPCC_ROOM(0x62, null),
    REQUEST_SEED_PHASE(0x63, RequestSeedPhase::new),
    REQUEST_EX_MPCC_PARTYMASTER_LIST(0x64, null),
    REQUEST_POST_ITEM_LIST(0x65, RequestPostItemList::new),
    REQUEST_SEND_POST(0x66, RequestSendPost::new),
    REQUEST_RECEIVED_POST_LIST(0x67, RequestReceivedPostList::new),
    REQUEST_DELETE_RECEIVED_POST(0x68, RequestDeleteReceivedPost::new),
    REQUEST_RECEIVED_POST(0x69, RequestReceivedPost::new),
    REQUEST_POST_ATTACHMENT(0x6A, RequestPostAttachment::new),
    REQUEST_REJECT_POST_ATTACHMENT(0x6B, RequestRejectPostAttachment::new),
    REQUEST_SENT_POST_LIST(0x6C, RequestSentPostList::new),
    REQUEST_DELETE_SENT_POST(0x6D, RequestDeleteSentPost::new),
    REQUEST_SENT_POST(0x6E, RequestSentPost::new),
    REQUEST_CANCEL_SENT_POST(0x6F, RequestCancelPostAttachment::new),
    REQUEST_SHOW_NEW_USER_PETITION(0x70, null),
    REQUEST_SHOW_STEP_TWO(0x71, null),
    REQUEST_SHOW_STEP_THREE(0x72, null),
    EX_CONNECT_TO_RAID_SERVER(0x73, null),
    EX_RETURN_FROM_RAID_SERVER(0x74, null),
    REQUEST_REFUND_ITEM(0x75, RequestRefundItem::new),
    REQUEST_BUI_SELL_UI_CLOSE(0x76, RequestBuySellUIClose::new),
    REQUEST_EX_EVENT_MATCH_OBSERVER_END(0x77, null),
    REQUEST_PARTY_LOOT_MODIFICATION(0x78, RequestPartyLootModification::new),
    ANSWER_PARTY_LOOT_MODIFICATION(0x79, AnswerPartyLootModification::new),
    ANSWER_COUPLE_ACTION(0x7A, AnswerCoupleAction::new),
    BR_EVENT_RANKER_LIST(0x7B, BrEventRankerList::new),
    REQUEST_ASK_MEMBERSHIP(0x7C, null),
    REQUEST_ADD_EXPAND_QUEST_ALARM(0x7D, null),
    REQUEST_NEW_VOTE_SYSTEM(0x7E, RequestVoteNew::new),
    REQUEST_AGIT_ACTION(0x80, null), // TODO: Implement / HANDLE SWITCH
    REQUEST_EX_ADD_POST_FRIEND_FOR_POST_BOX(0x84, RequestExAddContactToContactList::new),
    REQUEST_EX_DELETE_POST_FRIEND_FOR_POST_BOX(0x85, RequestExDeleteContactFromContactList::new),
    REQUEST_EX_SHOW_POST_FRIEND_FOR_POST_BOX(0x86, RequestExShowContactList::new),
    REQUEST_EX_FRIEND_LIST_FOR_POST_BOX(0x87, RequestExFriendListExtended::new),
    REQUEST_EX_OLYMPIAD_MATCH_LIST_REFRESH(0x88, RequestExOlympiadMatchListRefresh::new),
    REQUEST_BR_GAME_POINT(0x89, RequestBrGamePoint::new),
    REQUEST_BR_PRODUCT_LIST(0x8A, RequestBrProductList::new),
    REQUEST_BR_PRODUCT_INFO(0x8B, RequestBrProductInfo::new),
    REQUEST_BR_BUI_PRODUCT(0x8C, RequestBrBuyProduct::new),
    REQUEST_BR_RECENT_PRODUCT_LIST(0x8D, RequestBrRecentProductList::new),
    REQUEST_BR_MINI_GAME_LOAD_SCORES(0x8E, null),
    REQUEST_BR_MINI_GAME_INSERT_SCORE(0x8F, null),
    REQUEST_BR_LECTURE_MARK(0x90, null),
    REQUEST_GOODS_INVENTORY_INFO(0x91, null),
    REQUEST_USE_GOODS_INVENTORY_ITEM(0x92, null),
    REQUEST_HARDWARE_INFO(0x96, RequestHardWareInfo::new, GameContext.ACCOUNT),
    EX_MAX(0x97, null);

    @Getter
    private final int packetId;

    private final Supplier<ClientPacket> packetSupplier;
    private final boolean implemented;

    @Getter
    private final GameContext context;

    ExClientPackets(int packetId, Supplier<ClientPacket> packetSupplier) {
        this(packetId, packetSupplier, GameContext.WORLD);
    }

    ExClientPackets(int packetId, Supplier<ClientPacket> packetSupplier, GameContext context) {
        // Packet id is an unsigned short.
        if (packetId > 0xFFFF) {
            throw new IllegalArgumentException("Packet id must not be bigger than 0xFFFF");
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
        if (DevelopmentConfig.DEBUG_EX_CLIENT_PACKETS) {
            final String name = packet.getClass().getSimpleName();
            if (!DevelopmentConfig.EXCLUDED_DEBUG_PACKETS.contains(name)) {
                log.info("[C EX] {}", name);
            }
        }
        return packet;
    }
}
