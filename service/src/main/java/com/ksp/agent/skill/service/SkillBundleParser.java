package com.ksp.agent.skill.service;

import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Parses an uploaded skill into the set of files to store in Blob plus its frontmatter
 * metadata. Accepts either a bare {@code SKILL.md} or a {@code .zip} bundle. System
 * metadata files added by OSes/archivers (e.g. {@code __MACOSX/}, {@code .DS_Store})
 * are ignored, and zip-slip path traversal is rejected.
 */
@Component
public class SkillBundleParser {

    private static final String MANIFEST = "SKILL.md";

    /** relativePath -> file bytes (always contains SKILL.md at the root), plus parsed name/description. */
    public record ParsedSkill(String name, String description, Map<String, byte[]> files) {}

    public ParsedSkill parse(String filename, byte[] content) {
        String lower = filename == null ? "" : filename.toLowerCase();
        Map<String, byte[]> files = lower.endsWith(".zip")
                ? parseZip(content)
                : parseSingleMarkdown(content);

        byte[] manifest = files.get(MANIFEST);
        if (manifest == null) {
            throw new IllegalArgumentException("Skill upload must contain a SKILL.md file at its root.");
        }
        Frontmatter fm = parseFrontmatter(new String(manifest, StandardCharsets.UTF_8));
        return new ParsedSkill(fm.name, fm.description, files);
    }

    private Map<String, byte[]> parseSingleMarkdown(byte[] content) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put(MANIFEST, content);
        return files;
    }

    private Map<String, byte[]> parseZip(byte[] content) {
        Map<String, byte[]> raw = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName().replace('\\', '/');
                if (entry.isDirectory() || isSystemMetadata(name)) {
                    continue;
                }
                if (name.startsWith("/") || name.contains("../") || name.equals("..")) {
                    throw new IllegalArgumentException("Illegal path in zip entry: " + name);
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                raw.put(name, out.toByteArray());
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read zip bundle: " + e.getMessage(), e);
        }
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("Zip bundle is empty (after filtering system files).");
        }
        return stripSingleTopFolder(raw);
    }

    /** If every entry lives under one common top-level folder, strip it so SKILL.md sits at the root. */
    private Map<String, byte[]> stripSingleTopFolder(Map<String, byte[]> raw) {
        if (raw.containsKey(MANIFEST)) {
            return raw;
        }
        String common = null;
        for (String name : raw.keySet()) {
            int slash = name.indexOf('/');
            if (slash < 0) {
                return raw; // a root-level file that isn't under a single folder
            }
            String top = name.substring(0, slash);
            if (common == null) {
                common = top;
            } else if (!common.equals(top)) {
                return raw; // multiple top-level folders, leave as-is
            }
        }
        if (common == null) {
            return raw;
        }
        String prefix = common + "/";
        Map<String, byte[]> stripped = new LinkedHashMap<>();
        for (Map.Entry<String, byte[]> e : raw.entrySet()) {
            stripped.put(e.getKey().substring(prefix.length()), e.getValue());
        }
        return stripped;
    }

    private boolean isSystemMetadata(String name) {
        if (name.startsWith("__MACOSX/") || name.contains("/__MACOSX/")) {
            return true;
        }
        String base = name.substring(name.lastIndexOf('/') + 1);
        return base.equals(".DS_Store")
                || base.equals("Thumbs.db")
                || base.equals("desktop.ini")
                || base.startsWith("._");
    }

    /** Parsed YAML frontmatter fields from a SKILL.md body. */
    public record SkillFrontmatter(String name, String description, boolean defaultEnabled) {}

    public SkillFrontmatter readFrontmatter(String markdown) {
        Frontmatter fm = parseFrontmatter(markdown);
        return new SkillFrontmatter(fm.name, fm.description, fm.defaultEnabled);
    }

    private record Frontmatter(String name, String description, boolean defaultEnabled) {}

    private Frontmatter parseFrontmatter(String markdown) {
        String name = null;
        String description = null;
        boolean defaultEnabled = false;
        String text = markdown.stripLeading();
        if (text.startsWith("---")) {
            int firstNl = text.indexOf('\n');
            int end = text.indexOf("\n---", firstNl);
            if (firstNl > 0 && end > firstNl) {
                String block = text.substring(firstNl + 1, end);
                for (String line : block.split("\n")) {
                    int colon = line.indexOf(':');
                    if (colon <= 0) {
                        continue;
                    }
                    String key = line.substring(0, colon).trim().toLowerCase();
                    String value = unquote(line.substring(colon + 1).trim());
                    if (key.equals("name")) {
                        name = value;
                    } else if (key.equals("description")) {
                        description = value;
                    } else if (key.equals("default")) {
                        defaultEnabled = Boolean.parseBoolean(value);
                    }
                }
            }
        }
        return new Frontmatter(name, description, defaultEnabled);
    }

    private String unquote(String value) {
        if (value.length() >= 2
                && ((value.startsWith("\"") && value.endsWith("\""))
                || (value.startsWith("'") && value.endsWith("'")))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
