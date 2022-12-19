/*
 * CarbonChat
 *
 * Copyright (c) 2021 Josua Parks (Vicarious)
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
package net.draycia.carbon.common.users.db.mysql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;

import net.draycia.carbon.api.CarbonChat;
import net.draycia.carbon.api.CarbonChatProvider;
import net.draycia.carbon.api.channels.ChatChannel;
import net.draycia.carbon.api.users.ComponentPlayerResult;
import net.draycia.carbon.common.config.DatabaseSettings;
import net.draycia.carbon.common.users.CarbonPlayerCommon;
import net.draycia.carbon.common.users.SaveOnChange;
import net.draycia.carbon.common.users.db.AbstractUserManager;
import net.draycia.carbon.common.users.db.ComponentArgumentFactory;
import net.draycia.carbon.common.users.db.DBType;
import net.draycia.carbon.common.users.db.KeyArgumentFactory;
import net.draycia.carbon.common.users.db.QueriesLocator;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.checkerframework.framework.qual.DefaultQualifier;
import org.flywaydb.core.Flyway;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.Update;
import org.jdbi.v3.sqlobject.SqlObjectPlugin;

import static net.kyori.adventure.text.Component.empty;
import static net.kyori.adventure.text.Component.text;

// TODO: Dispatch updates using messaging system when users are modified
@DefaultQualifier(NonNull.class)
public final class MySQLUserManager extends AbstractUserManager implements SaveOnChange {

    private final Map<UUID, CarbonPlayerCommon> userCache = Collections.synchronizedMap(new HashMap<>());

    private MySQLUserManager(final Jdbi jdbi) {
        super(jdbi, new QueriesLocator(DBType.MYSQL));
    }

    public static MySQLUserManager manager(
        final DatabaseSettings databaseSettings
    ) {
        try {
            //Class.forName("org.postgresql.Driver");
            Class.forName("org.mariadb.jdbc.Driver");
            Class.forName("com.mysql.cj.jdbc.Driver"); // Manually loading this might not be necessary
        } catch (final Exception exception) {
            exception.printStackTrace();
        }

        final HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setMaximumPoolSize(20);
        hikariConfig.setJdbcUrl(databaseSettings.url());
        hikariConfig.setUsername(databaseSettings.username());
        hikariConfig.setPassword(databaseSettings.password());

        final DataSource dataSource = new HikariDataSource(hikariConfig);

        final Flyway flyway = Flyway.configure(CarbonChat.class.getClassLoader())
            .baselineVersion("0")
            .baselineOnMigrate(true)
            .locations("queries/migrations/mysql")
            .dataSource(dataSource)
            .validateOnMigrate(true)
            .load();

        flyway.repair();
        flyway.migrate();

        final Jdbi jdbi = Jdbi.create(dataSource)
            .registerArrayType(UUID.class, "uuid")
            .registerArgument(new ComponentArgumentFactory())
            .registerArgument(new KeyArgumentFactory())
            .registerArgument(new MySQLUUIDArgumentFactory())
            .registerRowMapper(new MySQLPlayerRowMapper())
            .installPlugin(new SqlObjectPlugin());

        return new MySQLUserManager(jdbi);
    }

    @Override
    public CompletableFuture<ComponentPlayerResult<CarbonPlayerCommon>> carbonPlayer(final UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            final var playerResult = this.userCache.computeIfAbsent(uuid, key -> this.jdbi.withHandle(handle -> {
                final Optional<CarbonPlayerCommon> carbonPlayerCommon = handle.createQuery(this.locator.query("select-player"))
                    .bind("id", uuid)
                    .mapTo(CarbonPlayerCommon.class)
                    .findOne();

                if (carbonPlayerCommon.isEmpty()) {
                    // Player doesn't exist in the DB, create them!
                    final String name = Objects.requireNonNull(CarbonChatProvider.carbonChat().server().resolveName(uuid).join());
                    final CarbonPlayerCommon player = new CarbonPlayerCommon(name, uuid);

                    this.bindPlayerArguments(handle.createUpdate(this.locator.query("insert-player")), player).execute();

                    return player;
                }

                handle.createQuery(this.locator.query("select-ignores"))
                    .bind("id", uuid)
                    .mapTo(UUID.class)
                    .forEach(ignoredPlayer -> carbonPlayerCommon.get().ignoredPlayers().add(ignoredPlayer));
                handle.createQuery(this.locator.query("select-leftchannels"))
                    .bind("id", uuid)
                    .mapTo(Key.class)
                    .forEach(channel -> {
                        @Nullable ChatChannel chatChannel = CarbonChatProvider.carbonChat()
                            .channelRegistry()
                            .get(channel);
                        if (chatChannel == null) {
                            return;
                        }
                        carbonPlayerCommon.get().leftChannels().add(channel);
                    });
                return carbonPlayerCommon.get();
            }));

            return new ComponentPlayerResult<>(playerResult, empty());
        }).completeOnTimeout(new ComponentPlayerResult<>(null, text("Timed out loading data of UUID [" + uuid + " ]")), 30, TimeUnit.SECONDS);
    }

    @Override
    protected Update bindPlayerArguments(final Update update, final CarbonPlayerCommon player) {
        return update
            .bind("id", player.uuid())
            .bind("muted", player.muted())
            .bind("deafened", player.deafened())
            .bind("selectedchannel", player.selectedChannelKey())
            .bind("username", player.username())
            .bind("displayname", player.displayName())
            .bind("lastwhispertarget", player.lastWhisperTarget())
            .bind("whisperreplytarget", player.whisperReplyTarget())
            .bind("spying", player.spying());
    }

    @Override
    public CompletableFuture<ComponentPlayerResult<CarbonPlayerCommon>> saveAndInvalidatePlayer(final CarbonPlayerCommon player) {
        return this.savePlayer(player).thenApply(result -> {
            this.userCache.remove(player.uuid());

            return result;
        });
    }

    @Override
    public int saveDisplayName(final UUID id, final @Nullable Component displayName) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.saveDisplayName(id, displayName));
    }

    @Override
    public int saveMuted(final UUID id, final boolean muted) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.saveMuted(id, muted));
    }

    @Override
    public int saveDeafened(final UUID id, final boolean deafened) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.saveDeafened(id, deafened));
    }

    @Override
    public int saveSpying(final UUID id, final boolean spying) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.saveSpying(id, spying));
    }

    @Override
    public int saveSelectedChannel(final UUID id, final @Nullable Key selectedChannel) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.saveSelectedChannel(id, selectedChannel));
    }

    @Override
    public int saveLastWhisperTarget(final UUID id, final @Nullable UUID lastWhisperTarget) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.saveLastWhisperTarget(id, lastWhisperTarget));
    }

    @Override
    public int saveWhisperReplyTarget(final UUID id, final @Nullable UUID whisperReplyTarget) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.saveWhisperReplyTarget(id, whisperReplyTarget));
    }

    @Override
    public int addIgnore(final UUID id, final UUID ignoredPlayer) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.addIgnore(id, ignoredPlayer));
    }

    @Override
    public int removeIgnore(final UUID id, final UUID ignoredPlayer) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.removeIgnore(id, ignoredPlayer));
    }

    @Override
    public int addLeftChannel(final UUID id, final Key channel) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.addLeftChannel(id, channel));
    }

    @Override
    public int removeLeftChannel(final UUID id, final Key channel) {
        return this.jdbi.withExtension(MySQLSaveOnChange.class, changeSaver -> changeSaver.removeLeftChannel(id, channel));
    }

}
