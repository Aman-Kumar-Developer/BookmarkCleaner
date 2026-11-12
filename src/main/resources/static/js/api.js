/**
 * Bookmark Cleaner - REST API Client
 */
(function(window) {
  'use strict';

  const BASE_URL = '';

  /**
   * Helper to parse error responses cleanly.
   */
  async function handleResponse(response) {
    if (!response.ok) {
      let errorMessage = `Request failed with status ${response.status}`;
      try {
        const errorData = await response.json();
        if (errorData && errorData.message) {
          errorMessage = errorData.message;
        }
      } catch (_) {
        // Fallback to text status
        errorMessage = response.statusText || errorMessage;
      }
      const error = new Error(errorMessage);
      error.status = response.status;
      throw error;
    }
    return response.json();
  }

  const ApiClient = {
    /**
     * Uploads Chromium bookmark JSON file.
     * @param {File} file
     * @returns {Promise<Object>} UploadResponse { scanId, filename, totalBookmarks, status, message }
     */
    async uploadBookmarks(file) {
      const formData = new FormData();
      formData.append('file', file);

      const response = await fetch(`${BASE_URL}/api/bookmarks/upload`, {
        method: 'POST',
        body: formData
      });
      return handleResponse(response);
    },

    /**
     * Starts bookmark analysis scan.
     * @param {string} scanId
     * @returns {Promise<Object>} ScanStatusResponse
     */
    async startScan(scanId) {
      const response = await fetch(`${BASE_URL}/api/scans`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({ scanId })
      });
      return handleResponse(response);
    },

    /**
     * Retrieves current scan status and progress.
     * @param {string} scanId
     * @returns {Promise<Object>} ScanStatusResponse
     */
    async getScanStatus(scanId) {
      const response = await fetch(`${BASE_URL}/api/scans/${encodeURIComponent(scanId)}`, {
        method: 'GET'
      });
      return handleResponse(response);
    },

    /**
     * Retrieves paginated comparison items with priority filtering and search.
     * @param {string} scanId
     * @param {string} priority
     * @param {string} search
     * @param {number} page
     * @param {number} size
     * @returns {Promise<Object>} ComparisonResultsPageResponse
     */
    async getResults(scanId, priority = 'ALL', search = '', page = 0, size = 50) {
      const params = new URLSearchParams();
      if (priority) params.append('priority', priority);
      if (search && search.trim()) params.append('search', search.trim());
      params.append('page', String(page));
      params.append('size', String(size));

      const response = await fetch(
        `${BASE_URL}/api/scans/${encodeURIComponent(scanId)}/results?${params.toString()}`,
        { method: 'GET' }
      );
      return handleResponse(response);
    },

    /**
     * Updates user decision toggle for a specific comparison item.
     * @param {string} scanId
     * @param {string} itemId
     * @param {boolean} enabled
     * @returns {Promise<Object>} DecisionUpdateResponse
     */
    async updateDecision(scanId, itemId, enabled) {
      const response = await fetch(
        `${BASE_URL}/api/scans/${encodeURIComponent(scanId)}/decisions/${encodeURIComponent(itemId)}`,
        {
          method: 'PATCH',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ enabled: Boolean(enabled) })
        }
      );
      return handleResponse(response);
    },

    /**
     * Generates cleaned Chromium bookmark JSON export.
     * @param {string} scanId
     * @returns {Promise<Object>} ExportResponse
     */
    async generateExport(scanId) {
      const response = await fetch(
        `${BASE_URL}/api/scans/${encodeURIComponent(scanId)}/export`,
        {
          method: 'POST'
        }
      );
      return handleResponse(response);
    },

    /**
     * Direct URL to download the exported bookmarks-cleaned.json.
     * @param {string} scanId
     * @returns {string}
     */
    getDownloadUrl(scanId) {
      return `${BASE_URL}/api/scans/${encodeURIComponent(scanId)}/export`;
    },

    /**
     * Discovers installed Chromium browsers and their readable bookmark profiles.
     * @returns {Promise<{browsers: Array}>} BrowserDiscoveryResponse
     */
    async discoverBrowsers() {
      const response = await fetch(`${BASE_URL}/api/browsers`, {
        method: 'GET'
      });
      return handleResponse(response);
    },

    /**
     * Imports bookmarks directly from a chosen browser and profile.
     * @param {string} browserId
     * @param {string} profileId
     * @returns {Promise<Object>} UploadResponse { scanId, filename, totalBookmarks, status }
     */
    async importBrowserProfile(browserId, profileId) {
      const response = await fetch(
        `${BASE_URL}/api/browsers/${encodeURIComponent(browserId)}/profiles/${encodeURIComponent(profileId)}/import`,
        {
          method: 'POST'
        }
      );
      return handleResponse(response);
    },

    /**
     * Retrieves versioned backup status and browser profile metadata for a scan.
     * @param {string} scanId
     * @returns {Promise<Object>} BackupManagementResponse
     */
    async getBackups(scanId) {
      const response = await fetch(
        `${BASE_URL}/api/scans/${encodeURIComponent(scanId)}/backups`,
        { method: 'GET' }
      );
      return handleResponse(response);
    },

    /**
     * Directly replaces bookmarks in the original browser profile with versioned backups.
     * @param {string} scanId
     * @returns {Promise<Object>} InstallResponse
     */
    async installBookmarks(scanId) {
      const response = await fetch(
        `${BASE_URL}/api/scans/${encodeURIComponent(scanId)}/install`,
        { method: 'POST' }
      );
      return handleResponse(response);
    },

    /**
     * Retrieves the status of any active or recent installation/restore operation.
     * @param {string} scanId
     * @returns {Promise<Object>} InstallationOperationDto
     */
    async getInstallStatus(scanId) {
      const response = await fetch(
        `${BASE_URL}/api/scans/${encodeURIComponent(scanId)}/install/status`,
        { method: 'GET' }
      );
      return handleResponse(response);
    },

    /**
     * Cancels an installation operation waiting for browser closure.
     * @param {string} scanId
     * @returns {Promise<Object>} InstallationOperationDto
     */
    async cancelInstall(scanId) {
      const response = await fetch(
        `${BASE_URL}/api/scans/${encodeURIComponent(scanId)}/install/cancel`,
        { method: 'POST' }
      );
      return handleResponse(response);
    },

    /**
     * Restores a previously created versioned backup into the active Bookmarks file.
     * @param {string} scanId
     * @param {string} backupId
     * @returns {Promise<Object>} RestoreResponse
     */
    async restoreBackup(scanId, backupId) {
      const response = await fetch(
        `${BASE_URL}/api/scans/${encodeURIComponent(scanId)}/backups/${encodeURIComponent(backupId)}/restore`,
        { method: 'POST' }
      );
      return handleResponse(response);
    }
  };

  window.BookmarkCleaner = window.BookmarkCleaner || {};
  window.BookmarkCleaner.Api = ApiClient;

})(window);
