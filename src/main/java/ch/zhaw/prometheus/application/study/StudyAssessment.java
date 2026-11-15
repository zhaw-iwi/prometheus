package ch.zhaw.prometheus.application.study;

import java.util.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;

/** Transparent descriptive scores; no model judge, imputation, or composite coordination scale. */
public final class StudyAssessment {
    private StudyAssessment(){}
    public static ObjectNode score(JsonNode exported){
        var json=new ObjectMapper();var result=json.createObjectNode();result.put("schema","hri-assessment-v1");
        result.put("understandingAssessment","OPEN_REFERENCE_MANUAL_CODING");
        var values=new HashMap<String,String>();for(var row:exported.path("answers"))values.put(row.path("item").asText(),row.path("value").asText());
        Double capabilities=rating(values.get("post.umux.capabilities")),ease=rating(values.get("post.umux.ease"));
        var umux=result.putObject("umuxLite");umux.put("source","https://doi.org/10.1145/2470654.2481287");
        umux.put("complete",capabilities!=null && ease!=null);
        if(capabilities==null || ease==null){umux.putNull("raw0to100");umux.putNull("regressionAdjusted2013");}
        else {double raw=(capabilities+ease-2)*100/12;umux.put("raw0to100",raw);umux.put("regressionAdjusted2013",.65*raw+22.9);}
        var exploratory=result.putObject("exploratoryRatings");for(String id:List.of("post.clarity","post.correction","post.control","post.effort")){
            Double score=rating(values.get(id));if(score==null)exploratory.putNull(id);else exploratory.put(id,score);
        }
        var missing=result.putObject("missingness");for(String id:List.of("post.umux.capabilities","post.umux.ease","post.clarity","post.correction","post.control","post.effort")){
            String value=values.get(id);if(rating(value)==null)missing.put(id,value==null?"NOT_SUBMITTED":value.isBlank()?"EMPTY":value);
        }
        result.put("acceptedAgreements",exported.path("agreements").size());result.put("revisionAccepted",exported.path("agreements").size()>=2);
        result.put("agreementAssessmentApplicable",!exported.path("agreements").isEmpty());
        result.put("finishReason",exported.path("session").path("finishReason").asText("NOT_FINISHED"));
        return result;
    }
    private static Double rating(String value){return value!=null && value.matches("[1-7]")?Double.valueOf(value):null;}
}
