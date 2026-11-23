package com.guidinglight.nexusquant.common.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;

/**
 * ProcessEnvironmentResolver 统一解析当前进程可见的运行时环境变量。
 * <p>
 * Why:
 * `mvn spring-boot:run`、IDE Run Configuration 和外部脚本都不会天然把仓库根目录 `.env` 注入成 OS 环境变量，
 * 但本项目的交易所 adapter 运行时配置直接读取进程环境。
 * 这里显式把 `.env`、System properties 和 System env 合并成同一份可复用视图，
 * 避免 local 启动链“文件里有凭证，但进程里看不到”的漂移继续污染恢复链和 E2E 验收。
 */
public final class ProcessEnvironmentResolver {

    private static final String DOT_ENV_FILE_NAME = ".env";
    private static final int MAX_PARENT_SCAN_DEPTH = 6;

    private ProcessEnvironmentResolver() {
    }

    /**
     * 解析当前进程的环境视图。
     *
     * @return 合并后的环境变量快照，优先级为 `.env` < System properties < OS env
     */
    public static Map<String, String> resolveForCurrentProcess() {
        return resolve(System.getenv(), System.getProperties(), Path.of("").toAbsolutePath());
    }

    static Map<String, String> resolve(Map<String, String> systemEnv, Properties systemProperties, Path workingDirectory) {
        Objects.requireNonNull(systemEnv, "systemEnv must not be null");
        Objects.requireNonNull(systemProperties, "systemProperties must not be null");
        Objects.requireNonNull(workingDirectory, "workingDirectory must not be null");

        Map<String, String> resolved = new LinkedHashMap<>();
        findNearestDotEnv(workingDirectory).ifPresent(dotEnvPath -> resolved.putAll(parseDotEnv(dotEnvPath)));
        for (String propertyName : systemProperties.stringPropertyNames()) {
            resolved.put(propertyName, systemProperties.getProperty(propertyName));
        }
        resolved.putAll(systemEnv);
        return Map.copyOf(resolved);
    }

    static Optional<Path> findNearestDotEnv(Path startDirectory) {
        Path current = startDirectory.toAbsolutePath().normalize();
        for (int depth = 0; depth <= MAX_PARENT_SCAN_DEPTH && current != null; depth++) {
            Path candidate = current.resolve(DOT_ENV_FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    static Map<String, String> parseDotEnv(Path dotEnvPath) {
        Objects.requireNonNull(dotEnvPath, "dotEnvPath must not be null");
        Map<String, String> parsed = new LinkedHashMap<>();
        try {
            List<String> lines = Files.readAllLines(dotEnvPath);
            for (String line : lines) {
                String trimmed = line == null ? "" : line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int delimiterIndex = trimmed.indexOf('=');
                if (delimiterIndex <= 0) {
                    continue;
                }
                String key = trimmed.substring(0, delimiterIndex).trim();
                String value = trimmed.substring(delimiterIndex + 1).trim();
                parsed.put(key, stripQuotes(value));
            }
        } catch (IOException ex) {
            throw new IllegalStateException("failed to read .env from " + dotEnvPath, ex);
        }
        return Map.copyOf(parsed);
    }

    private static String stripQuotes(String value) {
        if (value.length() >= 2) {
            boolean doubleQuoted = value.startsWith("\"") && value.endsWith("\"");
            boolean singleQuoted = value.startsWith("'") && value.endsWith("'");
            if (doubleQuoted || singleQuoted) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }
}
