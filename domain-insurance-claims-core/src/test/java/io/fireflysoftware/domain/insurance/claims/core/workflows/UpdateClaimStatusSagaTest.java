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
import io.fireflysoftware.domain.insurance.claims.core.commands.UpdateClaimStatusCommand;
import io.fireflysoftware.domain.insurance.claims.core.constants.ClaimSagaConstants;
import org.fireflyframework.cqrs.command.CommandBus;
import org.fireflyframework.cqrs.query.QueryBus;
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
 * Verifies a status change is written through the core and can be restored.
 */
@ExtendWith(MockitoExtension.class)
class UpdateClaimStatusSagaTest {

    @Mock
    private CommandBus commandBus;

    @Mock
    private QueryBus queryBus;

    private UpdateClaimStatusSaga saga;

    @BeforeEach
    void setUp() {
        saga = new UpdateClaimStatusSaga(commandBus, queryBus);
    }

    @Test
    void updateStatus_sendsTheRequestedStatusToTheCore() {
        UUID id = UUID.randomUUID();
        ClaimDTO current = new ClaimDTO(id);
        current.setClaimStatus(ClaimDTO.ClaimStatusEnum.NOTIFIED);
        when(commandBus.send(any(ApplyClaimStatusCommand.class))).thenReturn(Mono.just(current));

        ExecutionContext ctx = ExecutionContext.forSaga(ClaimSagaConstants.SAGA_UPDATE_STATUS, "test");
        ctx.putVariable(ClaimSagaConstants.CTX_RECORD, current);

        StepVerifier.create(saga.updateClaimStatus(new UpdateClaimStatusCommand(id, "OPEN", "Caller request"), ctx))
                .expectNext(current)
                .verifyComplete();

        ArgumentCaptor<ApplyClaimStatusCommand> captor = ArgumentCaptor.forClass(ApplyClaimStatusCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().claimId()).isEqualTo(id);
        assertThat(captor.getValue().record().getClaimStatus()).isEqualTo(ClaimDTO.ClaimStatusEnum.OPEN);
        assertThat(ctx.getVariableAs(ClaimSagaConstants.CTX_PREVIOUS_STATUS, String.class)).isEqualTo("NOTIFIED");
    }

    @Test
    void restore_writesThePreviousStatusBack() {
        UUID id = UUID.randomUUID();
        ClaimDTO updated = new ClaimDTO(id);
        updated.setClaimStatus(ClaimDTO.ClaimStatusEnum.OPEN);
        when(commandBus.send(any(ApplyClaimStatusCommand.class))).thenReturn(Mono.just(updated));

        ExecutionContext ctx = ExecutionContext.forSaga(ClaimSagaConstants.SAGA_UPDATE_STATUS, "test");
        ctx.putVariable(ClaimSagaConstants.CTX_PREVIOUS_STATUS, "NOTIFIED");

        StepVerifier.create(saga.restoreClaimStatus(updated, ctx))
                .verifyComplete();

        ArgumentCaptor<ApplyClaimStatusCommand> captor = ArgumentCaptor.forClass(ApplyClaimStatusCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().record().getClaimStatus()).isEqualTo(ClaimDTO.ClaimStatusEnum.NOTIFIED);
    }
}
