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
package net.draycia.carbon.paper.integration.dsrv;

import com.google.inject.Inject;
import com.google.inject.Injector;
import net.draycia.carbon.common.config.ConfigManager;
import net.draycia.carbon.common.integration.Integration;
import org.bukkit.Bukkit;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;

public final class DSRVIntegration implements Integration {

    private final Injector injector;
    private final DSRVIntegration.Config config;

    @Inject
    private DSRVIntegration(
        final Injector injector,
        final ConfigManager configManager
    ) {
        this.injector = injector;
        this.config = this.config(configManager, configMeta());
    }

    @Override
    public boolean eligible() {
        return this.config.enabled && Bukkit.getPluginManager().isPluginEnabled("DiscordSRV");
    }

    @Override
    public void register() {
        this.injector.getInstance(DSRVListener.class).register();
    }

    public static ConfigMeta configMeta() {
        return Integration.configMeta("discordsrv", DSRVIntegration.Config.class);
    }

    @ConfigSerializable
    public static final class Config {

        boolean enabled = true;

    }

}
