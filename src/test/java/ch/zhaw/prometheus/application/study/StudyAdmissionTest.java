package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class StudyAdmissionTest {
    static class Time extends Clock {
        Instant now=Instant.parse("2026-11-15T00:00:00Z");
        public ZoneId getZone(){return ZoneOffset.UTC;}
        public Clock withZone(ZoneId zone){return this;}
        public Instant instant(){return now;}
    }
    @Test void renewalExpiryAndReleasePreserveTheBound(){
        var time=new Time();var slots=new StudyAdmission(1,120,time);var a=UUID.randomUUID();var b=UUID.randomUUID();
        slots.acquire(a);time.now=time.now.plusSeconds(119);slots.acquire(a);
        assertEquals(429,assertThrows(ResponseStatusException.class,()->slots.acquire(b)).getStatusCode().value());
        time.now=time.now.plusSeconds(120);slots.acquire(b);assertEquals(1,slots.status().active());
        slots.release(b);slots.acquire(a);assertEquals(1,slots.status().active());
    }
    @Test void concurrentAdmissionNeverOverbooks()throws Exception{
        var slots=new StudyAdmission(8,120,new Time());
        try(var executor=Executors.newFixedThreadPool(20)){
            var start=new CountDownLatch(1);var results=new ArrayList<Future<Boolean>>();
            for(int i=0;i<20;i++)results.add(executor.submit(()->{start.await();try{slots.acquire(UUID.randomUUID());return true;}catch(ResponseStatusException full){return false;}}));
            start.countDown();int admitted=0;for(var result:results)if(result.get(5,TimeUnit.SECONDS))admitted++;
            assertEquals(8,admitted);assertEquals(8,slots.status().active());
        }
    }
}
