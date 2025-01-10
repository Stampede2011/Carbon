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
package net.draycia.carbon.api.event;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.framework.qual.DefaultQualifier;

/**
 * A subscription to a specific event type.
 *
 * @param <T> event type
 * @since 3.0.0
 */
@DefaultQualifier(NonNull.class)
public interface CarbonEventSubscription<T extends CarbonEvent> {

    /**
     * Gets the event type.
     *
     * @return the event type
     * @since 3.0.0
     */
    Class<T> event();

    /**
     * Gets the {@link CarbonEventSubscriber subscriber}.
     *
     * @return the subscriber
     * @since 3.0.0
     */
    CarbonEventSubscriber<T> subscriber();

    /**
     * Disposes this subscription.
     *
     * <p>The subscriber held by this subscription will no longer receive events.</p>
     *
     * @since 3.0.0
     */
    void dispose();

}
