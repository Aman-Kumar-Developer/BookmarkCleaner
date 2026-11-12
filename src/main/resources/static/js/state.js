/**
 * Bookmark Cleaner - In-Memory Frontend Application State
 */
(function(window) {
  'use strict';

  const STORAGE_KEY_SCAN_ID = 'bookmark_cleaner_active_scan_id';
  let savedScanId = null;
  try {
    savedScanId = localStorage.getItem(STORAGE_KEY_SCAN_ID);
  } catch (_) {}

  const initialState = {
    // Current Scan Session
    scanId: savedScanId || null,
    filename: null,
    totalBookmarks: 0,
    scanStatus: 'IDLE', // 'IDLE', 'PENDING', 'RUNNING', 'COMPLETED', 'FAILED'
    scanProgress: 0,
    processedBookmarks: 0,
    errorMessage: null,

    // Filter, Search & Pagination
    priorityFilter: 'ALL', // 'ALL', 'HIGH', 'MEDIUM', 'LOW'
    searchQuery: '',
    currentPage: 0,
    pageSize: 50,
    totalPages: 1,
    totalItems: 0,

    // Results & Summaries
    items: [],
    summary: null,

    // Export State
    exportReady: false,
    exportData: null,

    // Browser Profile Provenance
    importedFromBrowser: false,
    browserId: null,
    browserName: null,
    profileId: null,
    profileName: null,
    changesAppliedCount: 0,

    // Pending Decision Network Updates (Set of itemIds)
    pendingDecisions: new Set()
  };

  let state = { ...initialState, pendingDecisions: new Set() };
  const listeners = [];

  const StateManager = {
    /**
     * Retrieves current state snapshot.
     */
    getState() {
      return state;
    },

    /**
     * Updates state with a partial update and notifies listeners.
     * @param {Object} partialState
     */
    setState(partialState) {
      state = { ...state, ...partialState };
      if (partialState && 'scanId' in partialState) {
        try {
          if (partialState.scanId) {
            localStorage.setItem(STORAGE_KEY_SCAN_ID, partialState.scanId);
          } else {
            localStorage.removeItem(STORAGE_KEY_SCAN_ID);
          }
        } catch (_) {}
      }
      this.notify();
    },

    /**
     * Subscribes a callback to state changes.
     * @param {Function} listener
     * @returns {Function} unsubscribe function
     */
    subscribe(listener) {
      if (typeof listener === 'function') {
        listeners.push(listener);
      }
      return () => {
        const index = listeners.indexOf(listener);
        if (index > -1) {
          listeners.splice(index, 1);
        }
      };
    },

    /**
     * Notifies all registered listeners of a state change.
     */
    notify() {
      for (const listener of listeners) {
        try {
          listener(state);
        } catch (err) {
          console.error('[StateManager] Listener error:', err);
        }
      }
    },

    /**
     * Resets application state back to initial.
     */
    reset() {
      try {
        localStorage.removeItem(STORAGE_KEY_SCAN_ID);
      } catch (_) {}
      state = { ...initialState, scanId: null, pendingDecisions: new Set() };
      this.notify();
    },

    /**
     * Marks an item decision as pending network update.
     */
    addPendingDecision(itemId) {
      state.pendingDecisions.add(itemId);
      this.notify();
    },

    /**
     * Removes an item decision from pending set.
     */
    removePendingDecision(itemId) {
      state.pendingDecisions.delete(itemId);
      this.notify();
    },

    /**
     * Checks if an item decision is currently pending.
     */
    isDecisionPending(itemId) {
      return state.pendingDecisions.has(itemId);
    }
  };

  window.BookmarkCleaner = window.BookmarkCleaner || {};
  window.BookmarkCleaner.State = StateManager;

})(window);
