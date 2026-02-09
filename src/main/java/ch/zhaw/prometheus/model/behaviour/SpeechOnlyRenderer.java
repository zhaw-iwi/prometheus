package ch.zhaw.prometheus.model.behaviour;

public class SpeechOnlyRenderer implements BehaviourRenderer {
    @Override
    public String render(BehaviourPlan plan) {
        if (plan == null) {
            return null;
        }
        return plan.getSpeech();
    }
}
