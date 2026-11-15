package ch.zhaw.prometheus.application.study;

import java.security.SecureRandom;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;

/** Persisted randomized blocks of three; replacements do not redraw or consume an allocation. */
@Service
public class StudyAllocator {
    public record Assignment(String scenario,long ordinal){}
    private final StudyAllocationRepository allocations;
    private final StudyMaterials materials;
    private final SecureRandom random=new SecureRandom();
    public StudyAllocator(StudyAllocationRepository allocations,StudyMaterials materials){this.allocations=allocations;this.materials=materials;}
    @Transactional
    public Assignment next(StudyAttempt.Mode mode){
        var manifest=materials.manifest();var available=manifest.scenarios().stream().filter(s->mode==StudyAttempt.Mode.SYNTHETIC || s.ready()).map(StudyMaterials.Scenario::id).sorted().toList();
        if(available.size()!=3)throw StudySessions.failure(HttpStatus.CONFLICT,"All three scenarios must be ready for allocation");
        String key=mode+":"+manifest.version();allocations.ensure(key);var row=allocations.lock(key).orElseThrow();
        if(row.index%3==0){var order=new ArrayList<>(available);Collections.shuffle(order,random);row.blockOrder=String.join(",",order);}
        String assigned=row.blockOrder.split(",")[(int)(row.index%3)];long ordinal=++row.index;allocations.saveAndFlush(row);
        return new Assignment(assigned,ordinal);
    }
}
