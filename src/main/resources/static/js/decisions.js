/**
 * Bookmark Cleaner - Decision Toggle Controller
 */
(function(window) {
  'use strict';

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

    // Lock toggle in UI
    window.BookmarkCleaner.State.addPendingDecision(itemId);
    checkboxEl.disabled = true;
    if (switchContainer) switchContainer.classList.add('pending');

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
      }

      checkboxEl.checked = response.enabled;

      const actionText = response.enabled ? 'applied' : 'reverted';
      window.BookmarkCleaner.App.showToast(`Change ${actionText} for "${currentItem.bookmarkName}"`, 'info');

    } catch (err) {
      console.error('Decision update error:', err);
      // Rollback to previous state
      checkboxEl.checked = previousState;
      currentItem.enabled = previousState;

      window.BookmarkCleaner.App.showToast(`Failed to update decision: ${err.message}`, 'error');
    } finally {
      window.BookmarkCleaner.State.removePendingDecision(itemId);
      checkboxEl.disabled = false;
      if (switchContainer) switchContainer.classList.remove('pending');
    }
  }

  window.BookmarkCleaner = window.BookmarkCleaner || {};
  window.BookmarkCleaner.Decisions = {
    toggleDecision: toggleDecision
  };

})(window);
