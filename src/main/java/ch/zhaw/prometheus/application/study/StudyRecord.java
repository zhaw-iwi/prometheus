package ch.zhaw.prometheus.application.study;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.*;

/** Append-only research evidence, independent of bounded runtime diagnostics. */
@Entity
@Table(name="study_record",uniqueConstraints=@UniqueConstraint(columnNames={"attempt_id","record_key"}))
public class StudyRecord {
    @Id @GeneratedValue UUID id;
    @Column(name="attempt_id",nullable=false,updatable=false) UUID attemptId;
    @Column(name="record_key",nullable=false,updatable=false,length=100) String recordKey;
    @Column(nullable=false,updatable=false,length=24) String kind;
    @Column(nullable=false,updatable=false,columnDefinition="longtext") String payload;
    @Column(nullable=false,updatable=false) Instant recordedAt;
    protected StudyRecord() {}
    StudyRecord(UUID attempt,String key,String kind,String payload) {
        attemptId=attempt; recordKey=key; this.kind=kind; this.payload=payload; recordedAt=Instant.now();
    }
}
