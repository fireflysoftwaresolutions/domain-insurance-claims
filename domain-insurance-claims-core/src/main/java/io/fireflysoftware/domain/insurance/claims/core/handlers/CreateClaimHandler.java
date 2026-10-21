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

package io.fireflysoftware.domain.insurance.claims.core.handlers;

import io.fireflysoftware.core.insurance.claims.sdk.api.ClaimsApi;
import io.fireflysoftware.core.insurance.claims.sdk.model.ClaimDTO;
import io.fireflysoftware.domain.insurance.claims.core.commands.CreateClaimCommand;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Creates a claim by calling {@code core-insurance-claims}.
 */
@Slf4j
@CommandHandlerComponent
public class CreateClaimHandler extends CommandHandler<CreateClaimCommand, ClaimDTO> {

    private final ClaimsApi claimsApi;

    public CreateClaimHandler(ClaimsApi claimsApi) {
        this.claimsApi = claimsApi;
    }

    @Override
    protected Mono<ClaimDTO> doHandle(CreateClaimCommand command) {
        log.debug("Creating claim");
        return claimsApi.createClaim(command.record(), UUID.randomUUID().toString());
    }
}
