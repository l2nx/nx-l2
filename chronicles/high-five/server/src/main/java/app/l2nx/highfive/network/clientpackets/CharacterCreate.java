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
package app.l2nx.highfive.network.clientpackets;

import java.util.Collection;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;

import app.l2nx.highfive.config.PlayerConfig;
import app.l2nx.highfive.config.ServerConfig;
import app.l2nx.highfive.config.custom.AllowedPlayerRacesConfig;
import app.l2nx.highfive.config.custom.FactionSystemConfig;
import app.l2nx.highfive.config.custom.StartingLocationConfig;
import app.l2nx.highfive.config.custom.StartingTitleConfig;
import app.l2nx.highfive.data.sql.CharInfoTable;
import app.l2nx.highfive.data.xml.FakePlayerData;
import app.l2nx.highfive.data.xml.InitialEquipmentData;
import app.l2nx.highfive.data.xml.InitialShortcutData;
import app.l2nx.highfive.data.xml.PlayerTemplateData;
import app.l2nx.highfive.data.xml.SkillData;
import app.l2nx.highfive.data.xml.SkillTreeData;
import app.l2nx.highfive.entity.Location;
import app.l2nx.highfive.entity.World;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.actor.appearance.PlayerAppearance;
import app.l2nx.highfive.entity.actor.enums.player.PlayerClass;
import app.l2nx.highfive.entity.actor.stat.PlayerStat;
import app.l2nx.highfive.entity.actor.templates.PlayerTemplate;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.holders.InitialEquipment;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.mechanics.events.Containers;
import app.l2nx.highfive.mechanics.events.EventDispatcher;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.OnPlayerCreate;
import app.l2nx.highfive.mechanics.skill.holders.SkillLearn;
import app.l2nx.highfive.network.Disconnection;
import app.l2nx.highfive.network.GameClient;
import app.l2nx.highfive.network.PacketLogger;
import app.l2nx.highfive.network.serverpackets.CharCreateFail;
import app.l2nx.highfive.network.serverpackets.CharCreateOk;
import app.l2nx.highfive.network.serverpackets.CharSelectionInfo;

/**
 * @author Mobius
 */
public class CharacterCreate extends ClientPacket
{
	protected static final Logger LOGGER_ACCOUNTING = Logger.getLogger("accounting");
	
	// cSdddddddddddd
	private String _name;
	private boolean _isFemale;
	private int _classId;
	private byte _hairStyle;
	private byte _hairColor;
	private byte _face;
	
	@Override
	protected void readImpl()
	{
		_name = readString();
		readInt(); // race
		_isFemale = readInt() != 0;
		_classId = readInt();
		readInt(); // _int
		readInt(); // _str
		readInt(); // _con
		readInt(); // _men
		readInt(); // _dex
		readInt(); // _wit
		_hairStyle = (byte) readInt();
		_hairColor = (byte) readInt();
		_face = (byte) readInt();
	}
	
	@Override
	protected void runImpl()
	{
		final GameClient client = getClient();
		
		// Last Verified: May 30, 2009 - Gracia Final - Players are able to create characters with names consisting of as little as 1,2,3 letter/number combinations.
		if (_name.isEmpty() || (_name.length() > 16))
		{
			client.sendPacket(new CharCreateFail(CharCreateFail.REASON_16_ENG_CHARS));
			return;
		}
		
		if (PlayerConfig.FORBIDDEN_NAMES.length > 0)
		{
			for (String st : PlayerConfig.FORBIDDEN_NAMES)
			{
				if (_name.toLowerCase().contains(st.toLowerCase()))
				{
					client.sendPacket(new CharCreateFail(CharCreateFail.REASON_INCORRECT_NAME));
					return;
				}
			}
		}
		
		if (FakePlayerData.getInstance().getProperName(_name) != null)
		{
			client.sendPacket(new CharCreateFail(CharCreateFail.REASON_INCORRECT_NAME));
			return;
		}
		
		// Last Verified: May 30, 2009 - Gracia Final.
		if (!StringUtils.isAlphanumeric(_name) || !isValidName(_name))
		{
			client.sendPacket(new CharCreateFail(CharCreateFail.REASON_INCORRECT_NAME));
			return;
		}
		
		if ((_face > 2) || (_face < 0))
		{
			PacketLogger.warning("Character Creation Failure: Character face " + _face + " is invalid. Possible client hack. " + client);
			client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
			return;
		}
		
		if ((_hairStyle < 0) || (!_isFemale && (_hairStyle > 4)) || (_isFemale && (_hairStyle > 6)))
		{
			PacketLogger.warning("Character Creation Failure: Character hair style " + _hairStyle + " is invalid. Possible client hack. " + client);
			client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
			return;
		}
		
		if ((_hairColor > 3) || (_hairColor < 0))
		{
			PacketLogger.warning("Character Creation Failure: Character hair color " + _hairColor + " is invalid. Possible client hack. " + client);
			client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
			return;
		}
		
		Player newChar = null;
		PlayerTemplate template = null;
		
		/*
		 * DrHouse: Since checks for duplicate names are done using SQL, lock must be held until data is written to DB as well.
		 */
		synchronized (CharInfoTable.getInstance())
		{
			if ((CharInfoTable.getInstance().getAccountCharacterCount(client.getAccountName()) >= ServerConfig.MAX_CHARACTERS_NUMBER_PER_ACCOUNT) && (ServerConfig.MAX_CHARACTERS_NUMBER_PER_ACCOUNT != 0))
			{
				client.sendPacket(new CharCreateFail(CharCreateFail.REASON_TOO_MANY_CHARACTERS));
				return;
			}
			else if (CharInfoTable.getInstance().doesCharNameExist(_name))
			{
				client.sendPacket(new CharCreateFail(CharCreateFail.REASON_NAME_ALREADY_EXISTS));
				return;
			}
			
			template = PlayerTemplateData.getInstance().getTemplate(_classId);
			if ((template == null) || (PlayerClass.getPlayerClass(_classId).level() > 0))
			{
				client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
				return;
			}
			
			// Custom Feature: Disallow a race to be created.
			// Example: Humans can not be created if AllowHuman = False in Custom.properties.
			switch (template.getRace())
			{
				case HUMAN:
				{
					if (!AllowedPlayerRacesConfig.ALLOW_HUMAN)
					{
						client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
						return;
					}
					break;
				}
				case ELF:
				{
					if (!AllowedPlayerRacesConfig.ALLOW_ELF)
					{
						client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
						return;
					}
					break;
				}
				case DARK_ELF:
				{
					if (!AllowedPlayerRacesConfig.ALLOW_DARKELF)
					{
						client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
						return;
					}
					break;
				}
				case ORC:
				{
					if (!AllowedPlayerRacesConfig.ALLOW_ORC)
					{
						client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
						return;
					}
					break;
				}
				case DWARF:
				{
					if (!AllowedPlayerRacesConfig.ALLOW_DWARF)
					{
						client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
						return;
					}
					break;
				}
				case KAMAEL:
				{
					if (!AllowedPlayerRacesConfig.ALLOW_KAMAEL)
					{
						client.sendPacket(new CharCreateFail(CharCreateFail.REASON_CREATION_FAILED));
						return;
					}
					break;
				}
			}
			
			newChar = Player.create(template, client.getAccountName(), _name, new PlayerAppearance(_face, _hairColor, _hairStyle, _isFemale));
		}
		
		// HP and MP are at maximum and CP is zero by default.
		newChar.setCurrentHp(newChar.getMaxHp());
		newChar.setCurrentMp(newChar.getMaxMp());
		// newChar.setMaxLoad(template.getBaseLoad());
		
		initNewChar(client, newChar);
		client.sendPacket(CharCreateOk.STATIC_PACKET);
		
		LOGGER_ACCOUNTING.info("Created new character, " + newChar + ", " + client);
	}
	
	private boolean isValidName(String text)
	{
		return ServerConfig.CHARNAME_TEMPLATE_PATTERN.matcher(text).matches();
	}
	
	private void initNewChar(GameClient client, Player newChar)
	{
		World.addObject(newChar);
		
		if (PlayerConfig.STARTING_ADENA > 0)
		{
			newChar.addAdena(ItemProcessType.REWARD, PlayerConfig.STARTING_ADENA, null, false);
		}
		
		final PlayerTemplate template = newChar.getTemplate();
		if (StartingLocationConfig.CUSTOM_STARTING_LOC)
		{
			final Location createLoc = new Location(StartingLocationConfig.CUSTOM_STARTING_LOC_X, StartingLocationConfig.CUSTOM_STARTING_LOC_Y, StartingLocationConfig.CUSTOM_STARTING_LOC_Z);
			newChar.setXYZInvisible(createLoc.getX(), createLoc.getY(), createLoc.getZ());
		}
		else if (FactionSystemConfig.FACTION_SYSTEM_ENABLED)
		{
			newChar.setXYZInvisible(FactionSystemConfig.FACTION_STARTING_LOCATION.getX(), FactionSystemConfig.FACTION_STARTING_LOCATION.getY(), FactionSystemConfig.FACTION_STARTING_LOCATION.getZ());
		}
		else
		{
			final Location createLoc = template.getCreationPoint();
			newChar.setXYZInvisible(createLoc.getX(), createLoc.getY(), createLoc.getZ());
		}
		
		newChar.setTitle(StartingTitleConfig.ENABLE_CUSTOM_STARTING_TITLE ? StartingTitleConfig.CUSTOM_STARTING_TITLE : "");
		
		if (PlayerConfig.ENABLE_VITALITY)
		{
			newChar.setVitalityPoints(Math.min(PlayerConfig.STARTING_VITALITY_POINTS, PlayerStat.MAX_VITALITY_POINTS), true);
		}
		
		if (PlayerConfig.STARTING_LEVEL > 1)
		{
			newChar.getStat().addLevel((byte) (PlayerConfig.STARTING_LEVEL - 1));
		}
		
		if (PlayerConfig.STARTING_SP > 0)
		{
			newChar.getStat().addSp(PlayerConfig.STARTING_SP);
		}
		
		final Collection<InitialEquipment> classEquipment = InitialEquipmentData.getInstance().getClassEquipment(newChar.getPlayerClass());
		if (classEquipment != null)
		{
			for (InitialEquipment equipment : classEquipment)
			{
				final Item item = newChar.getInventory().addItem(ItemProcessType.REWARD, equipment.getId(), equipment.getCount(), newChar, null);
				if (item == null)
				{
					PacketLogger.warning("Could not create item during player creation: itemId " + equipment.getId() + ", amount " + equipment.getCount() + ".");
					continue;
				}
				
				if (item.isEquipable() && equipment.isEquipped())
				{
					newChar.getInventory().equipItem(item);
				}
			}
		}
		
		for (SkillLearn skill : SkillTreeData.getInstance().getAvailableSkills(newChar, newChar.getPlayerClass(), false, true))
		{
			newChar.addSkill(SkillData.getInstance().getSkill(skill.getSkillId(), skill.getSkillLevel()), true);
		}
		
		// Register all shortcuts for actions, skills and items for this new character.
		InitialShortcutData.getInstance().registerAllShortcuts(newChar);
		
		if (EventDispatcher.getInstance().hasListener(EventType.ON_PLAYER_CREATE, Containers.Players()))
		{
			EventDispatcher.getInstance().notifyEvent(new OnPlayerCreate(newChar, newChar.getObjectId(), newChar.getName(), client), Containers.Players());
		}
		
		newChar.setOnlineStatus(true, false);
		Disconnection.of(client, newChar).storeAndDelete();
		
		final CharSelectionInfo cl = new CharSelectionInfo(client.getAccountName(), client.getSessionId().playOkID1);
		client.setCharSelection(cl.getCharInfo());
	}
}
