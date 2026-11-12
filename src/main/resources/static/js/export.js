/**
 * Bookmark Cleaner - Export & Direct Browser Replacement Controller
 * Manages decoupled asynchronous replacement, live browser closure monitoring,
 * and 2-generation versioned restore points.
 */
(function(window) {
  'use strict';

  let currentExportData = null;
  let targetRestoreBackupId = null;
  let cachedBackupsData = null;
  let pollTimer = null;
  let isPolling = false;

  function formatFileSize(bytes) {
    if (!bytes || bytes <= 0) return '0 B';
    if (bytes < 1024) return bytes + ' B';
    if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return (bytes / (1024 * 1024)).toFixed(2) + ' MB';
  }

  function formatDate(timestamp) {
    if (!timestamp || timestamp <= 0) return 'Unknown';
    try {
      return new Date(timestamp).toLocaleString();
    } catch (_) {
      return 'Unknown';
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  // Modals management (Strictly mutually exclusive)
  function openModal(modal) {
    if (!modal) return;
    // Close other modal first to ensure mutually exclusive visibility
    const replaceModal = document.getElementById('modal-replace-confirmation');
    const restoreModal = document.getElementById('modal-restore-confirmation');
    if (modal !== replaceModal) closeModal(replaceModal);
    if (modal !== restoreModal) closeModal(restoreModal);

    modal.style.display = 'flex';
    modal.classList.add('active');
    modal.setAttribute('aria-hidden', 'false');
  }

  function closeModal(modal) {
    if (!modal) return;
    modal.style.display = 'none';
    modal.classList.remove('active');
    modal.setAttribute('aria-hidden', 'true');
  }

  function closeAllModals() {
    closeModal(document.getElementById('modal-replace-confirmation'));
    closeModal(document.getElementById('modal-restore-confirmation'));
  }

  /**
   * Synchronizes browser and profile provenance across the UI.
   */
  function updateProvenanceUI() {
    const state = window.BookmarkCleaner.State.getState();
    const isBrowserImport = Boolean(state.importedFromBrowser || (state.exportData && state.exportData.importedFromBrowser));
    const browserName = state.browserName || (state.exportData && state.exportData.browserName) || '';
    const profileName = state.profileName || (state.exportData && state.exportData.profileName) || '';

    const replaceCard = document.getElementById('browser-replacement-card');
    const versionsCard = document.getElementById('previous-versions-card');

    if (isBrowserImport && browserName) {
      if (replaceCard) {
        replaceCard.style.display = 'flex';
        const badgeEl = document.getElementById('provenance-browser-badge');
        const profileEl = document.getElementById('provenance-profile-name');
        if (badgeEl) badgeEl.textContent = browserName;
        if (profileEl) profileEl.textContent = profileName || 'Default';
      }

      if (versionsCard) {
        versionsCard.style.display = 'block';
        const vBadgeEl = document.getElementById('versions-browser-badge');
        const vProfileEl = document.getElementById('versions-profile-name');
        if (vBadgeEl) vBadgeEl.textContent = browserName;
        if (vProfileEl) vProfileEl.textContent = profileName || 'Default';
      }
    } else {
      if (replaceCard) replaceCard.style.display = 'none';
      if (versionsCard) versionsCard.style.display = 'none';
    }
  }

  function initExport() {
    const generateBtn = document.getElementById('btn-generate-export');
    const downloadBtn = document.getElementById('btn-download-export');
    const replaceBtn = document.getElementById('btn-replace-browser');
    const cancelOpBtn = document.getElementById('btn-cancel-operation');
    const refreshVersionsBtn = document.getElementById('btn-refresh-versions');
    const instructionsBox = document.getElementById('export-instructions');

    // Modals
    const replaceModal = document.getElementById('modal-replace-confirmation');
    const replaceModalClose = document.getElementById('btn-modal-replace-close');
    const replaceModalCancel = document.getElementById('btn-modal-replace-cancel');
    const replaceModalConfirm = document.getElementById('btn-modal-replace-confirm');

    const restoreModal = document.getElementById('modal-restore-confirmation');
    const restoreModalClose = document.getElementById('btn-modal-restore-close');
    const restoreModalCancel = document.getElementById('btn-modal-restore-cancel');
    const restoreModalConfirm = document.getElementById('btn-modal-restore-confirm');

    if (!generateBtn || !downloadBtn) return;

    // 1. Generate Cleaned Export
    generateBtn.addEventListener('click', async () => {
      const state = window.BookmarkCleaner.State.getState();
      const scanId = state.scanId;

      if (!scanId) {
        window.BookmarkCleaner.App.showToast('No active scan session found to export', 'error');
        return;
      }

      generateBtn.disabled = true;
      generateBtn.textContent = 'Generating Cleaned JSON...';

      try {
        const exportResult = await window.BookmarkCleaner.Api.generateExport(scanId);
        currentExportData = exportResult;

        const stateUpdate = {
          exportReady: true,
          exportData: exportResult,
          changesAppliedCount: exportResult.changesAppliedCount
        };

        if (exportResult.importedFromBrowser) {
          stateUpdate.importedFromBrowser = true;
          if (exportResult.browserName) stateUpdate.browserName = exportResult.browserName;
          if (exportResult.profileName) stateUpdate.profileName = exportResult.profileName;
          if (exportResult.browserId) stateUpdate.browserId = exportResult.browserId;
          if (exportResult.profileId) stateUpdate.profileId = exportResult.profileId;
        }

        window.BookmarkCleaner.State.setState(stateUpdate);

        generateBtn.disabled = false;
        generateBtn.textContent = 'Regenerate Cleaned Bookmarks';

        downloadBtn.style.display = 'inline-flex';
        downloadBtn.dataset.downloadUrl = exportResult.downloadUrl || window.BookmarkCleaner.Api.getDownloadUrl(scanId);

        if (instructionsBox) {
          instructionsBox.classList.add('active');
        }

        updateProvenanceUI();

        if (exportResult.importedFromBrowser) {
          loadPreviousVersions(scanId);
          startStatusPolling(scanId);
        }

        const msg = `Export generated: ${exportResult.changesAppliedCount} changes applied across ${exportResult.totalBookmarksExported} bookmarks.`;
        window.BookmarkCleaner.App.showToast(msg, 'success');

      } catch (err) {
        console.error('Export generation failed:', err);
        generateBtn.disabled = false;
        generateBtn.textContent = 'Generate Cleaned Bookmarks';
        window.BookmarkCleaner.App.showToast(`Export failed: ${err.message}`, 'error');
      }
    });

    // 2. Download Cleaned File
    downloadBtn.addEventListener('click', () => {
      const state = window.BookmarkCleaner.State.getState();
      const scanId = state.scanId;
      if (!scanId) return;

      const url = downloadBtn.dataset.downloadUrl || window.BookmarkCleaner.Api.getDownloadUrl(scanId);
      const filename = (state.exportData && state.exportData.exportFilename) || 'bookmarks-cleaned.json';

      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

      window.BookmarkCleaner.App.showToast(`Downloading ${filename}`, 'info');
    });

    // 3. Open Direct Browser Replacement Modal
    if (replaceBtn && replaceModal) {
      replaceBtn.addEventListener('click', () => {
        const state = window.BookmarkCleaner.State.getState();
        const isBrowser = Boolean(state.importedFromBrowser || (state.exportData && state.exportData.importedFromBrowser));

        if (!isBrowser) {
          window.BookmarkCleaner.App.showToast('Direct replacement requires bookmarks imported from a browser profile.', 'warning');
          return;
        }

        const browserName = state.browserName || (state.exportData && state.exportData.browserName) || 'Browser';
        const profileName = state.profileName || (state.exportData && state.exportData.profileName) || 'Default';
        const totalBookmarks = state.exportData ? state.exportData.totalBookmarksExported : (state.totalBookmarks || 0);
        const appliedChanges = state.exportData
          ? state.exportData.changesAppliedCount
          : (state.changesAppliedCount || (state.summary && state.summary.actionsAppliedCount) || 0);

        const browserEl = document.getElementById('modal-replace-browser-name');
        const profileEl = document.getElementById('modal-replace-profile-name');
        const countEl = document.getElementById('modal-replace-count');
        const changesEl = document.getElementById('modal-replace-changes');
        const alertBrowserEl = document.getElementById('modal-replace-alert-browser-name');

        if (browserEl) browserEl.textContent = browserName;
        if (profileEl) profileEl.textContent = profileName;
        if (countEl) countEl.textContent = `${totalBookmarks} bookmarks`;
        if (changesEl) changesEl.textContent = `${appliedChanges} changes applied`;
        if (alertBrowserEl) alertBrowserEl.textContent = browserName;

        openModal(replaceModal);
      });
    }

    // Modal Close / Cancel listeners
    if (replaceModalClose) replaceModalClose.addEventListener('click', () => closeModal(replaceModal));
    if (replaceModalCancel) replaceModalCancel.addEventListener('click', () => closeModal(replaceModal));
    if (restoreModalClose) restoreModalClose.addEventListener('click', () => closeModal(restoreModal));
    if (restoreModalCancel) restoreModalCancel.addEventListener('click', () => closeModal(restoreModal));

    // Global backdrop click and Escape key dismissal for modals
    [replaceModal, restoreModal].forEach(modal => {
      if (modal) {
        modal.addEventListener('click', (e) => {
          if (e.target === modal) {
            closeModal(modal);
          }
        });
      }
    });

    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') {
        closeAllModals();
      }
    });

    // 4. Confirm Direct Replacement (Initiates Asynchronous Staged Workflow)
    if (replaceModalConfirm) {
      replaceModalConfirm.addEventListener('click', async () => {
        const state = window.BookmarkCleaner.State.getState();
        const scanId = state.scanId;
        if (!scanId) return;

        replaceModalConfirm.disabled = true;
        replaceModalConfirm.textContent = 'Scheduling Replacement...';

        try {
          const result = await window.BookmarkCleaner.Api.installBookmarks(scanId);
          closeModal(replaceModal);

          replaceModalConfirm.disabled = false;
          replaceModalConfirm.textContent = 'Confirm & Schedule Replacement';

          renderOperationStatus(result);

          if (result.state === 'WAITING_FOR_BROWSER_CLOSURE') {
            window.BookmarkCleaner.App.showToast('Replacement scheduled. Please close your browser now.', 'info', 6000);
            startStatusPolling(scanId);
          } else if (result.state === 'COMPLETED') {
            window.BookmarkCleaner.App.showToast(result.message || 'Bookmarks installed successfully!', 'success');
            loadPreviousVersions(scanId);
          }

        } catch (err) {
          console.error('Bookmark installation failed:', err);
          replaceModalConfirm.disabled = false;
          replaceModalConfirm.textContent = 'Confirm & Schedule Replacement';
          window.BookmarkCleaner.App.showToast(`Replacement failed: ${err.message}`, 'error');
        }
      });
    }

    // 5. Cancel Pending Operation
    if (cancelOpBtn) {
      cancelOpBtn.addEventListener('click', async () => {
        const state = window.BookmarkCleaner.State.getState();
        const scanId = state.scanId;
        if (!scanId) return;

        cancelOpBtn.disabled = true;
        try {
          await window.BookmarkCleaner.Api.cancelInstall(scanId);
          stopStatusPolling();
          hideOperationStatus();
          window.BookmarkCleaner.App.showToast('Installation operation cancelled.', 'info');
        } catch (err) {
          window.BookmarkCleaner.App.showToast(`Cancel failed: ${err.message}`, 'error');
        } finally {
          cancelOpBtn.disabled = false;
        }
      });
    }

    // 6. Refresh Previous Versions
    if (refreshVersionsBtn) {
      refreshVersionsBtn.addEventListener('click', () => {
        const state = window.BookmarkCleaner.State.getState();
        if (state.scanId) {
          loadPreviousVersions(state.scanId);
          window.BookmarkCleaner.App.showToast('Backups refreshed.', 'info');
        }
      });
    }

    // Initial static buttons if present
    const staticRestoreV1Btn = document.getElementById('btn-restore-version-1');
    const staticRestoreV2Btn = document.getElementById('btn-restore-version-2');
    if (staticRestoreV1Btn) {
      staticRestoreV1Btn.addEventListener('click', () => {
        openRestoreModal('1', cachedBackupsData ? cachedBackupsData.backup1 : null);
      });
    }
    if (staticRestoreV2Btn) {
      staticRestoreV2Btn.addEventListener('click', () => {
        openRestoreModal('2', cachedBackupsData ? cachedBackupsData.backup2 : null);
      });
    }

    // 7. Confirm Restore Click
    if (restoreModalConfirm) {
      restoreModalConfirm.addEventListener('click', async () => {
        const state = window.BookmarkCleaner.State.getState();
        const scanId = state.scanId;
        if (!scanId || !targetRestoreBackupId) return;

        restoreModalConfirm.disabled = true;
        restoreModalConfirm.textContent = 'Restoring Backup...';

        try {
          const result = await window.BookmarkCleaner.Api.restoreBackup(scanId, targetRestoreBackupId);
          closeModal(restoreModal);

          restoreModalConfirm.disabled = false;
          restoreModalConfirm.textContent = 'Confirm & Restore';

          if (result.state === 'WAITING_FOR_BROWSER_CLOSURE') {
            renderOperationStatus(result);
            startStatusPolling(scanId);
            window.BookmarkCleaner.App.showToast('Restore scheduled. Please close your browser now.', 'info', 6000);
          } else {
            window.BookmarkCleaner.App.showToast(result.message || 'Backup restored successfully!', 'success');
            loadPreviousVersions(scanId);
          }

        } catch (err) {
          console.error('Backup restore failed:', err);
          restoreModalConfirm.disabled = false;
          restoreModalConfirm.textContent = 'Confirm & Restore';
          window.BookmarkCleaner.App.showToast(`Restore failed: ${err.message}`, 'error');
        }
      });
    }
  }

  /**
   * Opens the restore confirmation modal populated with the specific backup metadata.
   */
  function openRestoreModal(versionId, backupInfo) {
    const modal = document.getElementById('modal-restore-confirmation');
    const state = window.BookmarkCleaner.State.getState();

    const browserName = state.browserName || (state.exportData && state.exportData.browserName) || 'Browser';
    const profileName = state.profileName || (state.exportData && state.exportData.profileName) || 'Default';

    const versionTitle = versionId === '1'
      ? 'Previous version 1 (Most Recent)'
      : 'Previous version 2 (Older Backup)';
    const filename = versionId === '1' ? 'Bookmarks.old.1' : 'Bookmarks.old.2';

    const browserEl = document.getElementById('modal-restore-browser-name');
    const profileEl = document.getElementById('modal-restore-profile-name');
    const titleEl = document.getElementById('modal-restore-version-title');
    const fileEl = document.getElementById('modal-restore-filename');
    const statsEl = document.getElementById('modal-restore-stats');
    const validEl = document.getElementById('modal-restore-validation');
    const alertBrowserEl = document.getElementById('modal-restore-alert-browser-name');
    const confirmBtn = document.getElementById('btn-modal-restore-confirm');

    if (browserEl) browserEl.textContent = browserName;
    if (profileEl) profileEl.textContent = profileName;
    if (titleEl) titleEl.textContent = versionTitle;
    if (fileEl) fileEl.textContent = filename;
    if (alertBrowserEl) alertBrowserEl.textContent = browserName;

    if (statsEl) {
      if (backupInfo) {
        statsEl.textContent = `${backupInfo.bookmarkCount} bookmarks • ${formatFileSize(backupInfo.fileSize)} • Modified: ${formatDate(backupInfo.lastModified)}`;
      } else {
        statsEl.textContent = 'Backup file details pending';
      }
    }

    const isValid = Boolean(backupInfo && backupInfo.valid);
    if (validEl) {
      if (isValid) {
        validEl.innerHTML = `<span class="version-status-pill status-valid">● Valid Chromium JSON</span>`;
      } else {
        validEl.innerHTML = `<span class="version-status-pill status-invalid">⚠️ Invalid or Corrupted JSON</span>`;
      }
    }

    if (confirmBtn) {
      confirmBtn.disabled = !isValid;
    }

    targetRestoreBackupId = versionId;
    openModal(modal);
  }

  // Operation Status Panel Rendering
  function renderOperationStatus(op) {
    const panel = document.getElementById('installation-status-panel');
    const iconEl = document.getElementById('status-panel-icon');
    const headingEl = document.getElementById('status-panel-heading');
    const textEl = document.getElementById('status-panel-text');
    const actionsEl = document.getElementById('status-panel-actions');

    if (!panel || !op || !op.state) {
      hideOperationStatus();
      return;
    }

    panel.style.display = 'flex';
    panel.className = 'operation-status-panel';

    if (op.state === 'WAITING_FOR_BROWSER_CLOSURE') {
      panel.classList.add('waiting');
      if (iconEl) iconEl.innerHTML = '<span class="pulse-indicator"></span> ⏳';
      if (headingEl) headingEl.textContent = 'Waiting for Browser to Close';
      if (textEl) textEl.textContent = op.message || 'Close the browser completely. Bookmark Cleaner will finish automatically.';
      if (actionsEl) actionsEl.style.display = 'flex';
    } else if (op.state === 'INSTALLING' || op.state === 'VERIFYING') {
      panel.classList.add('installing');
      if (iconEl) iconEl.textContent = '🔄';
      if (headingEl) headingEl.textContent = 'Installing Bookmarks...';
      if (textEl) textEl.textContent = 'Browser closure detected. Rotating backups and replacing bookmarks...';
      if (actionsEl) actionsEl.style.display = 'none';
    } else if (op.state === 'COMPLETED') {
      panel.classList.add('completed');
      if (iconEl) iconEl.textContent = '✅';
      if (headingEl) headingEl.textContent = 'Cleaned Bookmarks Installed Successfully!';
      if (textEl) textEl.textContent = op.message || 'You may now reopen your browser.';
      if (actionsEl) actionsEl.style.display = 'none';
    } else if (op.state === 'FAILED') {
      panel.classList.add('failed');
      if (iconEl) iconEl.textContent = '❌';
      if (headingEl) headingEl.textContent = 'Installation Failed';
      if (textEl) textEl.textContent = op.errorMessage || op.message || 'An error occurred during replacement.';
      if (actionsEl) actionsEl.style.display = 'none';
    } else {
      panel.style.display = 'none';
    }
  }

  function hideOperationStatus() {
    const panel = document.getElementById('installation-status-panel');
    if (panel) panel.style.display = 'none';
  }

  function startStatusPolling(scanId) {
    stopStatusPolling();

    async function poll() {
      if (isPolling) return;
      isPolling = true;

      try {
        const op = await window.BookmarkCleaner.Api.getInstallStatus(scanId);
        if (op && op.state) {
          renderOperationStatus(op);

          if (op.state === 'COMPLETED') {
            stopStatusPolling();
            loadPreviousVersions(scanId);
            window.BookmarkCleaner.App.showToast(op.message || 'Bookmarks installed successfully!', 'success');
          } else if (op.state === 'FAILED' || op.state === 'CANCELLED') {
            stopStatusPolling();
          }
        } else {
          stopStatusPolling();
        }
      } catch (err) {
        console.warn('Status poll encountered an error:', err.message);
      } finally {
        isPolling = false;
      }
    }

    poll();
    pollTimer = setInterval(poll, 2000);
  }

  function stopStatusPolling() {
    if (pollTimer) {
      clearInterval(pollTimer);
      pollTimer = null;
    }
    isPolling = false;
  }

  /**
   * Renders the real two-generation version history grid.
   */
  function renderVersionHistory(data) {
    const container = document.getElementById('versions-container');
    if (!container) return;

    container.innerHTML = '';

    const hasV1 = Boolean(data.backup1 && data.backup1.exists);
    const hasV2 = Boolean(data.backup2 && data.backup2.exists);

    // Case 1: Neither backup exists
    if (!hasV1 && !hasV2) {
      const emptyDiv = document.createElement('div');
      emptyDiv.className = 'versions-empty-state';
      emptyDiv.innerHTML = `
        <div style="font-size: 1.5rem; margin-bottom: var(--space-2);">🛡️</div>
        <strong>No previous versions available yet</strong>
        <p style="margin-top: 4px;">Up to two versioned backups (<code>Bookmarks.old.1</code> and <code>Bookmarks.old.2</code>) will be created automatically before any replacement or restoration.</p>
      `;
      container.appendChild(emptyDiv);
      return;
    }

    // Helper to build a version card element
    function createVersionCard(versionNum, backupInfo, descText, isSlotEmpty) {
      const card = document.createElement('div');
      const filename = `Bookmarks.old.${versionNum}`;
      const versionTitle = versionNum === '1' ? 'Previous Version 1 (Most Recent)' : 'Previous Version 2 (Older Backup)';

      if (isSlotEmpty || !backupInfo || !backupInfo.exists) {
        card.className = 'version-card empty-slot';
        card.innerHTML = `
          <div class="version-card-header">
            <div>
              <div class="version-label">${escapeHtml(versionTitle)}</div>
              <p class="version-card-desc">${escapeHtml(descText)}</p>
            </div>
            <span class="version-file-code">${escapeHtml(filename)}</span>
          </div>
          <p class="version-empty-text">${escapeHtml(descText)}</p>
          <div class="version-card-actions">
            <button id="btn-restore-version-${versionNum}" class="btn btn-secondary btn-sm" disabled>↩ Restore Version ${versionNum}</button>
          </div>
        `;
        return card;
      }

      card.className = 'version-card';
      const isValid = Boolean(backupInfo.valid);
      const statusBadge = isValid
        ? `<span class="version-status-pill status-valid">● Valid</span>`
        : `<span class="version-status-pill status-invalid">⚠️ Invalid JSON</span>`;

      card.innerHTML = `
        <div class="version-card-header">
          <div>
            <div class="version-label">${escapeHtml(versionTitle)}</div>
            <p class="version-card-desc">${escapeHtml(descText)}</p>
          </div>
          <span class="version-file-code">${escapeHtml(filename)}</span>
        </div>
        <div class="version-meta">
          <div><strong>${backupInfo.bookmarkCount} bookmarks</strong> &bull; ${formatFileSize(backupInfo.fileSize)}</div>
          <div>Modified: ${formatDate(backupInfo.lastModified)}</div>
          <div style="margin-top: 4px;">${statusBadge}</div>
        </div>
        <div class="version-card-actions">
          <button type="button" id="btn-restore-version-${versionNum}" class="btn btn-secondary btn-sm btn-restore-action"
                  data-backup-id="${versionNum}"
                  ${isValid ? '' : 'disabled title="Cannot restore an invalid bookmark file"'}>
            ↩ Restore Version ${versionNum}
          </button>
        </div>
      `;

      const restoreBtn = card.querySelector('.btn-restore-action');
      if (restoreBtn && isValid) {
        restoreBtn.addEventListener('click', () => {
          openRestoreModal(versionNum, backupInfo);
        });
      }

      return card;
    }

    // Version 1
    if (hasV1) {
      container.appendChild(createVersionCard('1', data.backup1, 'Most recent backup', false));
    } else {
      container.appendChild(createVersionCard('1', null, 'No most-recent backup found.', true));
    }

    // Version 2
    if (hasV2) {
      container.appendChild(createVersionCard('2', data.backup2, 'Older backup generation', false));
    } else {
      container.appendChild(createVersionCard('2', null, 'No second backup created yet. A second generation will be preserved on next replacement or restore.', true));
    }
  }

  /**
   * Fetches backup management metadata from backend and renders active status and version grid.
   */
  async function loadPreviousVersions(scanId) {
    if (!scanId) return;

    const activeStatsEl = document.getElementById('active-version-stats');
    const activeStatusBadge = document.getElementById('active-browser-status');

    try {
      const data = await window.BookmarkCleaner.Api.getBackups(scanId);
      cachedBackupsData = data;

      // Update provenance state if provided by backups response
      if (data.importedFromBrowser) {
        const stateUpdate = { importedFromBrowser: true };
        if (data.browserName) stateUpdate.browserName = data.browserName;
        if (data.profileName) stateUpdate.profileName = data.profileName;
        if (data.browserId) stateUpdate.browserId = data.browserId;
        if (data.profileId) stateUpdate.profileId = data.profileId;
        window.BookmarkCleaner.State.setState(stateUpdate);
      }

      updateProvenanceUI();

      // Active file stats
      if (activeStatsEl) {
        const activeCount = data.activeBookmarkCount || 0;
        const activeSize = formatFileSize(data.activeFileSize);
        const activeMod = formatDate(data.activeLastModified);
        activeStatsEl.textContent = `${activeCount} bookmarks • ${activeSize} • Modified: ${activeMod}`;
      }

      if (activeStatusBadge) {
        if (data.browserRunning) {
          activeStatusBadge.textContent = 'Browser Running';
          activeStatusBadge.className = 'provenance-badge alert-warning';
        } else {
          activeStatusBadge.textContent = 'Browser Closed';
          activeStatusBadge.className = 'provenance-badge';
        }
      }

      // Render dynamic 2-generation version history
      renderVersionHistory(data);

      // Check if an operation was returned
      if (data.activeOperation) {
        renderOperationStatus(data.activeOperation);
        if (data.activeOperation.state === 'WAITING_FOR_BROWSER_CLOSURE') {
          startStatusPolling(scanId);
        }
      }

    } catch (err) {
      console.warn('Could not load previous versions:', err);
    }
  }

  function resetExport() {
    stopStatusPolling();

    const generateBtn = document.getElementById('btn-generate-export');
    const downloadBtn = document.getElementById('btn-download-export');
    const instructionsBox = document.getElementById('export-instructions');
    const replaceCard = document.getElementById('browser-replacement-card');
    const versionsCard = document.getElementById('previous-versions-card');

    currentExportData = null;
    targetRestoreBackupId = null;
    cachedBackupsData = null;

    if (generateBtn) {
      generateBtn.disabled = false;
      generateBtn.textContent = 'Generate Cleaned Bookmarks';
    }
    if (downloadBtn) {
      downloadBtn.style.display = 'none';
      delete downloadBtn.dataset.downloadUrl;
    }
    if (instructionsBox) {
      instructionsBox.classList.remove('active');
    }
    if (replaceCard) {
      replaceCard.style.display = 'none';
    }
    if (versionsCard) {
      versionsCard.style.display = 'none';
    }

    hideOperationStatus();
    closeAllModals();
  }

  window.BookmarkCleaner = window.BookmarkCleaner || {};
  window.BookmarkCleaner.Export = {
    init: initExport,
    reset: resetExport,
    updateProvenanceUI: updateProvenanceUI,
    loadPreviousVersions: loadPreviousVersions,
    startStatusPolling: startStatusPolling,
    stopStatusPolling: stopStatusPolling
  };

})(window);