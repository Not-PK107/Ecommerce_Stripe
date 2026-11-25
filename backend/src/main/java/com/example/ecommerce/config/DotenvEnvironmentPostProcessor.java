package com.example.ecommerce.config;

import org.jspecify.annotations.NonNull;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads entries from the backend {@code .env} file into Spring's {@link ConfigurableEnvironment}
 * after OS environment variables so external environment variables always take precedence.
 */
public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    public static final String DOTENV_PROPERTY_SOURCE_NAME =
            "dotenvPropertySource-" + StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;

    private static final String DOTENV_PATH_PROPERTY = "app.dotenv.path";
    private static final String DOTENV_REQUIRED_PROPERTY = "app.dotenv.required";

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(@NonNull ConfigurableEnvironment environment, @NonNull SpringApplication application) {
        if (environment.getPropertySources().contains(DOTENV_PROPERTY_SOURCE_NAME)) {
            return;
        }

        boolean required = Boolean.parseBoolean(environment.getProperty(DOTENV_REQUIRED_PROPERTY, "false"));
        Path envFile = resolveDotenvFile(environment);
        if (envFile == null) {
            if (required) {
                throw new IllegalStateException(
                        "Required .env file could not be found. Expected backend/.env or .env in the project directory.");
            }
            return;
        }

        if (!Files.isReadable(envFile)) {
            throw new IllegalStateException("Found .env file at " + envFile + " but it is not readable.");
        }

        Map<String, Object> entries = parseDotenvFile(envFile);
        if (entries.isEmpty()) {
            return;
        }

        var propertySource = new SystemEnvironmentPropertySource(DOTENV_PROPERTY_SOURCE_NAME, entries);
        var sources = environment.getPropertySources();
        if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, propertySource);
        } else if (sources.contains(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)) {
            sources.addAfter(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME, propertySource);
        } else {
            sources.addFirst(propertySource);
        }
    }

    static Path resolveDotenvFile(ConfigurableEnvironment environment) {
        String configuredPath = environment.getProperty(DOTENV_PATH_PROPERTY);
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path explicit = Path.of(configuredPath).toAbsolutePath().normalize();
            return Files.isRegularFile(explicit) ? explicit : null;
        }
        Path cwd = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        return findDotenvFrom(cwd);
    }

    static Path findDotenvFrom(Path startDir) {
        Path current = startDir;
        while (current != null) {
            Path direct = current.resolve(".env");
            if (Files.isRegularFile(direct) && Files.isRegularFile(current.resolve("pom.xml"))) {
                return direct;
            }
            Path backendEnv = current.resolve("backend/.env");
            if (Files.isRegularFile(backendEnv) && Files.isRegularFile(current.resolve("backend/pom.xml"))) {
                return backendEnv;
            }
            current = current.getParent();
        }
        return null;
    }

    static Map<String, Object> parseDotenvFile(Path envFile) {
        List<String> lines;
        try {
            lines = Files.readAllLines(envFile, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to read .env file at " + envFile, ex);
        }

        Map<String, Object> parsed = new LinkedHashMap<>();
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.startsWith("export ")) {
                line = line.substring("export ".length()).trim();
            }

            int equalsIdx = line.indexOf('=');
            if (equalsIdx <= 0) {
                continue;
            }

            String key = line.substring(0, equalsIdx).trim();
            String value = stripSurroundingQuotes(line.substring(equalsIdx + 1).trim());
            if (key.isEmpty() || isTemplatePlaceholder(value)) {
                continue;
            }

            parsed.put(key, value);
        }
        return Map.copyOf(parsed);
    }

    private static String stripSurroundingQuotes(String value) {
        if (value.length() >= 2) {
            char first = value.charAt(0);
            char last = value.charAt(value.length() - 1);
            if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
                return value.substring(1, value.length() - 1);
            }
        }
        return value;
    }

    private static boolean isTemplatePlaceholder(String value) {
        return value.startsWith("${") && value.contains("}");
    }
}
