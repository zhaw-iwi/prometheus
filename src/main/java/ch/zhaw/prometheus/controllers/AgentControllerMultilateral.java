package ch.zhaw.prometheus.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AgentControllerMultilateral {

    @GetMapping({ "/multilateral", "/multilateral/" })
    public String multilateral(HttpServletRequest request) {
        return redirectWithQuery("/multilateral/listen/index.html", request);
    }

    @GetMapping({ "/multilateral/listen", "/multilateral/listen/" })
    public String multilateralListen(HttpServletRequest request) {
        return redirectWithQuery("/multilateral/listen/index.html", request);
    }

    @GetMapping({ "/multilateral/reports", "/multilateral/reports/" })
    public String multilateralReports(HttpServletRequest request) {
        return redirectWithQuery("/multilateral/reports/index.html", request);
    }

    private String redirectWithQuery(String target, HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return "redirect:" + target;
        }
        return "redirect:" + target + "?" + query;
    }
}
