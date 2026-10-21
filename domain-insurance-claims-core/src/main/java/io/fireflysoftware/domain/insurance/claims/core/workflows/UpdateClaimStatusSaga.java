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
import io.fireflysoftware.domain.insurance.claims.core.commands.UpdateClaimStatusCommand;

import io.fireflysoftware.core.insurance.claims.sdk.model.ClaimStatusHistoryDTO;
import io.fireflysoftware.domain.insurance.claims.core.commands.RecordClaimStatusHistoryCommand;
import io.fireflysoftware.domain.insurance.claims.core.commands.RemoveClaimStatusHistoryCommand;
import java.time.LocalDateTime;

import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.COMPENSATE_REMOVE_STATUS_HISTORY;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.COMPENSATE_RESTORE;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.CTX_PREVIOUS_STATUS;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.CTX_RECORD;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.EVENT_RETRIEVED;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.EVENT_STATUS_HISTORY_RECORDED;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.EVENT_STATUS_UPDATED;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.SAGA_UPDATE_STATUS;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.STEP_RECORD_STATUS_HISTORY;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.STEP_RETRIEVE;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.STEP_UPDATE_STATUS;

/**
 * Changes a claim status by reading it and writing the new status through the core.
 */
@Saga(name = SAGA_UPDATE_STATUS)
@Service
public class UpdateClaimStatusSaga {

    private final CommandBus commandBus;
    private final QueryBus queryBus;

    @Autowired
    public UpdateClaimStatusSaga(CommandBus commandBus, QueryBus queryBus) {
        this.commandBus = commandBus;
        this.queryBus = queryBus;
    }

    @SagaStep(id = STEP_RETRIEVE)
    @StepEvent(type = EVENT_RETRIEVED)
    public Mono<ClaimDTO> retrieveClaim(GetClaimQuery query, ExecutionContext ctx) {
        return queryBus.query(query)
                .doOnNext(record -> ctx.putVariable(CTX_RECORD, record));
    }

    @SagaStep(id = STEP_UPDATE_STATUS, dependsOn = STEP_RETRIEVE, compensate = COMPENSATE_RESTORE)
    @StepEvent(type = EVENT_STATUS_UPDATED)
    public Mono<ClaimDTO> updateClaimStatus(UpdateClaimStatusCommand command, ExecutionContext ctx) {
        ClaimDTO current = ctx.getVariableAs(CTX_RECORD, ClaimDTO.class);
        if (current == null || current.getClaimId() == null) {
            return Mono.error(new IllegalStateException("Claim was not found"));
        }
        if (current.getClaimStatus() == null) {
            return Mono.error(new IllegalStateException("Claim has no current status"));
        }
        if (command.status() == null || command.status().isBlank()) {
            return Mono.error(new IllegalArgumentException("Status is required"));
        }
        ctx.putVariable(CTX_PREVIOUS_STATUS, current.getClaimStatus().getValue());
        current.setClaimStatus(ClaimDTO.ClaimStatusEnum.fromValue(command.status()));
        return commandBus.send(new ApplyClaimStatusCommand(current.getClaimId(), current))
                .doOnNext(updated -> ctx.putVariable(CTX_RECORD, updated));
    }

    public Mono<Void> restoreClaimStatus(ClaimDTO updated, ExecutionContext ctx) {
        if (updated == null || updated.getClaimId() == null) {
            return Mono.empty();
        }
        String previous = ctx.getVariableAs(CTX_PREVIOUS_STATUS, String.class);
        if (previous == null || previous.isBlank()) {
            return Mono.empty();
        }
        updated.setClaimStatus(ClaimDTO.ClaimStatusEnum.fromValue(previous));
        return commandBus.send(new ApplyClaimStatusCommand(updated.getClaimId(), updated)).then();
    }

    @SagaStep(id = STEP_RECORD_STATUS_HISTORY, dependsOn = STEP_UPDATE_STATUS, compensate = COMPENSATE_REMOVE_STATUS_HISTORY)
    @StepEvent(type = EVENT_STATUS_HISTORY_RECORDED)
    public Mono<ClaimStatusHistoryDTO> recordStatusHistory(UpdateClaimStatusCommand command, ExecutionContext ctx) {
        ClaimDTO current = ctx.getVariableAs(CTX_RECORD, ClaimDTO.class);
        if (current == null || current.getClaimId() == null || current.getClaimStatus() == null) {
            return Mono.error(new IllegalStateException("Claim status was not updated"));
        }
        ClaimStatusHistoryDTO history = new ClaimStatusHistoryDTO();
        history.setClaimId(current.getClaimId());
        history.setClaimStatus(ClaimStatusHistoryDTO.ClaimStatusEnum.fromValue(current.getClaimStatus().getValue()));
        String reason = command.statusReason();
        history.setStatusReason(reason == null || reason.isBlank()
                ? "Status updated by the domain service"
                : reason);
        history.setChangedAt(LocalDateTime.now());
        return commandBus.send(new RecordClaimStatusHistoryCommand(history));
    }

    public Mono<Void> removeStatusHistory(ClaimStatusHistoryDTO saved) {
        if (saved == null || saved.getClaimStatusHistoryId() == null || saved.getClaimId() == null) {
            return Mono.empty();
        }
        return commandBus.send(new RemoveClaimStatusHistoryCommand(saved.getClaimId(), saved.getClaimStatusHistoryId()));
    }

}
