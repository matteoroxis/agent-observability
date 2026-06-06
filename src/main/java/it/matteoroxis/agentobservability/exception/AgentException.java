package it.matteoroxis.agentobservability.exception;

public class AgentException extends RuntimeException {

    private final String agentName;

    public AgentException(String agentName, String message) {
        super(message);
        this.agentName = agentName;
    }

    public AgentException(String agentName, String message, Throwable cause) {
        super(message, cause);
        this.agentName = agentName;
    }

    public String agentName() {
        return agentName;
    }
}
