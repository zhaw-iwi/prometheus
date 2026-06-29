package ch.zhaw.prometheus.agentdefs;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ApplicationAgentDefinitionConfiguration {
    @Bean
    AgentDefinition tdsrCoreDeGuessingGameWithGestures() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.de.GuessingGameWithGestures();
    }

    @Bean
    AgentDefinition tdsrCoreDeSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.de.SocialContextSensitivity();
    }

    @Bean
    AgentDefinition tdsrCoreDeRockScissorPaper() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.de.RockScissorPaper();
    }

    @Bean
    AgentDefinition tdsrCoreDeTourConversation() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversation();
    }

    @Bean
    AgentDefinition tdsrCoreDeTourConversationSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.de.TourConversationSocialContextSensitivity();
    }

    @Bean
    AgentDefinition tdsrCoreFrGuessingGameWithGestures() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.fr.GuessingGameWithGestures();
    }

    @Bean
    AgentDefinition tdsrCoreFrSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.fr.SocialContextSensitivity();
    }

    @Bean
    AgentDefinition tdsrCoreFrRockScissorPaper() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.fr.RockScissorPaper();
    }

    @Bean
    AgentDefinition tdsrCoreFrTourConversation() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.fr.TourConversation();
    }

    @Bean
    AgentDefinition tdsrCoreFrTourConversationSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.fr.TourConversationSocialContextSensitivity();
    }

    @Bean
    AgentDefinition tdsrCoreItGuessingGameWithGestures() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.it.GuessingGameWithGestures();
    }

    @Bean
    AgentDefinition tdsrCoreItSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.it.SocialContextSensitivity();
    }

    @Bean
    AgentDefinition tdsrCoreItRockScissorPaper() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.it.RockScissorPaper();
    }

    @Bean
    AgentDefinition tdsrCoreItTourConversation() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.it.TourConversation();
    }

    @Bean
    AgentDefinition tdsrCoreItTourConversationSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.it.TourConversationSocialContextSensitivity();
    }

    @Bean
    AgentDefinition tdsrCoreEnGuessingGameWithGestures() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.en.GuessingGameWithGestures();
    }

    @Bean
    AgentDefinition tdsrCoreEnSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.en.SocialContextSensitivity();
    }

    @Bean
    AgentDefinition tdsrCoreEnRockScissorPaper() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.en.RockScissorPaper();
    }

    @Bean
    AgentDefinition tdsrCoreEnTourConversation() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.en.TourConversation();
    }

    @Bean
    AgentDefinition tdsrCoreEnTourConversationSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.en.TourConversationSocialContextSensitivity();
    }

    @Bean
    AgentDefinition tdsrCoreBabylonGuessingGameWithGestures() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.GuessingGameWithGestures();
    }

    @Bean
    AgentDefinition tdsrCoreBabylonSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.SocialContextSensitivity();
    }

    @Bean
    AgentDefinition tdsrCoreBabylonRockScissorPaper() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.RockScissorPaper();
    }

    @Bean
    AgentDefinition tdsrCoreBabylonTourConversation() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.TourConversation();
    }

    @Bean
    AgentDefinition tdsrCoreBabylonTourConversationSocialContextSensitivity() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.core.babylon.TourConversationSocialContextSensitivity();
    }

    @Bean
    AgentDefinition tdsrShhdDeEpflActive() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.EPFLActive();
    }

    @Bean
    AgentDefinition tdsrShhdDeFurka() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.Furka();
    }

    @Bean
    AgentDefinition tdsrShhdDeInterviewingPeople() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.InterviewingPeople();
    }

    @Bean
    AgentDefinition tdsrShhdDeSupsiActive() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.SUPSIActive();
    }

    @Bean
    AgentDefinition tdsrShhdDeUnisStudent() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.de.UnisStudent();
    }

    @Bean
    AgentDefinition tdsrShhdEnEpflActive() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.EPFLActive();
    }

    @Bean
    AgentDefinition tdsrShhdEnFurka() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.Furka();
    }

    @Bean
    AgentDefinition tdsrShhdEnInterviewingPeople() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.InterviewingPeople();
    }

    @Bean
    AgentDefinition tdsrShhdEnSupsiActive() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.SUPSIActive();
    }

    @Bean
    AgentDefinition tdsrShhdEnUnisStudent() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.en.UnisStudent();
    }

    @Bean
    AgentDefinition tdsrShhdItEpflActive() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.EPFLActive();
    }

    @Bean
    AgentDefinition tdsrShhdItFurka() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.Furka();
    }

    @Bean
    AgentDefinition tdsrShhdItInterviewingPeople() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.InterviewingPeople();
    }

    @Bean
    AgentDefinition tdsrShhdItSupsiActive() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.SUPSIActive();
    }

    @Bean
    AgentDefinition tdsrShhdItUnisStudent() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.it.UnisStudent();
    }

    @Bean
    AgentDefinition tdsrShhdFrEpflActive() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.EPFLActive();
    }

    @Bean
    AgentDefinition tdsrShhdFrFurka() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.Furka();
    }

    @Bean
    AgentDefinition tdsrShhdFrInterviewingPeople() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.InterviewingPeople();
    }

    @Bean
    AgentDefinition tdsrShhdFrSupsiActive() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.SUPSIActive();
    }

    @Bean
    AgentDefinition tdsrShhdFrUnisStudent() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.fr.UnisStudent();
    }

    @Bean
    AgentDefinition tdsrShhdBabylonEpflActive() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.EPFLActive();
    }

    @Bean
    AgentDefinition tdsrShhdBabylonFurka() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.Furka();
    }

    @Bean
    AgentDefinition tdsrShhdBabylonInterviewingPeople() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.InterviewingPeople();
    }

    @Bean
    AgentDefinition tdsrShhdBabylonSupsiActive() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.SUPSIActive();
    }

    @Bean
    AgentDefinition tdsrShhdBabylonUnisStudent() {
        return new ch.zhaw.prometheus.agentdefs.tdsr.shhd.babylon.UnisStudent();
    }

    @Bean
    AgentDefinition elderlyCareTherapyAppointmentReminder() {
        return new ch.zhaw.prometheus.agentdefs.elderlycare.SingleStateTherapyAppointmentReminder();
    }

    @Bean
    AgentDefinition elderlyCareGuessingGame() {
        return new ch.zhaw.prometheus.agentdefs.elderlycare.SingleStateGuessingGame();
    }

    @Bean
    AgentDefinition elderlyCareGuessingGameUserGuess() {
        return new ch.zhaw.prometheus.agentdefs.elderlycare.SingleStateGuessingGameUserGuess();
    }

    @Bean
    AgentDefinition elderlyCareSmartGoalCoaching() {
        return new ch.zhaw.prometheus.agentdefs.elderlycare.SingleStateSmartGoalCoaching();
    }
}
