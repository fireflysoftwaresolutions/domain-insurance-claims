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
import io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants;

import io.fireflysoftware.core.insurance.claims.sdk.model.ClaimStatusHistoryDTO;
import io.fireflysoftware.domain.insurance.claims.core.commands.RecordClaimStatusHistoryCommand;

import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.orchestration.core.context.ExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the register saga stores the core result and can compensate by deleting it.
 */
@ExtendWith(MockitoExtension.class)
class RegisterClaimSagaTest {

    @Mock
    private CommandBus commandBus;

    private RegisterClaimSaga saga;

    @BeforeEach
    void setUp() {
        saga = new RegisterClaimSaga(commandBus);
    }

    @Test
    void register_storesTheRecordReturnedByTheCore() {
        UUID id = UUID.randomUUID();
        ClaimDTO created = new ClaimDTO(id);
        created.setClaimStatus(ClaimDTO.ClaimStatusEnum.NOTIFIED);
        when(commandBus.send(any(CreateClaimCommand.class))).thenReturn(Mono.just(created));

        ExecutionContext ctx = ExecutionContext.forSaga(ClaimSagaConstants.SAGA_REGISTER, "test");
        ClaimDTO request = new ClaimDTO();
        request.setClaimStatus(ClaimDTO.ClaimStatusEnum.NOTIFIED);

        StepVerifier.create(saga.registerClaim(new CreateClaimCommand(request), ctx))
                .expectNext(created)
                .verifyComplete();

        assertThat(ctx.getVariableAs(ClaimSagaConstants.CTX_RECORD, ClaimDTO.class).getClaimId()).isEqualTo(id);
    }

    @Test
    void remove_sendsDeleteForTheCreatedRecord() {
        UUID id = UUID.randomUUID();
        when(commandBus.send(any(RemoveClaimCommand.class))).thenReturn(Mono.empty());

        StepVerifier.create(saga.removeClaim(new ClaimDTO(id)))
                .verifyComplete();

        ArgumentCaptor<RemoveClaimCommand> captor = ArgumentCaptor.forClass(RemoveClaimCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().claimId()).isEqualTo(id);
    }

    @Test
    void recordInitialStatus_copiesIdAndStatusFromTheCreatedRecord() {
        UUID id = UUID.randomUUID();
        ClaimDTO created = new ClaimDTO(id);
        created.setClaimStatus(ClaimDTO.ClaimStatusEnum.NOTIFIED);
        ClaimStatusHistoryDTO saved = new ClaimStatusHistoryDTO(UUID.randomUUID());
        when(commandBus.send(any(RecordClaimStatusHistoryCommand.class))).thenReturn(Mono.just(saved));

        ExecutionContext ctx = ExecutionContext.forSaga(ClaimSagaConstants.SAGA_REGISTER, "test");
        ctx.putVariable(ClaimSagaConstants.CTX_RECORD, created);

        StepVerifier.create(saga.recordInitialStatus(new CreateClaimCommand(created), ctx))
                .expectNext(saved)
                .verifyComplete();

        ArgumentCaptor<RecordClaimStatusHistoryCommand> captor = ArgumentCaptor.forClass(RecordClaimStatusHistoryCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().history().getClaimId()).isEqualTo(id);
        assertThat(captor.getValue().history().getClaimStatus().getValue()).isEqualTo("NOTIFIED");
    }

}
