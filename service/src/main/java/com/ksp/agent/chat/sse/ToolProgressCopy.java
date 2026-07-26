package com.ksp.agent.chat.sse;

/**
 * Officer-facing progress lines for the chat SSE {@code status} channel.
 * Never includes raw tool names — maps tool activity to friendly localized copy.
 */
public final class ToolProgressCopy {

    private ToolProgressCopy() {}

    public static String forTool(String toolName, String lang) {
        boolean kn = isKn(lang);
        String name = toolName == null ? "" : toolName.toLowerCase();

        if (name.contains("crime_sql")
                || name.contains("crime_schema")
                || name.contains("sql")
                || name.contains("schema")) {
            return kn
                    ? "\u0c85\u0caa\u0cb0\u0cbe\u0ca7 \u0ca6\u0cbe\u0c96\u0cb2\u0cc6\u0c97\u0cb3\u0ca8\u0ccd\u0ca8\u0cc1 \u0caa\u0cb0\u0cbf\u0cb6\u0cc0\u0cb2\u0cbf\u0cb8\u0cb2\u0cbe\u0c97\u0cc1\u0ca4\u0ccd\u0ca4\u0cbf\u0ca6\u0cc6\u2026"
                    : "Querying crime records\u2026";
        }
        if (name.contains("remember") || name.contains("recall") || name.contains("memory")) {
            return kn
                    ? "\u0cb8\u0ccd\u0cae\u0cb0\u0ca3\u0cc6\u0caf\u0ca8\u0ccd\u0ca8\u0cc1 \u0ca8\u0cb5\u0cc0\u0c95\u0cb0\u0cbf\u0cb8\u0cb2\u0cbe\u0c97\u0cc1\u0ca4\u0ccd\u0ca4\u0cbf\u0ca6\u0cc6\u2026"
                    : "Updating memory\u2026";
        }
        if (name.contains("search_tools") || name.contains("invoke_tool")) {
            return kn
                    ? "\u0cb5\u0cbf\u0cb6\u0ccd\u0cb2\u0cc7\u0cb7\u0ca3\u0cc6 \u0cb8\u0cbe\u0ca7\u0ca8\u0c97\u0cb3\u0ca8\u0ccd\u0ca8\u0cc1 \u0c86\u0caf\u0ccd\u0c95\u0cc6\u0cae\u0cbe\u0ca1\u0cb2\u0cbe\u0c97\u0cc1\u0ca4\u0ccd\u0ca4\u0cbf\u0ca6\u0cc6\u2026"
                    : "Selecting analysis tools\u2026";
        }
        if (name.contains("network") || name.contains("risk") || name.contains("offender")) {
            return kn
                    ? "\u0c85\u0caa\u0cb0\u0cbe\u0ca7 \u0c9c\u0cbe\u0cb2\u0cb5\u0ca8\u0ccd\u0ca8\u0cc1 \u0cb5\u0cbf\u0cb6\u0ccd\u0cb2\u0cc7\u0cb7\u0cbf\u0cb8\u0cb2\u0cbe\u0c97\u0cc1\u0ca4\u0ccd\u0ca4\u0cbf\u0ca6\u0cc6\u2026"
                    : "Analyzing criminal networks\u2026";
        }
        if (name.contains("financial") || name.contains("transaction") || name.contains("money")) {
            return kn
                    ? "\u0cb9\u0ca3\u0c95\u0cbe\u0cb8\u0cc1 \u0c9c\u0cbe\u0ca1\u0cc1\u0c97\u0cb3\u0ca8\u0ccd\u0ca8\u0cc1 \u0caa\u0cb0\u0cbf\u0cb6\u0cc0\u0cb2\u0cbf\u0cb8\u0cb2\u0cbe\u0c97\u0cc1\u0ca4\u0ccd\u0ca4\u0cbf\u0ca6\u0cc6\u2026"
                    : "Tracing financial links\u2026";
        }
        if (name.contains("hotspot") || name.contains("forecast") || name.contains("map")) {
            return kn
                    ? "\u0caa\u0ccd\u0cb0\u0cb5\u0cc3\u0ca4\u0ccd\u0ca4\u0cbf \u0cae\u0ca4\u0ccd\u0ca4\u0cc1 \u0cb9\u0cbe\u0c9f\u0ccd\u200c\u0cb8\u0ccd\u0caa\u0cbe\u0c9f\u0ccd\u200c\u0c97\u0cb3\u0ca8\u0ccd\u0ca8\u0cc1 \u0caa\u0cb0\u0cbf\u0cb6\u0cc0\u0cb2\u0cbf\u0cb8\u0cb2\u0cbe\u0c97\u0cc1\u0ca4\u0ccd\u0ca4\u0cbf\u0ca6\u0cc6\u2026"
                    : "Checking trends and hotspots\u2026";
        }
        if (name.contains("rag") || name.contains("document") || name.contains("search")) {
            return kn
                    ? "\u0ca6\u0cbe\u0c96\u0cb2\u0cc6\u0c97\u0cb3\u0ca8\u0ccd\u0ca8\u0cc1 \u0cb9\u0cc1\u0ca1\u0cc1\u0c95\u0cb2\u0cbe\u0c97\u0cc1\u0ca4\u0ccd\u0ca4\u0cbf\u0ca6\u0cc6\u2026"
                    : "Searching documents\u2026";
        }
        return working(lang);
    }

    public static String working(String lang) {
        return isKn(lang)
                ? "\u0c95\u0cc6\u0cb2\u0cb8 \u0cae\u0cbe\u0ca1\u0cc1\u0ca4\u0ccd\u0ca4\u0cbf\u0ca6\u0cc6\u2026"
                : "Working\u2026";
    }

    public static String answerQuestions(String lang) {
        return isKn(lang)
                ? "\u0c95\u0cc6\u0cb3\u0c97\u0cbf\u0ca8 \u0caa\u0ccd\u0cb0\u0cb6\u0ccd\u0ca8\u0cc6\u0c97\u0cb3\u0cbf\u0c97\u0cc6 \u0c89\u0ca4\u0ccd\u0ca4\u0cb0\u0cbf\u0cb8\u0cbf\u2026"
                : "Answer the questions below\u2026";
    }

    public static String reviewSkillUpdate(String lang) {
        return isKn(lang)
                ? "\u0caa\u0ccd\u0cb0\u0cb8\u0ccd\u0ca4\u0cbe\u0cb5\u0cbf\u0ca4 \u0c95\u0ccc\u0cb6\u0cb2 \u0ca8\u0cb5\u0cc0\u0c95\u0cb0\u0ca3\u0cb5\u0ca8\u0ccd\u0ca8\u0cc1 \u0caa\u0cb0\u0cbf\u0cb6\u0cc0\u0cb2\u0cbf\u0cb8\u0cbf\u2026"
                : "Review the proposed skill update below\u2026";
    }

    private static boolean isKn(String lang) {
        return lang != null && "kn".equalsIgnoreCase(lang.strip());
    }
}
