/**
 * Bookmark Cleaner - Upload & Browser Discovery Controller
 */
(function(window) {
  'use strict';

  let selectedFile = null;

  // Local inline SVGs for supported browsers
  const BROWSER_ICONS = {
    chrome: `<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <circle cx="12" cy="12" r="10" stroke="#4285F4" stroke-width="2"/>
      <circle cx="12" cy="12" r="4" fill="#4285F4"/>
      <path d="M12 2C15.5 2 18.5 4 20.3 7L12 12" stroke="#EA4335" stroke-width="2"/>
      <path d="M20.3 7C21.4 8.5 22 10.2 22 12C22 15.5 20 18.5 17 20.3L12 12" stroke="#FBBC05" stroke-width="2"/>
      <path d="M17 20.3C15.5 21.4 13.8 22 12 22C7.6 22 4 18.4 4 14C4 12.2 4.6 10.5 5.7 9L12 12" stroke="#34A853" stroke-width="2"/>
    </svg>`,
    edge: `<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M12 2C6.48 2 2 6.48 2 12C2 17.52 6.48 22 12 22C17.52 22 22 17.52 22 12" stroke="#0078D7" stroke-width="2"/>
      <path d="M2 12C2 16.5 5.5 20 10 20C14.5 20 18 16.5 18 12C18 7.5 14 4 10 4C7 4 4.5 5.5 3 8" stroke="#00A4EF" stroke-width="2"/>
      <path d="M7 13C7 10 9.5 8 12.5 8C15.5 8 18 10 18 13C18 16 15.5 18 12.5 18C9.5 18 7 16 7 13Z" fill="#0078D7"/>
    </svg>`,
    brave: `<svg viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
      <path d="M12 2L4 5V11C4 16.5 7.4 21.6 12 23C16.6 21.6 20 16.5 20 11V5L12 2Z" fill="#FF4724" stroke="#FF4724" stroke-width="1.5" stroke-linejoin="round"/>
      <path d="M9 10L12 7L15 10M12 7V16" stroke="#FFFFFF" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
    </svg>`
  };

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function initUpload() {
    // 1. Initialize Browser Discovery
    initBrowserDiscovery();

    // 2. Initialize Manual File Upload
    initManualUpload();
  }

  /**
   * Initializes browser discovery component.
   */
  function initBrowserDiscovery() {
    const retryBtn = document.getElementById('btn-retry-discovery');
    if (retryBtn) {
      retryBtn.addEventListener('click', () => {
        loadDiscoveredBrowsers();
      });
    }

    loadDiscoveredBrowsers();
  }

  /**
   * Fetches detected browsers and profiles from the discovery API and updates UI.
   */
  async function loadDiscoveredBrowsers() {
    const loadingEl = document.getElementById('browser-discovery-loading');
    const errorEl = document.getElementById('browser-discovery-error');
    const errorMsgEl = document.getElementById('browser-discovery-error-msg');
    const emptyEl = document.getElementById('browser-discovery-empty');
    const gridEl = document.getElementById('browser-cards-grid');

    if (!loadingEl || !gridEl) return;

    // Set initial loading state
    loadingEl.style.display = 'flex';
    if (errorEl) errorEl.style.display = 'none';
    if (emptyEl) emptyEl.style.display = 'none';
    gridEl.style.display = 'none';
    gridEl.innerHTML = '';

    try {
      const data = await window.BookmarkCleaner.Api.discoverBrowsers();
      loadingEl.style.display = 'none';

      if (!data || !data.browsers || data.browsers.length === 0) {
        if (emptyEl) emptyEl.style.display = 'flex';
        return;
      }

      // Check if any supported browser was found on this machine
      const anyDetected = data.browsers.some(b => b.available);
      if (!anyDetected) {
        if (emptyEl) emptyEl.style.display = 'flex';
      }

      // Render cards for Chrome, Edge, and Brave
      data.browsers.forEach(browser => {
        const card = createBrowserCard(browser);
        gridEl.appendChild(card);
      });

      gridEl.style.display = 'grid';

    } catch (err) {
      console.warn('Browser discovery API failed:', err);
      loadingEl.style.display = 'none';
      if (errorEl) {
        if (errorMsgEl) {
          errorMsgEl.textContent = `Could not detect browsers: ${err.message || 'Service unavailable'}. You can still upload a file manually below.`;
        }
        errorEl.style.display = 'flex';
      }
    }
  }

  /**
   * Creates a browser card DOM element for a discovered or supported browser.
   */
  function createBrowserCard(browser) {
    const card = document.createElement('div');
    card.className = `browser-card ${browser.available ? 'detected' : 'not-detected'}`;

    const iconSvg = BROWSER_ICONS[browser.id] || `<span style="font-size:20px;">🌐</span>`;

    const statusPillHtml = browser.available
      ? `<span class="browser-status-pill status-detected">● Detected</span>`
      : `<span class="browser-status-pill status-not-detected">Not Detected</span>`;

    let bodyHtml = '';

    if (!browser.available) {
      bodyHtml = `<div class="browser-not-detected-msg">Not detected on this device</div>`;
    } else if (!browser.profiles || browser.profiles.length === 0) {
      bodyHtml = `<div class="browser-no-bookmarks-msg">Browser detected, but no readable bookmark profile was found.</div>`;
    } else {
      let profilesHtml = '';
      browser.profiles.forEach(profile => {
        profilesHtml += `
          <div class="profile-item">
            <div class="profile-info">
              <span class="profile-icon" aria-hidden="true">👤</span>
              <span class="profile-name" title="${escapeHtml(profile.name)}">${escapeHtml(profile.name)}</span>
            </div>
            <button type="button" class="btn btn-primary btn-xs btn-import-profile"
                    data-browser-id="${escapeHtml(browser.id)}"
                    data-profile-id="${escapeHtml(profile.id)}"
                    data-profile-name="${escapeHtml(profile.name)}">
              Import Bookmarks
            </button>
          </div>
        `;
      });

      bodyHtml = `<div class="profile-list">${profilesHtml}</div>`;
    }

    card.innerHTML = `
      <div class="browser-card-header">
        <div class="browser-icon-container" aria-hidden="true">
          ${iconSvg}
        </div>
        <div>
          <h4 class="browser-card-title">${escapeHtml(browser.name)}</h4>
        </div>
        ${statusPillHtml}
      </div>
      <div class="browser-card-body">
        ${bodyHtml}
      </div>
    `;

    // Attach click listeners to import buttons inside this card
    const importBtns = card.querySelectorAll('.btn-import-profile');
    importBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        const browserId = btn.getAttribute('data-browser-id');
        const profileId = btn.getAttribute('data-profile-id');
        const profileName = btn.getAttribute('data-profile-name');
        handleDirectImport(browserId, profileId, profileName, btn);
      });
    });

    return card;
  }

  /**
   * Handles direct import submission from a chosen browser and profile.
   */
  async function handleDirectImport(browserId, profileId, profileName, clickedBtn) {
    const allImportBtns = document.querySelectorAll('.btn-import-profile');
    allImportBtns.forEach(b => b.disabled = true);

    const originalContent = clickedBtn.innerHTML;
    clickedBtn.innerHTML = `<span class="btn-spinner" aria-hidden="true"></span> Importing...`;

    try {
      const uploadResult = await window.BookmarkCleaner.Api.importBrowserProfile(browserId, profileId);

      window.BookmarkCleaner.State.setState({
        scanId: uploadResult.scanId,
        filename: uploadResult.filename,
        totalBookmarks: uploadResult.totalBookmarks,
        scanStatus: 'PENDING'
      });

      window.BookmarkCleaner.App.showToast(`Imported ${uploadResult.totalBookmarks} bookmarks from ${uploadResult.filename}`, 'success');

      // Automatically transition to scan view and start scan
      window.BookmarkCleaner.App.switchView('view-scan');
      window.BookmarkCleaner.Scan.startAnalysis(uploadResult.scanId);

    } catch (err) {
      console.error('Direct browser import failed:', err);
      allImportBtns.forEach(b => b.disabled = false);
      clickedBtn.innerHTML = originalContent;
      window.BookmarkCleaner.App.showToast(`Import failed: ${err.message}`, 'error');
    }
  }

  /**
   * Initializes the manual drag & drop and file selection workflow.
   */
  function initManualUpload() {
    const dropzone = document.getElementById('upload-dropzone');
    const fileInput = document.getElementById('file-input');
    const browseBtn = document.getElementById('btn-browse-file');
    const fileCard = document.getElementById('selected-file-card');
    const fileNameEl = document.getElementById('selected-file-name');
    const fileMetaEl = document.getElementById('selected-file-meta');
    const startUploadBtn = document.getElementById('btn-start-upload');

    if (!dropzone || !fileInput) return;

    // Trigger file dialog
    browseBtn.addEventListener('click', (e) => {
      e.stopPropagation();
      fileInput.click();
    });

    dropzone.addEventListener('click', () => {
      fileInput.click();
    });

    // Keyboard support for dropzone
    dropzone.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        fileInput.click();
      }
    });

    // File input change
    fileInput.addEventListener('change', (e) => {
      if (e.target.files && e.target.files.length > 0) {
        handleFileSelection(e.target.files[0]);
      }
    });

    // Drag and drop event handlers
    ['dragenter', 'dragover'].forEach(eventName => {
      dropzone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropzone.classList.add('drag-over');
      });
    });

    ['dragleave', 'drop'].forEach(eventName => {
      dropzone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropzone.classList.remove('drag-over');
      });
    });

    dropzone.addEventListener('drop', (e) => {
      const dt = e.dataTransfer;
      if (dt && dt.files && dt.files.length > 0) {
        handleFileSelection(dt.files[0]);
      }
    });

    // Handle File Selection
    function handleFileSelection(file) {
      if (!file) return;

      // Basic client-side check
      if (!file.name.toLowerCase().endsWith('.json') && file.type !== 'application/json') {
        window.BookmarkCleaner.App.showToast('Please select a valid Chromium bookmark JSON file (.json)', 'error');
        return;
      }

      if (file.size === 0) {
        window.BookmarkCleaner.App.showToast('Selected file is empty (0 bytes)', 'error');
        return;
      }

      selectedFile = file;
      fileNameEl.textContent = file.name;
      const sizeKb = (file.size / 1024).toFixed(1);
      fileMetaEl.textContent = `Size: ${sizeKb} KB`;

      fileCard.style.display = 'flex';
      startUploadBtn.disabled = false;
      startUploadBtn.textContent = 'Upload & Analyze';
    }

    // Start Upload and Analysis
    startUploadBtn.addEventListener('click', async () => {
      if (!selectedFile) {
        window.BookmarkCleaner.App.showToast('Please select a bookmark file first', 'error');
        return;
      }

      startUploadBtn.disabled = true;
      startUploadBtn.textContent = 'Uploading...';

      try {
        const uploadResult = await window.BookmarkCleaner.Api.uploadBookmarks(selectedFile);

        window.BookmarkCleaner.State.setState({
          scanId: uploadResult.scanId,
          filename: uploadResult.filename,
          totalBookmarks: uploadResult.totalBookmarks,
          scanStatus: 'PENDING'
        });

        window.BookmarkCleaner.App.showToast(uploadResult.message || 'File uploaded successfully', 'success');

        // Automatically transition to scan view and start scan
        window.BookmarkCleaner.App.switchView('view-scan');
        window.BookmarkCleaner.Scan.startAnalysis(uploadResult.scanId);

      } catch (err) {
        console.error('Upload failed:', err);
        startUploadBtn.disabled = false;
        startUploadBtn.textContent = 'Upload & Analyze';
        window.BookmarkCleaner.App.showToast(`Upload failed: ${err.message}`, 'error');
      }
    });
  }

  function resetUpload() {
    selectedFile = null;
    const fileInput = document.getElementById('file-input');
    const fileCard = document.getElementById('selected-file-card');
    const startUploadBtn = document.getElementById('btn-start-upload');

    if (fileInput) fileInput.value = '';
    if (fileCard) fileCard.style.display = 'none';
    if (startUploadBtn) {
      startUploadBtn.disabled = false;
      startUploadBtn.textContent = 'Upload & Analyze';
    }

    // Reload browser discovery cards
    loadDiscoveredBrowsers();
  }

  window.BookmarkCleaner = window.BookmarkCleaner || {};
  window.BookmarkCleaner.Upload = {
    init: initUpload,
    reset: resetUpload,
    loadBrowsers: loadDiscoveredBrowsers
  };

})(window);
