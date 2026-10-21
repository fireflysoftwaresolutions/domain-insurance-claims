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

package io.fireflysoftware.domain.insurance.claims.core.workflows;

import io.fireflysoftware.core.insurance.claims.sdk.model.ClaimDTO;
import io.fireflysoftware.domain.insurance.claims.core.commands.ApplyClaimStatusCommand;
import io.fireflysoftware.domain.insurance.claims.core.commands.GetClaimQuery;
import io.fireflysoftware.domain.insurance.claims.core.commands.UpdateClaimCommand;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.COMPENSATE_RESTORE_RECORD;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.CTX_PREVIOUS_RECORD;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.CTX_RECORD;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.EVENT_RECORD_UPDATED;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.EVENT_RETRIEVED;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.SAGA_UPDATE_RECORD;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.STEP_RETRIEVE_FOR_UPDATE;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.STEP_UPDATE_RECORD;

/**
 * Updates a claim on {@code core-insurance-claims} and compensates by writing the previous record back.
 * Status stays on the status saga.
 */
@Saga(name = SAGA_UPDATE_RECORD)
@Service
public class UpdateClaimSaga {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    @Autowired
    public UpdateClaimSaga(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @SagaStep(id = STEP_RETRIEVE_FOR_UPDATE)
    @StepEvent(type = EVENT_RETRIEVED)
    public Mono<ClaimDTO> retrieveClaimForUpdate(GetClaimQuery query, ExecutionContext ctx) {
        return queryBus.query(query)
                .doOnNext(record -> ctx.putVariable(CTX_RECORD, record));
    }

    @SagaStep(id = STEP_UPDATE_RECORD, dependsOn = STEP_RETRIEVE_FOR_UPDATE, compensate = COMPENSATE_RESTORE_RECORD)
    @StepEvent(type = EVENT_RECORD_UPDATED)
    public Mono<ClaimDTO> updateClaim(UpdateClaimCommand command, ExecutionContext ctx) {
        ClaimDTO current = ctx.getVariableAs(CTX_RECORD, ClaimDTO.class);
        if (current == null || current.getClaimId() == null) {
            return Mono.error(new IllegalStateException("Claim was not found"));
        }
        ClaimDTO incoming = command.record();
        if (incoming == null) {
            return Mono.error(new IllegalArgumentException("Claim is required"));
        }
        if (current.getClaimStatus() != null) {
            incoming.setClaimStatus(current.getClaimStatus());
        }
        ctx.putVariable(CTX_PREVIOUS_RECORD, current);
        return commandBus.send(new ApplyClaimStatusCommand(current.getClaimId(), incoming))
                .doOnNext(updated -> ctx.putVariable(CTX_RECORD, updated));
    }

    public Mono<Void> restoreClaim(ClaimDTO updated, ExecutionContext ctx) {
        ClaimDTO previous = ctx.getVariableAs(CTX_PREVIOUS_RECORD, ClaimDTO.class);
        if (previous == null || previous.getClaimId() == null) {
            return Mono.empty();
        }
        return commandBus.send(new ApplyClaimStatusCommand(previous.getClaimId(), previous)).then();
    }
}
