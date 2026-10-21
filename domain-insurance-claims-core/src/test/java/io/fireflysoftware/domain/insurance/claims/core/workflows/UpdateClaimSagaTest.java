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
import io.fireflysoftware.domain.insurance.claims.core.commands.UpdateClaimCommand;
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
 * Verifies a field update keeps the current status and can be restored.
 */
@ExtendWith(MockitoExtension.class)
class UpdateClaimSagaTest {

    @Mock
    private CommandBus commandBus;

    @Mock
    private QueryBus queryBus;

    private UpdateClaimSaga saga;

    @BeforeEach
    void setUp() {
        saga = new UpdateClaimSaga(commandBus, queryBus);
    }

    @Test
    void update_keepsTheCurrentStatus() {
        UUID id = UUID.randomUUID();
        ClaimDTO current = new ClaimDTO(id);
        current.setClaimStatus(ClaimDTO.ClaimStatusEnum.NOTIFIED);
        ClaimDTO incoming = new ClaimDTO();
        incoming.setClaimStatus(ClaimDTO.ClaimStatusEnum.OPEN);
        when(commandBus.send(any(ApplyClaimStatusCommand.class))).thenAnswer(invocation ->
                Mono.just(invocation.getArgument(0, ApplyClaimStatusCommand.class).record()));

        ExecutionContext ctx = ExecutionContext.forSaga(ClaimSagaConstants.SAGA_UPDATE_RECORD, "test");
        ctx.putVariable(ClaimSagaConstants.CTX_RECORD, current);

        StepVerifier.create(saga.updateClaim(new UpdateClaimCommand(id, incoming), ctx))
                .assertNext(saved -> assertThat(saved.getClaimStatus()).isEqualTo(ClaimDTO.ClaimStatusEnum.NOTIFIED))
                .verifyComplete();

        ArgumentCaptor<ApplyClaimStatusCommand> captor = ArgumentCaptor.forClass(ApplyClaimStatusCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().claimId()).isEqualTo(id);
        assertThat(captor.getValue().record().getClaimStatus()).isEqualTo(ClaimDTO.ClaimStatusEnum.NOTIFIED);
        assertThat(ctx.getVariableAs(ClaimSagaConstants.CTX_PREVIOUS_RECORD, ClaimDTO.class)).isSameAs(current);
    }

    @Test
    void restore_writesThePreviousRecordBack() {
        UUID id = UUID.randomUUID();
        ClaimDTO previous = new ClaimDTO(id);
        previous.setClaimStatus(ClaimDTO.ClaimStatusEnum.NOTIFIED);
        ClaimDTO updated = new ClaimDTO(id);
        updated.setClaimStatus(ClaimDTO.ClaimStatusEnum.OPEN);
        when(commandBus.send(any(ApplyClaimStatusCommand.class))).thenReturn(Mono.just(previous));

        ExecutionContext ctx = ExecutionContext.forSaga(ClaimSagaConstants.SAGA_UPDATE_RECORD, "test");
        ctx.putVariable(ClaimSagaConstants.CTX_PREVIOUS_RECORD, previous);

        StepVerifier.create(saga.restoreClaim(updated, ctx))
                .verifyComplete();

        ArgumentCaptor<ApplyClaimStatusCommand> captor = ArgumentCaptor.forClass(ApplyClaimStatusCommand.class);
        verify(commandBus).send(captor.capture());
        assertThat(captor.getValue().record()).isSameAs(previous);
    }
}
