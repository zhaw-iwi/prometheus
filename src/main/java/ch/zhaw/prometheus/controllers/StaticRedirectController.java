package ch.zhaw.prometheus.controllers;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class StaticRedirectController {

    @GetMapping({ "/", "/valerian", "/valerian/" })
    public String valerian(HttpServletRequest request) {
        return redirectWithQuery("/valerian/index.html", request);
    }

    @GetMapping({ "/valerian-admin", "/valerian-admin/" })
    public String valerianAdmin(HttpServletRequest request) {
        return redirectWithQuery("/valerian-admin/index.html", request);
    }

    @GetMapping({ "/valerian-design", "/valerian-design/" })
    public String valerianDesigner(HttpServletRequest request) {
        return redirectWithQuery("/valerian-design/index.html", request);
    }

    @GetMapping({ "/apiworkbench", "/apiworkbench/" })
    public String apiWorkbench(HttpServletRequest request) {
        return redirectWithQuery("/apiworkbench/index.html", request);
    }

    @GetMapping({ "/public/talktome", "/public/talktome/" })
    public String publicTalkToMe() {
        return "forward:/talktome/index.html";
    }

    @GetMapping({ "/talktome", "/talktome/" })
    public String talkToMe(HttpServletRequest request) {
        return redirectWithQuery("/talktome/index.html", request);
    }

    private String redirectWithQuery(String target, HttpServletRequest request) {
        String query = request.getQueryString();
        if (query == null || query.isBlank()) {
            return "redirect:" + target;
        }
        return "redirect:" + target + "?" + query;
    }
}
