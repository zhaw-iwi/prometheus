package ch.zhaw.prometheus.agentdefs.usecases.gala;

import java.util.List;
import java.util.Locale;

/** Fixed factual cards keep the target stable and unknown facts explicit. */
final class GalaDinnerPeople {
    record Person(String name, List<String> aliases, String facts) {}
    static final List<Person> PEOPLE = List.of(
            new Person("Albert Einstein", List.of("albert einstein", "einstein"),
                    "Man; physicist; born 1879 in Ulm; died 1955; developed relativity; Nobel Prize in Physics 1921. Not an actor or musician."),
            new Person("Marie Curie", List.of("marie curie", "curie"),
                    "Woman; physicist and chemist; born 1867 in Warsaw; died 1934; worked in France; researched radioactivity; Nobel Prizes in Physics 1903 and Chemistry 1911. Not an actor or musician."),
            new Person("Charlie Chaplin", List.of("charlie chaplin", "charles chaplin", "chaplin"),
                    "Man; actor, comedian and film director; born 1889 in London; died 1977 in Switzerland; famous for silent films and the Tramp character. Not a physicist."),
            new Person("Ada Lovelace", List.of("ada lovelace", "lovelace"),
                    "Woman; mathematician; born 1815 in London; died 1852; worked on Charles Babbage's Analytical Engine; wrote an early published computer algorithm. Not an actor or physicist."));
    private GalaDinnerPeople() {}
    static Person named(String name) {
        return PEOPLE.stream().filter(person -> person.name().equals(name)).findFirst().orElseThrow();
    }
    static boolean guessed(Person person, String utterance) {
        String candidate = utterance.toLowerCase(Locale.ROOT).trim()
                .replaceFirst("^(bist du|sind sie|ist es|ist das|heisst du|heißt du|are you|is it)\\s+", "")
                .replaceAll("[.!?]+$", "").trim();
        return person.aliases().contains(candidate);
    }
}
