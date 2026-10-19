/*
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
package app.l2nx.highfive.scripts.custom;

import app.l2nx.highfive.config.custom.BankingConfig;
import app.l2nx.highfive.entity.actor.Player;
import app.l2nx.highfive.entity.item.enums.ItemProcessType;
import app.l2nx.highfive.entity.item.instance.Item;
import app.l2nx.highfive.entity.itemcontainer.Inventory;
import app.l2nx.highfive.entity.itemcontainer.PlayerInventory;
import app.l2nx.highfive.mechanics.events.Containers;
import app.l2nx.highfive.mechanics.events.EventType;
import app.l2nx.highfive.mechanics.events.holders.actor.player.inventory.OnPlayerItemAdd;
import app.l2nx.highfive.mechanics.events.listeners.ConsumerEventListener;

/**
 * @author CostyKiller
 */
public class BankingAutoConvert
{
	public BankingAutoConvert()
	{
		if (BankingConfig.BANKING_SYSTEM_AUTO_CONVERT_ENABLED)
		{
			Containers.Players().addListener(new ConsumerEventListener(Containers.Players(), EventType.ON_PLAYER_ITEM_ADD, (OnPlayerItemAdd event) -> onPlayerItemAdd(event), this));
		}
	}
	
	private void onPlayerItemAdd(OnPlayerItemAdd event)
	{
		if (!BankingConfig.BANKING_SYSTEM_AUTO_CONVERT_ENABLED)
		{
			return;
		}
		
		final Item item = event.getItem();
		if ((item == null) || (item.getId() != Inventory.ADENA_ID))
		{
			return;
		}
		
		final Player player = event.getPlayer();
		if ((player == null) || (player.getAdena() <= BankingConfig.BANKING_SYSTEM_AUTO_CONVERT_ADENA_LIMIT))
		{
			return;
		}
		
		final PlayerInventory inventory = player.getInventory();
		inventory.reduceAdena(ItemProcessType.COMPENSATE, BankingConfig.BANKING_SYSTEM_ADENA_COUNT, player, null);
		inventory.addItem(ItemProcessType.COMPENSATE, 3470, BankingConfig.BANKING_SYSTEM_GOLDBAR_COUNT, player, null);
		inventory.updateDatabase();
		
		player.sendMessage("Automatically converted " + BankingConfig.BANKING_SYSTEM_ADENA_COUNT + " Adena to " + BankingConfig.BANKING_SYSTEM_GOLDBAR_COUNT + " Gold " + (BankingConfig.BANKING_SYSTEM_GOLDBAR_COUNT > 1 ? "Bars." : "Bar."));
	}
	
	public static void main(String[] args)
	{
		new BankingAutoConvert();
	}
}
