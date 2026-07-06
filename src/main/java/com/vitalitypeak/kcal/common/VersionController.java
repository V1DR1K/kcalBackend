package com.vitalitypeak.kcal.common;

import java.util.Map;

import org.springframework.boot.info.BuildProperties;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/version")
public class VersionController {

    private final BuildProperties buildProperties;
    private final String gitHash;

    public VersionController(BuildProperties buildProperties) {
        this.buildProperties = buildProperties;
        this.gitHash = loadGitHash();
    }

    @GetMapping
    public Map<String, String> getVersion() {
        return Map.of(
                "version", buildProperties.getVersion(),
                "buildTime", buildProperties.getTime().toString(),
                "gitHash", gitHash);
    }

    private String loadGitHash() {
        try {
            var resource = new ClassPathResource("git.properties");
            if (!resource.exists()) return "unknown";
            var props = new java.util.Properties();
            try (var is = resource.getInputStream()) {
                props.load(is);
            }
            return props.getProperty("git.commit.id.abbrev", "unknown");
        } catch (Exception e) {
            return "unknown";
        }
    }
}
