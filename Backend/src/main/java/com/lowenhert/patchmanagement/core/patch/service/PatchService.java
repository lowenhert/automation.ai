package com.lowenhert.patchmanagement.core.patch.service;

import com.lowenhert.patchmanagement.core.patch.model.PatchLog;
import com.lowenhert.patchmanagement.core.patch.model.PatchSchedule;
import com.lowenhert.patchmanagement.core.patch.model.Server;
import com.lowenhert.patchmanagement.core.patch.repository.PatchLogRepository;
import com.lowenhert.patchmanagement.core.patch.repository.PatchScheduleRepository;
import com.lowenhert.patchmanagement.core.patch.repository.ServerRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Base64;
import org.springframework.web.client.RestClientException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class PatchService {

	private static final Logger logger = LoggerFactory.getLogger(PatchService.class);

	private final ServerRepository serverRepo;
	private final PatchLogRepository logRepo;
	private final PatchScheduleRepository scheduleRepo;
	private final RestTemplate restTemplate;
	private final CompletionService completionService;

	@Value("${awx.base-url}")
	private String awxUrl;

	@Value("${awx.username}")
	private String awxUser;

	@Value("${awx.password}")
	private String awxPass;

	@Value("${awx.job-template-id}")
	private int jobTemplateId;

	public PatchService(ServerRepository serverRepo, PatchLogRepository logRepo, PatchScheduleRepository scheduleRepo, CompletionService completionService) {
		this.serverRepo = serverRepo;
		this.logRepo = logRepo;
		this.scheduleRepo = scheduleRepo;
		this.restTemplate = new RestTemplate();
		this.completionService= completionService;
	}

	/**
	 * =============================== Run Patch Immediately via AWX
	 * ===============================
	 */
	public String runPatch(Long serverId) {
		Server server = serverRepo.findById(serverId)
				.orElseThrow(() -> new IllegalArgumentException("Server not found"));

		try {
			String jobId = launchAwxJob(server);
			monitorAndSaveJobLogs(serverId, jobId);
			return "AWX Job launched successfully with ID: " + jobId;
		} catch (Exception e) {
			logger.error("Error launching AWX job: {}", e.getMessage());
			return "Error launching AWX job: " + e.getMessage();
		}
	}

	private String launchAwxJob(Server server) {
		String launchUrl = awxUrl + "/job_templates/" + jobTemplateId + "/launch/";
		HttpHeaders headers = createHeaders();

		Map<String, Object> body = new HashMap<>();
		body.put("extra_vars", Map.of("target_host", server.getIp(), "ansible_user", server.getSshUser()));

		HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
		ResponseEntity<Map> response = restTemplate.postForEntity(launchUrl, request, Map.class);

		if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
			return String.valueOf(response.getBody().get("job"));
		} else {
			throw new RuntimeException("AWX launch failed: " + response.getStatusCode());
		}
	}

	@Async
	public void monitorAndSaveJobLogs(Long serverId, String jobId) {
		try {
			String jobUrl = awxUrl + "/jobs/" + jobId + "/";
			String stdoutUrl = awxUrl + "/jobs/" + jobId + "/stdout/?format=txt";

			HttpHeaders headers = createHeaders();

			String status;
			do {
				ResponseEntity<Map> jobResp = restTemplate.exchange(jobUrl, HttpMethod.GET, new HttpEntity<>(headers),
						Map.class);
				status = (String) jobResp.getBody().get("status");
				Thread.sleep(5000);
			} while (status.equalsIgnoreCase("running") || status.equalsIgnoreCase("pending"));

			ResponseEntity<String> logResp = restTemplate.exchange(stdoutUrl, HttpMethod.GET, new HttpEntity<>(headers),
					String.class);

			PatchLog log = PatchLog.builder().serverId(serverId).patchType("PATCH").status(status)
					.output(logResp.getBody()).createdAt(LocalDateTime.now()).build();
			logRepo.save(log);

		} catch (Exception e) {
			logger.error("Error monitoring AWX job: {}", e.getMessage());
		}
	}

	/**
	 * =============================== Schedule Patch via AWX
	 * ===============================
	 */
	public String schedulePatch(Long serverId, LocalDateTime when) {
		Server server = serverRepo.findById(serverId)
				.orElseThrow(() -> new IllegalArgumentException("Server not found"));

		// Save local record
		PatchSchedule schedule = scheduleRepo.save(PatchSchedule.builder().serverId(serverId)
				.templateName("PATCH_TEMPLATE").scheduledAt(when).status("SCHEDULED").build());

		try {
			createAwxSchedule(schedule, server, when);
			return "Patch scheduled in AWX at: " + when;
		} catch (Exception e) {
			logger.error("Error creating AWX schedule: {}", e.getMessage());
			return "Schedule saved locally but AWX schedule creation failed: " + e.getMessage();
		}
	}

	private void createAwxSchedule(PatchSchedule schedule, Server server, LocalDateTime when) {
		String scheduleUrl = awxUrl + "/schedules/";
		HttpHeaders headers = createHeaders();

		Map<String, Object> body = new HashMap<>();
		body.put("name", "Patch-" + server.getHostname() + "-" + schedule.getId());
		body.put("unified_job_template", jobTemplateId);
		body.put("enabled", true);
		body.put("extra_data", Map.of("target_host", server.getIp(), "ansible_user", server.getSshUser()));
		body.put("rrule", "DTSTART:" + when.toString().replace("-", "").replace(":", "") + "\nRRULE:FREQ=ONCE");

		HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
		ResponseEntity<Map> response = restTemplate.postForEntity(scheduleUrl, request, Map.class);

		if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
			Object awxScheduleId = response.getBody().get("id");
			schedule.setAwxScheduleId(String.valueOf(awxScheduleId));
			scheduleRepo.save(schedule);
		} else {
			throw new RuntimeException("AWX schedule creation failed: " + response.getStatusCode());
		}
	}

	/**
	 * =============================== Cancel Operations
	 * ===============================
	 */

	// Cancel a running job in AWX
	public String cancelJob(String jobId) {
		try {
			String cancelUrl = awxUrl + "/jobs/" + jobId + "/cancel/";
			HttpHeaders headers = createHeaders();

			ResponseEntity<Map> response = restTemplate.exchange(cancelUrl, HttpMethod.POST, new HttpEntity<>(headers),
					Map.class);

			if (response.getStatusCode().is2xxSuccessful()) {
				logger.info("Job {} cancelled successfully", jobId);
				return "Job " + jobId + " cancelled successfully.";
			} else {
				return "Failed to cancel job. Status: " + response.getStatusCode();
			}
		} catch (Exception e) {
			logger.error("Error cancelling job {}: {}", jobId, e.getMessage());
			return "Error cancelling job: " + e.getMessage();
		}
	}

	// Cancel a scheduled patch in AWX
	public String cancelScheduledPatch(Long scheduleId) {
		PatchSchedule schedule = scheduleRepo.findById(scheduleId)
				.orElseThrow(() -> new IllegalArgumentException("Schedule not found"));

		schedule.setStatus("CANCELLED");
		scheduleRepo.save(schedule);

		if (schedule.getAwxScheduleId() != null) {
			try {
				String deleteUrl = awxUrl + "/schedules/" + schedule.getAwxScheduleId() + "/";
				HttpHeaders headers = createHeaders();
				restTemplate.exchange(deleteUrl, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
				return "Schedule canceled in AWX and updated locally.";
			} catch (Exception e) {
				logger.error("Error canceling AWX schedule: {}", e.getMessage());
				return "Local schedule canceled, but AWX cancel failed: " + e.getMessage();
			}
		}
		return "Local schedule canceled (no AWX ID found).";
	}

	/**
	 * =============================== Utility ===============================
	 */
	public List<PatchLog> getPatchLogs(Long serverId) {
		return logRepo.findByServerId(serverId);
	}

	private HttpHeaders createHeaders() {
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(awxUser, awxPass);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	public Map<String, Object> analyzePatchLogs(Long serverId) {
	    Map<String, Object> response = new HashMap<>();

	    try {
	        List<PatchLog> logs = logRepo.findByServerId(serverId);
	        if (logs.isEmpty()) {
	            response.put("status", "error");
	            response.put("message", "No logs found for server ID: " + serverId);
	            return response;
	        }

	        // Prepare log text
	        StringBuilder logData = new StringBuilder();
	        for (PatchLog log : logs) {
	            logData.append("\n[STATUS: ").append(log.getStatus()).append("]\n")
	                    .append(log.getOutput()).append("\n");
	        }

	        // Build AI prompt
	        String question = "Analyze the following patch logs. Identify key errors, " +
	                "failures, and possible remediation steps:\n" + logData;

	        // Call completion service
	        String aiResponse = completionService.fetchCompletion(question);

	        // Prepare JSON response
	        response.put("status", "success");
	        response.put("serverId", serverId);
	        response.put("totalLogsAnalyzed", logs.size());
	        response.put("aiAnalysis", aiResponse);

	        return response;

	    } catch (Exception e) {
	        logger.error("Error analyzing logs for server {}: {}", serverId, e.getMessage());
	        response.put("status", "error");
	        response.put("message", "Error analyzing logs: " + e.getMessage());
	        return response;
	    }
	}


	/**
	 * =============================== Get Schedule Operations ===============================
	 */

	public PatchSchedule getSchedule(Long scheduleId) {
	    return scheduleRepo.findById(scheduleId)
	            .orElseThrow(() -> new IllegalArgumentException("Schedule not found with ID: " + scheduleId));
	}

	public List<PatchSchedule> getAllSchedules() {
	    return scheduleRepo.findAll();
	}

	public List<PatchSchedule> getSchedulesByServer(Long serverId) {
	    return scheduleRepo.findByServerId(serverId);
	}

	// Optional: Get schedules by status
	public List<PatchSchedule> getSchedulesByStatus(String status) {
	    return scheduleRepo.findByStatus(status);
	}

	// Optional: Get upcoming schedules
	public List<PatchSchedule> getUpcomingSchedules() {
	    return scheduleRepo.findByScheduledAtAfterAndStatus(LocalDateTime.now(), "SCHEDULED");
	}
	
	// Add these methods to PatchService class

	/**
	 * =============================== Get All Logs with Server Details ===============================
	 */
	public List<Map<String, Object>> getAllLogsWithServerDetails() {
	    List<Object[]> results = logRepo.findAllLogsWithServerDetails();
	    return transformLogResults(results);
	}

	/**
	 * =============================== Get Log by ID with Server Details ===============================
	 */
	public Map<String, Object> getLogByIdWithServerDetails(Long logId) {
	    List<Object[]> results = logRepo.findLogByIdWithServerDetails(logId);
	    if (results.isEmpty()) {
	        throw new IllegalArgumentException("Log not found with ID: " + logId);
	    }
	    return transformLogResult(results.get(0));
	}

	/**
	 * =============================== Utility Methods for Transformation ===============================
	 */
	private List<Map<String, Object>> transformLogResults(List<Object[]> results) {
	    List<Map<String, Object>> transformedLogs = new ArrayList<>();
	    for (Object[] result : results) {
	        transformedLogs.add(transformLogResult(result));
	    }
	    return transformedLogs;
	}

	private Map<String, Object> transformLogResult(Object[] result) {
	    PatchLog log = (PatchLog) result[0];
	    String hostname = (String) result[1];
	    String ipAddress = (String) result[2];
	    
	    Map<String, Object> logWithDetails = new HashMap<>();
	    logWithDetails.put("id", log.getId());
	    logWithDetails.put("serverId", log.getServerId());
	    logWithDetails.put("hostname", hostname);
	    logWithDetails.put("ipAddress", ipAddress);
	    logWithDetails.put("patchType", log.getPatchType());
	    logWithDetails.put("status", log.getStatus());
	    logWithDetails.put("output", log.getOutput());
	    logWithDetails.put("createdAt", log.getCreatedAt());
	    
	    return logWithDetails;
	}

	/**
	 * =============================== Get Logs by Server ID with Server Details ===============================
	 */
	public List<Map<String, Object>> getPatchLogsWithServerDetails(Long serverId) {
	    List<PatchLog> logs = logRepo.findByServerId(serverId);
	    Server server = serverRepo.findById(serverId)
	            .orElseThrow(() -> new IllegalArgumentException("Server not found"));
	    
	    List<Map<String, Object>> logsWithDetails = new ArrayList<>();
	    for (PatchLog log : logs) {
	        Map<String, Object> logWithDetails = new HashMap<>();
	        logWithDetails.put("id", log.getId());
	        logWithDetails.put("serverId", log.getServerId());
	        logWithDetails.put("hostname", server.getHostname());
	        logWithDetails.put("ipAddress", server.getIp());
	        logWithDetails.put("patchType", log.getPatchType());
	        logWithDetails.put("status", log.getStatus());
	        logWithDetails.put("output", log.getOutput());
	        logWithDetails.put("createdAt", log.getCreatedAt());
	        logsWithDetails.add(logWithDetails);
	    }
	    return logsWithDetails;
	}
}
