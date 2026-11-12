/**
 * Bookmark Cleaner - Decision Toggle Controller
 */
(function(window) {
  'use strict';

  /**
   * Updates visible decision label and expanded status indicator.
   * @param {string} itemId
   * @param {boolean} enabled
   */
  function updateDecisionUi(itemId, enabled) {
    const labelEl = document.getElementById(`decision-label-${itemId}`);
    if (labelEl) {
      labelEl.textContent = enabled ? 'Apply' : 'Keep Original';
      labelEl.className = `decision-label ${enabled ? 'applied' : 'kept'}`;
    }
    const detailsStatusEl = document.getElementById(`details-decision-status-${itemId}`);
    if (detailsStatusEl) {
      detailsStatusEl.innerHTML = `Current choice: <strong class="${enabled ? 'text-primary' : 'text-muted'}">${enabled ? 'Apply Proposed Change' : 'Keep Original Bookmark'}</strong>`;
    }
  }

  /**
   * Dispatches a decision update to the backend with locking and rollback.
   * @param {string} itemId
   * @param {boolean} newEnabledState
   * @param {HTMLInputElement} checkboxEl
   */
  async function toggleDecision(itemId, newEnabledState, checkboxEl) {
    const state = window.BookmarkCleaner.State.getState();
    const scanId = state.scanId;

    if (!scanId || !itemId) return;

    // Prevent duplicate updates while this item is pending
    if (window.BookmarkCleaner.State.isDecisionPending(itemId)) {
      return;
    }

    // Find the item in state
    const currentItem = state.items.find(i => i.itemId === itemId);
    if (!currentItem) return;

    // Guard against non-actionable items
    if (!currentItem.applicable) {
      window.BookmarkCleaner.App.showToast('This item is not actionable and cannot be modified', 'info');
      checkboxEl.checked = false;
      return;
    }

    const previousState = currentItem.enabled;
    const switchContainer = checkboxEl.closest('.switch');

    // Optimistically update label and lock toggle in UI
    window.BookmarkCleaner.State.addPendingDecision(itemId);
    checkboxEl.disabled = true;
    if (switchContainer) switchContainer.classList.add('pending');
    updateDecisionUi(itemId, newEnabledState);

    try {
      const response = await window.BookmarkCleaner.Api.updateDecision(scanId, itemId, newEnabledState);

      // Successfully updated
      currentItem.enabled = response.enabled;

      // Update summary counts locally
      if (state.summary) {
        const delta = response.enabled ? 1 : -1;
        state.summary.actionsAppliedCount = Math.max(0, state.summary.actionsAppliedCount + delta);
        const statAppliedEl = document.getElementById('stat-applied');
        if (statAppliedEl) {
          statAppliedEl.textContent = state.summary.actionsAppliedCount;
        }
        window.BookmarkCleaner.State.setState({ changesAppliedCount: state.summary.actionsAppliedCount });
        if (window.BookmarkCleaner.Export && window.BookmarkCleaner.Export.updateProvenanceUI) {
          window.BookmarkCleaner.Export.updateProvenanceUI();
        }
      }

      checkboxEl.checked = response.enabled;
      updateDecisionUi(itemId, response.enabled);

      const actionText = response.enabled ? 'applied' : 'reverted';
      window.BookmarkCleaner.App.showToast(`Change ${actionText} for "${currentItem.bookmarkName}"`, 'info');

    } catch (err) {
      console.error('Decision update error:', err);
      // Rollback to previous state
      checkboxEl.checked = previousState;
      currentItem.enabled = previousState;
      updateDecisionUi(itemId, previousState);

      window.BookmarkCleaner.App.showToast(`Failed to update decision: ${err.message}`, 'error');
    } finally {
      window.BookmarkCleaner.State.removePendingDecision(itemId);
      checkboxEl.disabled = false;
      if (switchContainer) switchContainer.classList.remove('pending');
    }
  }

  window.BookmarkCleaner = window.BookmarkCleaner || {};
  window.BookmarkCleaner.Decisions = {
    toggleDecision: toggleDecision,
    updateDecisionUi: updateDecisionUi
  };

})(window);
