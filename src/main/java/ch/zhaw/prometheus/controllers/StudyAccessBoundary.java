package ch.zhaw.prometheus.controllers;

import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.*;
import org.springframework.web.servlet.config.annotation.*;
import jakarta.servlet.http.*;
import ch.zhaw.prometheus.application.study.StudyAgentRegistry;

/** Study agents are only reachable through the authenticated study procedure. */
@Configuration
public class StudyAccessBoundary implements WebMvcConfigurer {
    private final StudyAgentRegistry ownership;
    public StudyAccessBoundary(StudyAgentRegistry ownership) { this.ownership=ownership; }
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) throws Exception {
                Object attributes=request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
                if (attributes instanceof Map<?,?> variables) for (String key:List.of("agentID","agentId","id")) {
                    Object value=variables.get(key);
                    if (value==null) continue;
                    UUID id;
                    try { id=UUID.fromString(value.toString()); } catch (IllegalArgumentException ignored) { continue; }
                    if (ownership.contains(id)) { response.setStatus(404); return false; }
                }
                return true;
            }
        }).excludePathPatterns("/api/study/**","/admin/study/**");
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler) {
                response.setHeader("Cache-Control","no-store"); return true;
            }
        }).addPathPatterns("/api/study/**","/admin/study/**");
    }
    public Set<UUID> privateAgents() { return ownership.ids(); }
}
