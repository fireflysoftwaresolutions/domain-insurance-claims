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
import io.fireflysoftware.domain.insurance.claims.core.commands.GetClaimQuery;
import io.fireflysoftware.domain.insurance.claims.core.commands.UpdateClaimStatusCommand;
import io.fireflysoftware.domain.insurance.claims.core.commands.UpdateClaimCommand;
import io.fireflysoftware.domain.insurance.claims.core.commands.RemoveClaimCommand;
import io.fireflysoftware.domain.insurance.claims.core.lifecycle.ClaimRecordLifecycle;
import io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
import org.fireflyframework.orchestration.saga.engine.SagaEngine;
import org.fireflyframework.orchestration.saga.engine.SagaResult;
import org.fireflyframework.orchestration.saga.engine.StepInputs;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * Runs the claim sagas and reads through the query bus.
 */
@Service
public class ClaimsServiceImpl implements ClaimsService {

    private final SagaEngine sagaEngine;
    private final QueryBus queryBus;
    private final CommandBus commandBus;

    public ClaimsServiceImpl(SagaEngine sagaEngine, QueryBus queryBus, CommandBus commandBus) {
        this.sagaEngine = sagaEngine;
        this.queryBus = queryBus;
        this.commandBus = commandBus;
    }

    @Override
    public Mono<ClaimDTO> create(CreateClaimCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(ClaimSagaConstants.STEP_REGISTER, command)
                .forStepId(ClaimSagaConstants.STEP_RECORD_INITIAL_STATUS, command)
        .build();
        return sagaEngine.execute(ClaimSagaConstants.SAGA_REGISTER, inputs)
                .flatMap(result -> outcome(result, ClaimSagaConstants.STEP_REGISTER, ClaimDTO.class));
    }

    @Override
    public Mono<ClaimDTO> updateStatus(UpdateClaimStatusCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(ClaimSagaConstants.STEP_RETRIEVE, new GetClaimQuery(command.claimId()))
                .forStepId(ClaimSagaConstants.STEP_UPDATE_STATUS, command)
                .forStepId(ClaimSagaConstants.STEP_RECORD_STATUS_HISTORY, command)
        .build();
        return sagaEngine.execute(ClaimSagaConstants.SAGA_UPDATE_STATUS, inputs)
                .flatMap(result -> outcome(result, ClaimSagaConstants.STEP_UPDATE_STATUS, ClaimDTO.class));
    }

    @Override
    public Mono<ClaimDTO> get(UUID claimId) {
        return queryBus.query(new GetClaimQuery(claimId));
    }


    @Override
    public Mono<ClaimDTO> update(UpdateClaimCommand command) {
        StepInputs inputs = StepInputs.builder()
                .forStepId(ClaimSagaConstants.STEP_RETRIEVE_FOR_UPDATE, new GetClaimQuery(command.claimId()))
                .forStepId(ClaimSagaConstants.STEP_UPDATE_RECORD, command)
                .build();
        return sagaEngine.execute(ClaimSagaConstants.SAGA_UPDATE_RECORD, inputs)
                .flatMap(result -> outcome(result, ClaimSagaConstants.STEP_UPDATE_RECORD, ClaimDTO.class));
    }

    @Override
    public Mono<ClaimDTO> close(UUID claimId, String statusReason) {
        return transition(claimId, ClaimDTO.ClaimStatusEnum.CLOSED, statusReason);
    }

    @Override
    public Mono<ClaimDTO> deny(UUID claimId, String statusReason) {
        return transition(claimId, ClaimDTO.ClaimStatusEnum.DENIED, statusReason);
    }

    private Mono<ClaimDTO> transition(UUID claimId, ClaimDTO.ClaimStatusEnum target, String statusReason) {
        return get(claimId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Claim was not found")))
                .flatMap(current -> {
                    if (!ClaimRecordLifecycle.canTransition(current.getClaimStatus(), target)) {
                        String from = current.getClaimStatus() == null
                                ? "unset"
                                : current.getClaimStatus().getValue();
                        return Mono.<ClaimDTO>error(new IllegalStateException(
                                "Claim " + claimId + " cannot move from " + from + " to " + target.getValue()));
                    }
                    return updateStatus(new UpdateClaimStatusCommand(claimId, target.getValue(), statusReason));
                });
    }

    @Override
    public Mono<Void> remove(UUID claimId) {
        return get(claimId)
                .switchIfEmpty(Mono.error(new IllegalStateException("Claim was not found")))
                .flatMap(current -> {
                    if (!ClaimRecordLifecycle.canRemove(current.getClaimStatus())) {
                        return Mono.error(new IllegalStateException(
                                "Claim " + claimId + " is " + current.getClaimStatus().getValue()
                                        + " and cannot be deleted; close it so the claim history is kept"));
                    }
                    return commandBus.send(new RemoveClaimCommand(claimId));
                });
    }

    private static <T> Mono<T> outcome(SagaResult result, String stepId, Class<T> type) {
        if (result.isFailed()) {
            Throwable error = result.error().orElseGet(() ->
                    new IllegalStateException("Saga " + result.sagaName() + " failed"));
            return Mono.error(error);
        }
        return Mono.justOrEmpty(result.resultOf(stepId, type))
                .switchIfEmpty(Mono.error(new IllegalStateException("Saga step " + stepId + " produced no result")));
    }
}
