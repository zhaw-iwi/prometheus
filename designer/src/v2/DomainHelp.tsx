export function DomainHelp() {
  return <details className="domain-help" data-testid="domain-help">
    <summary>How the Designer concepts fit together</summary>
    <p className="domain-help-intro">Use this guide when deciding where an idea belongs. Opening it does not change the draft.</p>
    <div className="domain-help-grid">
      <section>
        <h3>Situation or context?</h3>
        <p>A <strong>situation</strong> is a durable interaction phase that changes how later events are handled. <strong>Starting context</strong> is information available when the interaction begins; it is not a phase.</p>
      </section>
      <section>
        <h3>Ordinary response or rule?</h3>
        <p>The <strong>ordinary response</strong> handles routine events in a situation. Add an <strong>interaction rule</strong> for a named trigger, optional conditions, ordered effects, or a different continuation.</p>
      </section>
      <section>
        <h3>Stay, move, or finish?</h3>
        <p><strong>Stay</strong> keeps the current situation. <strong>Move</strong> enters another durable situation. <strong>Finish</strong> ends the interaction and can produce its outcome report.</p>
      </section>
      <section>
        <h3>Where does guidance apply?</h3>
        <p><strong>Agent-wide guidance</strong> applies throughout the agent. <strong>Situation guidance</strong> adds local instructions while that situation is active. Guidance is ordered and does not create a rule by itself.</p>
      </section>
      <section>
        <h3>Which data role?</h3>
        <p><strong>Starting context</strong> exists at the beginning, <strong>working data</strong> supports registered operations, <strong>learned information</strong> is recorded during interaction, and an <strong>outcome report</strong> is returned to the caller.</p>
      </section>
      <section>
        <h3>What can prompt guidance guarantee?</h3>
        <p>Prompt guidance can shape model behavior, but it cannot guarantee safety, access control, privacy, or clinical correctness. Use registered deterministic behavior and application controls where enforcement is required, and review outputs with appropriate human oversight.</p>
      </section>
    </div>
  </details>;
}
