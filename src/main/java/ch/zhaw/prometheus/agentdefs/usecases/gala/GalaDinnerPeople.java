package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.util.List;
import java.util.Locale;

/** Fixed game cards shared with Live; source audit (checked 2026-10-21) stays out of model context. */
final class GalaDinnerPeople {
    record Person(String name, List<String> aliases, String facts) {}
    static final List<Person> PEOPLE = List.of(
            // https://www.nobelprize.org/prizes/physics/1921/einstein/biographical/
            // https://patents.google.com/patent/US1781541A/en
            new Person("Albert Einstein", List.of("albert einstein", "einstein"),
                    "Real historical man, not fictional; physicist; born 1879 in Ulm, Germany; died 1955 in Princeton, USA; not alive. "
                    + "Lived in the 19th and 20th centuries, not the 18th century or Middle Ages. "
                    + "Lived and worked in Germany, Switzerland (including Zurich and Bern) and the USA; lived in Europe. "
                    + "Famous chiefly for physics, especially relativity; Nobel Prize in Physics 1921 for the photoelectric effect. "
                    + "Also co-invented a refrigerator with Leo Szilard (US patent 1781541, 1930): invented something, "
                    + "but his principal fame is scientific theories, not that appliance or construction of buildings. "
                    + "Played violin as a hobby, not a professional musician or actor. Other residence countries are not established here."),
            // https://www.nobelprize.org/prizes/physics/1903/marie-curie/questions-and-answers/
            // https://www.nobelprize.org/prizes/physics/1903/marie-curie/facts/
            // https://www.nobelprize.org/prizes/chemistry/1911/marie-curie/biographical/
            new Person("Marie Curie", List.of("marie curie", "curie"),
                    "Real historical woman, not fictional; physicist and chemist; born 1867 in Warsaw (now Poland); "
                    + "died 1934 in France; not alive. Lived in the 19th and 20th centuries, not the 18th century or Middle Ages. "
                    + "Lived in Poland and France, studied and worked in Paris; lived in Europe. "
                    + "Known for radioactivity research and the discovery of polonium and radium with Pierre Curie. "
                    + "Nobel Prizes in Physics 1903 and Chemistry 1911. Her listed achievements are scientific discoveries, "
                    + "not evidence that she never invented anything. Not an actor or professional musician; other residences are unknown here."),
            // https://www.charliechaplin.com/en/biography/articles/21-Overview-of-His-Life
            new Person("Charlie Chaplin", List.of("charlie chaplin", "charles chaplin", "chaplin"),
                    "Real historical man, not fictional; British actor, comedian, film director and composer. "
                    + "Born 1889 in London, England; died 1977 in Switzerland; not alive. "
                    + "Lived in the 19th and 20th centuries, not the 18th century or Middle Ages. "
                    + "Lived in England, the USA and Switzerland (Corsier-sur-Vevey); lived in Europe. "
                    + "Famous chiefly for films, especially silent comedy and his fictional Tramp character; the actor himself was real. "
                    + "Not a physicist. Whether he held inventions/patents or lived in other countries is not established here."),
            // https://collection.sciencemuseumgroup.org.uk/people/cp38764/ada-lovelace
            new Person("Ada Lovelace", List.of("ada lovelace", "lovelace"),
                    "Real historical woman, not fictional; British mathematician and computing pioneer. "
                    + "Born 1815 in London, England; died 1852; not alive. Lived in the 19th century only, "
                    + "not the 18th or 20th century or Middle Ages. Lived in England, hence Europe. "
                    + "Worked on Charles Babbage's Analytical Engine; published notes in 1843 including an algorithm "
                    + "for calculating Bernoulli numbers and ideas about general computation. Babbage designed the machine; "
                    + "she did not build a working computer. Not an actor or physicist. Other residences and patents are unknown here."));
    private GalaDinnerPeople() {}
    static Person named(String name) {
        return PEOPLE.stream().filter(person -> person.name().equals(name)).findFirst().orElseThrow();
    }
    static boolean guessed(Person person, String utterance) {
        String candidate = utterance.toLowerCase(Locale.ROOT).trim()
                .replaceFirst("^(bist du|sind sie|ist es|war es|ist das|war das|es ist|es war|ich tippe auf|mein tipp ist|heisst du|heißt du|are you|is it|was it|my guess is)\\s+", "")
                .replaceAll("[.!?]+$", "").trim();
        return person.aliases().contains(candidate);
    }
}
