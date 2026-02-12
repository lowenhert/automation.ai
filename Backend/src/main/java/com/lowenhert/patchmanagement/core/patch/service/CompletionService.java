package com.lowenhert.patchmanagement.core.patch.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class CompletionService {

    @Value("${wipro.api.key}")
    private String apiKey;

    private static final String COMPLETION_ENDPOINT =
            "https://api.waip.wiprocms.com/v1.1/skills/completion/query";

    public String fetchCompletion(String question) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;

        try {
            // Build JSON safely using ObjectMapper to avoid bad escaping
            ObjectMapper mapper = new ObjectMapper();

            Map<String, Object> message = Map.of("content", question, "role", "user");

            Map<String, Object> skillParams = Map.ofEntries(
                    Map.entry("model_name", "gpt-4"),
                    Map.entry("retrieval_chain", "custom"),
                    Map.entry("max_output_tokens", 2000),
                    Map.entry("temperature", 0),
                    Map.entry("top_p", 1),
                    Map.entry("frequency_penalty", 0),
                    Map.entry("presence_penalty", 0),
                    Map.entry("emb_type", "openai"),
                    Map.entry("top_k", 5),
                    Map.entry("return_sources", false)
            );

            Map<String, Object> payloadMap = Map.of(
                    "messages", List.of(message),
                    "skill_parameters", skillParams
            );

            String payload = mapper.writeValueAsString(payloadMap);

            // Print payload for debugging
            System.out.println("REQUEST BODY:\n" + payload);

            // Setup connection
            URL url = new URL(COMPLETION_ENDPOINT);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Accept", "application/json");
            connection.setDoOutput(true);

            // Send JSON body
            try (OutputStream os = connection.getOutputStream()) {
                os.write(payload.getBytes());
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            // Read response based on code
            InputStream responseStream = (responseCode >= 200 && responseCode < 300)
                    ? connection.getInputStream()
                    : connection.getErrorStream();

            reader = new BufferedReader(new InputStreamReader(responseStream));
            StringBuilder fullResponse = new StringBuilder();
            String line;
            Pattern pattern = Pattern.compile("\"content\"\\s*:\\s*\"(.*?)\"");

            while ((line = reader.readLine()) != null) {
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    fullResponse.append(matcher.group(1));
                }
            }

            // If 400, show raw response for debugging
            if (responseCode >= 400) {
                return "Error " + responseCode + ": " + fullResponse;
            }

            return fullResponse.toString().trim().isEmpty()
                    ? "No response content found."
                    : fullResponse.toString().trim();

        } catch (Exception e) {
            e.printStackTrace();
            return "Error fetching response: " + e.getMessage();
        } finally {
            try {
                if (reader != null) reader.close();
                if (connection != null) connection.disconnect();
            } catch (IOException ignored) {}
        }
    }
}
