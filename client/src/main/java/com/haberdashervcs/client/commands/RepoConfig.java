package com.haberdashervcs.client.commands;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;

import com.google.common.base.Joiner;
import org.yaml.snakeyaml.Yaml;


public final class RepoConfig {


    private static final String DOT_HD_LOCAL_FOLDER_NAME = ".hdlocal";
    private static final String CONFIG_FILE_NAME = "hdlocal.conf";

    private static final String INIT_FILE_TEMPLATE = Joiner.on('\n').join(
            "---",
            "host: %s",
            "org: %s",
            "repo: %s",
            "token: %s",
            "");


    public static Optional<RepoConfig> find() throws IOException {
        Path current = Paths.get("");
        while (current != null) {
            Path configFolder = current.resolve(DOT_HD_LOCAL_FOLDER_NAME);
            if (configFolder.toFile().exists()) {
                Path configFile = configFolder.resolve(CONFIG_FILE_NAME);
                String yamlContents = Files.readString(configFile, StandardCharsets.UTF_8);
                return Optional.of(parseConfig(current, yamlContents));
            } else {
                current = current.getParent();
            }
        }
        return Optional.empty();
    }


    static RepoConfig create(String host, String org, String repo, String cliToken) throws IOException {
        Path repoDir = Paths.get(repo);
        Files.createDirectory(repoDir);

        Path dotFolder = repoDir.resolve(DOT_HD_LOCAL_FOLDER_NAME);
        Files.createDirectory(dotFolder);

        Path configFile = dotFolder.resolve(CONFIG_FILE_NAME);

        String configContents = String.format(
                INIT_FILE_TEMPLATE,
                host, org, repo, cliToken);
        Files.write(configFile, configContents.getBytes(StandardCharsets.UTF_8));

        return parseConfig(repoDir, configContents);
    }


    private static RepoConfig parseConfig(Path repoRoot, String yamlContents) {
        Yaml yaml = new Yaml();
        Map<String, String> parsed = yaml.load(yamlContents);
        return new RepoConfig(
                repoRoot,
                parsed.get("host"),
                parsed.get("org"),
                parsed.get("repo"),
                parsed.get("token"));
    }


    private final Path root;
    private final String host;
    private final String org;
    private final String repo;
    private final String cliToken;

    private RepoConfig(Path root, String host, String org, String repo, String cliToken) {
        this.root = root;
        this.host = host;
        this.org = org;
        this.repo = repo;
        this.cliToken = cliToken;
    }

    public Path getRoot() {
        return root;
    }

    public Path getDotHdLocalFolder() {
        return root.resolve(DOT_HD_LOCAL_FOLDER_NAME);
    }

    public String getHost() {
        return host;
    }

    public String getOrg() {
        return org;
    }

    public String getRepo() {
        return repo;
    }

    public String getCliToken() {
        return cliToken;
    }

    public boolean isHdInternalPath(Path localPath) {
        // TODO! Make this better...
        String name = localPath.getFileName().toString();
        if (name.equals(DOT_HD_LOCAL_FOLDER_NAME)) {
            return true;
        } else {
            return false;
        }
    }
}
