/*
 * Copyright 2026 Firefly Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.fireflysoftware.domain.insurance.claims.core.lifecycle;

import io.fireflysoftware.core.insurance.claims.sdk.model.ClaimDTO;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Moves copied from the claim lifecycle on {@code core-insurance-claims}.
 * A missing status is treated as NOTIFIED, which is the only status the core will delete.
 */
public final class ClaimRecordLifecycle {

    private static final Map<ClaimDTO.ClaimStatusEnum, Set<ClaimDTO.ClaimStatusEnum>> ALLOWED = Map.of(
            ClaimDTO.ClaimStatusEnum.NOTIFIED, EnumSet.of(ClaimDTO.ClaimStatusEnum.OPEN, ClaimDTO.ClaimStatusEnum.DENIED, ClaimDTO.ClaimStatusEnum.CLOSED),
            ClaimDTO.ClaimStatusEnum.OPEN, EnumSet.of(ClaimDTO.ClaimStatusEnum.UNDER_ASSESSMENT, ClaimDTO.ClaimStatusEnum.DENIED, ClaimDTO.ClaimStatusEnum.CLOSED),
            ClaimDTO.ClaimStatusEnum.UNDER_ASSESSMENT, EnumSet.of(ClaimDTO.ClaimStatusEnum.RESERVED, ClaimDTO.ClaimStatusEnum.OPEN, ClaimDTO.ClaimStatusEnum.DENIED, ClaimDTO.ClaimStatusEnum.CLOSED),
            ClaimDTO.ClaimStatusEnum.RESERVED, EnumSet.of(ClaimDTO.ClaimStatusEnum.SETTLED, ClaimDTO.ClaimStatusEnum.UNDER_ASSESSMENT, ClaimDTO.ClaimStatusEnum.DENIED, ClaimDTO.ClaimStatusEnum.CLOSED),
            ClaimDTO.ClaimStatusEnum.SETTLED, EnumSet.of(ClaimDTO.ClaimStatusEnum.CLOSED, ClaimDTO.ClaimStatusEnum.REOPENED),
            ClaimDTO.ClaimStatusEnum.CLOSED, EnumSet.of(ClaimDTO.ClaimStatusEnum.REOPENED),
            ClaimDTO.ClaimStatusEnum.REOPENED, EnumSet.of(ClaimDTO.ClaimStatusEnum.OPEN, ClaimDTO.ClaimStatusEnum.UNDER_ASSESSMENT, ClaimDTO.ClaimStatusEnum.CLOSED),
            ClaimDTO.ClaimStatusEnum.DENIED, EnumSet.of(ClaimDTO.ClaimStatusEnum.REOPENED)
    );

    private ClaimRecordLifecycle() {
    }

    public static boolean canRemove(ClaimDTO.ClaimStatusEnum status) {
        return status == null || status == ClaimDTO.ClaimStatusEnum.NOTIFIED;
    }

    public static boolean canTransition(ClaimDTO.ClaimStatusEnum from, ClaimDTO.ClaimStatusEnum to) {
        if (from == null || to == null) {
            return false;
        }
        if (from == to) {
            return true;
        }
        Set<ClaimDTO.ClaimStatusEnum> next = ALLOWED.get(from);
        return next != null && next.contains(to);
    }
}
