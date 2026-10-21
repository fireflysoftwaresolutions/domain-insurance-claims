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

import io.fireflysoftware.core.insurance.claims.sdk.api.ClaimStatusHistoryApi;
import io.fireflysoftware.core.insurance.claims.sdk.model.ClaimStatusHistoryDTO;
import io.fireflysoftware.domain.insurance.claims.core.commands.RecordClaimStatusHistoryCommand;
import lombok.extern.slf4j.Slf4j;
import org.fireflyframework.cqrs.annotations.CommandHandlerComponent;
import org.fireflyframework.cqrs.command.CommandHandler;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Appends a status history row on {@code core-insurance-claims}.
 */
@Slf4j
@CommandHandlerComponent
public class RecordClaimStatusHistoryHandler extends CommandHandler<RecordClaimStatusHistoryCommand, ClaimStatusHistoryDTO> {

    private final ClaimStatusHistoryApi claimStatusHistoryApi;

    public RecordClaimStatusHistoryHandler(ClaimStatusHistoryApi claimStatusHistoryApi) {
        this.claimStatusHistoryApi = claimStatusHistoryApi;
    }

    @Override
    protected Mono<ClaimStatusHistoryDTO> doHandle(RecordClaimStatusHistoryCommand command) {
        log.debug("Recording claim status history");
        return claimStatusHistoryApi.createClaimStatusHistory(
                command.history().getClaimId(),
                command.history(),
                UUID.randomUUID().toString());
    }
}
