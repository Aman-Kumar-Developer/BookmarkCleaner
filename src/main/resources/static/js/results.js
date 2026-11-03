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
   * Renders the comparison table using safe DOM construction APIs.
   * Untrusted strings are strictly inserted via textContent.
   */
  function renderTable(items) {
    const tbody = document.getElementById('comparison-table-body');
    const emptyState = document.getElementById('table-empty-state');

    if (!tbody) return;

    // Clear existing rows safely
    while (tbody.firstChild) {
      tbody.removeChild(tbody.firstChild);
    }

    if (!items || items.length === 0) {
      if (emptyState) emptyState.style.display = 'block';
      return;
    }

    if (emptyState) emptyState.style.display = 'none';

    items.forEach(item => {
      const row = createTableRow(item);
      tbody.appendChild(row);
    });
  }

  /**
   * Creates a single sanitized <tr> element for a ComparisonItemDto.
   */
  function createTableRow(item) {
    const tr = document.createElement('tr');
    tr.dataset.itemId = item.itemId;

    // 1. Priority Column
    const tdPriority = document.createElement('td');
    const priorityBadge = document.createElement('span');
    const priorityClass = (item.priority || 'LOW').toLowerCase();
    priorityBadge.className = `badge-priority ${priorityClass}`;
    priorityBadge.textContent = item.priority || 'LOW';
    priorityBadge.title = `Priority: ${item.priority}`;
    tdPriority.appendChild(priorityBadge);
    tr.appendChild(tdPriority);

    // 2. Bookmark Name & Folder Path
    const tdBookmark = document.createElement('td');
    const nameEl = document.createElement('div');
    nameEl.className = 'bookmark-cell-title';
    nameEl.textContent = item.bookmarkName || 'Untitled Bookmark';
    nameEl.title = item.bookmarkName || '';

    const pathEl = document.createElement('div');
    pathEl.className = 'bookmark-cell-path';
    pathEl.textContent = item.folderPath ? `📁 ${item.folderPath}` : '📁 Bookmarks';
    pathEl.title = item.folderPath || '';

    tdBookmark.appendChild(nameEl);
    tdBookmark.appendChild(pathEl);
    tr.appendChild(tdBookmark);

    // 3. Original URL
    const tdOrigUrl = document.createElement('td');
    const origUrlText = document.createElement('div');
    origUrlText.className = 'url-text';
    origUrlText.textContent = item.originalUrl || '';
    origUrlText.title = item.originalUrl || '';
    tdOrigUrl.appendChild(origUrlText);

    if (isSafeHttpUrl(item.originalUrl)) {
      const linkPreview = document.createElement('a');
      linkPreview.className = 'url-link-preview';
      linkPreview.href = item.originalUrl;
      linkPreview.target = '_blank';
      linkPreview.rel = 'noopener noreferrer';
      linkPreview.textContent = '↗ Visit URL';
      tdOrigUrl.appendChild(linkPreview);
    }
    tr.appendChild(tdOrigUrl);

    // 4. Proposed Change
    const tdChange = document.createElement('td');
    const changeBox = document.createElement('div');
    changeBox.className = 'proposed-change-box';

    const descEl = document.createElement('div');
    descEl.className = 'proposed-desc';

    if (item.outcome === 'BROKEN') {
      descEl.classList.add('desc-broken');
      descEl.textContent = `❌ Broken Link (${item.changeDescription || 'HTTP Error'})`;
    } else if (item.outcome === 'REDIRECTED') {
      descEl.classList.add('desc-redirect');
      const redType = item.redirectType === 'CROSS_DOMAIN' ? 'Cross-Domain Redirect' : 'Same-Site Redirect';
      descEl.textContent = `↪ ${redType} (${item.changeDescription || 'Redirected'})`;
    } else if (item.duplicateType) {
      descEl.classList.add('desc-duplicate');
      descEl.textContent = `♊ Duplicate Bookmark (${item.duplicateType})`;
    } else {
      descEl.textContent = '✓ Healthy Bookmark (No change needed)';
    }
    changeBox.appendChild(descEl);

    if (item.proposedUrl) {
      const propUrlText = document.createElement('div');
      propUrlText.className = 'url-text';
      propUrlText.style.color = 'var(--color-primary)';
      propUrlText.textContent = `↳ ${item.proposedUrl}`;
      propUrlText.title = item.proposedUrl;
      changeBox.appendChild(propUrlText);

      if (isSafeHttpUrl(item.proposedUrl)) {
        const propLink = document.createElement('a');
        propLink.className = 'url-link-preview';
        propLink.href = item.proposedUrl;
        propLink.target = '_blank';
        propLink.rel = 'noopener noreferrer';
        propLink.textContent = '↗ Test Proposed URL';
        changeBox.appendChild(propLink);
      }
    }

    tdChange.appendChild(changeBox);
    tr.appendChild(tdChange);

    // 5. Action Badge
    const tdAction = document.createElement('td');
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
    tdAction.appendChild(actionBadge);
    tr.appendChild(tdAction);

    // 6. Decision Toggle Switch
    const tdDecision = document.createElement('td');
    tdDecision.className = 'col-decision';

    const switchLabel = document.createElement('label');
    switchLabel.className = 'switch';

    const switchInput = document.createElement('input');
    switchInput.type = 'checkbox';
    switchInput.checked = Boolean(item.enabled);
    switchInput.disabled = !item.applicable;
    switchInput.setAttribute('aria-label', `Apply proposal for ${item.bookmarkName || 'item'}`);

    const switchSlider = document.createElement('span');
    switchSlider.className = 'switch-slider';

    switchInput.addEventListener('change', (e) => {
      window.BookmarkCleaner.Decisions.toggleDecision(item.itemId, e.target.checked, switchInput);
    });

    switchLabel.appendChild(switchInput);
    switchLabel.appendChild(switchSlider);
    tdDecision.appendChild(switchLabel);
    tr.appendChild(tdDecision);

    return tr;
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
