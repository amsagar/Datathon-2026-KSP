package com.ksp.agent.chat.skillupdate;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Validates skill-update proposals before showing them to the admin — catches common model mistakes
 * like rewriting a CSV with a different schema instead of applying a minimal edit.
 *
 * <p>The generic CSV checks (header preservation, existing-row preservation) apply to any file.
 * Domain-specific rules for a particular reference file (e.g. a future crime-code taxonomy CSV)
 * can be added via {@link #FILE_RULES} without touching the generic pipeline.
 */
public final class SkillUpdateProposalValidator {

    /** A pluggable, filename-scoped rule. {@code header} is the proposed content's raw header row. */
    public interface FileRule {
        boolean appliesTo(String filePath);

        ValidationResult validate(String header, String proposedContent);
    }

    // No domain-specific reference-file rules exist yet — add entries here as new skill CSVs
    // acquire their own required-column/format conventions.
    private static final List<FileRule> FILE_RULES = List.of();

    private SkillUpdateProposalValidator() {
    }

    public record ValidationResult(boolean valid, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult reject(String message) {
            return new ValidationResult(false, message);
        }
    }

    public static ValidationResult validate(String filePath, String currentContent, String proposedContent) {
        if (currentContent == null || proposedContent == null) {
            return ValidationResult.ok();
        }
        if (!isCsv(filePath)) {
            return ValidationResult.ok();
        }
        String currentHeader = csvHeader(currentContent);
        String proposedHeader = csvHeader(proposedContent);
        if (currentHeader == null || proposedHeader == null) {
            return ValidationResult.ok();
        }
        if (!headersEquivalent(currentHeader, proposedHeader)) {
            return ValidationResult.reject(
                    "Proposal rejected: CSV header must stay unchanged. Current header is ["
                            + currentHeader + "] but the proposal uses ["
                            + proposedHeader + "]. Read the current file with getFileContent (or from "
                            + "the tool's context), copy it exactly, and apply only the requested row edit.");
        }
        ValidationResult rowsPreserved = validateExistingRowsPreserved(currentContent, proposedContent);
        if (!rowsPreserved.valid()) {
            return rowsPreserved;
        }
        for (FileRule rule : FILE_RULES) {
            if (rule.appliesTo(filePath)) {
                ValidationResult result = rule.validate(proposedHeader, proposedContent);
                if (!result.valid()) {
                    return result;
                }
            }
        }
        return ValidationResult.ok();
    }

    /** Every existing data row must appear unchanged in the proposal (additions allowed). */
    private static ValidationResult validateExistingRowsPreserved(String currentContent, String proposedContent) {
        List<String> currentRows = dataRows(currentContent);
        List<String> proposedRows = dataRows(proposedContent);
        for (String row : currentRows) {
            if (!proposedRows.contains(row)) {
                return ValidationResult.reject(
                        "Proposal rejected: an existing CSV row was removed or altered: ["
                                + row + "]. Copy the current file verbatim and apply only the "
                                + "requested addition or edit.");
            }
        }
        return ValidationResult.ok();
    }

    private static List<String> dataRows(String content) {
        List<String> lines = Arrays.stream(content.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
        if (lines.size() <= 1) {
            return List.of();
        }
        return lines.subList(1, lines.size());
    }

    private static boolean isCsv(String filePath) {
        return filePath != null && filePath.toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    private static String csvHeader(String content) {
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return null;
    }

    private static boolean headersEquivalent(String current, String proposed) {
        return normalizeHeaderRow(current).equals(normalizeHeaderRow(proposed));
    }

    private static String normalizeHeaderRow(String header) {
        return Arrays.stream(header.split(","))
                .map(SkillUpdateProposalValidator::normalizeHeaderCell)
                .collect(Collectors.joining(","));
    }

    private static String normalizeHeaderCell(String cell) {
        return cell.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
    }
}
