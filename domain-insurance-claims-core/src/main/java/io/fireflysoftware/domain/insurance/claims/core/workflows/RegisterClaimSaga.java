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
import io.fireflysoftware.domain.insurance.claims.core.commands.CreateClaimCommand;
import io.fireflysoftware.domain.insurance.claims.core.commands.RemoveClaimCommand;

import io.fireflysoftware.core.insurance.claims.sdk.model.ClaimStatusHistoryDTO;
import io.fireflysoftware.domain.insurance.claims.core.commands.RecordClaimStatusHistoryCommand;
import io.fireflysoftware.domain.insurance.claims.core.commands.RemoveClaimStatusHistoryCommand;
import java.time.LocalDateTime;

import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.fireflyframework.orchestration.saga.annotation.Saga;
import org.fireflyframework.orchestration.saga.annotation.SagaStep;
import org.fireflyframework.orchestration.saga.annotation.StepEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.COMPENSATE_REMOVE;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.COMPENSATE_REMOVE_INITIAL_STATUS;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.CTX_RECORD;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.EVENT_INITIAL_STATUS_RECORDED;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.EVENT_REGISTERED;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.SAGA_REGISTER;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.STEP_RECORD_INITIAL_STATUS;
import static io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants.STEP_REGISTER;

/**
 * Creates a claim on {@code core-insurance-claims} and compensates by deleting it.
 */
@Saga(name = SAGA_REGISTER)
@Service
public class RegisterClaimSaga {

    private final CommandBus commandBus;

    @Autowired
    public RegisterClaimSaga(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @SagaStep(id = STEP_REGISTER, compensate = COMPENSATE_REMOVE)
    @StepEvent(type = EVENT_REGISTERED)
    public Mono<ClaimDTO> registerClaim(CreateClaimCommand command, ExecutionContext ctx) {
        return commandBus.send(command)
                .doOnNext(created -> ctx.putVariable(CTX_RECORD, created));
    }

    public Mono<Void> removeClaim(ClaimDTO created) {
        if (created == null || created.getClaimId() == null) {
            return Mono.empty();
        }
        return commandBus.send(new RemoveClaimCommand(created.getClaimId()));
    }

    @SagaStep(id = STEP_RECORD_INITIAL_STATUS, dependsOn = STEP_REGISTER, compensate = COMPENSATE_REMOVE_INITIAL_STATUS)
    @StepEvent(type = EVENT_INITIAL_STATUS_RECORDED)
    public Mono<ClaimStatusHistoryDTO> recordInitialStatus(CreateClaimCommand command, ExecutionContext ctx) {
        ClaimDTO created = ctx.getVariableAs(CTX_RECORD, ClaimDTO.class);
        if (created == null || created.getClaimId() == null) {
            return Mono.error(new IllegalStateException("Claim was not created"));
        }
        if (created.getClaimStatus() == null) {
            return Mono.error(new IllegalStateException("Claim status is required before history can be recorded"));
        }
        ClaimStatusHistoryDTO history = new ClaimStatusHistoryDTO();
        history.setClaimId(created.getClaimId());
        history.setClaimStatus(ClaimStatusHistoryDTO.ClaimStatusEnum.fromValue(created.getClaimStatus().getValue()));
        history.setStatusReason("Initial status recorded by the domain service");
        history.setChangedAt(LocalDateTime.now());
        return commandBus.send(new RecordClaimStatusHistoryCommand(history));
    }

    public Mono<Void> removeInitialStatus(ClaimStatusHistoryDTO saved) {
        if (saved == null || saved.getClaimStatusHistoryId() == null || saved.getClaimId() == null) {
            return Mono.empty();
        }
        return commandBus.send(new RemoveClaimStatusHistoryCommand(saved.getClaimId(), saved.getClaimStatusHistoryId()));
    }

}
