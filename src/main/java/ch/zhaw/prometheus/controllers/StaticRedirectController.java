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

    @GetMapping({ "/visual/multifacial", "/visual/multifacial/" })
    public String visualMultifacial(HttpServletRequest request) {
        return redirectWithQuery("/visual/multifacial/index.html", request);
    }

    @GetMapping({ "/nonverbal", "/nonverbal/", "/visual/nonverbal", "/visual/nonverbal/" })
    public String nonverbal(HttpServletRequest request) {
        return redirectWithQuery("/nonverbal/index.html", request);
    }

    @GetMapping({ "/visual/social", "/visual/social/" })
    public String visualSocial(HttpServletRequest request) {
        return redirectWithQuery("/visual/social/index.html", request);
    }

    @GetMapping({ "/rps", "/rps/" })
    public String rps(HttpServletRequest request) {
        return redirectWithQuery("/rps/index.html", request);
    }

    @GetMapping({ "/gigi-demo", "/gigi-demo/", "/gigi", "/gigi/", "/tdsr", "/tdsr/" })
    public String gigiDemo(HttpServletRequest request) {
        return redirectWithQuery("/gigi-demo/index.html", request);
    }

    private String redirectWithQuery(String target, HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return "redirect:" + target;
        }
        return "redirect:" + target + "?" + query;
    }
}
