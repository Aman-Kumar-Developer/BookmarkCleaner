package com.aman.bookmark_cleaner.service;

import com.aman.bookmark_cleaner.model.BookmarkDocument;
import com.aman.bookmark_cleaner.model.BookmarkNode;
import com.aman.bookmark_cleaner.model.BookmarkRoots;
import com.aman.bookmark_cleaner.model.ComparisonItem;
import com.aman.bookmark_cleaner.model.ProposedAction;
import com.aman.bookmark_cleaner.model.ScanSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service that reconstructs a separate, cleaned Chromium bookmark tree from the original
 * parsed document by applying only the user's accepted decisions (ON).
 * The original document remains strictly immutable.
 */
@Service
public class BookmarkTreeReconstructionService {

    public record ReconstructionResult(BookmarkDocument reconstructedDocument, int changesAppliedCount) {}

    /**
     * Reconstructs the bookmark document based on active decisions in the session.
     */
    public ReconstructionResult reconstruct(ScanSession session) {
        if (session == null || session.getOriginalDocument() == null) {
            return new ReconstructionResult(new BookmarkDocument(new BookmarkRoots()), 0);
        }

        BookmarkDocument original = session.getOriginalDocument();
        AtomicInteger changesCounter = new AtomicInteger(0);

        BookmarkRoots reconstructedRoots = new BookmarkRoots();

        if (original.getRoots() != null) {
            for (Map.Entry<String, BookmarkNode> entry : original.getRoots().getAllRoots().entrySet()) {
                String rootName = entry.getKey();
                BookmarkNode origRoot = entry.getValue();

                if (origRoot != null) {
                    BookmarkNode recRoot = reconstructNode(origRoot, session, changesCounter);
                    reconstructedRoots.setRoot(rootName, recRoot);
                }
            }
        }

        BookmarkDocument reconstructedDoc = new BookmarkDocument(reconstructedRoots);
        reconstructedDoc.setChecksum(original.getChecksum());
        reconstructedDoc.setVersion(original.getVersion());

        // Copy unknown document-level properties
        for (Map.Entry<String, Object> extra : original.getAdditionalProperties().entrySet()) {
            reconstructedDoc.setAdditionalProperty(extra.getKey(), extra.getValue());
        }

        return new ReconstructionResult(reconstructedDoc, changesCounter.get());
    }

    private BookmarkNode reconstructNode(BookmarkNode originalNode, ScanSession session, AtomicInteger changesCounter) {
        if (originalNode == null) {
            return null;
        }

        if (originalNode.isFolder()) {
            BookmarkNode folderCopy = new BookmarkNode();
            folderCopy.setId(originalNode.getId());
            folderCopy.setName(originalNode.getName());
            folderCopy.setType(originalNode.getType() != null ? originalNode.getType() : BookmarkNode.TYPE_FOLDER);
            folderCopy.setDateAdded(originalNode.getDateAdded());
            folderCopy.setDateModified(originalNode.getDateModified());
            folderCopy.setDateLastUsed(originalNode.getDateLastUsed());
            folderCopy.setGuid(originalNode.getGuid());
            folderCopy.setMetaInfo(originalNode.getMetaInfo());

            for (Map.Entry<String, Object> extra : originalNode.getAdditionalProperties().entrySet()) {
                folderCopy.setAdditionalProperty(extra.getKey(), extra.getValue());
            }

            List<BookmarkNode> reconstructedChildren = new ArrayList<>();
            if (originalNode.getChildren() != null) {
                for (BookmarkNode child : originalNode.getChildren()) {
                    if (child != null) {
                        if (child.isFolder()) {
                            // Recursively reconstruct folder
                            BookmarkNode childFolder = reconstructNode(child, session, changesCounter);
                            if (childFolder != null) {
                                reconstructedChildren.add(childFolder);
                            }
                        } else if (child.isUrl()) {
                            // Check decision for URL bookmark
                            ComparisonItem item = session.getComparisonItemByBookmarkId(child.getId());
                            if (item != null && item.isEnabled()) {
                                // Decision is ON: Apply proposed action
                                if (item.getProposedAction() == ProposedAction.REMOVE_BOOKMARK ||
                                        item.getProposedAction() == ProposedAction.REMOVE_DUPLICATE) {
                                    // Remove bookmark: do not include in children
                                    changesCounter.incrementAndGet();
                                    continue;
                                } else if (item.getProposedAction() == ProposedAction.UPDATE_URL && item.getProposedUrl() != null) {
                                    // Update URL
                                    BookmarkNode updatedUrl = cloneUrlNode(child, item.getProposedUrl());
                                    reconstructedChildren.add(updatedUrl);
                                    changesCounter.incrementAndGet();
                                    continue;
                                }
                            }

                            // Retain original URL node (either decision is OFF, NO_CHANGE, or disabled)
                            BookmarkNode retainedCopy = cloneUrlNode(child, child.getUrl());
                            reconstructedChildren.add(retainedCopy);
                        }
                    }
                }
            }

            folderCopy.setChildren(reconstructedChildren);
            return folderCopy;
        } else {
            // Root URL (if any root is directly a URL)
            return cloneUrlNode(originalNode, originalNode.getUrl());
        }
    }

    private BookmarkNode cloneUrlNode(BookmarkNode source, String url) {
        BookmarkNode copy = new BookmarkNode();
        copy.setId(source.getId());
        copy.setName(source.getName());
        copy.setType(source.getType() != null ? source.getType() : BookmarkNode.TYPE_URL);
        copy.setUrl(url);
        copy.setDateAdded(source.getDateAdded());
        copy.setDateModified(source.getDateModified());
        copy.setDateLastUsed(source.getDateLastUsed());
        copy.setGuid(source.getGuid());
        copy.setMetaInfo(source.getMetaInfo());

        for (Map.Entry<String, Object> extra : source.getAdditionalProperties().entrySet()) {
            copy.setAdditionalProperty(extra.getKey(), extra.getValue());
        }

        return copy;
    }
}
