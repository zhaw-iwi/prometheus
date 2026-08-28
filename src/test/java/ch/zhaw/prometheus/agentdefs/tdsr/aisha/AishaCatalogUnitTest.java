package ch.zhaw.prometheus.agentdefs.tdsr.aisha;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class AishaCatalogUnitTest {

    @Test
    void defaultCatalogIsVersionedValidatedAndMatchesArabicAliases() {
        AishaCatalog catalog = AishaCatalog.loadDefault();

        assertEquals("1.0-demo", catalog.catalogVersion());
        assertEquals("DEMO_DRAFT", catalog.reviewStatus());
        assertEquals(7, catalog.entries().size());
        assertNotNull(catalog.entry("invest_qatar_overview"));
        assertEquals("invest_qatar_overview",
                catalog.candidates("ما هي وكالةُ ترويجِ الاستثمار في قطر؟", 3).get(0).entry().id());
        assertEquals("qatar_national_vision",
                catalog.candidates("حدثيني عن رؤية قطر الوطنية 2030", 3).get(0).entry().id());
    }

    @Test
    void unrelatedArabicQuestionHasNoCandidate() {
        List<AishaCatalog.Candidate> candidates = AishaCatalog.loadDefault()
                .candidates("هل ستمطر في الدوحة مساء اليوم؟", 3);

        assertTrue(candidates.isEmpty());
    }
}
