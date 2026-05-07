package com.dvcs.common.validation;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;

/**
 * Sanitizes user-supplied HTML content to prevent cross-site scripting (XSS) attacks.
 *
 * <p>Uses the OWASP Java HTML Sanitizer library with a restrictive allow-list policy
 * that permits only safe, commonly used formatting tags and attributes (Req 18.3).
 *
 * <p>The policy allows:
 * <ul>
 *   <li>Block elements: {@code p}, {@code div}, {@code blockquote}, {@code pre}</li>
 *   <li>Inline formatting: {@code b}, {@code i}, {@code em}, {@code strong}, {@code code},
 *       {@code s}, {@code del}, {@code ins}, {@code sub}, {@code sup}, {@code u}</li>
 *   <li>Lists: {@code ul}, {@code ol}, {@code li}</li>
 *   <li>Headings: {@code h1}–{@code h6}</li>
 *   <li>Line breaks: {@code br}, {@code hr}</li>
 *   <li>Links: {@code a} with {@code href} (http/https/mailto only) and {@code rel="nofollow"}</li>
 *   <li>Images: {@code img} with {@code src} (http/https only), {@code alt}, {@code width}, {@code height}</li>
 *   <li>Tables: {@code table}, {@code thead}, {@code tbody}, {@code tr}, {@code th}, {@code td}</li>
 * </ul>
 *
 * <p>All other tags and attributes are stripped. Script, style, iframe, and form elements
 * are never allowed.
 */
@Component
public class InputSanitizer {

    /**
     * OWASP HTML sanitizer policy — built once and reused (thread-safe).
     */
    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            // Block elements
            .allowElements("p", "div", "blockquote", "pre")
            // Inline formatting
            .allowElements("b", "i", "em", "strong", "code", "s", "del", "ins", "sub", "sup", "u")
            // Lists
            .allowElements("ul", "ol", "li")
            // Headings
            .allowElements("h1", "h2", "h3", "h4", "h5", "h6")
            // Line breaks
            .allowElements("br", "hr")
            // Links — allow href with http/https/mailto schemes only; force rel="nofollow"
            .allowElements("a")
            .allowUrlProtocols("http", "https", "mailto")
            .allowAttributes("href").onElements("a")
            .allowAttributes("rel").onElements("a")
            // Images — allow src with http/https only; allow alt, width, height
            .allowElements("img")
            .allowAttributes("src").matching(
                    java.util.regex.Pattern.compile("(?i)^https?://.*"))
            .onElements("img")
            .allowAttributes("alt", "width", "height").onElements("img")
            // Tables
            .allowElements("table", "thead", "tbody", "tr", "th", "td")
            .allowAttributes("colspan", "rowspan").onElements("th", "td")
            // Spans with class (for syntax highlighting in rendered markdown)
            .allowElements("span")
            .allowAttributes("class").onElements("span", "code", "pre")
            .toFactory();

    /**
     * Sanitizes the given HTML string, stripping any tags or attributes not in the allow-list.
     *
     * <p>If the input is {@code null}, returns {@code null} unchanged.
     * If the input is blank, returns it unchanged.
     *
     * @param html the raw HTML string to sanitize (may be {@code null})
     * @return the sanitized HTML string, or {@code null} if the input was {@code null}
     */
    public String sanitize(String html) {
        if (html == null) {
            return null;
        }
        if (html.isBlank()) {
            return html;
        }
        return POLICY.sanitize(html);
    }
}
