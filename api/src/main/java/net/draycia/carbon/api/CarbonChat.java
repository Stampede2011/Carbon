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
package net.draycia.carbon.api;

import net.draycia.carbon.api.channels.ChannelRegistry;
import net.draycia.carbon.api.event.CarbonEvent;
import net.draycia.carbon.api.event.CarbonEventHandler;
import net.draycia.carbon.api.users.UserManager;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

/**
 * The {@link CarbonChat} interface is the gateway to interacting with the majority of the CarbonChat API.
 *
 * <p>Instances may be obtained through {@link CarbonChatProvider#carbonChat()} once Carbon is loaded.</p>
 * <p>On most platforms, you should use the provided load order mechanism to ensure your addon loads after
 * Carbon.</p>
 * <p>On Fabric, use the {@code carbonchat} entrypoint (type: {@code Consumer<CarbonChat>}) to have a callback
 * when Carbon is loaded.</p>
 *
 * @since 1.0.0
 */
@DefaultQualifier(NonNull.class)
public interface CarbonChat {

    /**
     * The {@link CarbonEventHandler event handler}, used for listening to
     * and emitting {@link CarbonEvent events}.
     *
     * @return the event handler
     * @since 2.0.0
     */
    CarbonEventHandler eventHandler();

    /**
     * The server that carbon is running on.
     *
     * @return the server
     * @since 2.0.0
     */
    CarbonServer server();

    /**
     * The user manager.
     *
     * @return the user manager
     * @since 3.0.0
     */
    UserManager<?> userManager();

    /**
     * The registry that channels are registered to.
     *
     * @return the channel registry
     * @since 2.0.0
     */
    ChannelRegistry channelRegistry();

}
