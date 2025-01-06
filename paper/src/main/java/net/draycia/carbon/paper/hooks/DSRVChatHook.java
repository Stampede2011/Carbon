/*
 * CarbonChat
 *
 * Copyright (c) 2024 Josua Parks (Vicarious)
 *                    Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.draycia.carbon.paper.hooks;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.inject.Inject;
import github.scarsz.discordsrv.Debug;
import github.scarsz.discordsrv.DiscordSRV;
import github.scarsz.discordsrv.api.Subscribe;
import github.scarsz.discordsrv.api.events.GameChatMessagePreProcessEvent;
import github.scarsz.discordsrv.hooks.chat.ChatHook;
import java.time.Duration;
import net.draycia.carbon.api.channels.ChatChannel;
import net.draycia.carbon.api.event.CarbonEventHandler;
import net.draycia.carbon.api.event.events.CarbonChatEvent;
import net.draycia.carbon.api.users.CarbonPlayer;
import net.draycia.carbon.common.channels.CarbonChannelRegistry;
import net.draycia.carbon.common.config.ConfigManager;
import net.draycia.carbon.common.integration.Integration;
import net.draycia.carbon.common.messages.TagPermissions;
import net.draycia.carbon.common.users.ConsoleCarbonPlayer;
import net.draycia.carbon.common.users.WrappedCarbonPlayer;
import net.draycia.carbon.common.util.ChannelUtils;
import net.draycia.carbon.paper.users.CarbonPlayerPaper;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

public final class DSRVChatHook implements ChatHook, Integration {

    private final CarbonChannelRegistry channelRegistry;
    private final JavaPlugin plugin;
    private final CarbonEventHandler eventHandler;
    private final Config config;

    @Inject
    private DSRVChatHook(
        final CarbonEventHandler eventHandler,
        final CarbonChannelRegistry channelRegistry,
        final JavaPlugin plugin,
        final ConfigManager configManager
    ) {
        this.channelRegistry = channelRegistry;
        this.eventHandler = eventHandler;
        this.plugin = plugin;
        this.config = this.config(configManager, configMeta());
    }

    @Override
    public boolean eligible() {
        return this.config.enabled && Bukkit.getPluginManager().isPluginEnabled("DiscordSRV");
    }

    @Override
    public void register() {
        DiscordSRV.getPlugin().getPluginHooks().add(this);

        final Cache<ImmutablePair<CarbonPlayer, ChatChannel>, Component> awaitingEvent = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMillis(25))
            .build();

        this.eventHandler.subscribe(CarbonChatEvent.class, 100, false, event -> {
            final ChatChannel chatChannel = event.chatChannel();
            final CarbonPlayer carbonPlayer = event.sender();

            if (carbonPlayer instanceof ConsoleCarbonPlayer) {
                return;
            }

            final ImmutablePair<CarbonPlayer, ChatChannel> pair = new ImmutablePair<>(carbonPlayer, chatChannel);
            Component messageComponent = awaitingEvent.getIfPresent(pair);
            awaitingEvent.invalidate(pair);

            if (messageComponent == null) {
                messageComponent = event.message();
            }

            final String messageContents = PlainTextComponentSerializer.plainText().serialize(messageComponent);
            final Component eventMessage;

            if (carbonPlayer instanceof WrappedCarbonPlayer wrapped) {
                eventMessage = wrapped.parseMessageTags(messageContents);
            } else {
                eventMessage = TagPermissions.parseTags(TagPermissions.MESSAGE, messageContents, carbonPlayer::hasPermission);
            }

            DiscordSRV.debug(Debug.MINECRAFT_TO_DISCORD, "Received a CarbonChatEvent (player: " + carbonPlayer.username() + ")");

            final @Nullable Player player = ((CarbonPlayerPaper) carbonPlayer).bukkitPlayer();

            if (player != null) {
                DiscordSRV.getPlugin().processChatMessage(player, toDsrv(eventMessage), chatChannel.commandName(), event.cancelled(), null);
            }
        });

        DiscordSRV.api.subscribe(new Object() {
            @Subscribe
            public void handle(final GameChatMessagePreProcessEvent event) {
                if (event.getTriggeringBukkitEvent() == null) {
                    return;
                }

                event.setCancelled(true);
            }
        });
    }

    @Override
    public void broadcastMessageToChannel(final String channel, final github.scarsz.discordsrv.dependencies.kyori.adventure.text.Component message) {
        final @Nullable ChatChannel chatChannel = this.channelRegistry.channelByValue(channel);

        if (chatChannel == null) {
            this.plugin.getLogger().warning("Error sending message from Discord to Minecraft, no matching channel found for [" + channel + "]");
        } else {
            ChannelUtils.broadcastMessageToChannel(fromDsrv(message), chatChannel);
        }
    }

    @Override
    public Plugin getPlugin() {
        return this.plugin;
    }

    private static github.scarsz.discordsrv.dependencies.kyori.adventure.text.Component toDsrv(final Component component) {
        return github.scarsz.discordsrv.dependencies.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().deserialize(
            GsonComponentSerializer.gson().serialize(component)
        );
    }

    private static Component fromDsrv(final github.scarsz.discordsrv.dependencies.kyori.adventure.text.Component component) {
        return GsonComponentSerializer.gson().deserialize(
            github.scarsz.discordsrv.dependencies.kyori.adventure.text.serializer.gson.GsonComponentSerializer.gson().serialize(component)
        );
    }

    public static ConfigMeta configMeta() {
        return Integration.configMeta("discordsrv", DSRVChatHook.Config.class);
    }

    @ConfigSerializable
    public static final class Config {

        boolean enabled = true;

    }

}
