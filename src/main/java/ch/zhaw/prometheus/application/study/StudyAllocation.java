package ch.zhaw.prometheus.application.study;

import jakarta.persistence.*;

@Entity @Table(name="study_allocation")
public class StudyAllocation {
    @Id @Column(length=100) String id;
    @Version long version;
    @Column(name="allocation_index") long index;
    @Column(length=80) String blockOrder;
    protected StudyAllocation(){}
}
