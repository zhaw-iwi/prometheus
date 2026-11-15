package ch.zhaw.prometheus.application.study;

import java.time.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Procedural resource bounds; reaching one does not assert task success or discard assessment access. */
@Component
public class StudyLimits {
    private final int turns,minutes;
    private final StudyTurnRepository receipts;
    public StudyLimits(@Value("${prometheus.study.max-turns:50}")int turns,@Value("${prometheus.study.interaction-minutes:45}")int minutes,StudyTurnRepository receipts){
        if(turns<1 || turns>100 || minutes<1 || minutes>120)throw new IllegalArgumentException("Invalid study interaction bounds");
        this.turns=turns;this.minutes=minutes;this.receipts=receipts;
    }
    public record Bounds(int maxTurns,int interactionMinutes){}
    public Bounds bounds(){return new Bounds(turns,minutes);}
    void check(StudyAttempt attempt){
        if(receipts.countByAttemptId(attempt.id)>=turns || attempt.interactionStartedAt!=null && !Instant.now().isBefore(attempt.interactionStartedAt.plusSeconds(minutes*60L)))
            throw StudySessions.failure(HttpStatus.TOO_MANY_REQUESTS,"This interaction reached its study limit. Finish the interaction to save your assessments, or withdraw.");
    }
}
