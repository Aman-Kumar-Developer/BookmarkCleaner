/**
 * Bookmark Cleaner - Export & Download Controller
 */
(function(window) {
  'use strict';

  function initExport() {
    const generateBtn = document.getElementById('btn-generate-export');
    const downloadBtn = document.getElementById('btn-download-export');
    const instructionsBox = document.getElementById('export-instructions');

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

        window.BookmarkCleaner.State.setState({
          exportReady: true,
          exportData: exportResult
        });

        generateBtn.disabled = false;
        generateBtn.textContent = 'Regenerate Cleaned Bookmarks';

        downloadBtn.style.display = 'inline-flex';
        downloadBtn.dataset.downloadUrl = exportResult.downloadUrl || window.BookmarkCleaner.Api.getDownloadUrl(scanId);

        if (instructionsBox) {
          instructionsBox.classList.add('active');
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

      // Standard non-intrusive browser download anchor
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);

      window.BookmarkCleaner.App.showToast(`Downloading ${filename}`, 'info');
    });
  }

  function resetExport() {
    const generateBtn = document.getElementById('btn-generate-export');
    const downloadBtn = document.getElementById('btn-download-export');
    const instructionsBox = document.getElementById('export-instructions');

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
  }

  window.BookmarkCleaner = window.BookmarkCleaner || {};
  window.BookmarkCleaner.Export = {
    init: initExport,
    reset: resetExport
  };

})(window);
