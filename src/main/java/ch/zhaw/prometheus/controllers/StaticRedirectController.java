package ch.zhaw.prometheus.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaticRedirectController {

    @GetMapping({ "/monitor", "/monitor/" })
    public String monitor(HttpServletRequest request) {
        return redirectWithQuery("/monitor/index.html", request);
    }

    @GetMapping({ "/realtime", "/realtime/" })
    public String realtime(HttpServletRequest request) {
        return redirectWithQuery("/realtime/index.html", request);
    }

    @GetMapping({ "/visual/facial", "/visual/facial/" })
    public String visualFacial(HttpServletRequest request) {
        return redirectWithQuery("/visual/facial/index.html", request);
    }

    @GetMapping({ "/visual/nonverbal", "/visual/nonverbal/" })
    public String visualNonverbal(HttpServletRequest request) {
        return redirectWithQuery("/visual/nonverbal/index.html", request);
    }

    @GetMapping({ "/visual/social", "/visual/social/" })
    public String visualSocial(HttpServletRequest request) {
        return redirectWithQuery("/visual/social/index.html", request);
    }

    private String redirectWithQuery(String target, HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return "redirect:" + target;
        }
        return "redirect:" + target + "?" + query;
    }
}
