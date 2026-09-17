package ch.zhaw.prometheus.logging;

import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/** Export request-local durations without buffering or changing response bodies. */
@ControllerAdvice
public class LatencyResponseAdvice implements ResponseBodyAdvice<Object> {
    @Override public boolean supports(MethodParameter method, Class<? extends HttpMessageConverter<?>> converter) {
        return true;
    }

    @Override public Object beforeBodyWrite(Object body, MethodParameter method, MediaType mediaType,
            Class<? extends HttpMessageConverter<?>> converter, ServerHttpRequest request, ServerHttpResponse response) {
        String timing = LatencyTrace.responseHeader();
        if (timing != null) response.getHeaders().set(LatencyTrace.TIMING_HEADER, timing);
        return body;
    }
}
