/**
 * Bookmark Cleaner - Scan Orchestration & Polling Controller
 */
(function(window) {
  'use strict';

  let pollTimer = null;
  let isPollingActive = false;

  const POLLING_INTERVAL_MS = 1500;

  function initScan() {
    // UI elements initialized when needed
  }

  /**
   * Initiates the analysis scan on the backend and starts polling.
   * @param {string} scanId
   */
  async function startAnalysis(scanId) {
    stopPolling();

    const statusBadge = document.getElementById('scan-status-badge');
    const statusDesc = document.getElementById('scan-status-desc');
    const progressBarFill = document.getElementById('progress-bar-fill');
    const progressCount = document.getElementById('scan-progress-count');
    const progressPercent = document.getElementById('scan-progress-percent');
    const errorBox = document.getElementById('scan-error-box');

    if (errorBox) errorBox.style.display = 'none';
    if (statusBadge) {
      statusBadge.className = 'scan-status-pill running';
      statusBadge.textContent = 'RUNNING';
    }
    if (statusDesc) {
      statusDesc.textContent = 'Validating URLs, inspecting redirects, and grouping duplicate bookmarks...';
    }
    if (progressBarFill) progressBarFill.style.width = '0%';
    if (progressPercent) progressPercent.textContent = '0%';

    try {
      const response = await window.BookmarkCleaner.Api.startScan(scanId);
      updateScanUI(response);

      if (response.status === 'COMPLETED') {
        onScanComplete(scanId);
      } else if (response.status === 'FAILED') {
        onScanFailed(response.errorMessage || 'Scan failed to complete');
      } else {
        // Asynchronous processing: start polling
        startPolling(scanId);
      }
    } catch (err) {
      console.error('Failed to start scan:', err);
      onScanFailed(err.message || 'Failed to start scan');
    }
  }

  /**
   * Polls scan status until terminal state.
   * @param {string} scanId
   */
  function startPolling(scanId) {
    if (pollTimer) return;

    pollTimer = setInterval(async () => {
      if (isPollingActive) return; // Prevent overlapping requests
      isPollingActive = true;

      try {
        const status = await window.BookmarkCleaner.Api.getScanStatus(scanId);
        updateScanUI(status);

        if (status.status === 'COMPLETED') {
          stopPolling();
          onScanComplete(scanId);
        } else if (status.status === 'FAILED') {
          stopPolling();
          onScanFailed(status.errorMessage || 'Scan failed during analysis');
        }
      } catch (err) {
        console.error('Polling error:', err);
        // Do not immediately fail on a transient network glitch, let it retry next interval
      } finally {
        isPollingActive = false;
      }
    }, POLLING_INTERVAL_MS);
  }

  function stopPolling() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
    isPollingActive = false;
  }

  function updateScanUI(statusData) {
    const progressBarFill = document.getElementById('progress-bar-fill');
    const progressCount = document.getElementById('scan-progress-count');
    const progressPercent = document.getElementById('scan-progress-percent');

    const percent = Math.min(100, Math.max(0, statusData.progressPercentage || 0));
    const processed = statusData.processedBookmarks || 0;
    const total = statusData.totalBookmarks || 0;

    if (progressBarFill) progressBarFill.style.width = `${percent}%`;
    if (progressPercent) progressPercent.textContent = `${percent}%`;
    if (progressCount) progressCount.textContent = `Processed ${processed} of ${total} bookmarks`;

    window.BookmarkCleaner.State.setState({
      scanProgress: percent,
      processedBookmarks: processed,
      totalBookmarks: total,
      scanStatus: statusData.status
    });
  }

  function onScanComplete(scanId) {
    const statusBadge = document.getElementById('scan-status-badge');
    const progressBarFill = document.getElementById('progress-bar-fill');
    const progressPercent = document.getElementById('scan-progress-percent');

    if (statusBadge) {
      statusBadge.className = 'scan-status-pill completed';
      statusBadge.textContent = 'COMPLETED';
    }
    if (progressBarFill) progressBarFill.style.width = '100%';
    if (progressPercent) progressPercent.textContent = '100%';

    window.BookmarkCleaner.App.showToast('Bookmark analysis completed successfully!', 'success');

    // Switch to results dashboard after a brief moment
    setTimeout(() => {
      window.BookmarkCleaner.App.switchView('view-results');
      window.BookmarkCleaner.Results.loadResults();
    }, 400);
  }

  function onScanFailed(errorMessage) {
    stopPolling();

    const statusBadge = document.getElementById('scan-status-badge');
    const errorBox = document.getElementById('scan-error-box');
    const errorMsgEl = document.getElementById('scan-error-message');

    if (statusBadge) {
      statusBadge.className = 'scan-status-pill failed';
      statusBadge.textContent = 'FAILED';
    }
    if (errorBox) errorBox.style.display = 'flex';
    if (errorMsgEl) errorMsgEl.textContent = errorMessage;

    window.BookmarkCleaner.State.setState({
      scanStatus: 'FAILED',
      errorMessage: errorMessage
    });

    window.BookmarkCleaner.App.showToast(`Analysis failed: ${errorMessage}`, 'error');
  }

  window.BookmarkCleaner = window.BookmarkCleaner || {};
  window.BookmarkCleaner.Scan = {
    init: initScan,
    startAnalysis: startAnalysis,
    stopPolling: stopPolling
  };

})(window);
