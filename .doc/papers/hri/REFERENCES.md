# Reference and source guide

Prepared 15 November 2026 for [PAPER_BRIEF.md](PAPER_BRIEF.md). This is a curated
reading/citation guide, not a finished bibliography or exhaustive related-work
review. Verify publisher metadata and the exact supporting passage before final
manuscript citation. Do not interpret a listed source as validation of PROMETHEUS.

The five owner-supplied PDFs below were inspected during the planning conversation.
They remain in the local Downloads directory and are not repository artifacts.
Their paths were verified on the preparation date. A new machine may need the
public version or an owner-provided copy. Do not copy the PDFs into Git merely to
make the source links portable. PDF page counts include any repository cover page.

## How the literature supports the paper

Organize the argument by the job each source performs. The categories overlap:
one paper can motivate a principle, describe a related system, and inform a
measure. The guide preserves the supplied publications and earlier discussion
leads; Clark and Brennan, Klein et al., and SayCan are additions from this
reference audit. This is a targeted foundation, not a systematic review.

| Literature strand | Role in our argument | Starting sources and location |
| --- | --- | --- |
| Multimodal perception and behavior | Explain how people convey meaning and anticipate robot actions, and why cues need contextual interpretation | Belardinelli and Duarte et al. (A2-A3); additional gaze/expressive-behavior papers (F) |
| Design knowledge and interaction frameworks | Establish precedents for guidelines, reusable abstractions, and executable HRI infrastructure; identify actual comparison targets | Cila et al., Drury et al., TA-HRI; Robot Behavior Toolkit, IrisTK, RISE, Andrist et al., Rajendran et al. (B) |
| Common ground, joint activity, and human-robot coordination | Ground the need to establish, maintain, and repair goals and expectations throughout an activity | Clark and Brennan; Klein et al.; Hoffman and Breazeal; Chai et al. (G) |
| Generative interaction, orchestration, and robot execution | Locate the contribution among dialogue managers, LLM-based social robots, stateful orchestration, and grounded action systems | PROMISE (A1); Johnson thesis/manuscript (A4-A5); Reimann et al., FurChat, Pekarek Rosin et al., SayCan (C) |
| Evaluation of shared understanding and interaction | Justify inspecting agreement fidelity, participant understanding, repair, and behavior separately; guide reproducible study artifacts | Chai et al. (G); Poelitz et al. (H); Rajendran et al. and RISE (B); venue reporting guidance (E) |
| Scenario and domain context | Keep the game protocol and fictional health vignette accurate and appropriately bounded | Scenario sources and NICE (D); clinical-effect claims would require a separate evidence base |

The first five strands support the scientific contribution. The sixth supports
study materials; it does not turn this into a clinical validation paper. Industry
harness descriptions and ROS documentation (C) explain terminology and interfaces,
not peer-reviewed evidence of a research gap.

**Central distinction:** an accepted record is a system artifact; mutual
understanding is an interaction outcome; successful execution is a further
outcome. Related work motivates examining their alignment. It does not establish
that PROMETHEUS already achieves it.

## A. Owner-supplied papers: primary reading set

### A1. PROMISE and the inherited interaction model

**Wu et al. (2024). PROMISE: A Framework for Model-Driven Stateful Prompt
Orchestration.** In *Intelligent Information Systems*, LNBIP 520, pp. 157-165.

- [Publisher DOI](https://doi.org/10.1007/978-3-031-61000-4_18).
- [ZORA record](https://www.zora.uzh.ch/handle/20.500.14742/219561).
- [Local accepted manuscript](</C:/Users/Admin/Downloads/Promise_CAiSE_Zora.pdf>),
  9 PDF pages.
- Read the motivation and interaction model, especially Figures 3, 4, and 6 and
  the validation example. The manuscript describes state prompts, prompt/code
  transition decisions and actions, storage, reusable special-purpose states,
  nested conversations, and history-based continuation after an interruption.
- **Use:** establish lineage and inherited primitives. The flexibility/control
  tension and complex health conversations already appear here. Identify the
  specific extension to multimodal observations, explicit coordination agreements,
  and behavior/delivery evidence.
- **Limit:** its illustrative applications are not evidence for the proposed HRI
  student outcomes. Do not relabel inherited stateful prompt orchestration as a
  newly invented mechanism.

### A2. Gaze, inferred intentions, and human agency

**Belardinelli, Anna (2024). Gaze-Based Intention Estimation: Principles,
Methodologies, and Applications in HRI.** *ACM Transactions on Human-Robot
Interaction* 13(3), Article 31, 30 pages.

- [Publisher DOI](https://doi.org/10.1145/3656376).
- [Local published paper](</C:/Users/Admin/Downloads/GazeHonda.pdf>), 30 PDF pages.
- Prioritize Sections 2 and 5-6: intention levels; context-dependent gaze;
  multitasking and covert attention; the Midas-touch issue; co-adaptation;
  and the relationship between assistance, speed, and sense of agency.
- **Use:** ground the value and ambiguity of nonverbal evidence. Looking can
  serve perception rather than a command; users may change their gaze when they
  learn the system responds to it. This motivates distinguishing observation,
  interpretation, and permitted consequences.
- **Limit:** it is a review, not one experiment establishing a general effect
  size. Proximal action inference does not automatically reveal distal goals,
  a complete task specification, or agreement. Consult original studies for
  specific empirical claims attributed through the review.

### A3. Coordinated nonverbal behavior and action legibility

**Duarte et al. (2018). Action Anticipation: Reading the Intentions of Humans and
Robots.** *IEEE Robotics and Automation Letters*.

- [Publisher DOI](https://doi.org/10.1109/LRA.2018.2861569).
- [arXiv record and manuscript](https://arxiv.org/abs/1802.02788).
- [Local manuscript](</C:/Users/Admin/Downloads/billard-gaze.pdf>), 8 PDF pages.
- This is the paper the owner supplied as the Billard reference. Do not confuse
  it with the later *Gaze Dialogue Model* paper mentioned as an earlier search lead.
- The authors record a human actor's gaze/body movements during giving/placing
  actions in three directions, model arm trajectories and gaze behavior, and
  implement coordinated behavior on iCub. Video-based action-judgment studies
  involve 55 participants for the human actor and 20 for the robot. See Sections
  III-VII and Figures 2, 5-7. The gaze controller uses a state machine.
- **Use:** mutual legibility and task-relevant multimodal output. Robot gaze,
  head, and arm behavior can help a partner anticipate its actions in the studied
  setting; input sensing is only one side of coordination.
- **Limit:** a restricted set of six actions and video judgments do not establish
  live clinical collaboration, task negotiation, or effects of our harness. Cue
  timing and embodiment matter. Do not claim a universal accuracy advantage.
- The local PDF and arXiv metadata list authors in differing orders; use the
  publisher's bibliographic record when constructing the final reference.

### A4. Possible downstream robot execution layer

**Johnson, Michal (5 June 2026). An Evaluation of Agentic-AI Approaches for
Long-Horizon Robotic Tasks.** Bachelor's thesis, ZHAW School of Engineering.

- [Local thesis](</C:/Users/Admin/Downloads/BA FS 26_toff_193.pdf>), 71 PDF pages.
- [Skill-based implementation discussed in the session](https://github.com/michaljohnson/skill_based).
- Compares a single model using low-level tools, an orchestrator with specialized
  model agents, and a planner calling deterministic approach/pick/place skills.
  A shared MCP tool environment connects simulation/navigation/manipulation.
- **Use:** motivate an execution component beneath an accepted agreement. The
  deterministic skill approach is a promising boundary for constrained execution,
  checks, and recovery. MCP is an interface mechanism, not the paper's novelty.
- **Limit:** small uneven samples, simulation, operator assistance in the single-
  agent condition, differing run/subtask denominators, and successful-run timing
  limit direct rankings. The simulation uses artificial attachment and known
  environment assumptions. Read the experiment and appendix evidence together
  before quoting success rates. The code has environment-specific assumptions;
  it is not an already integrated, portable robot executor for PROMETHEUS.

### A5. Architectural comparison and long-horizon positioning

**Johnson, Michal; Munasinghe, Charith; Fu, Lei; Peña Queralta, Jorge; Bohnert,
Thomas Michael; and Toffetti, Giovanni. Agentic AI for Long-Horizon Robotic Tasks:
A Qualitative Comparison of Design Approaches.** Owner-supplied manuscript;
publication/acceptance status and final bibliographic year have not been verified.

- [Local manuscript](</C:/Users/Admin/Downloads/ERF27_Agentic_Long_Horizon.pdf>),
  8 PDF pages. The filename is not proof of publication at ERF 2027.
- Reviews 21 frameworks along planning structure, feedback, grounding, memory,
  and agent topology. Its definition puts a foundation model repeatedly in the
  decision loop choosing lower-level capabilities; motor control can remain
  conventional. It reports incomparable benchmarks and limited validation of
  model-based success judgments.
- **Use:** distinguish coordination support from physical long-horizon execution.
  Adopt its evaluation discipline: control executor, skills, model, and budgets
  when comparing architectures, and assess success judgments independently.
- **Limit:** do not transfer results from surveyed systems to PROMETHEUS. An
  explicit state machine is not by itself an agent hierarchy; persistent memory
  is not learned procedural skill; conversational reflection is not demonstrated
  learning. Verify permission/public availability if distributing this manuscript.

## B. Contribution positioning and related systems

These sources establish precedents and comparison targets. The final related-work
analysis still needs to identify what the specific PROMETHEUS composition adds.

| Source | Why it matters | Reading status/boundary |
| --- | --- | --- |
| Cila et al. (2024), [Bridging HRI Theory and Practice: Design Guidelines for Robot Communication in Dairy Farming](https://doi.org/10.1145/3610977.3634991), HRI 2024; [author-hosted paper](https://repository.tudelft.nl/file/File_4c6d8380-4b4e-405b-bc04-b42a9b33a0be) | Direct precedent for design guidelines as intermediate-level knowledge connecting theory and practice | Abstract and framing inspected; examine derivation/evaluation before comparing methods |
| Drury, Hestand, Yanco, and Scholtz (2004), [Design Guidelines for Improved Human-Robot Interaction](https://www.nist.gov/publications/design-guidelines-improved-human-robot-interaction), CHI Extended Abstracts | Early HRI guidelines derived from awareness anomalies | Official NIST record/abstract inspected; a CHI poster, not an HRI conference paper or comprehensive validation of guidelines |
| Kraus, Babel, Hock, Hauber, and Baumann (2022), [The trustworthy and acceptable HRI checklist (TA-HRI): questions and design recommendations to support a trust-worthy and acceptable design of human-robot interaction](https://doi.org/10.1007/s11612-022-00643-8), Gruppe. Interaktion. Organisation. | Design recommendations integrating literature and interdisciplinary project/expert work | Publisher text inspected; the authors emphasize context-dependent design impulses and further evaluation, not universal rules |
| Huang and Mutlu (2012), [Robot Behavior Toolkit: Generating Effective Social Behaviors for Robots](https://doi.org/10.1145/2157689.2157694), HRI 2012 | Social-behavior specifications, an executable framework, and interaction evaluation | Abstract inspected; useful design-to-implementation precedent, not an exhausted novelty comparison |
| Skantze and Al Moubayed (2012), [IrisTK: a statechart-based toolkit for multi-party face-to-face interaction](https://doi.org/10.1145/2388676.2388698), ICMI; [author's research description](https://www.speech.kth.se/~gabriel/research.html) | Event/statechart authoring and multimodal interaction predate this work | Author description inspected; original-paper reading remains necessary for detailed mechanism comparisons |
| [RISE: an open-source architecture for interdisciplinary and reproducible human-robot interaction research](https://www.frontiersin.org/journals/robotics-and-ai/articles/10.3389/frobt.2023.1245501/full) (2023) | Research infrastructure, inspectability, and comparison to existing toolkits | Relevant comparison lead; perform a focused architectural comparison before asserting a gap |
| Andrist, Bohus, and Feniello (2019), [Demonstrating a Framework for Rapid Development of Physically Situated Interactive Systems](https://www.microsoft.com/en-us/research/publication/demonstrating-a-framework-for-rapid-development-of-physically-situated-interactive-systems/), HRI | Another framework/programming-model precedent for situated interaction | Abstract inspected; not evidence of PROMETHEUS's authoring benefit |
| Rajendran, Carreno-Medrano, Fisher, Werner, and Kulic (2020), [A Framework for Human-Robot Interaction User Studies](https://doi.org/10.1109/IROS45743.2020.9341286), IROS, pp. 6215-6222; [institutional record](https://research.monash.edu/en/publications/a-framework-for-human-robot-interaction-user-studies/) | Modular perception, decision, action, and metrics infrastructure for reproducible studies | Record/abstract inspected; toolbox-assembly example, not a demonstration of our framework's usability or our proposed online study |

These are concrete precedents for legitimate contribution forms. They do not
establish how frequent those forms are at HRI, nor that relabeling our principles
as a framework is sufficient. Distinguish publication venues: HRI conference,
THRI journal, CHI, ICMI, IROS, and other journals are related communities, not
interchangeable labels. In the manuscript, prioritize the nearest mechanisms and
evidence; explain what PROMETHEUS adds to inherited ideas.

Grounding and joint-action papers from the discussion now have dedicated entries
in G, including their implications for the proposed student study.

## C. Generative interaction, harness terminology, and execution

### C1. Scholarly comparisons

Read these alongside PROMISE (A1) and the two Johnson documents (A4-A5). Prefer
the published versions over their earlier preprints when available.

| Source | Role and comparison boundary | Reading status |
| --- | --- | --- |
| Reimann, Kunneman, Oertel, and Hindriks (2024), [A Survey on Dialogue Management in Human-Robot Interaction](https://doi.org/10.1145/3648605), THRI 13(2), Article 22, 22 pages; [published version in institutional repository](https://research-portal.uu.nl/ws/files/239364965/3648605.pdf); [earlier preprint](https://arxiv.org/abs/2307.10897) | Situate state/context management, module coordination, physical situatedness, and multimodality within existing dialogue-management research. Do not treat the engineering term harness as replacing this literature | Published abstract/introduction inspected; use its original cited systems for detailed priority claims |
| Cherakara et al. (2023), [FurChat: An Embodied Conversational Agent using LLMs, Combining Open and Closed-Domain Dialogue with Facial Expressions](https://aclanthology.org/2023.sigdial-1.55/), SIGDIAL, pp. 588-592, DOI 10.18653/v1/2023.sigdial-1.55; [earlier preprint](https://arxiv.org/abs/2308.15214) | Direct precedent for LLM-generated social-robot conversation and expressive output. Compare how an activity's commitments are represented and changed; do not claim multimodal LLM interaction itself as novel | Official proceedings record/abstract inspected; detailed agreement/recovery comparison still required |
| Pekarek Rosin, Hassouna, Sun, Krohm, Kordt, Beetz, and Wermter (2025), [A Framework for Adapting Human-Robot Interaction to Diverse User Groups](https://doi.org/10.1007/978-981-96-3525-2_3), ICSR + AI 2024 proceedings, LNAI 15563, pp. 24-38; [2024 preprint](https://arxiv.org/abs/2410.11377) | Important comparison for a ROS-based framework using an LLM dialogue bridge and supporting interruptions/plan changes. Continuing user involvement and interruption support already have precedents | Publisher record/abstract inspected; inspect mechanisms and evaluation before claiming a distinguishing capability |
| Ichter et al. (2023), [Do As I Can, Not As I Say: Grounding Language in Robotic Affordances](https://proceedings.mlr.press/v205/ichter23a.html), CoRL 2022 proceedings, PMLR 205, pp. 287-318 (SayCan) | Published robotic-execution anchor added to the audit: combines language-model task knowledge with skills and their value functions to ground action selection. Our proposed agreement layer addresses an additional question: what activity has the person authorized? | Proceedings abstract/metadata inspected. Do not equate skill feasibility with mutual understanding; do not claim PROMETHEUS includes SayCan. Use proceedings author order/year, which differ from some preprint citations |

For a comparison table, inspect each selected system for: activity/role
specification, draft versus accepted state, revision authority, contextual
interruptions, nonverbal input/output, executor boundary, and evidence recorded.
Mark unsupported comparisons **not established from the inspected source**;
absence from an abstract is not evidence that a system lacks a feature. Limit
the final table to systems actually read and dimensions central to our claim.

### C2. Engineering terminology and interfaces

- [Google Cloud: What is an agent harness?](https://cloud.google.com/discover/agent-harness)
  (page updated 15 October 2026, checked 15 November): describes runtime state,
  execution, memory, guardrails, recovery, and human approval. Use as a source for
  contemporary terminology, not peer-reviewed evidence of novelty or efficacy.
- [Anthropic: Effective harnesses for long-running agents](https://www.anthropic.com/research/effective-harnesses-for-long-running-agents)
  (26 November 2025): an engineering example of environment/context/progress
  management across sessions. It is primarily about long-running work and is not
  a direct HRI comparison.
- [ROS 2 action design](https://design.ros2.org/articles/actions.html): goal,
  feedback, result, and cancellation mechanisms for a potential future executor.
  Transport semantics do not establish physical success or stop completion.

Define “interaction harness” operationally in the paper rather than relying on a
universally settled taxonomy. Existing human-in-the-loop controls are not novel
by themselves. The proposed emphasis is sustained human participation in forming,
grounding, performing, and revising an activity through multiple modalities.

## D. Scenario terminology and health framing

- [20Q description](https://20q.net/about.html): one party thinks of a secret;
  the other asks questions to identify it. Use “Twenty Questions-style” when the
  protocol changes the traditional limit.
- [Simon Says rules](https://www.playworks.org/game-library/simon-says/): following
  an instruction only when preceded by “Simon says.” This is a different activity.
- [NICE CG76 recommendations on medicines adherence](https://www.nice.org.uk/guidance/cg76/chapter/Recommendations):
  support informed choice, explore beliefs/concerns and practical difficulties,
  and use a nonjudgmental approach. This informs the fictional support vignette;
  it is not an institutional ethics determination or validation of clinical robot
  advice. Obtain appropriate review of study content and make no adherence-effect
  claim from student role-play. Direct page retrieval sometimes returned 403;
  indexed excerpts of the official recommendations were retrieved. Verify the
  complete recommendations before finalizing clinical-content wording.

Do not cite a guessing game as an effective therapy on the basis of these sources.
If therapeutic claims become necessary, they require separate clinical literature
and a different evaluation scope.

## E. Venue and research reporting sources

- [HRI 2027 LBR call](https://humanrobotinteraction.org/2027/late-breaking-reports/):
  authoritative deadline, format, anonymization, presentation, and publication
  requirements. The brief records the checked date; recheck before submission.
- [HRI 2027 full-paper contribution descriptions](https://humanrobotinteraction.org/2027/full-papers/):
  Design, Systems, Technical, Theory/Methods, and User Studies contribution types.
  Useful for positioning; LBR should not be described as a Systems-track submission.
- [HRI 2027 studies with human participants](https://humanrobotinteraction.org/2027/studies-with-human-participants/):
  sampling, environment, methods, consent, ethics statement, robot/autonomy details,
  and Wizard-of-Oz reporting. The institutional route and any approval/exemption
  remain unresolved for our proposed study.
- [ACM research-involving-human-participants policy](https://www.acm.org/publications/policies/research-involving-human-participants-and-subjects):
  linked by HRI; direct retrieval returned 403 in the discussion. Consult the
  current policy through the institution if needed; do not invent approval status.

Record generative-AI assistance used in research design, implementation, data,
analysis, or artifacts so the final methods disclosure accurately follows the
venue policy. Human authors remain responsible for the research and claims.

## F. Additional gaze and expressive-behavior reading

These are optional supporting references, not substitutes for A2 and A3:

- Calinon and Billard (2006), [Teaching a Humanoid Robot to Recognize and Reproduce Social Cues](https://infoscience.epfl.ch/server/api/core/bitstreams/fb384ced-40a0-4178-954c-854e74ce0932/content),
  RO-MAN: learned communicative gestures and a pointing-at-objects scenario.
- Rakovic et al., [The Gaze Dialogue Model: Nonverbal Communication in HHI and HRI](https://doi.org/10.1109/TCYB.2022.3222077);
  [author-hosted manuscript](https://vislab.isr.tecnico.ulisboa.pt/wp-content/uploads/paper_pdfs/2022_mrakovic_theg_dhri.pdf):
  gaze during collaborative activity and a humanoid gaze controller. Verify final
  publication year/metadata if selected for the bibliography.
- Khoramshahi et al. (2016), [Role of Gaze Cues in Interpersonal Motor Coordination: Towards Higher Affiliation in Human-Robot Interaction](https://doi.org/10.1371/journal.pone.0156874):
  task-relevant gaze in a human-avatar mirror game. Keep its embodiment and task
  scope explicit when using its results.
- Breazeal (2009), [Role of expressive behaviour for robots that learn from people](https://pmc.ncbi.nlm.nih.gov/articles/PMC2781892/),
  Philosophical Transactions of the Royal Society B 364(1535), pp. 3527-3538,
  [DOI 10.1098/rstb.2009.0157](https://doi.org/10.1098/rstb.2009.0157):
  connects social cues, transparency of a robot's internal process, and teaching
  as joint activity. The paper synthesizes examples from the author's research;
  follow the original experiments for specific effects. Abstract and relevant
  transparency/joint-action passages inspected. It does not establish learning
  or therapeutic benefit in PROMETHEUS.

## G. Common ground, joint activity, and human-robot coordination

### G1. Grounding as an ongoing collaborative process

**Clark, Herbert H., and Susan E. Brennan (1991). Grounding in Communication.**
In *Perspectives on Socially Shared Cognition*, pp. 127-149. APA.

- [Author-hosted chapter](https://web.stanford.edu/~clark/1990s/Clark%2C%20H.H.%20_%20Brennan%2C%20S.E.%20_Grounding%20in%20communication_%201991.pdf).
- **Use:** theoretical foundation added in the audit. People coordinate both
  content and process; grounding seeks understanding adequate for the current
  purpose, with effort shaped by the communication medium.
- **Implication for us:** acceptance should be accompanied by evidence of what
  the user understands. Clarification and repair are part of coordination, not
  automatically failures. Our explicit acceptance boundary is an engineering
  choice, not a requirement that this theory imposes on every interaction.
- **Status/limit:** introductory and concluding passages inspected. Human
  communication theory motivates the design; it does not validate this system.

### G2. Joint human-agent activity and continuing coordination

**Klein, Gary; David D. Woods; Jeffrey M. Bradshaw; Robert R. Hoffman; and Paul J.
Feltovich (2004). Ten Challenges for Making Automation a "Team Player" in Joint
Human-Agent Activity.** *IEEE Intelligent Systems* 19(6), pp. 91-95.

- [DOI 10.1109/MIS.2004.74](https://doi.org/10.1109/MIS.2004.74);
  [author-institution copy](https://www.ihmc.us/wp-content/uploads/2021/04/17.-Team-Players.pdf).
- **Use:** additional foundation for continuing commitment, mutual predictability,
  directability, and maintaining common ground. Their account includes tacit
  agreements and repair as circumstances change.
- **Implication for us:** relate navigability, revision, recovery, and behavior
  predictability to existing joint-activity requirements. A persisted contract
  alone does not fulfill those requirements.
- **Status/limit:** requirements and discussion passages inspected. This is a
  conceptual analysis, not evidence that explicit contracts outperform a baseline.

### G3. Implemented collaboration in human-robot teams

**Hoffman, Guy, and Cynthia Breazeal (2004). Collaboration in Human-Robot Teams.**
*AIAA First Intelligent Systems Technical Conference*.

- [Author-hosted paper](https://hrc2.io/assets/pdfs/papers/HoffmanBreazealAIAA04.pdf);
  [author-lab publication record](https://robots.media.mit.edu/publications-conferences/).
- **Use:** earlier discussion source with a concrete architecture for shared goals,
  hierarchical tasks, turn-taking, mutual support, and communication of task state.
  Establishes that agreed plans and adjustment during execution predate LLMs.
- **Implication for us:** compare the interaction and commitment mechanisms,
  not merely whether a system has a human in the loop or maintains task state.
- **Status/limit:** abstract and introductory architecture account inspected.
  Its collaboration demonstration is not a controlled test of PROMETHEUS's
  proposed agreement/revision management.

### G4. Apparent agreement versus grounded understanding

**Chai, Joyce Y.; Lanbo She; Rui Fang; Spencer Ottarson; Cody Littley; Changsong
Liu; and Kenneth Hanson (2014). Collaborative Effort towards Common Ground in
Situated Human-Robot Dialogue.** *HRI 2014*.

- [DOI 10.1145/2559636.2559677](https://doi.org/10.1145/2559636.2559677);
  [author-hosted paper](https://www.cse.msu.edu/~jchai/Papers/HRI2014.pdf).
- **Use:** an empirical HRI anchor from the earlier discussion. The paper studies
  mismatched perceptual representations and the robot's collaborative effort.
  Its reported findings distinguish a person's belief that common ground exists
  from actual mutual understanding; communicating the robot's representation
  matters beyond simple confirmation.
- **Implication for us:** independently compare private intentions, accepted
  specification, participant predictions, and observed behavior. Acceptance rate
  or satisfaction alone cannot answer our research question.
- **Status/limit:** the author-hosted abstract and introduction were retrieved
  through search indexing in this audit; direct PDF access remains intermittent.
  Read the full methods/results before reporting sample sizes or effect estimates.
  Object-reference grounding and our activity agreements are related but different
  constructs; this paper does not validate our custom fidelity rubric.

## H. Evaluation of common ground and human-AI collaboration

**Poelitz, Christian; Finale Doshi-Velez; and Siân Lindley (2026). A Benchmark to
Assess Common Ground in Human-AI Collaboration.** arXiv:2602.21337, submitted
24 February 2026.

- [Preprint](https://arxiv.org/abs/2602.21337);
  [authors' benchmark repository](https://github.com/microsoft/CommonGround).
- **Use:** restore this earlier discussion reference. A collaborative puzzle
  elicits iterative interaction, referential coordination, joint action, and
  repair under different situational-information conditions. The authors report
  a confirmatory human-AI study.
- **Implication for us:** draw methodological inspiration from controlled private
  information, visible actions, and repair opportunities. Our three scenarios
  need their own task-grounded rubric and independent evidence; they are not a
  replication or validated adaptation of this benchmark.
- **Status/limit:** arXiv metadata/abstract and repository description inspected;
  peer-reviewed publication status has not been established. Inspect full methods
  before borrowing procedures or numerical results. The proposed student study
  remains descriptive unless a comparator is actually implemented and evaluated.

For research infrastructure and reproducibility, also use Rajendran et al. and
RISE (B), and the participant-study reporting sources (E). For the distinction
between confirmation and understanding, use Chai et al. (G4). Do not describe a
new checklist, custom rating, or model judge as a validated instrument merely
because related work uses a similar construct.

For the later text-study preparation, add **Lewis, James R.; Brian S. Utesch; and
Deborah E. Maher (2013), [UMUX-LITE: When There's No Time for the SUS](https://doi.org/10.1145/2470654.2481287)**,
CHI 2013. The original paper proposes and evaluates a two-item perceived-usability
instrument; its abstract was inspected during the study review. Preserve original
wording, response format, and scoring when implementing it. It does not measure
agreement fidelity or validate the proposed custom coordination ratings. See
[STUDY.md](STUDY.md#7-prepost-instruments-and-understanding-assessments) for its role.

## I. Citation plan and remaining reading

Suggested compact citation backbone, to refine with the actual manuscript:

1. **Why coordination throughout an activity?** Clark and Brennan; Hoffman and
   Breazeal or Klein et al.; Chai et al.
2. **Why multimodality?** Belardinelli for the overview/ambiguity and Duarte et al.
   for a concrete empirical example of action legibility. Add other gaze papers
   only for specific claims they support.
3. **Why this contribution form and what is inherited?** Cila et al.; one close
   framework such as IrisTK or Robot Behavior Toolkit; PROMISE. Use RISE or
   Rajendran et al. when the research-infrastructure claim is developed.
4. **What distinguishes current generative interaction?** Reimann et al. for
   dialogue-management context; FurChat and/or Pekarek Rosin et al. as concrete
   comparisons. Use SayCan and the Johnson documents if the execution handoff
   receives substantive discussion.
5. **Why these measures?** Chai et al.; Poelitz et al. as explicitly preliminary
   methodological inspiration. Explain our operationalization and its limits.

For a four-page paper, select references that support a specific statement.
Avoid a long catalogue of gaze or agent architectures disconnected from the
mechanisms and evidence actually presented.

Before drafting the final bibliography, obtain canonical author order, venue,
year, pages/article number, and DOI from the cited version. The guide deliberately
uses some abbreviated author lists. Resolve the noted Duarte and Gaze Dialogue
Model metadata issues; verify the status/citability of the owner manuscripts.
Read the closest systems and empirical methods in full, record claim-to-passage
notes, and complete a focused novelty comparison. Preserve ordinary scholarly
self-citations while following the venue's anonymization rules. The repository
poster is a design artifact, not independent peer-reviewed validation.
