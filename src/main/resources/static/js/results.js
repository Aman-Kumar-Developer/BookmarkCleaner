/**
 * Bookmark Cleaner - Results Dashboard & Comparison Table Controller
 */
(function(window) {
  'use strict';

  let searchDebounceTimer = null;

  function initResults() {
    // 1. Priority Filter Clicks
    const filterButtons = document.querySelectorAll('.filter-btn');
    filterButtons.forEach(btn => {
      btn.addEventListener('click', () => {
        const priority = btn.dataset.priority;
        if (!priority) return;

        filterButtons.forEach(b => {
          b.classList.remove('active');
          b.setAttribute('aria-selected', 'false');
        });
        btn.classList.add('active');
        btn.setAttribute('aria-selected', 'true');

        window.BookmarkCleaner.State.setState({
          priorityFilter: priority,
          currentPage: 0
        });

        loadResults();
      });
    });

    // 2. Search Input with 300ms Debounce
    const searchInput = document.getElementById('search-input');
    const searchClearBtn = document.getElementById('search-clear-btn');

    if (searchInput) {
      searchInput.addEventListener('input', (e) => {
        const query = e.target.value;
        if (searchClearBtn) {
          searchClearBtn.style.display = query ? 'block' : 'none';
        }

        clearTimeout(searchDebounceTimer);
        searchDebounceTimer = setTimeout(() => {
          window.BookmarkCleaner.State.setState({
            searchQuery: query,
            currentPage: 0
          });
          loadResults();
        }, 300);
      });
    }

    if (searchClearBtn) {
      searchClearBtn.addEventListener('click', () => {
        if (searchInput) searchInput.value = '';
        searchClearBtn.style.display = 'none';
        window.BookmarkCleaner.State.setState({
          searchQuery: '',
          currentPage: 0
        });
        loadResults();
      });
    }

    // 3. Pagination Controls
    const prevBtn = document.getElementById('btn-prev-page');
    const nextBtn = document.getElementById('btn-next-page');
    const pageSizeSelect = document.getElementById('page-size-select');

    if (prevBtn) {
      prevBtn.addEventListener('click', () => {
        const state = window.BookmarkCleaner.State.getState();
        if (state.currentPage > 0) {
          window.BookmarkCleaner.State.setState({ currentPage: state.currentPage - 1 });
          loadResults();
        }
      });
    }

    if (nextBtn) {
      nextBtn.addEventListener('click', () => {
        const state = window.BookmarkCleaner.State.getState();
        if (state.currentPage < state.totalPages - 1) {
          window.BookmarkCleaner.State.setState({ currentPage: state.currentPage + 1 });
          loadResults();
        }
      });
    }

    if (pageSizeSelect) {
      pageSizeSelect.addEventListener('change', (e) => {
        const size = parseInt(e.target.value, 10) || 50;
        window.BookmarkCleaner.State.setState({
          pageSize: size,
          currentPage: 0
        });
        loadResults();
      });
    }
  }

  /**
   * Fetches comparison results and summary from backend and renders UI.
   */
  async function loadResults() {
    const state = window.BookmarkCleaner.State.getState();
    const scanId = state.scanId;

    if (!scanId) return;

    try {
      const response = await window.BookmarkCleaner.Api.getResults(
        scanId,
        state.priorityFilter,
        state.searchQuery,
        state.currentPage,
        state.pageSize
      );

      window.BookmarkCleaner.State.setState({
        items: response.items || [],
        summary: response.summary,
        totalItems: response.totalItems,
        totalPages: Math.max(1, response.totalPages),
        currentPage: response.page
      });

      renderSummary(response.summary);
      renderTable(response.items || []);
      renderPagination(response);

    } catch (err) {
      console.error('Failed to load results:', err);
      window.BookmarkCleaner.App.showToast(`Failed to load comparison results: ${err.message}`, 'error');
    }
  }

  /**
   * Updates summary cards and priority filter badges.
   */
  function renderSummary(summary) {
    if (!summary) return;

    // Stat card values
    setElementText('stat-total', summary.total);
    setElementText('stat-high', summary.highCount);
    setElementText('stat-medium', summary.mediumCount);
    setElementText('stat-low', summary.lowCount);
    setElementText('stat-broken', summary.brokenCount);
    setElementText('stat-redirected', summary.redirectedCount);
    setElementText('stat-duplicate', summary.duplicateCount);
    setElementText('stat-applied', summary.actionsAppliedCount);

    // Filter pill badge counts
    setElementText('badge-all-count', summary.total);
    setElementText('badge-high-count', summary.highCount);
    setElementText('badge-medium-count', summary.mediumCount);
    setElementText('badge-low-count', summary.lowCount);
  }

  /**
   * Formats a concise, human-readable summary for the collapsed row view.
   * Matches the actual analysis classification without verbose URL dumps.
   */
  function getCompactSummary(item) {
    if (item.outcome === 'BROKEN') {
      const desc = item.changeDescription || '';
      if (desc.includes('404')) {
        return 'Destination returned a confirmed 404 response.';
      }
      if (desc.includes('410')) {
        return 'Destination permanently removed (HTTP 410).';
      }
      if (desc.includes('500') || desc.includes('502') || desc.includes('503')) {
        return 'Destination returned a server error.';
      }
      return 'Destination confirmed broken or unreachable.';
    }

    if (item.outcome === 'REDIRECTED') {
      if (item.redirectType === 'CROSS_DOMAIN') {
        return 'Redirects to a different website.';
      }
      return 'Redirects to a new URL on the same website.';
    }

    if (item.duplicateType === 'EXACT') {
      return 'Another bookmark points to the same destination.';
    }

    if (item.duplicateType === 'POTENTIAL') {
      return 'Similar destination detected. Review before removing.';
    }

    if (item.proposedAction === 'REMOVE_BOOKMARK') {
      return 'Bookmark recommended for removal.';
    }

    if (item.proposedAction === 'UPDATE_URL') {
      return 'Updated target URL available.';
    }

    return 'Working as expected. No change needed.';
  }

  /**
   * Formats a clean, readable shortened URL for the compact row.
   */
  function formatShortUrl(url) {
    if (!url || typeof url !== 'string') return '';
    try {
      const parsed = new URL(url);
      let path = parsed.pathname;
      if (path === '/' && !parsed.search) {
        return parsed.hostname;
      }
      let display = parsed.hostname + (path.length > 25 ? path.substring(0, 24) + '…' : path);
      if (parsed.search && display.length < 35) {
        display += parsed.search.length > 10 ? parsed.search.substring(0, 9) + '…' : parsed.search;
      }
      return display;
    } catch (_) {
      return url.length > 40 ? url.substring(0, 39) + '…' : url;
    }
  }

  /**
   * Renders the comparison table using clean DOM construction.
   * Generates compact summary rows with independent expandable detail cards.
   */
  function renderTable(items) {
    const tbody = document.getElementById('comparison-table-body');
    const emptyState = document.getElementById('table-empty-state');

    if (!tbody) return;

    // Clear existing rows
    while (tbody.firstChild) {
      tbody.removeChild(tbody.firstChild);
    }

    if (!items || items.length === 0) {
      if (emptyState) emptyState.style.display = 'block';
      return;
    }

    if (emptyState) emptyState.style.display = 'none';

    items.forEach(item => {
      const fragment = createTableRowGroup(item);
      tbody.appendChild(fragment);
    });
  }

  /**
   * Creates a pair of rows: compact summary row + expandable detail row.
   * @param {Object} item ComparisonItemDto
   * @returns {DocumentFragment}
   */
  function createTableRowGroup(item) {
    const fragment = document.createDocumentFragment();

    // ─── 1. COMPACT SUMMARY ROW ──────────────────────────────────────────────
    const trMain = document.createElement('tr');
    trMain.className = 'bookmark-row';
    trMain.dataset.itemId = item.itemId;

    // A. Expand Toggle Column
    const tdExpand = document.createElement('td');
    tdExpand.className = 'col-expand';
    const btnExpand = document.createElement('button');
    btnExpand.type = 'button';
    btnExpand.className = 'btn-row-expand';
    btnExpand.setAttribute('aria-expanded', 'false');
    btnExpand.setAttribute('aria-controls', `details-${item.itemId}`);
    btnExpand.setAttribute('aria-label', `Toggle full details for ${item.bookmarkName || 'bookmark'}`);
    btnExpand.title = 'Click to show/hide full technical details';
    btnExpand.innerHTML = '<span class="expand-chevron" aria-hidden="true">▸</span>';
    tdExpand.appendChild(btnExpand);
    trMain.appendChild(tdExpand);

    // B. Priority Column
    const tdPriority = document.createElement('td');
    tdPriority.className = 'col-priority';
    const priorityBadge = document.createElement('span');
    const priorityClass = (item.priority || 'LOW').toLowerCase();
    priorityBadge.className = `badge-priority ${priorityClass}`;
    priorityBadge.textContent = item.priority || 'LOW';
    tdPriority.appendChild(priorityBadge);
    trMain.appendChild(tdPriority);

    // C. Bookmark & Folder Column
    const tdBookmark = document.createElement('td');
    tdBookmark.className = 'col-bookmark';
    const titleEl = document.createElement('div');
    titleEl.className = 'bookmark-cell-title';
    titleEl.textContent = item.bookmarkName || 'Untitled Bookmark';
    titleEl.title = item.bookmarkName || '';

    const pathEl = document.createElement('div');
    pathEl.className = 'bookmark-cell-path';
    pathEl.textContent = item.folderPath ? `📁 ${item.folderPath}` : '📁 Bookmarks bar';
    pathEl.title = item.folderPath || '';

    tdBookmark.appendChild(titleEl);
    tdBookmark.appendChild(pathEl);
    trMain.appendChild(tdBookmark);

    // D. Original URL Column (Shortened with preview link)
    const tdUrl = document.createElement('td');
    tdUrl.className = 'col-url';
    const shortUrlEl = document.createElement('div');
    shortUrlEl.className = 'compact-url';
    shortUrlEl.textContent = formatShortUrl(item.originalUrl);
    shortUrlEl.title = item.originalUrl || '';
    tdUrl.appendChild(shortUrlEl);

    if (isSafeHttpUrl(item.originalUrl)) {
      const visitLink = document.createElement('a');
      visitLink.className = 'url-link-preview';
      visitLink.href = item.originalUrl;
      visitLink.target = '_blank';
      visitLink.rel = 'noopener noreferrer';
      visitLink.textContent = '↗ Visit';
      visitLink.title = 'Open original URL in new tab';
      tdUrl.appendChild(visitLink);
    }
    trMain.appendChild(tdUrl);

    // E. Proposed Change Column (Compact summary + action badge)
    const tdChange = document.createElement('td');
    tdChange.className = 'col-change';
    const changeWrapper = document.createElement('div');
    changeWrapper.className = 'compact-change-wrapper';

    // Action badge
    const actionBadge = document.createElement('span');
    actionBadge.className = 'badge-action';
    if (item.proposedAction === 'REMOVE_BOOKMARK' || item.proposedAction === 'REMOVE_DUPLICATE') {
      actionBadge.classList.add('remove');
      actionBadge.textContent = 'REMOVE';
    } else if (item.proposedAction === 'UPDATE_URL') {
      actionBadge.classList.add('update');
      actionBadge.textContent = 'UPDATE URL';
    } else {
      actionBadge.classList.add('no-change');
      actionBadge.textContent = 'NO CHANGE';
    }
    changeWrapper.appendChild(actionBadge);

    // Short summary text
    const summaryText = document.createElement('span');
    summaryText.className = 'compact-change-summary';
    summaryText.textContent = getCompactSummary(item);
    changeWrapper.appendChild(summaryText);

    // Quick inline "Details ▾" toggle link
    const detailsTrigger = document.createElement('button');
    detailsTrigger.type = 'button';
    detailsTrigger.className = 'btn-details-text';
    detailsTrigger.textContent = 'Details ▾';
    detailsTrigger.title = 'Inspect full URLs, redirect paths and technical explanation';
    changeWrapper.appendChild(detailsTrigger);

    tdChange.appendChild(changeWrapper);
    trMain.appendChild(tdChange);

    // F. Decision Column (Visible toggle switch with clear label)
    const tdDecision = document.createElement('td');
    tdDecision.className = 'col-decision';

    const decisionWrapper = document.createElement('div');
    decisionWrapper.className = 'decision-control-wrapper';

    if (item.applicable) {
      const switchLabel = document.createElement('label');
      switchLabel.className = 'switch';
      switchLabel.title = item.enabled ? 'Click to revert: keep original bookmark' : 'Click to apply proposed modification';

      const switchInput = document.createElement('input');
      switchInput.type = 'checkbox';
      switchInput.className = 'decision-checkbox';
      switchInput.checked = Boolean(item.enabled);
      switchInput.dataset.itemId = item.itemId;
      switchInput.setAttribute('aria-label', `Apply proposed change for ${item.bookmarkName || 'item'}`);

      const switchSlider = document.createElement('span');
      switchSlider.className = 'switch-slider';

      switchLabel.appendChild(switchInput);
      switchLabel.appendChild(switchSlider);

      const decisionLabel = document.createElement('span');
      decisionLabel.id = `decision-label-${item.itemId}`;
      decisionLabel.className = `decision-label ${item.enabled ? 'applied' : 'kept'}`;
      decisionLabel.textContent = item.enabled ? 'Apply' : 'Keep Original';

      // Clicking label also toggles switch
      decisionLabel.addEventListener('click', (e) => {
        e.stopPropagation();
        switchInput.click();
      });

      // Toggling switch triggers decision update
      switchInput.addEventListener('change', (e) => {
        e.stopPropagation();
        window.BookmarkCleaner.Decisions.toggleDecision(item.itemId, e.target.checked, switchInput);
      });

      decisionWrapper.appendChild(switchLabel);
      decisionWrapper.appendChild(decisionLabel);
    } else {
      const unchangedBadge = document.createElement('span');
      unchangedBadge.className = 'decision-badge-unchanged';
      unchangedBadge.textContent = 'Keep Original';
      unchangedBadge.title = 'Healthy bookmark; no change proposed.';
      decisionWrapper.appendChild(unchangedBadge);
    }

    tdDecision.appendChild(decisionWrapper);
    trMain.appendChild(tdDecision);

    // ─── 2. EXPANDABLE DETAILS ROW ───────────────────────────────────────────
    const trDetails = document.createElement('tr');
    trDetails.className = 'details-row';
    trDetails.id = `details-${item.itemId}`;
    trDetails.style.display = 'none';

    const tdDetailsColspan = document.createElement('td');
    tdDetailsColspan.colSpan = 6;
    tdDetailsColspan.className = 'details-cell';

    const detailsCard = document.createElement('div');
    detailsCard.className = 'details-card';

    // Header of details card
    const detailsHeader = document.createElement('div');
    detailsHeader.className = 'details-header';
    detailsHeader.innerHTML = `
      <div class="details-title-group">
        <span class="details-heading">Detailed Inspection: ${escapeHtml(item.bookmarkName || 'Bookmark')}</span>
        <span class="details-folder-tag">📁 ${escapeHtml(item.folderPath || 'Bookmarks bar')}</span>
      </div>
      <div class="details-header-badges">
        <span class="badge-priority ${(item.priority || 'LOW').toLowerCase()}">${escapeHtml(item.priority || 'LOW')} PRIORITY</span>
        <span class="badge-outcome ${(item.outcome || '').toLowerCase()}">${escapeHtml(item.outcome || 'UNKNOWN')}</span>
      </div>
    `;
    detailsCard.appendChild(detailsHeader);

    // Details Grid Layout
    const detailsGrid = document.createElement('div');
    detailsGrid.className = 'details-grid';

    // Left Column: Technical Explanation & Findings
    const colFindings = document.createElement('div');
    colFindings.className = 'details-grid-col';

    let extraFindingsHtml = '';
    if (item.redirectType && item.redirectType !== 'NONE') {
      const redLabel = item.redirectType === 'CROSS_DOMAIN' ? 'Cross-Domain Redirect (Target Host Changed)' : 'Same-Domain Redirect (URL Path Updated)';
      extraFindingsHtml += `
        <div class="details-meta-row">
          <span class="details-meta-label">Redirect Category:</span>
          <span class="details-meta-value text-info">↪ ${escapeHtml(redLabel)}</span>
        </div>
      `;
    }

    if (item.duplicateType && item.duplicateType !== 'NONE') {
      const dupLabel = item.duplicateType === 'EXACT' ? 'Exact Duplicate (Identical Canonical URL)' : 'Potential Duplicate (Matching Destination)';
      extraFindingsHtml += `
        <div class="details-meta-row">
          <span class="details-meta-label">Duplicate Status:</span>
          <span class="details-meta-value text-warning">♊ ${escapeHtml(dupLabel)}</span>
        </div>
      `;
    }

    const decisionStatusText = item.applicable
      ? (item.enabled ? 'Apply Proposed Change' : 'Keep Original Bookmark')
      : 'No Change Needed (Healthy)';

    colFindings.innerHTML = `
      <h5 class="details-section-title">Technical Findings &amp; Explanation</h5>
      <div class="details-explanation-box">
        <p class="details-explanation-text">${escapeHtml(item.changeDescription || 'No detailed explanation provided.')}</p>
      </div>
      ${extraFindingsHtml}
      <div class="details-decision-status" id="details-decision-status-${item.itemId}">
        Current choice: <strong class="${item.enabled ? 'text-primary' : 'text-muted'}">${escapeHtml(decisionStatusText)}</strong>
      </div>
    `;
    detailsGrid.appendChild(colFindings);

    // Right Column: URL Comparison & Actions
    const colUrls = document.createElement('div');
    colUrls.className = 'details-grid-col';

    // Original URL Box
    const origBox = document.createElement('div');
    origBox.className = 'details-url-card';
    origBox.innerHTML = `
      <div class="details-url-header">
        <span class="details-url-tag">ORIGINAL DESTINATION</span>
        <button type="button" class="btn btn-secondary btn-xs btn-copy-url" data-url="${escapeHtml(item.originalUrl || '')}">
          📋 Copy
        </button>
      </div>
      <div class="details-url-val">${escapeHtml(item.originalUrl || '')}</div>
    `;
    if (isSafeHttpUrl(item.originalUrl)) {
      const visitA = document.createElement('a');
      visitA.className = 'url-link-preview';
      visitA.href = item.originalUrl;
      visitA.target = '_blank';
      visitA.rel = 'noopener noreferrer';
      visitA.textContent = '↗ Open Original URL';
      origBox.appendChild(visitA);
    }
    colUrls.appendChild(origBox);

    // Proposed URL Box (if applicable)
    if (item.proposedUrl) {
      const propBox = document.createElement('div');
      propBox.className = 'details-url-card proposed';
      propBox.innerHTML = `
        <div class="details-url-header">
          <span class="details-url-tag tag-proposed">↳ PROPOSED NEW DESTINATION</span>
          <button type="button" class="btn btn-secondary btn-xs btn-copy-url" data-url="${escapeHtml(item.proposedUrl)}">
            📋 Copy
          </button>
        </div>
        <div class="details-url-val text-primary">${escapeHtml(item.proposedUrl)}</div>
      `;
      if (isSafeHttpUrl(item.proposedUrl)) {
        const testA = document.createElement('a');
        testA.className = 'url-link-preview text-primary';
        testA.href = item.proposedUrl;
        testA.target = '_blank';
        testA.rel = 'noopener noreferrer';
        testA.textContent = '↗ Test Proposed URL in New Tab';
        propBox.appendChild(testA);
      }
      colUrls.appendChild(propBox);
    }

    detailsGrid.appendChild(colUrls);
    detailsCard.appendChild(detailsGrid);

    // Footer of details card with collapse button
    const detailsFooter = document.createElement('div');
    detailsFooter.className = 'details-footer';
    const btnCloseDetails = document.createElement('button');
    btnCloseDetails.type = 'button';
    btnCloseDetails.className = 'btn btn-secondary btn-xs';
    btnCloseDetails.textContent = '▲ Hide Details';
    detailsFooter.appendChild(btnCloseDetails);
    detailsCard.appendChild(detailsFooter);

    tdDetailsColspan.appendChild(detailsCard);
    trDetails.appendChild(tdDetailsColspan);

    // ─── 3. INTERACTION BINDINGS ─────────────────────────────────────────────
    function toggleExpand(e) {
      if (e) e.stopPropagation();
      const isExpanded = trDetails.style.display !== 'none';
      if (isExpanded) {
        trDetails.style.display = 'none';
        btnExpand.classList.remove('expanded');
        btnExpand.setAttribute('aria-expanded', 'false');
        trMain.classList.remove('row-expanded');
        detailsTrigger.textContent = 'Details ▾';
      } else {
        trDetails.style.display = 'table-row';
        btnExpand.classList.add('expanded');
        btnExpand.setAttribute('aria-expanded', 'true');
        trMain.classList.add('row-expanded');
        detailsTrigger.textContent = 'Hide Details ▴';
      }
    }

    btnExpand.addEventListener('click', toggleExpand);
    detailsTrigger.addEventListener('click', toggleExpand);
    btnCloseDetails.addEventListener('click', toggleExpand);

    // Hook copy buttons inside details card
    const copyBtns = detailsCard.querySelectorAll('.btn-copy-url');
    copyBtns.forEach(cBtn => {
      cBtn.addEventListener('click', (e) => {
        e.stopPropagation();
        const urlToCopy = cBtn.getAttribute('data-url');
        if (urlToCopy && navigator.clipboard) {
          navigator.clipboard.writeText(urlToCopy).then(() => {
            const origText = cBtn.textContent;
            cBtn.textContent = '✓ Copied!';
            cBtn.classList.add('btn-success-subtle');
            setTimeout(() => {
              cBtn.textContent = origText;
              cBtn.classList.remove('btn-success-subtle');
            }, 1800);
          }).catch(err => {
            console.error('Clipboard copy failed:', err);
          });
        }
      });
    });

    fragment.appendChild(trMain);
    fragment.appendChild(trDetails);
    return fragment;
  }

  /**
   * Renders pagination metadata and enables/disables controls.
   */
  function renderPagination(response) {
    const summaryEl = document.getElementById('pagination-summary');
    const pageIndicator = document.getElementById('pagination-page-indicator');
    const prevBtn = document.getElementById('btn-prev-page');
    const nextBtn = document.getElementById('btn-next-page');

    const total = response.totalItems || 0;
    const page = response.page || 0;
    const size = response.size || 50;
    const totalPages = Math.max(1, response.totalPages || 1);

    const from = total === 0 ? 0 : page * size + 1;
    const to = Math.min((page + 1) * size, total);

    if (summaryEl) {
      summaryEl.textContent = `Showing ${from}–${to} of ${total} items`;
    }

    if (pageIndicator) {
      pageIndicator.textContent = `Page ${page + 1} of ${totalPages}`;
    }

    if (prevBtn) {
      prevBtn.disabled = page <= 0;
    }

    if (nextBtn) {
      nextBtn.disabled = page >= totalPages - 1;
    }
  }

  function setElementText(id, text) {
    const el = document.getElementById(id);
    if (el) el.textContent = text !== undefined && text !== null ? text : '0';
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  /**
   * Safety check: only allow safe http: and https: protocols for anchor hrefs.
   */
  function isSafeHttpUrl(url) {
    if (!url || typeof url !== 'string') return false;
    const trimmed = url.trim().toLowerCase();
    return trimmed.startsWith('http://') || trimmed.startsWith('https://');
  }

  window.BookmarkCleaner = window.BookmarkCleaner || {};
  window.BookmarkCleaner.Results = {
    init: initResults,
    loadResults: loadResults
  };

})(window);
