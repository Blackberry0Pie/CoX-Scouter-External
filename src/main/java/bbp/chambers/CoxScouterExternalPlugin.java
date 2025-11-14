/*
 * Copyright (c) 2018, Kamiel
 * Copyright (c) 2020, Truth Forger <https://github.com/Blackberry0Pie>
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

import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Provides;
import javax.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.SpriteID;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatColorType;
import net.runelite.client.chat.ChatMessageBuilder;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.config.RuneLiteConfig;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.OverlayMenuClicked;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.raids.Raid;
import net.runelite.client.plugins.raids.RaidRoom;
import net.runelite.client.plugins.raids.RoomType;
import net.runelite.client.plugins.raids.events.RaidScouted;
import net.runelite.client.plugins.raids.events.RaidReset;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.raids.solver.Room;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageCapture;
import net.runelite.client.util.ImageUploadStyle;
import net.runelite.client.util.Text;
import net.runelite.client.party.PartyMember;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.messages.PartyChatMessage;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@PluginDescriptor(
	name = "CoX Scouter External",
	tags = {"combat", "overlay", "pve", "pvm", "bosses", "chambers", "xeric", "raids"}
)
public class CoxScouterExternalPlugin extends Plugin
{
	@Getter
	private Raid raid;

	@Inject
	private Client client;

	@Inject
	private RuneLiteConfig runeLiteConfig;

	@Inject
	private ImageCapture imageCapture;

	@Inject
	private CoxScouterExternalConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private CoxScouterExternalOverlay overlay;

	@Inject
	private CoxScouterExternalTutorialOverlay tutorialOverlay;

	@Inject
	private ItemManager itemManager;

	@Inject
	private PartyService party;

	@Inject
	private ChatMessageManager chatMessageManager;

	@Inject
	private KeyManager keyManager;

	@Inject
	private ClientThread clientThread;

	@Getter
	private final Set<String> roomWhitelist = new HashSet<>();

	@Getter
	private final Set<String> roomBlacklist = new HashSet<>();

	@Getter
	private final Set<String> rotationWhitelist = new HashSet<>();

	@Getter
	private final Set<String> layoutWhitelist = new HashSet<>();

	@Getter
	private final Set<String> roomHighlightedList = new HashSet<>();

	@Getter
	private final Map<String, List<Integer>> recommendedItemsList = new HashMap<>();

	@Getter
	private int raidPartyID;

	@Getter
	private boolean shouldShowOverlays;

	// if the player is inside of a raid or not
	@Getter
	private boolean inRaidChambers;
	private static int raidState;
	private static final Pattern ROTATION_REGEX = Pattern.compile("\\[(.*?)]");
	private static final int OLM_PLANE = 0;

	@Override
	protected void startUp() throws Exception
	{
		overlayManager.add(overlay);
		overlayManager.add(tutorialOverlay);
		updateLists();
		this.clientThread.invokeLater(this::checkRaidPresence);
		keyManager.registerKeyListener(screenshotHotkeyListener);
	}

	@Override
	protected void shutDown() throws Exception
	{
		overlayManager.remove(overlay);
		overlayManager.remove(tutorialOverlay);
		inRaidChambers = false;
		keyManager.unregisterKeyListener(screenshotHotkeyListener);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("raids") && !event.getGroup().equals("coxscouterexternal"))
		{
			return;
		}

		updateLists();
	}

	@Subscribe
	public void onRaidScouted(RaidScouted raidScouted)
	{
		this.raid = raidScouted.getRaid();
	}

	@Subscribe
	public void onRaidReset(RaidReset raidReset)
	{
		this.raid = null;
	}

	@Subscribe
	public void onOverlayMenuClicked(final OverlayMenuClicked event)
	{
		if (!(event.getEntry().getMenuAction() == MenuAction.RUNELITE_OVERLAY
				&& event.getOverlay() == overlay))
		{
			return;
		}

		if (event.getEntry().getOption().equals(CoxScouterExternalOverlay.BROADCAST_ACTION))
		{
			sendRaidLayoutMessage();
		}
		else if (event.getEntry().getOption().equals(CoxScouterExternalOverlay.SCREENSHOT_ACTION))
		{
			clientThread.invoke(CoxScouterExternalPlugin.this::screenshotScoutOverlay);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		this.clientThread.invokeLater(this::checkRaidPresence);
	}

	@Subscribe
	public void onGameTick(GameTick gameTick)
	{
		shouldShowOverlays = shouldShowOverlays();
	}

	@Provides
	CoxScouterExternalConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(CoxScouterExternalConfig.class);
	}

	@VisibleForTesting
	private void updateLists()
	{
		updateList(roomWhitelist, configManager.getConfiguration("raids", "whitelistedRooms"));
		updateList(roomBlacklist, configManager.getConfiguration("raids", "blacklistedRooms"));
		updateList(layoutWhitelist, configManager.getConfiguration("raids", "whitelistedLayouts"));
		updateList(roomHighlightedList, config.highlightedRooms());
		updateMap(recommendedItemsList, config.recommendedItems());

		// Update rotation whitelist
		rotationWhitelist.clear();
		if (configManager.getConfiguration("raids", "whitelistedRotations") != null)
		{
			for (String line : configManager.getConfiguration("raids", "whitelistedRotations").split("\\n"))
			{
				rotationWhitelist.add(line.toLowerCase().replace(" ", ""));
			}
		}
	}

	private void updateList(Collection<String> list, String input)
	{
		if (input == null)
		{
			return;
		}

		list.clear();
		for (String s : Text.fromCSV(input.toLowerCase()))
		{
			if (s.equals("unknown"))
			{
				list.add("unknown (combat)");
				list.add("unknown (puzzle)");
			}
			else
			{
				list.add(s);
			}
		}
	}

	private void updateMap(Map<String, List<Integer>> map, String input)
	{
		map.clear();

		Matcher m = ROTATION_REGEX.matcher(input);
		while (m.find())
		{
			String everything = m.group(1).toLowerCase();
			int split = everything.indexOf(',');
			if (split < 0)
				continue;
			String key = everything.substring(0, split);
			if (key.length() < 1)
				continue;
			List<String> itemNames = Text.fromCSV(everything.substring(split));

			map.computeIfAbsent(key, k -> new ArrayList<>());

			for (String itemName : itemNames)
			{
				if (itemName.equals(""))
					continue;
				if (itemName.equals("ice barrage"))
					map.get(key).add(SpriteID.SPELL_ICE_BARRAGE);
				else if (itemName.matches("\\d*"))
					try {
						map.get(key).add(Integer.parseInt(itemName));
					} catch(NumberFormatException e) {
						log.warn("Caught NumberFormatException for explicit ItemID in scouter item overlay");
					}
				else if (itemName.startsWith("salve"))
					map.get(key).add(ItemID.SALVE_AMULETEI);
				else if (itemName.contains("blowpipe"))
					map.get(key).add(ItemID.TOXIC_BLOWPIPE);
				else if (itemName.contains("slayer helm"))
					map.get(key).add(ItemID.SLAYER_HELMET_I);
				else if (itemManager.search(itemName).size() > 0)
					map.get(key).add(itemManager.search(itemName).get(0).getId());
				else
					log.info("RaidsPlugin: Could not find an item ID for item: " + itemName);
			}
		}
	}

	boolean getRotationMatches()
	{
		RaidRoom[] combatRooms = getCombatRooms();
		String rotation = Arrays.stream(combatRooms)
				.map(RaidRoom::getName)
				.map(String::toLowerCase)
				.collect(Collectors.joining(","));

		return rotationWhitelist.contains(rotation);
	}

	private RaidRoom[] getCombatRooms()
	{
		List<RaidRoom> combatRooms = new ArrayList<>();

		for (Room room : raid.getLayout().getRooms())
		{
			if (room == null)
			{
				continue;
			}

			if (raid.getRooms()[room.getPosition()].getType() == RoomType.COMBAT)
			{
				combatRooms.add(raid.getRooms()[room.getPosition()]);
			}
		}

		return combatRooms.toArray(new RaidRoom[0]);
	}

	private void sendRaidLayoutMessage()
	{
		final String layout = getRaid().getLayout().toCodeString();
		final String rooms = toRoomString(getRaid());
		final String raidData = "[" + layout + "]: " + rooms;

		final String layoutMessage = new ChatMessageBuilder()
				.append(ChatColorType.HIGHLIGHT)
				.append("Layout: ")
				.append(ChatColorType.NORMAL)
				.append(raidData)
				.build();

		final PartyMember localMember = party.getLocalMember();

		if (party.getMembers().isEmpty() || localMember == null)
		{
			chatMessageManager.queue(QueuedMessage.builder()
					.type(ChatMessageType.FRIENDSCHATNOTIFICATION)
					.runeLiteFormattedMessage(layoutMessage)
					.build());
		}
		else
		{
			final PartyChatMessage message = new PartyChatMessage(layoutMessage);
			message.setMemberId(localMember.getMemberId());
			party.send(message);
		}
	}

	private String toRoomString(Raid raid)
	{
		final StringBuilder sb = new StringBuilder();

		for (RaidRoom room : getOrderedRooms(raid))
		{
			switch (room.getType())
			{
				case PUZZLE:
				case COMBAT:
					sb.append(room.getName()).append(", ");
					break;
			}
		}

		final String roomsString = sb.toString();
		return roomsString.substring(0, roomsString.length() - 2);
	}

	private List<RaidRoom> getOrderedRooms(Raid raid)
	{
		List<RaidRoom> orderedRooms = new ArrayList<>();
		for (Room r : raid.getLayout().getRooms())
		{
			final int position = r.getPosition();
			final RaidRoom room = raid.getRoom(position);

			if (room == null)
			{
				continue;
			}

			orderedRooms.add(room);
		}

		return orderedRooms;
	}

	private void screenshotScoutOverlay()
	{
		if (!shouldShowOverlays)
		{
			return;
		}

		Rectangle overlayDimensions = overlay.getBounds();
		BufferedImage overlayImage = new BufferedImage(overlayDimensions.width, overlayDimensions.height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphic = overlayImage.createGraphics();
		graphic.setFont(runeLiteConfig.interfaceFontType().getFont());
		graphic.setColor(Color.BLACK);
		graphic.fillRect(0, 0, overlayDimensions.width, overlayDimensions.height);
		overlay.render(graphic);

		imageCapture.saveScreenshot(overlayImage, "CoX_scout-", null, false, configManager.getConfiguration("raids", "copyToClipboard", Boolean.class));
		graphic.dispose();
	}

	private final HotkeyListener screenshotHotkeyListener = new HotkeyListener(() -> config.screenshotHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(CoxScouterExternalPlugin.this::screenshotScoutOverlay);
		}
	};

	boolean isAtOlm()
	{
		return client.getLocalPlayer().getWorldView().getPlane() == 0;
	}

	boolean shouldShowOverlays()
	{
		if (raid == null
				|| raid.getLayout() == null
				|| !config.scoutOverlay()
				|| isAtOlm())
		{
			return false;
		}

		if (isInRaidChambers())
		{
			// If the raid has started
			if (raidState > 0)
			{
				return configManager.getConfiguration("raids", "scoutOverlayInRaid", Boolean.class);
			}
			else
			{
				return true;
			}
		}

		boolean overlayAtBank = configManager.getConfiguration("raids", "scoutOverlayAtBank", Boolean.class);
		return getRaidPartyID() != -1 && overlayAtBank;
	}

	private void checkRaidPresence()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		int tempRaidState = client.getVarbitValue(Varbits.RAID_STATE);
		int tempPartyID = client.getVarpValue(VarPlayer.IN_RAID_PARTY);
		boolean tempInRaid = client.getVarbitValue(Varbits.IN_RAID) == 1;

		// if the player's party state has changed
		if (tempPartyID != raidPartyID)
		{
			raidPartyID = tempPartyID;
		}

		// if the player's raid state has changed
		if (tempInRaid != inRaidChambers)
		{
			inRaidChambers = tempInRaid;
		}

		// if the player's raid state has changed
		if (tempRaidState != raidState)
		{
			raidState = tempRaidState;
		}
	}
}
