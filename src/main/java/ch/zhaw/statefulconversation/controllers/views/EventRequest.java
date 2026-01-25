package ch.zhaw.statefulconversation.controllers.views;

public class EventRequest {
    private String type;
    private String content;

    public EventRequest() {
    }

    public EventRequest(String content) {
        this.content = content;
    }

    public EventRequest(String type, String content) {
        this.type = type;
        this.content = content;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
