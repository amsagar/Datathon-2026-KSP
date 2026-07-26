package com.ksp.agent.skill.runtime;

import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the directory of a working Python interpreter so skill shell commands
 * (e.g. {@code python3 scripts/foo.py}) can run even when the host's default
 * {@code python3}/{@code python} on PATH is the non-functional Microsoft Store execution-alias stub.
 *
 * <p>On a Windows host with no real Python installed, {@code %LOCALAPPDATA%\Microsoft\WindowsApps}
 * contains zero-byte {@code python.exe}/{@code python3.exe} shims that exit 255 with
 * "the system cannot find the file …". Those shims are skipped here: a candidate is only accepted
 * when running {@code <candidate> --version} returns exit 0. The resolved directory is then
 * prepended to PATH by {@link SkillWorkspaceShellToolCallback} so the real interpreter wins.
 */
@Slf4j
public final class PythonLauncher {

    private static final boolean WINDOWS =
            System.getProperty("os.name", "").toLowerCase().contains("win");

    private static final List<String> CANDIDATE_NAMES = WINDOWS
            ? List.of("python.exe", "python3.exe")
            : List.of("python3", "python");

    /** Resolved lazily once. {@code null} = not yet probed, {@code ""} = probed, none found. */
    private static volatile String cachedDir;

    private PythonLauncher() {
    }

    /** Absolute directory of a working interpreter, or an empty string when none was found. */
    public static String interpreterDir() {
        String dir = cachedDir;
        if (dir == null) {
            synchronized (PythonLauncher.class) {
                dir = cachedDir;
                if (dir == null) {
                    // Probing must never break a shell command: any failure resolves to "no
                    // interpreter" (callers then skip the PATH prefix and run the command as-is).
                    try {
                        dir = probe();
                    } catch (RuntimeException e) {
                        log.warn("Python interpreter probe failed; running shell commands without "
                                + "a PATH override: {}", e.toString());
                        dir = "";
                    }
                    cachedDir = dir;
                }
            }
        }
        return dir;
    }

    private static String probe() {
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String entry : pathEnv.split(File.pathSeparator)) {
                if (entry.isBlank() || isWindowsStoreShim(entry)) {
                    continue;
                }
                // A single malformed/quoted PATH entry (e.g. on Windows, a stray quote making
                // Path.of throw InvalidPathException) must not abort the whole scan.
                try {
                    Path dir = Path.of(entry);
                    for (String name : CANDIDATE_NAMES) {
                        Path candidate = dir.resolve(name);
                        if (Files.isRegularFile(candidate) && runsCleanly(candidate.toString())) {
                            log.info("Resolved Python interpreter for skill scripts: {}", candidate);
                            return dir.toAbsolutePath().toString();
                        }
                    }
                } catch (RuntimeException e) {
                    log.debug("Skipping unparseable PATH entry '{}': {}", entry, e.toString());
                }
            }
        }
        // Windows-only fallback: the `py` launcher reports the real interpreter path.
        if (WINDOWS) {
            String viaPyLauncher = resolveViaPyLauncher();
            if (!viaPyLauncher.isBlank()) {
                return viaPyLauncher;
            }
        }
        log.warn("No working Python interpreter found on PATH. Skill shell scripts may fail. "
                + "Install Python and ensure it is on PATH (on Windows, disable the Microsoft Store "
                + "python/python3 'App execution aliases').");
        return "";
    }

    /** A real install never lives under the Store alias directory {@code …\WindowsApps}. */
    private static boolean isWindowsStoreShim(String pathEntry) {
        return WINDOWS && pathEntry.replace('\\', '/').toLowerCase().contains("/windowsapps");
    }

    private static String resolveViaPyLauncher() {
        try {
            Process p = new ProcessBuilder("py", "-3", "-c", "import sys;print(sys.executable)")
                    .redirectErrorStream(true)
                    .start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            if (p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0 && !out.isBlank()) {
                Path exe = Path.of(out);
                if (Files.isRegularFile(exe) && exe.getParent() != null) {
                    log.info("Resolved Python interpreter via 'py' launcher: {}", exe);
                    return exe.getParent().toAbsolutePath().toString();
                }
            } else {
                p.destroyForcibly();
            }
        } catch (Exception ignored) {
            // py launcher unavailable; fall through to WARN
        }
        return "";
    }

    private static boolean runsCleanly(String executable) {
        try {
            Process p = new ProcessBuilder(executable, "--version")
                    .redirectErrorStream(true)
                    .start();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return false;
            }
            return p.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }
}
