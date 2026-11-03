package com.aman.bookmark_cleaner.service;

import org.springframework.stereotype.Service;

import java.net.IDN;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Service that identifies public suffixes (effective top-level domains)
 * to reliably compute registrable domains (eTLD+1).
 * Supports multi-part ccTLDs, modern gTLDs, and common public hosting suffixes.
 */
@Service
public class PublicSuffixService {

    private final Set<String> publicSuffixes;

    public PublicSuffixService() {
        Set<String> suffixes = new HashSet<>();

        // UK
        suffixes.add("co.uk");
        suffixes.add("org.uk");
        suffixes.add("gov.uk");
        suffixes.add("ac.uk");
        suffixes.add("net.uk");
        suffixes.add("sch.uk");
        suffixes.add("me.uk");
        suffixes.add("ltd.uk");
        suffixes.add("plc.uk");

        // Australia
        suffixes.add("com.au");
        suffixes.add("net.au");
        suffixes.add("org.au");
        suffixes.add("edu.au");
        suffixes.add("gov.au");
        suffixes.add("asn.au");
        suffixes.add("id.au");

        // New Zealand
        suffixes.add("co.nz");
        suffixes.add("net.nz");
        suffixes.add("org.nz");
        suffixes.add("govt.nz");
        suffixes.add("ac.nz");
        suffixes.add("school.nz");
        suffixes.add("geek.nz");

        // Japan
        suffixes.add("co.jp");
        suffixes.add("ne.jp");
        suffixes.add("or.jp");
        suffixes.add("go.jp");
        suffixes.add("ac.jp");
        suffixes.add("ad.jp");
        suffixes.add("ed.jp");
        suffixes.add("gr.jp");
        suffixes.add("lg.jp");

        // India
        suffixes.add("co.in");
        suffixes.add("net.in");
        suffixes.add("org.in");
        suffixes.add("gen.in");
        suffixes.add("firm.in");
        suffixes.add("ind.in");
        suffixes.add("nic.in");
        suffixes.add("ac.in");
        suffixes.add("edu.in");
        suffixes.add("res.in");
        suffixes.add("gov.in");
        suffixes.add("mil.in");

        // Brazil
        suffixes.add("com.br");
        suffixes.add("net.br");
        suffixes.add("org.br");
        suffixes.add("gov.br");
        suffixes.add("edu.br");
        suffixes.add("art.br");
        suffixes.add("blog.br");
        suffixes.add("eco.br");
        suffixes.add("emp.br");
        suffixes.add("esp.br");
        suffixes.add("far.br");
        suffixes.add("imb.br");
        suffixes.add("ind.br");
        suffixes.add("inf.br");
        suffixes.add("med.br");
        suffixes.add("mus.br");
        suffixes.add("radio.br");
        suffixes.add("rec.br");
        suffixes.add("srv.br");
        suffixes.add("tmp.br");
        suffixes.add("tur.br");
        suffixes.add("tv.br");
        suffixes.add("vet.br");
        suffixes.add("wiki.br");

        // China
        suffixes.add("com.cn");
        suffixes.add("net.cn");
        suffixes.add("org.cn");
        suffixes.add("gov.cn");
        suffixes.add("edu.cn");

        // South Africa
        suffixes.add("co.za");
        suffixes.add("net.za");
        suffixes.add("org.za");
        suffixes.add("gov.za");
        suffixes.add("ac.za");
        suffixes.add("edu.za");

        // Singapore
        suffixes.add("com.sg");
        suffixes.add("net.sg");
        suffixes.add("org.sg");
        suffixes.add("gov.sg");
        suffixes.add("edu.sg");

        // Mexico
        suffixes.add("com.mx");
        suffixes.add("net.mx");
        suffixes.add("org.mx");
        suffixes.add("gob.mx");
        suffixes.add("edu.mx");

        // Turkey
        suffixes.add("com.tr");
        suffixes.add("net.tr");
        suffixes.add("org.tr");
        suffixes.add("gov.tr");
        suffixes.add("edu.tr");

        // Hong Kong
        suffixes.add("com.hk");
        suffixes.add("net.hk");
        suffixes.add("org.hk");
        suffixes.add("gov.hk");
        suffixes.add("edu.hk");

        // Germany, France, Canada, Israel, etc.
        suffixes.add("co.il");
        suffixes.add("org.il");
        suffixes.add("gov.il");
        suffixes.add("ac.il");
        suffixes.add("gc.ca");

        // Common multi-tenant hosting platforms where subdomains are independent registrations
        suffixes.add("github.io");
        suffixes.add("gitlab.io");
        suffixes.add("pages.dev");
        suffixes.add("vercel.app");
        suffixes.add("netlify.app");
        suffixes.add("herokuapp.com");
        suffixes.add("firebaseapp.com");
        suffixes.add("web.app");
        suffixes.add("cloudfront.net");

        this.publicSuffixes = Collections.unmodifiableSet(suffixes);
    }

    /**
     * Extracts the registrable domain (eTLD+1) for a given hostname.
     * E.g. "foo.example.co.uk" -> "example.co.uk"
     *      "www.google.com" -> "google.com"
     *      "user.github.io" -> "user.github.io"
     */
    public String getRegistrableDomain(String hostname) {
        if (hostname == null || hostname.isBlank()) {
            return "";
        }

        String normalizedHost = normalizeHostname(hostname);
        String[] labels = normalizedHost.split("\\.");
        if (labels.length <= 1) {
            return normalizedHost;
        }

        // Check for matching multi-part public suffixes from longest to shortest
        for (int i = 1; i < labels.length; i++) {
            StringBuilder candidateSuffix = new StringBuilder();
            for (int j = i; j < labels.length; j++) {
                if (!candidateSuffix.isEmpty()) {
                    candidateSuffix.append(".");
                }
                candidateSuffix.append(labels[j]);
            }

            String suffix = candidateSuffix.toString();
            if (publicSuffixes.contains(suffix)) {
                // The registrable domain is this suffix plus the immediately preceding label
                return labels[i - 1] + "." + suffix;
            }
        }

        // Standard single-label TLD fallback: registrable domain is the last two labels (e.g. example.com)
        return labels[labels.length - 2] + "." + labels[labels.length - 1];
    }

    /**
     * Normalizes a hostname by converting Punycode/IDN to ASCII lowercase and removing trailing dots.
     */
    public String normalizeHostname(String hostname) {
        if (hostname == null) {
            return "";
        }
        String trimmed = hostname.trim();
        if (trimmed.endsWith(".")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        try {
            return IDN.toASCII(trimmed, IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return trimmed.toLowerCase(Locale.ROOT);
        }
    }
}
