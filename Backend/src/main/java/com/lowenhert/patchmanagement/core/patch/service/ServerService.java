package com.lowenhert.patchmanagement.core.patch.service;

import com.lowenhert.patchmanagement.core.patch.dto.AddServerRequest;
import com.lowenhert.patchmanagement.core.patch.model.PatchCriticality;
import com.lowenhert.patchmanagement.core.patch.model.PatchLog;
import com.lowenhert.patchmanagement.core.patch.model.Server;
import com.lowenhert.patchmanagement.core.patch.model.PatchSchedule; // Add this import
import com.lowenhert.patchmanagement.core.patch.repository.PatchCriticalityRepository;
import com.lowenhert.patchmanagement.core.patch.repository.PatchLogRepository;
import com.lowenhert.patchmanagement.core.patch.repository.ServerRepository;
import com.lowenhert.patchmanagement.core.patch.repository.PatchScheduleRepository; // Add this import

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ServerService {
	private static final Logger logger = LoggerFactory.getLogger(ServerService.class);
	private final ServerRepository serverRepo;
	private final PatchLogRepository patchLogRepo;
	private final PatchCriticalityRepository patchCriticalityRepo;
	private final PatchScheduleRepository patchScheduleRepo; // Add this

	public ServerService(ServerRepository serverRepo, PatchLogRepository patchLogRepo,
			PatchCriticalityRepository patchCriticalityRepo, PatchScheduleRepository patchScheduleRepo) {
		this.serverRepo = serverRepo;
		this.patchLogRepo = patchLogRepo;
		this.patchCriticalityRepo = patchCriticalityRepo;
		this.patchScheduleRepo = patchScheduleRepo; // Add this
	}

	// ✅ Add a new server
	public Server addServer(AddServerRequest request) {
		String logIdentifier = request.getHostname() != null ? request.getHostname() : request.getIp();
		logger.info("Adding server data for host: {}", logIdentifier);

		Server server = Server.builder().hostname(request.getHostname()).ip(request.getIp()).osType(request.getOsType())
				.sshUser(request.getSshUser()).sshPort(request.getSshPort()).ansibleGroup(request.getAnsibleGroup())
				.addedOn(LocalDateTime.now())
				.active(Boolean.TRUE.equals(request.getActive()) || request.getActive() == null).build();

		return serverRepo.save(server);
	}

	// ✅ Get all servers
	public List<Server> listServers() {
		logger.info("Fetching all servers from database");
		return serverRepo.findAll();
	}

	// ✅ Get server count
	public long getServerCount() {
		return serverRepo.count();
	}

	// ✅ Get detailed info (Server + Patch Logs + Patch Criticality)
	public Map<String, Object> getServerDetails(Long serverId) {
		Optional<Server> serverOpt = serverRepo.findById(serverId);
		if (serverOpt.isEmpty()) {
			logger.warn("Server not found for ID: {}", serverId);
			return null;
		}

		Server server = serverOpt.get();
		List<PatchLog> logs = patchLogRepo.findByServerId(serverId);
		List<PatchCriticality> criticalities = patchCriticalityRepo.findByServerId(serverId);

		Map<String, Object> response = new HashMap<>();
		response.put("serverInfo", server);
		response.put("patchLogs", logs);
		response.put("patchCriticality", criticalities);

		return response;
	}

	// ✅ NEW: Get comprehensive compliance dashboard data
	public Map<String, Object> getComplianceDashboardData() {
		logger.info("Generating compliance dashboard data");
		
		Map<String, Object> dashboard = new HashMap<>();
		
		// Server Statistics
		dashboard.put("serverStatistics", getServerStatistics());
		
		// Patch Log Statistics
		dashboard.put("patchLogStatistics", getPatchLogStatistics());
		
		// Patch Schedule Statistics
		dashboard.put("patchScheduleStatistics", getPatchScheduleStatistics());
		
		// Overall Compliance Summary
		dashboard.put("complianceSummary", getComplianceSummary());
		
		return dashboard;
	}

	private Map<String, Object> getServerStatistics() {
		List<Server> allServers = serverRepo.findAll();
		
		// Group servers by SSH User
		Map<String, Long> serversBySshUser = allServers.stream()
				.collect(Collectors.groupingBy(
						Server::getSshUser, 
						Collectors.counting()
				));
		
		// Group servers by OS Type
		Map<String, Long> serversByOsType = allServers.stream()
				.collect(Collectors.groupingBy(
						Server::getOsType, 
						Collectors.counting()
				));
		
		// Group servers by Active status
		Map<String, Long> serversByActiveStatus = allServers.stream()
				.collect(Collectors.groupingBy(
						server -> server.getActive() ? "ACTIVE" : "INACTIVE",
						Collectors.counting()
				));
		
		// Group servers by Ansible Group
		Map<String, Long> serversByAnsibleGroup = allServers.stream()
				.collect(Collectors.groupingBy(
						server -> server.getAnsibleGroup() != null ? server.getAnsibleGroup() : "UNGROUPED",
						Collectors.counting()
				));
		
		Map<String, Object> serverStats = new HashMap<>();
		serverStats.put("totalServers", allServers.size());
		serverStats.put("bySshUser", serversBySshUser);
		serverStats.put("byOsType", serversByOsType);
		serverStats.put("byActiveStatus", serversByActiveStatus);
		serverStats.put("byAnsibleGroup", serversByAnsibleGroup);
		
		return serverStats;
	}

	private Map<String, Object> getPatchLogStatistics() {
		List<PatchLog> allLogs = patchLogRepo.findAll();
		
		// Group patch logs by patch type
		Map<String, Long> logsByPatchType = allLogs.stream()
				.collect(Collectors.groupingBy(
						PatchLog::getPatchType,
						Collectors.counting()
				));
		
		// Group patch logs by status
		Map<String, Long> logsByStatus = allLogs.stream()
				.collect(Collectors.groupingBy(
						PatchLog::getStatus,
						Collectors.counting()
				));
		
		// Group by patch type and status combination
		Map<String, Map<String, Long>> logsByTypeAndStatus = allLogs.stream()
				.collect(Collectors.groupingBy(
						PatchLog::getPatchType,
						Collectors.groupingBy(
								PatchLog::getStatus,
								Collectors.counting()
						)
				));
		
		// Success rate by patch type
		Map<String, Double> successRateByType = new HashMap<>();
		for (String patchType : logsByTypeAndStatus.keySet()) {
			Map<String, Long> statusCounts = logsByTypeAndStatus.get(patchType);
			long total = statusCounts.values().stream().mapToLong(Long::longValue).sum();
			long successful = statusCounts.getOrDefault("SUCCESS", 0L) + statusCounts.getOrDefault("COMPLETED", 0L);
			double successRate = total > 0 ? (double) successful / total * 100 : 0.0;
			successRateByType.put(patchType, Math.round(successRate * 100.0) / 100.0);
		}
		
		Map<String, Object> patchLogStats = new HashMap<>();
		patchLogStats.put("totalPatchLogs", allLogs.size());
		patchLogStats.put("byPatchType", logsByPatchType);
		patchLogStats.put("byStatus", logsByStatus);
		patchLogStats.put("byTypeAndStatus", logsByTypeAndStatus);
		patchLogStats.put("successRateByType", successRateByType);
		
		return patchLogStats;
	}

	private Map<String, Object> getPatchScheduleStatistics() {
		List<PatchSchedule> allSchedules = patchScheduleRepo.findAll();
		
		// Group patch schedules by status
		Map<String, Long> schedulesByStatus = allSchedules.stream()
				.collect(Collectors.groupingBy(
						PatchSchedule::getStatus,
						Collectors.counting()
				));
		
		// Count overdue schedules
		long overdueSchedules = allSchedules.stream()
				.filter(schedule -> "OVERDUE".equals(schedule.getStatus()) || 
								   ("PENDING".equals(schedule.getStatus()) && 
								    schedule.getScheduledAt().isBefore(LocalDateTime.now())))
				.count();
		
		// Count upcoming schedules (next 7 days)
		long upcomingSchedules = allSchedules.stream()
				.filter(schedule -> schedule.getScheduledAt().isAfter(LocalDateTime.now()) &&
								   schedule.getScheduledAt().isBefore(LocalDateTime.now().plusDays(7)))
				.count();
		
		Map<String, Object> scheduleStats = new HashMap<>();
		scheduleStats.put("totalSchedules", allSchedules.size());
		scheduleStats.put("byStatus", schedulesByStatus);
		scheduleStats.put("overdueSchedules", overdueSchedules);
		scheduleStats.put("upcomingSchedules", upcomingSchedules);
		
		return scheduleStats;
	}

	private Map<String, Object> getComplianceSummary() {
		List<Server> allServers = serverRepo.findAll();
		List<PatchLog> allLogs = patchLogRepo.findAll();
		List<PatchSchedule> allSchedules = patchScheduleRepo.findAll();
		
		long totalServers = allServers.size();
		long activeServers = allServers.stream().filter(Server::getActive).count();
		
		// Calculate patch compliance rate
		long totalPatchOperations = allLogs.size();
		long successfulPatches = allLogs.stream()
				.filter(log -> "SUCCESS".equals(log.getStatus()) || "COMPLETED".equals(log.getStatus()))
				.count();
		double patchComplianceRate = totalPatchOperations > 0 ? 
				(double) successfulPatches / totalPatchOperations * 100 : 0.0;
		
		// Calculate schedule compliance rate
		long totalSchedules = allSchedules.size();
		long completedSchedules = allSchedules.stream()
				.filter(schedule -> "COMPLETED".equals(schedule.getStatus()))
				.count();
		double scheduleComplianceRate = totalSchedules > 0 ? 
				(double) completedSchedules / totalSchedules * 100 : 0.0;
		
		// Overall compliance score (weighted average)
		double overallCompliance = 0.0;
		int factors = 0;
		
		if (totalServers > 0) {
			double serverHealth = (double) activeServers / totalServers * 100;
			overallCompliance += serverHealth;
			factors++;
		}
		
		if (totalPatchOperations > 0) {
			overallCompliance += patchComplianceRate;
			factors++;
		}
		
		if (totalSchedules > 0) {
			overallCompliance += scheduleComplianceRate;
			factors++;
		}
		
		overallCompliance = factors > 0 ? overallCompliance / factors : 0.0;
		
		Map<String, Object> complianceSummary = new HashMap<>();
		complianceSummary.put("overallComplianceScore", Math.round(overallCompliance * 100.0) / 100.0);
		complianceSummary.put("patchComplianceRate", Math.round(patchComplianceRate * 100.0) / 100.0);
		complianceSummary.put("scheduleComplianceRate", Math.round(scheduleComplianceRate * 100.0) / 100.0);
		complianceSummary.put("serverHealthScore", Math.round((double) activeServers / totalServers * 100 * 100.0) / 100.0);
		complianceSummary.put("totalActiveServers", activeServers);
		complianceSummary.put("totalServers", totalServers);
		
		return complianceSummary;
	}
}