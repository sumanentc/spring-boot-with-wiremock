package com.wiremock.example.springbootwiremock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.common.ConsoleNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.github.tomakehurst.wiremock.recording.RecordSpec;
import com.github.tomakehurst.wiremock.recording.RecordingStatus;
import com.wiremock.example.springbootwiremock.config.WireMockConfig;
import com.wiremock.example.springbootwiremock.config.WireMockProxy;
import io.vavr.control.Try;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.util.CollectionUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles(value = "integration")
@SpringBootTest(classes = SpringBootWiremockApplication.class, webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@Slf4j
@AutoConfigureMockMvc
class SpringBootWiremockApplicationTests {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private WireMockConfig wireMockConfig;

    protected final ObjectMapper objectMapper = new ObjectMapper();

    private final List<WireMockServer> servers = new ArrayList<>();


    Function<WireMockProxy, WireMockServer> getMockServer =
            (WireMockProxy proxy) ->
                    new WireMockServer(
                            WireMockConfiguration.options()
                                    .port(proxy.getPort())
                                    .notifier(new ConsoleNotifier(true)));

    @BeforeEach
    public void startRecording() {
        List<WireMockProxy> proxies = wireMockConfig.getProxies();
        if (!CollectionUtils.isEmpty(proxies)) {
            for (WireMockProxy proxy : proxies) {
                WireMockServer wireMockServer = getMockServer.apply(proxy);
                wireMockServer.start();
                if (proxy.isRecording()) {
                    wireMockServer.startRecording(config(proxy.getUrl(), true));
                }
                servers.add(wireMockServer);
            }
        }
    }

    @AfterEach
    public void stopRecording() {
        if (!CollectionUtils.isEmpty(servers)) {
            for (WireMockServer server : servers) {
                if (server.getRecordingStatus().getStatus().equals(RecordingStatus.Recording)) {
                    server.stopRecording();
                }
                server.stop();
            }
        }
    }


    @ParameterizedTest
    @CsvSource({"india,recorded/expected-response.json"})
    void getUniversitiesForCountry(String country, String expectedResponseFilePath) throws Exception {
        String actualResponse = mockMvc.perform(get("/api/university")
                        .contentType("application/json")
                        .param("country", country)
                )
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Path currentFilePath = saveResult(expectedResponseFilePath, actualResponse);
        String expectedResponse = getExpectedResponse(expectedResponseFilePath);
        JSONAssert.assertEquals(expectedResponse, actualResponse, JSONCompareMode.LENIENT);
        cleanup(currentFilePath);
    }

    protected Path saveResult(String path, String result) throws IOException {
        String[] segments = path.split("/");
        // Grab the last segment
        String fileName = segments[segments.length - 1];
        File file = new File(this.getClass().getClassLoader().getResource(".").getFile() + fileName);
        Try<Path> saveRes =
                Try.of(
                        () -> {
                            try (Writer writer =
                                         new BufferedWriter(
                                                 new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8))) {
                                objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
                                Object json = objectMapper.readValue(result, Object.class);
                                writer.write(
                                        objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
                            }
                            return file.toPath();
                        });
        if (saveRes.isFailure()) {
            log.error("Not able to save the results ", saveRes.getCause());
            return null;
        }
        if (isRecordingEnabled()) {
            Path copied = Paths.get("src/test/resources/recorded/" + saveRes.get().getFileName());
            Files.copy(saveRes.get(), copied, StandardCopyOption.REPLACE_EXISTING);
        }
        return saveRes.get();
    }

    private boolean isRecordingEnabled() {
        List<WireMockProxy> proxies = wireMockConfig.getProxies();
        if (!CollectionUtils.isEmpty(proxies)) {
            return proxies.stream().filter(r -> r.isRecording())
                    .map(s -> s.isRecording())
                    .findAny().orElse(false);
        }
        return false;
    }

    protected void cleanup(Path path) throws IOException {
        boolean result = Files.deleteIfExists(path);
        if (result) {
            log.info("File is deleted!");
        } else {
            log.error("Sorry, unable to delete the file={}", path.getFileName());
        }
    }

    protected String getExpectedResponse(String recordedFilePath) throws IOException {
        Path resourceDirectory = Paths.get("src", "test", "resources");
        Path pathToFile = resourceDirectory.resolve(recordedFilePath);
        if (!pathToFile.toFile().exists()) {
            throw new IllegalArgumentException(
                    "Recorded file doesn't exists " + pathToFile.getFileName());
        }
        Object savedResult = objectMapper.readValue(pathToFile.toFile(), Object.class);
        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(savedResult);
    }

    private RecordSpec config(String recordingURL, boolean recordingEnabled) {
        return WireMock.recordSpec()
                .forTarget(recordingURL)
                .onlyRequestsMatching(RequestPatternBuilder.allRequests())
                .captureHeader("Accept")
                .makeStubsPersistent(recordingEnabled)
                .ignoreRepeatRequests()
                .matchRequestBodyWithEqualToJson(true, true)
                .build();
    }

}
