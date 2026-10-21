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

package io.fireflysoftware.domain.insurance.claims.core.services;

import io.fireflysoftware.core.insurance.claims.sdk.model.ClaimDTO;
import io.fireflysoftware.domain.insurance.claims.core.commands.CreateClaimCommand;
import io.fireflysoftware.domain.insurance.claims.core.commands.UpdateClaimStatusCommand;
import io.fireflysoftware.domain.insurance.claims.core.commands.UpdateClaimCommand;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Application service for claim orchestration.
 */
public interface ClaimsService {

    /**
     * Creates a claim through the register saga.
     *
     * @param command the create command
     * @return the record returned by the core
     */
    Mono<ClaimDTO> create(CreateClaimCommand command);

    /**
     * Moves a claim to a new status through the core.
     *
     * @param command the status change
     * @return the updated record
     */
    Mono<ClaimDTO> updateStatus(UpdateClaimStatusCommand command);

    /**
     * Reads one claim.
     *
     * @param claimId the core identifier
     * @return the record, or empty when the core has none
     */
    Mono<ClaimDTO> get(UUID claimId);

    /**
     * Updates fields on a claim and leaves its status unchanged.
     *
     * @param command the update
     * @return the record returned by the core
     */
    Mono<ClaimDTO> update(UpdateClaimCommand command);

    /**
     * Closes a claim when the claims core allows CLOSED.
     *
     * @param claimId the core identifier
     * @param statusReason optional reason stored by the status saga when the core keeps one
     * @return the updated record
     */
    Mono<ClaimDTO> close(UUID claimId, String statusReason);

    /**
     * Denies a claim when the claims core allows DENIED.
     *
     * @param claimId the core identifier
     * @param statusReason optional reason stored by the status saga when the core keeps one
     * @return the updated record
     */
    Mono<ClaimDTO> deny(UUID claimId, String statusReason);

    /**
     * Deletes a claim when the core lifecycle allows removal.
     *
     * @param claimId the core identifier
     * @return completion when the core has deleted the record
     */
    Mono<Void> remove(UUID claimId);
}
