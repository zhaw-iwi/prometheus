package ch.zhaw.prometheus.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import ch.zhaw.prometheus.agentdefs.core.CoordinationAgent;
import ch.zhaw.prometheus.spi.*;

class SceneTargetCaptureGatewayUnitTest {
    @Test void onlyTheOwningDecisionThreadRefreshesItsAggregate() throws Exception {
        var service=spy(new SceneTargetCaptureService(null,null,mock(PlatformTransactionManager.class)));
        var agent=new CoordinationAgent().createAgent();doReturn(false).when(service).refresh(agent);
        var provider=mock(LanguageModelGateway.class);var request=new InferenceRequest(InferencePurpose.EXTRACTION,List.of(),InferenceRequest.Output.JSON_OBJECT);
        when(provider.infer(request)).thenReturn("{}");var gateway=service.gateway(agent,provider);
        try(var pool=Executors.newSingleThreadExecutor()) { assertEquals("{}",pool.submit(()->gateway.infer(request)).get()); }
        verify(service,never()).refresh(agent);
        assertEquals("{}",gateway.infer(request));verify(service).refresh(agent);
        when(provider.infer(request)).thenThrow(new IllegalStateException("provider failure"));
        assertThrows(IllegalStateException.class,()->gateway.infer(request));verify(service,times(2)).refresh(agent);
    }
}
