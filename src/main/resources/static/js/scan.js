/**
 * Bookmark Cleaner - Scan Orchestration & Polling Controller
 */
(function(window) {
  'use strict';

  let pollTimer = null;
  let isPollingActive = false;
  let currentScanId = null;
  let consecutiveErrors = 0;

  const POLLING_INTERVAL_MS = 800;

  function initScan() {
    // UI elements initialized when needed
  }

  /**
   * Initiates the analysis scan on the backend and starts polling.
   * @param {string} scanId
   */
  async function startAnalysis(scanId) {
    stopPolling();
    currentScanId = scanId;
    consecutiveErrors = 0;

    const statusBadge = document.getElementById('scan-status-badge');
    const statusDesc = document.getElementById('scan-status-desc');
    const progressBarFill = document.getElementById('progress-bar-fill');
    const progressBarTrack = document.getElementById('progress-bar-track');
    const progressCount = document.getElementById('scan-progress-count');
    const progressRemaining = document.getElementById('scan-progress-remaining');
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
    if (progressBarTrack) progressBarTrack.setAttribute('aria-valuenow', '0');
    if (progressPercent) progressPercent.textContent = '0%';

    const state = window.BookmarkCleaner.State.getState();
    const initialTotal = state.totalBookmarks || 0;
    if (progressCount) {
      progressCount.textContent = `Processed 0 of ${initialTotal} bookmarks`;
    }
    if (progressRemaining) {
      progressRemaining.textContent = `Remaining: ${initialTotal}`;
    }

    try {
      const response = await window.BookmarkCleaner.Api.startScan(scanId);

      // Guard against stale response if user cancelled or switched scans
      if (currentScanId !== scanId) return;

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
      if (currentScanId !== scanId) return;
      console.error('Failed to start scan:', err);
      onScanFailed(err.message || 'Failed to start scan');
    }
  }

  /**
   * Polls scan status until terminal state.
   * @param {string} scanId
   */
  function startPolling(scanId) {
    stopPolling();
    currentScanId = scanId;
    consecutiveErrors = 0;

    pollTimer = setInterval(async () => {
      if (isPollingActive) return; // Prevent overlapping requests
      if (currentScanId !== scanId) {
        stopPolling();
        return;
      }

      isPollingActive = true;

      try {
        const status = await window.BookmarkCleaner.Api.getScanStatus(scanId);

        // Discard stale responses if scan changed
        if (currentScanId !== scanId) return;

        consecutiveErrors = 0;
        updateScanUI(status);

        if (status.status === 'COMPLETED') {
          stopPolling();
          onScanComplete(scanId);
        } else if (status.status === 'FAILED') {
          stopPolling();
          onScanFailed(status.errorMessage || 'Scan failed during analysis');
        } else if (status.status === 'CANCELLED') {
          stopPolling();
          onScanFailed('Scan was cancelled');
        }
      } catch (err) {
        console.warn('Polling attempt encountered an error:', err);
        consecutiveErrors++;
        // If consecutive errors persist, notify user rather than failing on transient hiccup
        if (consecutiveErrors >= 5) {
          stopPolling();
          onScanFailed('Connection lost while monitoring scan progress');
        }
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
    if (!statusData) return;

    const progressBarFill = document.getElementById('progress-bar-fill');
    const progressBarTrack = document.getElementById('progress-bar-track');
    const progressCount = document.getElementById('scan-progress-count');
    const progressRemaining = document.getElementById('scan-progress-remaining');
    const progressPercent = document.getElementById('scan-progress-percent');
    const statusBadge = document.getElementById('scan-status-badge');
    const statusDesc = document.getElementById('scan-status-desc');

    const total = statusData.totalBookmarks || 0;
    const processed = (statusData.completedBookmarks !== undefined && statusData.completedBookmarks !== null)
      ? statusData.completedBookmarks
      : (statusData.processedBookmarks || 0);

    const pending = (statusData.pendingBookmarks !== undefined && statusData.pendingBookmarks !== null)
      ? statusData.pendingBookmarks
      : Math.max(0, total - processed);

    let percent = Math.min(100, Math.max(0, statusData.progressPercentage || 0));

    // Never show 100% while scan is still RUNNING
    if (statusData.status === 'RUNNING' && percent >= 100) {
      percent = 99;
    }

    if (progressBarFill) {
      progressBarFill.style.width = `${percent}%`;
    }
    if (progressBarTrack) {
      progressBarTrack.setAttribute('aria-valuenow', String(percent));
    }
    if (progressPercent) {
      progressPercent.textContent = `${percent}%`;
    }
    if (progressCount) {
      progressCount.textContent = `Processed ${processed} of ${total} bookmarks`;
    }
    if (progressRemaining) {
      progressRemaining.textContent = `Remaining: ${pending}`;
    }

    if (statusBadge && statusData.status) {
      statusBadge.className = `scan-status-pill ${statusData.status.toLowerCase()}`;
      statusBadge.textContent = statusData.status;
    }

    if (statusDesc) {
      if (statusData.status === 'RUNNING') {
        if (total > 0 && processed >= total) {
          statusDesc.textContent = 'Aggregating results and preparing comparison items...';
        } else {
          statusDesc.textContent = 'Validating URLs, inspecting redirects, and grouping duplicate bookmarks...';
        }
      } else if (statusData.status === 'COMPLETED') {
        statusDesc.textContent = 'Analysis complete!';
      }
    }

    const stateUpdate = {
      scanProgress: percent,
      processedBookmarks: processed,
      totalBookmarks: total,
      scanStatus: statusData.status
    };

    if (statusData.importedFromBrowser) {
      stateUpdate.importedFromBrowser = true;
      if (statusData.browserId) stateUpdate.browserId = statusData.browserId;
      if (statusData.browserName) stateUpdate.browserName = statusData.browserName;
      if (statusData.profileId) stateUpdate.profileId = statusData.profileId;
      if (statusData.profileName) stateUpdate.profileName = statusData.profileName;
    }

    window.BookmarkCleaner.State.setState(stateUpdate);
  }

  function onScanComplete(scanId) {
    const statusBadge = document.getElementById('scan-status-badge');
    const statusDesc = document.getElementById('scan-status-desc');
    const progressBarFill = document.getElementById('progress-bar-fill');
    const progressBarTrack = document.getElementById('progress-bar-track');
    const progressPercent = document.getElementById('scan-progress-percent');
    const progressRemaining = document.getElementById('scan-progress-remaining');

    if (statusBadge) {
      statusBadge.className = 'scan-status-pill completed';
      statusBadge.textContent = 'COMPLETED';
    }
    if (statusDesc) {
      statusDesc.textContent = 'Analysis complete!';
    }
    if (progressBarFill) progressBarFill.style.width = '100%';
    if (progressBarTrack) progressBarTrack.setAttribute('aria-valuenow', '100');
    if (progressPercent) progressPercent.textContent = '100%';
    if (progressRemaining) progressRemaining.textContent = 'Remaining: 0';

    window.BookmarkCleaner.App.showToast('Bookmark analysis completed successfully!', 'success');

    // Switch to results dashboard after a brief moment
    setTimeout(() => {
      window.BookmarkCleaner.App.switchView('view-results');
      window.BookmarkCleaner.Results.loadResults();
    }, 500);
  }

  function onScanFailed(errorMessage) {
    stopPolling();

    const statusBadge = document.getElementById('scan-status-badge');
    const statusDesc = document.getElementById('scan-status-desc');
    const errorBox = document.getElementById('scan-error-box');
    const errorMsgEl = document.getElementById('scan-error-message');

    if (statusBadge) {
      statusBadge.className = 'scan-status-pill failed';
      statusBadge.textContent = 'FAILED';
    }
    if (statusDesc) {
      statusDesc.textContent = 'Analysis encountered an error and could not complete.';
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
    startPolling: startPolling,
    stopPolling: stopPolling,
    updateScanUI: updateScanUI
  };

})(window);
