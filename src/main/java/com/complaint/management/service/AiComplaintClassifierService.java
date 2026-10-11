package com.complaint.management.service;

import com.complaint.management.dto.AiPredictionRequest;
import com.complaint.management.dto.AiPredictionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

@Service
public class AiComplaintClassifierService {

    private static final Logger logger = LoggerFactory.getLogger(AiComplaintClassifierService.class);
    private static final Set<String> CATEGORIES = Set.of(
            "TECHNICAL", "BILLING", "SERVICE", "SECURITY", "FACILITY", "ACADEMIC", "OTHER"
    );
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

    private final ObjectMapper objectMapper;
    private final String pythonCommand;
    private final Path scriptPath;
    private final Path modelDirectory;
    private final long timeoutSeconds;

    public AiComplaintClassifierService(
            ObjectMapper objectMapper,
            @Value("${ai.python-command:python}") String pythonCommand,
            @Value("${ai.script-path:ai/predict_complaint.py}") String scriptPath,
            @Value("${ai.process-timeout-seconds:30}") long timeoutSeconds
    ) {
        this.objectMapper = objectMapper;
        this.pythonCommand = resolvePythonCommand(pythonCommand);
        this.scriptPath = resolveScriptPath(scriptPath);
        this.modelDirectory = resolveModelDirectory(this.scriptPath);
        this.timeoutSeconds = timeoutSeconds;
    }

    public AiPredictionResponse predict(AiPredictionRequest request) {
        verifyModelFiles();

        Process process;
        try {
            process = new ProcessBuilder(
                    pythonCommand,
                    scriptPath.toString(),
                    "--title", request.title(),
                    "--description", request.description()
            ).start();
        } catch (IOException exception) {
            logger.error("Unable to start the local complaint prediction process", exception);
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The local AI prediction service could not be started"
            );
        }

        CompletableFuture<String> stdout = readAsync(process.getInputStream());
        CompletableFuture<String> stderr = readAsync(process.getErrorStream());
        try {
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                logger.error("Local complaint prediction process exceeded its {} second timeout", timeoutSeconds);
                throw new ResponseStatusException(
                        HttpStatus.GATEWAY_TIMEOUT,
                        "The local AI prediction timed out"
                );
            }

            String output = stdout.join();
            String errorOutput = stderr.join();
            if (process.exitValue() != 0) {
                logger.error("Local complaint prediction process failed with exit code {}: {}",
                        process.exitValue(), errorOutput);
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The local AI prediction process failed"
                );
            }

            return parseResponse(output);
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The local AI prediction was interrupted",
                    exception
            );
        } catch (CompletionException exception) {
            process.destroyForcibly();
            logger.error("Unable to read the local complaint prediction output", exception);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The local AI prediction returned an unreadable response"
            );
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private String resolvePythonCommand(String configuredPythonCommand) {
        String command = configuredPythonCommand == null ? "python" : configuredPythonCommand.trim();
        if (command.isEmpty()) {
            command = "python";
        }

        if (!"python".equalsIgnoreCase(command) && !"python3".equalsIgnoreCase(command)) {
            return command;
        }

        Path projectRoot = findProjectRoot();
        String executableName = System.getProperty("os.name").toLowerCase().contains("win") ? "python.exe" : "python";
        Path[] candidatePaths = new Path[] {
                projectRoot.resolve(".venv").resolve("Scripts").resolve(executableName),
                projectRoot.resolve(".venv").resolve("bin").resolve(executableName),
                projectRoot.resolve("backend").resolve(".venv").resolve("Scripts").resolve(executableName),
                projectRoot.resolve("backend").resolve(".venv").resolve("bin").resolve(executableName)
        };

        for (Path candidate : candidatePaths) {
            if (Files.isRegularFile(candidate) && hasRequiredPythonPackages(candidate.toString())) {
                logger.info("Using project-local Python interpreter at {}", candidate);
                return candidate.toString();
            }
        }

        return command;
    }

    private boolean hasRequiredPythonPackages(String interpreterPath) {
        try {
            Process process = new ProcessBuilder(
                    interpreterPath,
                    "-c",
                    "import pandas, numpy, sklearn, joblib; print('ok')"
            ).start();

            if (!process.waitFor(10, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                logger.warn("Python interpreter {} is missing required ML dependencies: {}", interpreterPath, errorOutput);
                return false;
            }
            return output.trim().equals("ok");
        } catch (IOException | InterruptedException exception) {
            logger.warn("Unable to validate Python interpreter {} for ML dependencies", interpreterPath, exception);
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    private Path resolveScriptPath(String configuredScriptPath) {
        Path configured = Path.of(configuredScriptPath == null ? "ai/predict_complaint.py" : configuredScriptPath);
        Path currentDirectory = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path projectRoot = findProjectRoot();

        Path[] candidatePaths = new Path[] {
                configured.isAbsolute() ? configured : currentDirectory.resolve(configured),
                configured.isAbsolute() ? configured : projectRoot.resolve(configured),
                configured.isAbsolute() ? configured : currentDirectory.resolve("backend").resolve(configured),
                configured.isAbsolute() ? configured : projectRoot.resolve("backend").resolve(configured)
        };

        for (Path candidate : candidatePaths) {
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        return configured.isAbsolute()
                ? configured.toAbsolutePath().normalize()
                : currentDirectory.resolve(configured).toAbsolutePath().normalize();
    }

    private Path resolveModelDirectory(Path resolvedScriptPath) {
        Path[] candidateDirectories = new Path[] {
                resolvedScriptPath.getParent().getParent().resolve("models"),
                findProjectRoot().resolve("models"),
                findProjectRoot().resolve("backend").resolve("models")
        };

        for (Path candidate : candidateDirectories) {
            if (Files.isDirectory(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        return resolvedScriptPath.getParent().getParent().resolve("models").toAbsolutePath().normalize();
    }

    private Path findProjectRoot() {
        Path root = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path current = root;
        while (current != null) {
            if (Files.isDirectory(current.resolve(".venv"))) {
                return current;
            }
            if (Files.isDirectory(current.resolve("backend")) && Files.isDirectory(current.resolve("backend").resolve("src"))) {
                return current;
            }
            current = current.getParent();
        }
        return root;
    }

    private void verifyModelFiles() {
        Path[] requiredFiles = {
                scriptPath,
                modelDirectory.resolve("category_model.joblib"),
                modelDirectory.resolve("priority_model.joblib"),
                modelDirectory.resolve("vectorizer.joblib")
        };

        for (Path file : requiredFiles) {
            if (!Files.isRegularFile(file)) {
                logger.error("Required local AI file is missing: {}", file);
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "The local AI model is not available"
                );
            }
        }
    }

    private CompletableFuture<String> readAsync(java.io.InputStream inputStream) {
        return CompletableFuture.supplyAsync(() -> {
            try (inputStream) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new UncheckedIOException(exception);
            }
        });
    }

    private AiPredictionResponse parseResponse(String output) {
        AiPredictionResponse response;
        try {
            response = objectMapper.readValue(output, AiPredictionResponse.class);
        } catch (JsonProcessingException exception) {
            logger.error("Local complaint prediction returned invalid JSON", exception);
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The local AI prediction returned invalid JSON"
            );
        }

        if (response == null
                || response.category() == null || !CATEGORIES.contains(response.category())
                || response.priority() == null || !PRIORITIES.contains(response.priority())
                || response.confidence() == null
                || !Double.isFinite(response.confidence())
                || response.confidence() < 0 || response.confidence() > 1) {
            logger.error("Local complaint prediction returned values outside the expected response format");
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "The local AI prediction returned an invalid response"
            );
        }
        return response;
    }
}
