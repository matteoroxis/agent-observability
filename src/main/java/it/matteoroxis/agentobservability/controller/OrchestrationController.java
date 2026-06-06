package it.matteoroxis.agentobservability.controller;

import it.matteoroxis.agentobservability.dto.request.UserQueryRequest;
import it.matteoroxis.agentobservability.dto.response.OrchestrationResponse;
import it.matteoroxis.agentobservability.service.OrchestratorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrate")
public class OrchestrationController {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationController.class);

    private final OrchestratorService orchestratorService;

    public OrchestrationController(OrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping
    public ResponseEntity<OrchestrationResponse> orchestrate(@RequestBody UserQueryRequest request) {
        log.info("Received orchestration request: {}", request.query());
        OrchestrationResponse response = orchestratorService.orchestrate(request.query());
        return ResponseEntity.ok(response);
    }
}
