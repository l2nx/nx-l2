/*
 * SPDX-FileCopyrightText: 2026 L2NX contributors
 * SPDX-License-Identifier: Apache-2.0
 */
package app.l2nx.highfive.movement.view;

import app.l2nx.engine.core.session.outbound.ViewEvent;
import app.l2nx.highfive.entity.WorldObject;
import lombok.Builder;

@Builder(toBuilder = true)
public record PositionCorrected(int handle, int x, int y, int z, int heading) implements ViewEvent {
    public static PositionCorrected of(WorldObject object) {
        return builder()
                .handle(object.getObjectId())
                .x(object.getX())
                .y(object.getY())
                .z(object.getZ())
                .heading(object.getHeading())
                .build();
    }
}
