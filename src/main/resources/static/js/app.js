/**
 * Bookmark Cleaner - Main Application Orchestrator
 */
(function(window) {
  'use strict';

  /**
   * Switches the active view section in the main content area.
   * @param {string} viewId ('view-upload', 'view-scan', 'view-results')
   */
  function switchView(viewId) {
    const sections = document.querySelectorAll('.view-section');
    sections.forEach(sec => {
      sec.classList.remove('active');
    });

    const targetSection = document.getElementById(viewId);
    if (targetSection) {
      targetSection.classList.add('active');
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }

    // Update header controls based on view
    const sessionPill = document.getElementById('session-info-pill');
    const resetBtn = document.getElementById('btn-reset-session');
    const state = window.BookmarkCleaner.State.getState();

    if (viewId === 'view-upload') {
      if (sessionPill) sessionPill.style.display = 'none';
      if (resetBtn) resetBtn.style.display = 'none';
    } else {
      if (sessionPill) {
        sessionPill.style.display = 'flex';
        const filenameDisplay = document.getElementById('session-filename-display');
        if (filenameDisplay) filenameDisplay.textContent = state.filename || 'bookmarks.json';
      }
      if (resetBtn) resetBtn.style.display = 'inline-flex';
    }
  }

  /**
   * Displays a floating non-intrusive toast notification.
   * @param {string} message
   * @param {'success'|'error'|'info'} type
   * @param {number} durationMs
   */
  function showToast(message, type = 'info', durationMs = 4500) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    // Limit maximum active toasts
    while (container.children.length >= 4) {
      container.removeChild(container.firstChild);
    }

    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;

    const iconSpan = document.createElement('span');
    iconSpan.setAttribute('aria-hidden', 'true');
    if (type === 'success') iconSpan.textContent = '✓';
    else if (type === 'error') iconSpan.textContent = '⚠️';
    else iconSpan.textContent = 'ℹ';

    const textSpan = document.createElement('span');
    textSpan.textContent = message;

    const closeBtn = document.createElement('button');
    closeBtn.className = 'toast-close-btn';
    closeBtn.setAttribute('aria-label', 'Close notification');
    closeBtn.textContent = '✕';
    closeBtn.onclick = () => removeToast(toast);

    toast.appendChild(iconSpan);
    toast.appendChild(textSpan);
    toast.appendChild(closeBtn);

    container.appendChild(toast);

    const autoDismissTimer = setTimeout(() => {
      removeToast(toast);
    }, durationMs);

    function removeToast(el) {
      clearTimeout(autoDismissTimer);
      el.style.opacity = '0';
      el.style.transform = 'translateX(20px)';
      setTimeout(() => {
        if (el.parentNode) el.parentNode.removeChild(el);
      }, 200);
    }
  }

  /**
   * Resets the entire session back to upload state.
   */
  function resetSession() {
    window.BookmarkCleaner.Scan.stopPolling();
    window.BookmarkCleaner.Upload.reset();
    window.BookmarkCleaner.Export.reset();
    window.BookmarkCleaner.State.reset();

    switchView('view-upload');
    showToast('Session reset. Ready for a new bookmark file.', 'info');
  }

  function initApp() {
    // 1. Initialize sub-controllers
    window.BookmarkCleaner.Upload.init();
    window.BookmarkCleaner.Scan.init();
    window.BookmarkCleaner.Results.init();
    window.BookmarkCleaner.Export.init();

    // 2. Register global reset button
    const resetBtn = document.getElementById('btn-reset-session');
    if (resetBtn) {
      resetBtn.addEventListener('click', resetSession);
    }

    // 3. State change listener for session header sync
    window.BookmarkCleaner.State.subscribe(state => {
      const filenameDisplay = document.getElementById('session-filename-display');
      if (filenameDisplay && state.filename) {
        filenameDisplay.textContent = state.filename;
      }
    });

    console.log('Bookmark Cleaner frontend initialized.');
  }

  window.BookmarkCleaner = window.BookmarkCleaner || {};
  window.BookmarkCleaner.App = {
    init: initApp,
    switchView: switchView,
    showToast: showToast,
    resetSession: resetSession
  };

  document.addEventListener('DOMContentLoaded', initApp);

})(window);
