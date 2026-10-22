package my2Dgame;

import java.awt.FontMetrics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/**
 * Pixel-measured text and card layout for the fixed-size campaign canvas.
 * Keeping these rules outside GamePanel makes overflow behaviour directly
 * testable and prevents each menu from inventing its own truncation logic.
 */
final class CampaignTextLayout {
    private CampaignTextLayout() {}

    static String fit(FontMetrics metrics, String text, int maxWidth) {
        String value = text == null ? "" : text.replace('\n', ' ').trim();
        if (value.isEmpty() || maxWidth <= 0) return "";
        if (metrics == null) {
            int maxChars = Math.max(0, maxWidth / 6);
            if (value.length() <= maxChars) return value;
            return maxChars <= 3 ? value.substring(0, maxChars)
                    : value.substring(0, maxChars - 3).stripTrailing() + "...";
        }
        if (metrics.stringWidth(value) <= maxWidth) return value;
        String ellipsis = "...";
        int ellipsisWidth = metrics.stringWidth(ellipsis);
        if (maxWidth < ellipsisWidth) {
            String dots = ".";
            while (dots.length() < 3 && metrics.stringWidth(dots + ".") <= maxWidth) dots += ".";
            return metrics.stringWidth(dots) <= maxWidth ? dots : "";
        }
        int low = 0;
        int high = value.length();
        while (low < high) {
            int mid = (low + high + 1) / 2;
            String candidate = value.substring(0, mid).stripTrailing() + ellipsis;
            if (metrics.stringWidth(candidate) <= maxWidth) low = mid;
            else high = mid - 1;
        }
        return value.substring(0, low).stripTrailing() + ellipsis;
    }

    static List<String> wrap(FontMetrics metrics, String text, int maxWidth, int maxLines) {
        List<String> result = new ArrayList<>();
        if (text == null || maxWidth <= 0 || maxLines <= 0) return result;
        String normalized = text.replace("\r", "");
        List<String> tokens = new ArrayList<>();
        for (String paragraph : normalized.split("\n", -1)) {
            String trimmed = paragraph.trim().replaceAll("\\s+", " ");
            if (trimmed.isEmpty()) {
                tokens.add("\n");
                continue;
            }
            for (String word : trimmed.split(" ")) {
                tokens.addAll(splitWord(metrics, word, maxWidth));
            }
            tokens.add("\n");
        }

        StringBuilder line = new StringBuilder();
        int tokenIndex = 0;
        while (tokenIndex < tokens.size()) {
            String token = tokens.get(tokenIndex++);
            if ("\n".equals(token)) {
                result.add(line.toString());
                line.setLength(0);
                if (result.size() >= maxLines) break;
                continue;
            }
            String candidate = line.length() == 0 ? token : line + " " + token;
            if (width(metrics, candidate) <= maxWidth) {
                if (line.length() > 0) line.append(' ');
                line.append(token);
            } else {
                if (line.length() > 0) result.add(line.toString());
                line.setLength(0);
                line.append(token);
                if (result.size() >= maxLines) break;
            }
        }
        if (result.size() < maxLines && line.length() > 0) result.add(line.toString());
        while (!result.isEmpty() && result.get(result.size() - 1).isEmpty()) {
            result.remove(result.size() - 1);
        }
        boolean truncated = tokenIndex < tokens.size()
                || result.size() == maxLines && line.length() > 0
                        && (result.isEmpty() || !line.toString().equals(result.get(result.size() - 1)));
        if (truncated && !result.isEmpty()) {
            int last = result.size() - 1;
            String remaining = remainingText(tokens, Math.max(0, tokenIndex - 1));
            String combined = result.get(last);
            if (!remaining.isBlank() && !combined.contains(remaining)) combined += " " + remaining;
            result.set(last, fit(metrics, combined, maxWidth));
        }
        return result;
    }

    static int centeredX(FontMetrics metrics, String text, int x, int width) {
        int textWidth = width(metrics, text == null ? "" : text);
        return x + Math.max(0, (width - textWidth) / 2);
    }

    static Rectangle clamp(Rectangle desired, Rectangle bounds, int margin) {
        if (desired == null) return new Rectangle(bounds.x + margin, bounds.y + margin, 0, 0);
        int safeMargin = Math.max(0, margin);
        int maxWidth = Math.max(0, bounds.width - safeMargin * 2);
        int maxHeight = Math.max(0, bounds.height - safeMargin * 2);
        int width = Math.max(0, Math.min(desired.width, maxWidth));
        int height = Math.max(0, Math.min(desired.height, maxHeight));
        int minX = bounds.x + safeMargin;
        int minY = bounds.y + safeMargin;
        int maxX = bounds.x + bounds.width - safeMargin - width;
        int maxY = bounds.y + bounds.height - safeMargin - height;
        return new Rectangle(clamp(desired.x, minX, Math.max(minX, maxX)),
                clamp(desired.y, minY, Math.max(minY, maxY)), width, height);
    }

    static Rectangle chooseLeastOverlapping(List<Rectangle> candidates,
            List<Rectangle> obstacles, Rectangle bounds) {
        Rectangle best = null;
        double bestScore = Double.POSITIVE_INFINITY;
        Rectangle preferred = candidates == null || candidates.isEmpty() ? null : candidates.get(0);
        if (preferred == null) return new Rectangle(bounds.x, bounds.y, 0, 0);
        for (Rectangle raw : candidates) {
            Rectangle candidate = clamp(raw, bounds, 2);
            double score = Math.hypot(candidate.getCenterX() - preferred.getCenterX(),
                    candidate.getCenterY() - preferred.getCenterY());
            if (obstacles != null) {
                for (Rectangle obstacle : obstacles) {
                    if (obstacle == null) continue;
                    Rectangle intersection = candidate.intersection(obstacle);
                    if (!intersection.isEmpty()) {
                        score += intersection.width * (double) intersection.height * 4.0 + 250.0;
                    }
                }
            }
            if (score < bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private static List<String> splitWord(FontMetrics metrics, String word, int maxWidth) {
        List<String> chunks = new ArrayList<>();
        String remaining = word;
        while (!remaining.isEmpty() && width(metrics, remaining) > maxWidth) {
            int low = 1;
            int high = remaining.length();
            while (low < high) {
                int mid = (low + high + 1) / 2;
                if (width(metrics, remaining.substring(0, mid)) <= maxWidth) low = mid;
                else high = mid - 1;
            }
            chunks.add(remaining.substring(0, low));
            remaining = remaining.substring(low);
        }
        if (!remaining.isEmpty()) chunks.add(remaining);
        return chunks;
    }

    private static String remainingText(List<String> tokens, int start) {
        StringBuilder text = new StringBuilder();
        for (int i = start; i < tokens.size(); i++) {
            String token = tokens.get(i);
            if ("\n".equals(token)) continue;
            if (text.length() > 0) text.append(' ');
            text.append(token);
            if (text.length() > 240) break;
        }
        return text.toString();
    }

    private static int width(FontMetrics metrics, String text) {
        return metrics == null ? text.length() * 6 : metrics.stringWidth(text);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
