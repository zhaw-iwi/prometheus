package ch.zhaw.prometheus.application.study;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

class StudyAssessmentTest {
    ObjectNode fixture(String first,String second){var value=new ObjectMapper().createObjectNode();var answers=value.putArray("answers");
        answers.addObject().put("item","post.umux.capabilities").put("value",first);answers.addObject().put("item","post.umux.ease").put("value",second);value.putArray("agreements");return value;}
    @Test void publishedTransformKeepsRawAndAdjustedValuesDistinct(){
        var high=StudyAssessment.score(fixture("7","7")).path("umuxLite");assertEquals(100,high.path("raw0to100").asDouble());assertEquals(87.9,high.path("regressionAdjusted2013").asDouble(),.00001);
        var low=StudyAssessment.score(fixture("1","1")).path("umuxLite");assertEquals(0,low.path("raw0to100").asDouble());assertEquals(22.9,low.path("regressionAdjusted2013").asDouble());
        assertEquals(50,StudyAssessment.score(fixture("4","4")).path("umuxLite").path("raw0to100").asDouble());
    }
    @Test void missingOrInapplicableAnswersAreNotImputedOrCombined(){
        var fixture=fixture("SKIP","4");fixture.withArray("answers").addObject().put("item","post.effort").put("value","NOT_APPLICABLE");
        var result=StudyAssessment.score(fixture);assertFalse(result.path("umuxLite").path("complete").asBoolean());assertTrue(result.path("umuxLite").path("raw0to100").isNull());
        assertEquals("SKIP",result.path("missingness").path("post.umux.capabilities").asText());assertEquals("NOT_APPLICABLE",result.path("missingness").path("post.effort").asText());assertFalse(result.path("agreementAssessmentApplicable").asBoolean());
        assertFalse(result.has("coordinationComposite"));
    }
    @Test void csvIncludesFailedAttemptsWithoutAnswersAndEscapesSpreadsheetFormulas(){
        var dataset=new ObjectMapper().createObjectNode();var attempts=dataset.putArray("attempts");var empty=attempts.addObject();empty.putObject("session").put("id","failed").put("stage","WITHDRAWN");empty.putArray("answers");
        var withAnswer=attempts.addObject();withAnswer.putObject("session").put("id","with-answer");withAnswer.putArray("answers").addObject().put("form","post").put("item","feedback").put("value","=1+1,\"quoted\"\nsecond line");
        var csv=StudyOperations.csv(dataset);assertTrue(csv.contains("WITHDRAWN"));assertTrue(csv.contains("\"'=1+1,\"\"quoted\"\"\nsecond line\""));assertTrue(dataset.toString().contains("=1+1"));
    }
}
