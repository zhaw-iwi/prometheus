// Display-only identities. Full IDs remain request/selection values; names are never HTML.
globalThis.PrometheusInstanceLabels = Object.freeze({
  optionLabel(label) {
    const split = label.lastIndexOf(' · '), name = label.slice(0,split), suffix = label.slice(split);
    const budget = Math.max(8,38-suffix.length);
    let width = 0, short = '';
    for (const character of name) {
      width += character.codePointAt(0) > 255 ? 2 : 1;
      if (width > budget) return short + '…' + suffix;
      short += character;
    }
    return label;
  },
  labels(instances) {
    const unique = new Map();
    for (const item of instances || []) {
      if (typeof item?.id !== 'string' || !item.id.trim()) continue;
      unique.set(item.id, typeof item.name === 'string' && item.name.trim() ? item.name : 'Agent');
    }
    const ids = [...unique.keys()];
    return new Map(ids.map(id => {
      let length = Math.min(8, id.length);
      while (length < id.length && ids.some(other => other !== id && other.slice(0, length) === id.slice(0, length))) length++;
      return [id, `${unique.get(id)} · ${id.slice(0, length)}`];
    }));
  }
});
