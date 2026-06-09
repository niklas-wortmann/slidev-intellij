// Slidev decks embed Vue template syntax in their inline HTML (`<span @click="...">`,
// `:class="..."`, `#slot`). Those are not valid DOM attribute names, so the markdown
// preview's incremental-DOM renderer throws InvalidCharacterError from `setAttribute`
// and aborts the entire preview update. Tolerate such names by dropping just the
// offending attribute; the static preview cannot execute Vue directives anyway.
(function () {
  if (window.__slidevVueAttributesPatchInstalled) {
    return;
  }
  window.__slidevVueAttributesPatchInstalled = true;

  function tolerant(original) {
    return function () {
      try {
        return original.apply(this, arguments);
      } catch (error) {
        if (error instanceof DOMException && error.name === "InvalidCharacterError") {
          return; // Vue directive shorthand — skip the attribute, keep rendering.
        }
        throw error;
      }
    };
  }

  Element.prototype.setAttribute = tolerant(Element.prototype.setAttribute);
  Element.prototype.setAttributeNS = tolerant(Element.prototype.setAttributeNS);
})();
