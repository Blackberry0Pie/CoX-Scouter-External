/*
 * Copyright (c) 2026, Truth Forger <https://github.com/Blackberry0Pie>
 * Copyright (c) 2026, stutify <https://github.com/stutify>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bbp.chambers;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemVariationMapping;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

public class CoxScouterExternalBankOverlay extends WidgetItemOverlay
{
	private final Client client;
	private final CoxScouterExternalPlugin plugin;
	private final CoxScouterExternalConfig config;
	private int lastGameCycle = -1;
	private Set<Integer> cachedPlayerItems;

	@Inject
	private CoxScouterExternalBankOverlay(Client client, CoxScouterExternalPlugin plugin, CoxScouterExternalConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		showOnBank();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem) {
		if (!config.highlightBankItems()) {
			return;
		}

		if (!isRecommendedItem(itemId)) {
			return;
		}

		if (playerHasItem(itemId)) {
			return;
		}

		highlightItem(graphics, widgetItem);
	}

	private boolean isRecommendedItem(int itemId)
	{
		Set<Integer> recommendedIds = plugin.getRaidRecommendedItems();
		if (recommendedIds.contains(itemId))
		{
			return true;
		}

		int baseId = ItemVariationMapping.map(itemId);
		for (int recommendedId : recommendedIds)
		{
			if (ItemVariationMapping.map(recommendedId) == baseId)
			{
				return true;
			}
		}

		return false;
	}

	private boolean playerHasItem(int itemId)
	{
		Set<Integer> playerItems = getPlayerItems();
		if (playerItems.contains(itemId))
		{
			return true;
		}

		int baseItemId = ItemVariationMapping.map(itemId);
		for (int playerItemId : playerItems)
		{
			if (playerItemId != -1 && ItemVariationMapping.map(playerItemId) == baseItemId)
			{
				return true;
			}
		}
		return false;
	}

	private Set<Integer> getPlayerItems()
	{
		int currentCycle = client.getGameCycle();
		if (currentCycle == lastGameCycle && cachedPlayerItems != null)
		{
			// to avoid building a new set of player items for each banked item
			return cachedPlayerItems;
		}
		lastGameCycle = currentCycle;
		cachedPlayerItems = getPlayerItemIds();
		return cachedPlayerItems;
	}

	private Set<Integer> getPlayerItemIds() {
		Set<Integer> playerItemIds = new HashSet<>();
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				playerItemIds.add(item.getId());
			}
		}

		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment != null)
		{
			for (Item item : equipment.getItems())
			{
				playerItemIds.add(item.getId());
			}
		}

		return playerItemIds;
	}

	private void highlightItem(Graphics2D graphics, WidgetItem widgetItem) {
		Rectangle bounds = widgetItem.getCanvasBounds();
		if (bounds == null)
		{
			return;
		}

		Color color = config.highlightBankItemsColor();
		graphics.setColor(color);
		graphics.fill(bounds);
	}
}
